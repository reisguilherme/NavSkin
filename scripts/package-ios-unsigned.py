#!/usr/bin/env python3
"""Validate and package an Xcode-built iPhone app for later local re-signing.

Usage: python3 scripts/package-ios-unsigned.py APP_PATH OUTPUT_IPA
This script neither builds nor signs apps and has no fallback app generator.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import shutil
import stat
import struct
import sys
import tempfile
from xml.parsers.expat import ExpatError
import zipfile


# Format/constants verified in Apple's published headers (not inferred from CPU):
# https://github.com/apple-oss-distributions/xnu/blob/main/EXTERNAL_HEADERS/mach-o/loader.h
# https://github.com/apple-oss-distributions/xnu/blob/main/EXTERNAL_HEADERS/mach-o/fat.h
# https://github.com/apple-oss-distributions/xnu/blob/main/osfmk/mach/machine.h
MH_MAGIC_64 = 0xFEEDFACF
CPU_TYPE_ARM64 = 0x0100000C
MH_EXECUTE = 2
MH_DYLIB = 6
LC_BUILD_VERSION = 0x32
PLATFORM_IOS = 2
FAT_MAGIC = 0xCAFEBABE


def _within(path: Path, directory: Path) -> bool:
    return path == directory or directory in path.parents


def _bundle_entries(app: Path) -> list[Path]:
    """Inspect links before opening any bundle files; never traverse a link."""
    entries = []
    pending = [app]
    while pending:
        path = pending.pop()
        if "\\" in path.name:
            raise ValueError(f"Unsupported backslash in bundle filename: {path.name}")
        mode = path.lstat().st_mode
        entries.append(path)
        if stat.S_ISLNK(mode):
            target = os.readlink(path)
            if Path(target).is_absolute() or "\\" in target:
                raise ValueError(f"Bundle symlink must have a relative target: {path.name}")
            try:
                resolved = path.resolve(strict=True)
            except (OSError, RuntimeError) as error:
                raise ValueError(f"Broken or cyclic bundle symlink: {path.name}") from error
            if not _within(resolved, app):
                raise ValueError(f"Bundle symlink escapes app: {path.name}")
        elif stat.S_ISDIR(mode):
            pending.extend(sorted(path.iterdir(), reverse=True))
        elif not stat.S_ISREG(mode):
            raise ValueError(f"Unsupported special file in app: {path.name}")
    return entries


def _validate_thin_macho(stream, offset: int, size: int, filetype: int, label: str):
    stream.seek(offset)
    header = stream.read(min(size, 32))
    if len(header) != 32 or struct.unpack_from("<I", header)[0] != MH_MAGIC_64:
        raise ValueError(f"{label}: expected a compiled 64-bit Mach-O binary")
    _, cpu, _, actual_type, count, command_bytes, _, _ = struct.unpack("<8I", header)
    if cpu != CPU_TYPE_ARM64:
        raise ValueError(f"{label}: expected ARM64 device architecture")
    if actual_type != filetype:
        expected = "linked executable" if filetype == MH_EXECUTE else "dynamic framework"
        raise ValueError(f"{label}: expected {expected} Mach-O file type")
    if command_bytes > size - 32 or count > command_bytes // 8:
        raise ValueError(f"{label}: truncated Mach-O load commands")
    commands = stream.read(command_bytes)
    if len(commands) != command_bytes:
        raise ValueError(f"{label}: truncated Mach-O load commands")
    cursor = 0
    platforms = []
    for _ in range(count):
        if cursor + 8 > command_bytes:
            raise ValueError(f"{label}: truncated Mach-O load command")
        command, length = struct.unpack_from("<2I", commands, cursor)
        if length < 8 or length % 8 or cursor + length > command_bytes:
            raise ValueError(f"{label}: invalid Mach-O load command size")
        if command == LC_BUILD_VERSION:
            if length < 24:
                raise ValueError(f"{label}: truncated LC_BUILD_VERSION load command")
            platform, _, _, tools = struct.unpack_from("<4I", commands, cursor + 8)
            if 24 + tools * 8 > length:
                raise ValueError(f"{label}: truncated LC_BUILD_VERSION tools")
            platforms.append(platform)
        cursor += length
    if cursor != command_bytes:
        raise ValueError(f"{label}: inconsistent Mach-O load command length")
    if not platforms:
        raise ValueError(f"{label}: LC_BUILD_VERSION is required to verify iOS device platform")
    if any(platform != PLATFORM_IOS for platform in platforms):
        raise ValueError(f"{label}: expected iOS device platform 2; found {platforms}")


def _validate_macho(path: Path, filetype: int):
    if not path.is_file():
        raise ValueError(f"Missing required executable: {path.name}")
    attributes = path.stat()
    if not attributes.st_mode & (stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH):
        raise ValueError(f"{path.name}: required executable permission is missing")
    size = attributes.st_size
    with path.open("rb") as stream:
        header = stream.read(8)
        if len(header) == 8 and struct.unpack_from(">I", header)[0] == FAT_MAGIC:
            count = struct.unpack_from(">I", header, 4)[0]
            if not count or count > (size - 8) // 20:
                raise ValueError(f"{path.name}: invalid universal Mach-O architecture table")
            table = stream.read(count * 20)
            slices = []
            for index in range(count):
                cpu, _, offset, length, alignment = struct.unpack_from(">5I", table, index * 20)
                if cpu != CPU_TYPE_ARM64:
                    raise ValueError(f"{path.name}: universal binary contains a non-ARM64 architecture")
                if (offset < 8 + count * 20 or length < 32 or offset + length > size
                        or alignment > 31 or offset % (1 << alignment)):
                    raise ValueError(f"{path.name}: invalid universal Mach-O slice bounds")
                if any(offset < end and offset + length > start for start, end in slices):
                    raise ValueError(f"{path.name}: overlapping universal Mach-O slices")
                slices.append((offset, offset + length))
            for start, end in slices:
                _validate_thin_macho(stream, start, end - start, filetype, path.name)
        else:
            _validate_thin_macho(stream, 0, size, filetype, path.name)


def _write_bundle(archive: zipfile.ZipFile, app: Path, entries: list[Path]):
    for path in entries:
        mode = path.lstat().st_mode
        name = "Payload/" + path.relative_to(app.parent).as_posix()
        directory = stat.S_ISDIR(mode)
        info = zipfile.ZipInfo(name + ("/" if directory else ""))
        info.create_system = 3
        info.external_attr = (mode & 0xFFFF) << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        if directory:
            info.external_attr |= 0x10
            archive.writestr(info, b"")
        elif stat.S_ISLNK(mode):
            archive.writestr(info, os.fsencode(os.readlink(path)))
        else:
            with path.open("rb") as source, archive.open(info, "w", force_zip64=True) as destination:
                shutil.copyfileobj(source, destination)


def package_app(app: Path, output: Path) -> dict:
    """Write a validated unsigned IPA and adjacent JSON; return public metadata.

    Structural validation cannot establish installation or runtime success. All
    output requires signing/provisioning locally before an iPhone can run it.
    """
    app = Path(app).resolve()
    output = Path(os.path.abspath(output))
    manifest_path = output.with_suffix(".json")
    if not app.is_dir() or app.suffix != ".app":
        raise ValueError("APP_PATH must be an existing Xcode-built .app directory")
    for destination in (output, manifest_path):
        if _within(destination, app) or _within(destination.resolve(), app):
            raise ValueError("IPA and manifest outputs must be outside the input app")
    if output.suffix.lower() != ".ipa":
        raise ValueError("OUTPUT_IPA must have an .ipa extension")

    entries = _bundle_entries(app)
    try:
        with (app / "Info.plist").open("rb") as source:
            info = plistlib.load(source)
    except (OSError, ValueError, plistlib.InvalidFileException, ExpatError) as error:
        raise ValueError("A valid built Info.plist is required") from error
    if not isinstance(info, dict):
        raise ValueError("Info.plist must contain a dictionary")
    platforms = info.get("CFBundleSupportedPlatforms")
    if not isinstance(platforms, list) or "iPhoneOS" not in platforms:
        raise ValueError("Info.plist CFBundleSupportedPlatforms must include iPhoneOS")
    if info.get("DTPlatformName") != "iphoneos":
        raise ValueError("Info.plist DTPlatformName must be iphoneos")
    executable = info.get("CFBundleExecutable")
    if (not isinstance(executable, str) or not executable or executable in (".", "..")
            or "/" in executable or "\\" in executable):
        raise ValueError("Info.plist CFBundleExecutable must name one file inside the app")
    identifier = info.get("CFBundleIdentifier")
    if not isinstance(identifier, str) or not identifier:
        raise ValueError("Info.plist CFBundleIdentifier is required")
    _validate_macho(app / executable, MH_EXECUTE)
    framework = app / "Frameworks/MapLibre.framework/MapLibre"
    if not framework.is_file():
        raise ValueError("Required embedded dynamic Frameworks/MapLibre.framework/MapLibre is missing")
    _validate_macho(framework, MH_DYLIB)

    output.parent.mkdir(parents=True, exist_ok=True)
    temporary_paths = []
    try:
        descriptor, ipa_temp = tempfile.mkstemp(prefix=".gamemap-ipa-", dir=output.parent)
        os.close(descriptor)
        ipa_temp = Path(ipa_temp)
        temporary_paths.append(ipa_temp)
        with zipfile.ZipFile(ipa_temp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            _write_bundle(archive, app, entries)
        digest = hashlib.sha256()
        with ipa_temp.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        manifest = {
            "signing_status": "REQUIRES_RE_SIGNING",
            "direct_install": False,
            "platform": "iPhoneOS",
            "architecture": "arm64",
            "app_bundle": app.name,
            "executable": executable,
            "bundle_identifier": identifier,
            "ipa_file": output.name,
            "sha256": digest.hexdigest(),
            "size_bytes": ipa_temp.stat().st_size,
            "installation_note": "Sign and provision locally before installing; this IPA cannot be installed directly.",
        }
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", prefix=".gamemap-manifest-",
                                         dir=output.parent, delete=False) as destination:
            json_temp = Path(destination.name)
            temporary_paths.append(json_temp)
            json.dump(manifest, destination, indent=2, ensure_ascii=False)
            destination.write("\n")
        os.replace(ipa_temp, output)
        os.replace(json_temp, manifest_path)
        return manifest
    finally:
        for temporary in temporary_paths:
            temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app_path", type=Path)
    parser.add_argument("output_ipa", type=Path)
    args = parser.parse_args()
    try:
        manifest = package_app(args.app_path, args.output_ipa)
    except (ValueError, OSError) as error:
        print(f"IPA packaging failed: {error}", file=sys.stderr)
        return 1
    print(json.dumps(manifest, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
