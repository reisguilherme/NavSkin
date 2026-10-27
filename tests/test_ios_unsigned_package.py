"""Packaging tests only: generated Mach-O headers are NOT runnable iOS apps.

All synthetic bundles and resulting ZIPs live in TemporaryDirectory. These
fixtures must never be published as a GameMap build or installed on a device.
"""

import hashlib
import importlib.util
import json
from pathlib import Path
import plistlib
import stat
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/package-ios-unsigned.py"


def synthetic_macho(*, cpu=0x0100000C, platform=2, filetype=2, command=True):
    """Minimal test-only header + LC_BUILD_VERSION; no executable instructions."""
    commands = struct.pack("<6I", 0x32, 24, platform, 17 << 16, 26 << 16, 0) if command else b""
    return struct.pack("<8I", 0xFEEDFACF, cpu, 0, filetype, int(command), len(commands), 0, 0) + commands


def synthetic_fat_macho(*, platform=2, filetype=2):
    """One-slice universal wrapper, also test-only and not runnable."""
    thin = synthetic_macho(platform=platform, filetype=filetype)
    return struct.pack(">7I", 0xCAFEBABE, 1, 0x0100000C, 0, 28, len(thin), 0) + thin


class UnsignedIPAPackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="gamemap-packaging-test-only-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.root / "GameMap.app"
        self.app.mkdir()
        self.output = self.root / "artifacts/GameMap-unsigned.ipa"
        self.info = {
            "CFBundleExecutable": "GameMap",
            "CFBundleIdentifier": "test.only.synthetic.gamemap",
            "CFBundleSupportedPlatforms": ["iPhoneOS"],
            "DTPlatformName": "iphoneos",
        }
        self.write_info()
        self.executable = self.app / "GameMap"
        self.executable.write_bytes(synthetic_macho())
        self.executable.chmod(0o755)
        self.framework = self.app / "Frameworks/MapLibre.framework/MapLibre"
        self.framework.parent.mkdir(parents=True)
        self.framework.write_bytes(synthetic_macho(filetype=6))
        self.framework.chmod(0o755)
        self.resource = self.app / "Resources/maps/map.json"
        self.resource.parent.mkdir(parents=True)
        self.resource.write_text('{"test_fixture_only":true}\n', encoding="utf-8")
        self.resource.chmod(0o644)

    def write_info(self):
        (self.app / "Info.plist").write_bytes(plistlib.dumps(self.info))

    def module(self):
        self.assertTrue(SCRIPT.is_file(), "Production IPA packager has not been implemented")
        spec = importlib.util.spec_from_file_location("package_ios_unsigned", SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def package(self, output=None):
        return self.module().package_app(self.app, output or self.output)

    def assert_rejected(self, message):
        with self.assertRaisesRegex(ValueError, message):
            self.package()
        self.assertFalse(self.output.exists(), "Rejected input must not publish an IPA")
        self.assertFalse(self.output.with_suffix(".json").exists())

    def test_packages_actual_bundle_under_payload_and_records_resigning_requirement(self):
        manifest = self.package()
        with zipfile.ZipFile(self.output) as archive:
            self.assertIn("Payload/GameMap.app/GameMap", archive.namelist())
            self.assertIn("Payload/GameMap.app/Info.plist", archive.namelist())
            self.assertIn("Payload/GameMap.app/Frameworks/MapLibre.framework/MapLibre", archive.namelist())
            self.assertEqual(archive.read("Payload/GameMap.app/Resources/maps/map.json"), self.resource.read_bytes())
            self.assertTrue(all(name.startswith("Payload/") for name in archive.namelist()))
        persisted = json.loads(self.output.with_suffix(".json").read_text(encoding="utf-8"))
        self.assertEqual(manifest, persisted)
        self.assertEqual(manifest["signing_status"], "REQUIRES_RE_SIGNING")
        self.assertIs(manifest["direct_install"], False)
        self.assertEqual(manifest["platform"], "iPhoneOS")
        self.assertEqual(manifest["architecture"], "arm64")
        self.assertEqual(manifest["bundle_identifier"], "test.only.synthetic.gamemap")
        self.assertEqual(manifest["sha256"], hashlib.sha256(self.output.read_bytes()).hexdigest())
        self.assertEqual(manifest["size_bytes"], self.output.stat().st_size)
        self.assertNotIn(str(self.root), json.dumps(manifest))

    def test_preserves_executable_resource_and_empty_directory_permissions(self):
        empty = self.app / "Resources/empty"
        empty.mkdir(mode=0o750)
        self.package()
        with zipfile.ZipFile(self.output) as archive:
            executable = archive.getinfo("Payload/GameMap.app/GameMap")
            resource = archive.getinfo("Payload/GameMap.app/Resources/maps/map.json")
            directory = archive.getinfo("Payload/GameMap.app/Resources/empty/")
            self.assertEqual(executable.create_system, 3)
            self.assertEqual(stat.S_IMODE(executable.external_attr >> 16), 0o755)
            self.assertEqual(stat.S_IMODE(resource.external_attr >> 16), 0o644)
            self.assertEqual(stat.S_IMODE(directory.external_attr >> 16), 0o750)
            self.assertTrue(stat.S_ISDIR(directory.external_attr >> 16))

    def test_preserves_safe_relative_symlink_without_dereferencing_it(self):
        link = self.resource.parent / "current.json"
        link.symlink_to("map.json")
        self.package()
        with zipfile.ZipFile(self.output) as archive:
            entry = archive.getinfo("Payload/GameMap.app/Resources/maps/current.json")
            self.assertTrue(stat.S_ISLNK(entry.external_attr >> 16))
            self.assertEqual(archive.read(entry), b"map.json")

    def test_preserves_safe_directory_symlink_without_recursive_duplication(self):
        (self.app / "Resources/current").symlink_to("maps", target_is_directory=True)
        self.package()
        with zipfile.ZipFile(self.output) as archive:
            entry = archive.getinfo("Payload/GameMap.app/Resources/current")
            self.assertTrue(stat.S_ISLNK(entry.external_attr >> 16))
            self.assertEqual(archive.read(entry), b"maps")
            self.assertNotIn("Payload/GameMap.app/Resources/current/map.json", archive.namelist())

    def test_rejects_arm64_simulator_even_when_plist_claims_iphoneos(self):
        self.executable.write_bytes(synthetic_macho(platform=7))
        self.assert_rejected("iOS device|platform")

    def test_rejects_arm64_maccatalyst(self):
        self.executable.write_bytes(synthetic_macho(platform=6))
        self.assert_rejected("iOS device|platform")

    def test_accepts_device_arm64_inside_universal_wrapper(self):
        self.framework.write_bytes(synthetic_fat_macho(filetype=6))
        self.assertEqual(self.package()["architecture"], "arm64")

    def test_rejects_simulator_platform_inside_universal_wrapper(self):
        self.framework.write_bytes(synthetic_fat_macho(platform=7, filetype=6))
        self.assert_rejected("iOS device|platform")

    def test_rejects_x86_64_even_when_platform_claims_ios(self):
        self.executable.write_bytes(synthetic_macho(cpu=0x01000007))
        self.assert_rejected("ARM64|arm64")

    def test_rejects_missing_platform_load_command(self):
        self.executable.write_bytes(synthetic_macho(command=False))
        self.assert_rejected("LC_BUILD_VERSION")

    def test_rejects_non_macho_placeholder_executable(self):
        self.executable.write_text("not a compiled app", encoding="utf-8")
        self.assert_rejected("Mach-O")

    def test_rejects_truncated_load_commands(self):
        self.executable.write_bytes(synthetic_macho()[:-4])
        self.assert_rejected("truncated|load command")

    def test_rejects_invalid_load_command_size(self):
        data = bytearray(synthetic_macho())
        struct.pack_into("<I", data, 36, 0)
        self.executable.write_bytes(data)
        self.assert_rejected("load command")

    def test_rejects_object_file_instead_of_linked_executable(self):
        self.executable.write_bytes(synthetic_macho(filetype=1))
        self.assert_rejected("executable|file type")

    def test_rejects_wrong_supported_platforms(self):
        self.info["CFBundleSupportedPlatforms"] = ["iPhoneSimulator"]
        self.write_info()
        self.assert_rejected("iPhoneOS|platform")

    def test_rejects_wrong_dtplatformname(self):
        self.info["DTPlatformName"] = "iphonesimulator"
        self.write_info()
        self.assert_rejected("iphoneos|platform")

    def test_rejects_missing_info_plist(self):
        (self.app / "Info.plist").unlink()
        self.assert_rejected("Info.plist")

    def test_rejects_malformed_info_plist_as_validation_error(self):
        (self.app / "Info.plist").write_text('<?xml version="1.0"?><plist><dict>', encoding="utf-8")
        self.assert_rejected("Info.plist")

    def test_rejects_missing_executable(self):
        self.executable.unlink()
        self.assert_rejected("executable|GameMap")

    def test_rejects_compiled_binary_without_executable_permission(self):
        self.executable.chmod(0o644)
        self.assert_rejected("executable|permission")

    def test_rejects_executable_path_traversal_from_info_plist(self):
        self.info["CFBundleExecutable"] = "../outside"
        self.write_info()
        self.assert_rejected("CFBundleExecutable")

    def test_rejects_missing_embedded_maplibre_framework(self):
        self.framework.unlink()
        self.assert_rejected("MapLibre")

    def test_rejects_simulator_maplibre_embedded_in_device_app(self):
        self.framework.write_bytes(synthetic_macho(platform=7, filetype=6))
        self.assert_rejected("iOS device|platform")

    def test_rejects_static_or_placeholder_maplibre(self):
        self.framework.write_bytes(synthetic_macho(filetype=1))
        self.assert_rejected("dynamic|file type")

    def test_rejects_symlink_escaping_app(self):
        (self.root / "outside-secret.txt").write_text("do not package", encoding="utf-8")
        (self.app / "Resources/leak").symlink_to("../../outside-secret.txt")
        self.assert_rejected("symlink")

    def test_rejects_absolute_symlink_even_when_target_is_internal(self):
        (self.app / "Resources/absolute").symlink_to(self.resource)
        self.assert_rejected("symlink")

    def test_rejects_broken_or_cyclic_symlink(self):
        for target in ("missing", "loop"):
            with self.subTest(target=target):
                link = self.app / "loop"
                link.symlink_to(target)
                try:
                    self.assert_rejected("symlink")
                finally:
                    link.unlink()

    def test_rejects_output_or_manifest_inside_app(self):
        with self.assertRaisesRegex(ValueError, "inside.*app|outside.*app"):
            self.package(self.app / "GameMap-unsigned.ipa")
        self.assertFalse((self.app / "GameMap-unsigned.ipa").exists())

    def test_rejects_output_directory_alias_into_app(self):
        (self.root / "app-alias").symlink_to(self.app, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "inside.*app|outside.*app"):
            self.package(self.root / "app-alias/unsigned.ipa")

    def test_rejected_bundle_does_not_replace_existing_artifact(self):
        self.output.parent.mkdir(parents=True)
        self.output.write_bytes(b"pre-existing artifact sentinel, test only")
        self.executable.write_bytes(synthetic_macho(platform=7))
        with self.assertRaises(ValueError):
            self.package()
        self.assertEqual(self.output.read_bytes(), b"pre-existing artifact sentinel, test only")

    def test_cli_packages_or_reports_validation_error_without_traceback(self):
        self.module()
        valid = subprocess.run([sys.executable, str(SCRIPT), str(self.app), str(self.output)], capture_output=True, text=True)
        self.assertEqual(valid.returncode, 0, valid.stderr)
        self.assertEqual(json.loads(valid.stdout)["signing_status"], "REQUIRES_RE_SIGNING")
        self.executable.write_bytes(synthetic_macho(platform=7))
        invalid = subprocess.run([sys.executable, str(SCRIPT), str(self.app), str(self.root / "bad.ipa")], capture_output=True, text=True)
        self.assertNotEqual(invalid.returncode, 0)
        self.assertIn("platform", invalid.stderr.lower())
        self.assertNotIn("Traceback", invalid.stderr)
        self.assertFalse((self.root / "bad.ipa").exists())


if __name__ == "__main__":
    unittest.main()
