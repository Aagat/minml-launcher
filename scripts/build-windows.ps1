param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$SdkRoot = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    [string]$JdkHome = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
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
Assert-Exists -PathToCheck (Join-Path $SdkRoot 'platform-tools\adb.exe') -Description 'adb'

$JavaExe = Join-Path $JdkHome 'bin\java.exe'
Assert-Exists -PathToCheck $JavaExe -Description 'Java executable'

$env:JAVA_HOME = $JdkHome
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:Path = "$JdkHome\bin;$SdkRoot\platform-tools;$SdkRoot\cmdline-tools\latest\bin;$env:Path"

Write-Step "Verifying the local toolchain"
& $JavaExe -version
& (Join-Path $SdkRoot 'platform-tools\adb.exe') version

Write-Step "Building the project"
Set-Location $RepoRoot
& .\gradlew.bat clean assembleDebug lintDebug testDebugUnitTest
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

Write-Host ""
Write-Host "Build complete."


