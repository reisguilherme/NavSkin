[CmdletBinding(PositionalBinding = $false)]
param(
    [ValidateSet('Doctor', 'Bootstrap', 'InstallSdk', 'Licenses', 'Gradle', 'Devices', 'Dhu')]
    [string]$Action = 'Doctor',
    [string]$ProjectPath = 'android',
    [string[]]$SdkPackages = @('platform-tools', 'platforms;android-36', 'build-tools;35.0.0', 'extras;google;auto'),
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ToolArguments = @()
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = Join-Path $workspaceRoot '.runtime'
$downloadRoot = Join-Path $runtimeRoot 'android-downloads'
$jdkRoot = Join-Path $runtimeRoot 'jdk-17.0.20.1+1'
$gradleRoot = Join-Path $runtimeRoot 'gradle-8.13'
$sdkRoot = Join-Path $runtimeRoot 'android-sdk'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'

function Assert-WorkspaceRuntimePath([string]$Path) {
    $resolvedPath = [IO.Path]::GetFullPath($Path)
    $allowedRoot = [IO.Path]::GetFullPath($runtimeRoot) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedPath.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Toolchain path is outside the workspace runtime: $resolvedPath"
    }
    return $resolvedPath
}

function Get-VerifiedArchive([string]$Uri, [string]$FileName, [string]$Sha256) {
    $archivePath = Assert-WorkspaceRuntimePath (Join-Path $downloadRoot $FileName)
    if (-not (Test-Path -LiteralPath $archivePath)) {
        Write-Host "Downloading $FileName"
        $savedProgressPreference = $ProgressPreference
        try {
            $ProgressPreference = 'SilentlyContinue'
            Invoke-WebRequest -Uri $Uri -OutFile $archivePath
        } finally {
            $ProgressPreference = $savedProgressPreference
        }
    }
    $actualSha256 = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $Sha256) {
        throw "SHA-256 mismatch for $FileName. Expected $Sha256; received $actualSha256."
    }
    "$Sha256  $FileName" | Set-Content -LiteralPath "$archivePath.sha256" -Encoding ascii
    Write-Host "Verified $FileName ($actualSha256)"
    return $archivePath
}

function Set-WorkspaceToolEnvironment {
    if (-not (Test-Path -LiteralPath (Join-Path $jdkRoot 'bin\java.exe'))) {
        throw 'Portable JDK is missing. Run this script with -Action Bootstrap first.'
    }
    $env:JAVA_HOME = $jdkRoot
    $env:ANDROID_HOME = $sdkRoot
    $env:ANDROID_SDK_ROOT = $sdkRoot
    $env:GRADLE_USER_HOME = Join-Path $runtimeRoot 'gradle-user-home'
    # Windows JDK selectors use AF_UNIX pipes; long inherited sandbox TEMP paths
    # can exceed its socket-name limit. This property affects this tool process only.
    $socketTmp = Join-Path $runtimeRoot 'tmp'
    New-Item -ItemType Directory -Path $socketTmp -Force | Out-Null
    if ($env:JAVA_TOOL_OPTIONS -notmatch 'jdk\.net\.unixdomain\.tmpdir=') {
        $socketOption = '"-Djdk.net.unixdomain.tmpdir=' + $socketTmp + '"'
        $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' ' + $socketOption).Trim()
    }
    $env:PATH = "$jdkRoot\bin;$sdkRoot\platform-tools;$sdkRoot\emulator;$env:PATH"
}

