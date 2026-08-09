param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$JdkHome = 'C:\Program Files\Android\openjdk\jdk-21.0.8',
    [string]$CmdlineToolsZipUrl = 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip',
    [string]$CmdlineToolsZipPath = (Join-Path $env:TEMP 'commandlinetools-win-15859902_latest.zip'),
    [string]$ExtractDir = (Join-Path $env:TEMP 'android-cmdline-tools')
)

$ErrorActionPreference = 'Stop'

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message"
}

function Assert-Exists {
    param(
        [string]$PathToCheck,
        [string]$Description
    )

    if (-not (Test-Path -LiteralPath $PathToCheck)) {
        throw "$Description not found: $PathToCheck"
    }
}

Write-Step "Checking prerequisites"
Assert-Exists -PathToCheck $RepoRoot -Description 'Repository root'
Assert-Exists -PathToCheck $JdkHome -Description 'JDK home'

$JavaExe = Join-Path $JdkHome 'bin\java.exe'
Assert-Exists -PathToCheck $JavaExe -Description 'Java executable'

Write-Host "Using repo root: $RepoRoot"
Write-Host "Using SDK root:  $SdkRoot"
Write-Host "Using JDK home:  $JdkHome"

Write-Step "Creating Android SDK directories"
New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null
New-Item -ItemType Directory -Force -Path $ExtractDir | Out-Null

Write-Step "Downloading Android command-line tools"
Invoke-WebRequest -Uri $CmdlineToolsZipUrl -OutFile $CmdlineToolsZipPath

Write-Step "Installing command-line tools"
Expand-Archive -Path $CmdlineToolsZipPath -DestinationPath $ExtractDir -Force

$LatestCmdlineTools = Join-Path $SdkRoot 'cmdline-tools\latest'
if (Test-Path -LiteralPath $LatestCmdlineTools) {
    Remove-Item -LiteralPath $LatestCmdlineTools -Recurse -Force
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LatestCmdlineTools) | Out-Null
Move-Item -Path (Join-Path $ExtractDir 'cmdline-tools') -Destination $LatestCmdlineTools

$SdkManager = Join-Path $LatestCmdlineTools 'bin\sdkmanager.bat'
Assert-Exists -PathToCheck $SdkManager -Description 'sdkmanager'

Write-Step "Writing local.properties"
$LocalPropertiesPath = Join-Path $RepoRoot 'local.properties'
$SdkPathForGradle = $SdkRoot -replace '\\', '/'
@"
sdk.dir=$SdkPathForGradle
"@ | Set-Content -Encoding ASCII -LiteralPath $LocalPropertiesPath

Write-Step "Accepting Android SDK licenses"
$env:JAVA_HOME = $JdkHome
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:Path = "$JdkHome\bin;$SdkRoot\platform-tools;$LatestCmdlineTools\bin;$env:Path"

1..80 | ForEach-Object { 'y' } | & $SdkManager --sdk_root="$SdkRoot" --licenses

Write-Step "Installing required Android SDK packages"
& $SdkManager --sdk_root="$SdkRoot" `
    'platform-tools' `
    'platforms;android-37.0' `
    'build-tools;36.0.0'

Write-Host ""
Write-Host "Setup complete."
Write-Host "The project is configured to use the local Android SDK at: $SdkRoot"
Write-Host "The repo-local SDK pointer is: $LocalPropertiesPath"



