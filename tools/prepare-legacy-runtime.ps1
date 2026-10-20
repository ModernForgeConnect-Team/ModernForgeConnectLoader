# Prepares the private Minecraft 1.7.10 / Forge 10.13.4.1614 runtime and installs it into a game directory.
# Only -GameDirectory is needed for a normal installation:
#   .\tools\prepare-legacy-runtime.ps1 -GameDirectory 'C:/my-game' -Boot
# -NeiJar additionally prepares the NEI reference fixture and installs NEI/CodeChicken into that game.
# -Force rebuilds a runtime that already passes MFCL's startup check.
# Players do not need this script: MFCL prepares the same runtime automatically on first launch.
param(
    [string]$NeiJar,
    [string]$JavaHome,
    [string]$GameDirectory,
    [switch]$Boot,
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (!$JavaHome) {
    # Prefer an explicit JAVA_HOME, then a Gradle-provisioned JDK 17.
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $gradleJdks = Join-Path $HOME '.gradle/jdks'
    if (Test-Path -LiteralPath $gradleJdks) {
        $candidates += Get-ChildItem -LiteralPath $gradleJdks -Directory -Filter '*17*' | ForEach-Object {
            $_.FullName; Get-ChildItem -LiteralPath $_.FullName -Directory | ForEach-Object FullName }
    }
    foreach ($candidate in $candidates) {
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath (Join-Path $candidate 'bin/java.exe')) -and (Test-Path -LiteralPath $release) -and
            ((Get-Content -LiteralPath $release -Raw) -match 'JAVA_VERSION="17')) { $JavaHome = $candidate; break }
    }
    if (!$JavaHome) { throw 'No JDK 17 found; pass -JavaHome or set JAVA_HOME' }
    Write-Output "Using JDK 17: $JavaHome"
}
if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'JavaHome must point to a Java 17 JDK' }
$spec = Get-Content -LiteralPath (Join-Path $workspaceRoot 'docs/nei-reference-inputs.json') -Raw | ConvertFrom-Json
if ($NeiJar) { & (Join-Path $PSScriptRoot 'prepare-nei-probe.ps1') -NeiJar $NeiJar -JavaHome $JavaHome }
if (!$GameDirectory) { $GameDirectory = Join-Path $workspaceRoot 'build/nei-smoke/game' }
$GameDirectory = [IO.Path]::GetFullPath($GameDirectory)
# Verified downloads are kept here between runs; the mod itself uses a temporary cache next to the runtime.
$downloadRoot = Join-Path $workspaceRoot 'build/mfcl-runtime-downloads'
$previousJavaHome = $env:JAVA_HOME
Push-Location $workspaceRoot
try {
    $env:JAVA_HOME = $JavaHome
    $installedRuntime = Join-Path $GameDirectory 'mfcl/runtime/1.7.10'
    $installedMods = Join-Path $GameDirectory 'mods/1.7.10'
    # The same RuntimeInstaller that MFCL runs on first launch: pinned downloads, Forge patches, SRG mapping, manifest,
    # language libraries and language assets, installed atomically.
    $installArgs = @('installLegacyRuntime', "-PlegacyGameDir=$GameDirectory", "-PlegacyDownloadDir=$downloadRoot", '--console=plain')
    if ($Force) { $installArgs += '-PlegacyForceInstall' }
    & .\gradlew.bat @installArgs
    if ($LASTEXITCODE -ne 0) { throw "Runtime preparation failed: $LASTEXITCODE" }
    # Development tasks (bootLegacyRuntime, prepareLegacyRuntime, smoke tests) default to these build folders.
    $inputRoot = Join-Path $workspaceRoot 'build/research-round2/artifacts'
    $runtimeRoot = Join-Path $workspaceRoot 'build/nei-investigation/runtime'
    [IO.Directory]::CreateDirectory($inputRoot) | Out-Null
    [IO.Directory]::CreateDirectory($runtimeRoot) | Out-Null
    foreach ($name in @('minecraft-1.7.10-version.json', 'minecraft-client-1.7.10.jar', 'joined-1.7.10.srg')) {
        $source = Join-Path $downloadRoot $name
        if (Test-Path -LiteralPath $source) { [IO.File]::Copy($source, (Join-Path $inputRoot $name), $true) }
    }
    $forgeJar = Join-Path $downloadRoot 'forge-1.7.10-10.13.4.1614-1.7.10-universal.jar'
    if (Test-Path -LiteralPath $forgeJar) { [IO.File]::Copy($forgeJar, (Join-Path $runtimeRoot ([IO.Path]::GetFileName($forgeJar))), $true) }
    foreach ($runtimeInput in Get-ChildItem -LiteralPath $installedRuntime -Filter '*.jar' -File) {
        [IO.File]::Copy($runtimeInput.FullName, (Join-Path $runtimeRoot $runtimeInput.Name), $true)
    }
    if ($NeiJar) { foreach ($name in @($spec.nei.file, 'CodeChickenCore-1.7.10-1.0.7.47-universal.jar', 'CodeChickenLib-1.7.10-1.1.3.138-universal.jar')) {
        $source = Join-Path (Join-Path $workspaceRoot 'build/nei-smoke/game/mods/1.7.10') $name
        $destination = Join-Path $installedMods $name
        if (!(Test-Path -LiteralPath $destination)) { [IO.File]::Copy($source, $destination, $false) }
        elseif ((Get-FileHash -LiteralPath $destination).Hash -ne (Get-FileHash -LiteralPath $source).Hash) { throw "Existing mod differs: $destination" }
    } }
    Write-Output "Installed private Java 17 runtime: $installedRuntime"
    Write-Output "Put original 1.7.10 mods and their dependencies in: $installedMods"
    if ($Boot) {
        & .\gradlew.bat bootLegacyRuntime "-PlegacyGameDir=$GameDirectory" "-PlegacyRuntimeDir=$installedRuntime" --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Legacy FML boot failed; see $GameDirectory/logs/mfcl/runtime-1.7.10.json" }
    }
} finally { $env:JAVA_HOME = $previousJavaHome; Pop-Location }