if ($Action -eq 'Bootstrap') {
    New-Item -ItemType Directory -Path $downloadRoot -Force | Out-Null
    $jdkZip = Get-VerifiedArchive `
        'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip' `
        'OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip' `
        'e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0'
    $gradleZip = Get-VerifiedArchive `
        'https://services.gradle.org/distributions/gradle-8.13-bin.zip' `
        'gradle-8.13-bin.zip' `
        '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
    $sdkZip = Get-VerifiedArchive `
        'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' `
        'commandlinetools-win-15859902_latest.zip' `
        '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    if (-not (Test-Path -LiteralPath $jdkRoot)) {
        Expand-Archive -LiteralPath $jdkZip -DestinationPath $runtimeRoot
    }
    if (-not (Test-Path -LiteralPath $gradleRoot)) {
        Expand-Archive -LiteralPath $gradleZip -DestinationPath $runtimeRoot
    }
    if (-not (Test-Path -LiteralPath $sdkManager)) {
        $extractRoot = Assert-WorkspaceRuntimePath (Join-Path $downloadRoot 'commandline-tools-extract')
        $toolsDestination = Assert-WorkspaceRuntimePath (Join-Path $sdkRoot 'cmdline-tools\latest')
        Expand-Archive -LiteralPath $sdkZip -DestinationPath $extractRoot -Force
        New-Item -ItemType Directory -Path (Split-Path -Parent $toolsDestination) -Force | Out-Null
        $toolsSource = Assert-WorkspaceRuntimePath (Join-Path $extractRoot 'cmdline-tools')
        Move-Item -LiteralPath $toolsSource -Destination $toolsDestination
    }
    Write-Host 'Portable tools are ready. SDK packages and their interactive licenses are a separate step.'
}

Set-WorkspaceToolEnvironment

switch ($Action) {
    'Doctor' {
        & (Join-Path $jdkRoot 'bin\java.exe') -version
        & (Join-Path $gradleRoot 'bin\gradle.bat') --version
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host "Android SDK: $sdkRoot"
        Write-Host "Gradle cache: $env:GRADLE_USER_HOME"
        & $sdkManager "--sdk_root=$sdkRoot" --list_installed
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host "ADB installed: $(Test-Path -LiteralPath $adb)"
        Write-Host "Android emulator installed: $(Test-Path -LiteralPath (Join-Path $sdkRoot 'emulator\emulator.exe'))"
        Write-Host "Desktop Head Unit installed: $(Test-Path -LiteralPath (Join-Path $sdkRoot 'extras\google\auto\desktop-head-unit.exe'))"
        $licenseRoot = Join-Path $sdkRoot 'licenses'
        if (Test-Path -LiteralPath $licenseRoot) {
            Write-Host "Recorded SDK license files: $((Get-ChildItem -LiteralPath $licenseRoot -File).Name -join ', ')"
        } else {
            Write-Host 'No SDK license acceptance files exist in this workspace.'
        }
    }
    'InstallSdk' {
        # Deliberately interactive: never pipe answers or write SDK license hashes.
        & $sdkManager "--sdk_root=$sdkRoot" @SdkPackages
        exit $LASTEXITCODE
    }
    'Licenses' {
        # The person running this command reviews and answers Google's prompts.
        & $sdkManager "--sdk_root=$sdkRoot" --licenses
        exit $LASTEXITCODE
    }
    'Gradle' {
        $resolvedProject = if ([IO.Path]::IsPathRooted($ProjectPath)) { $ProjectPath } else { Join-Path $workspaceRoot $ProjectPath }
        if (-not (Test-Path -LiteralPath $resolvedProject -PathType Container)) {
            throw "Android project directory does not exist: $resolvedProject"
        }
        $env:ANDROID_USER_HOME = Join-Path $runtimeRoot 'android-user-home'
        New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME -Force | Out-Null
        [string[]]$gradleArguments = if ($ToolArguments.Count) { $ToolArguments } else { @('assembleDebug') }
        Push-Location -LiteralPath $resolvedProject
        try {
            & (Join-Path $gradleRoot 'bin\gradle.bat') @gradleArguments
            $buildExitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        exit $buildExitCode
    }
    'Devices' {
        if (-not (Test-Path -LiteralPath $adb)) {
            throw 'ADB is missing. Install platform-tools after reviewing the SDK license.'
        }
        # Read-only inventory; no APK is installed and no existing device is changed.
        & $adb devices -l
        exit $LASTEXITCODE
    }
    'Dhu' {
        $dhu = Join-Path $sdkRoot 'extras\google\auto\desktop-head-unit.exe'
        if (-not (Test-Path -LiteralPath $dhu)) {
            throw 'Desktop Head Unit is missing. Install extras;google;auto after reviewing its SDK license.'
        }
        & $dhu @ToolArguments
        exit $LASTEXITCODE
    }
}
