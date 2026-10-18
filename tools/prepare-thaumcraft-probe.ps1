param(
    [Parameter(Mandatory=$true)][string]$ThaumcraftJar,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$BaublesJar,
    [string]$LegacyRuntimeDir,
    [switch]$RunChecks
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$spec = Get-Content -LiteralPath (Join-Path $workspaceRoot 'docs/thaumcraft-reference-inputs.json') -Raw | ConvertFrom-Json
$gameRoot = Join-Path $workspaceRoot 'build/thaumcraft-smoke/game'
$modsRoot = Join-Path $gameRoot 'mods/1.7.10'
[IO.Directory]::CreateDirectory($modsRoot) | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'JavaHome must point to a Java 17 JDK' }
if ((Get-FileHash -LiteralPath $ThaumcraftJar -Algorithm SHA256).Hash -ne $spec.thaumcraft.sha256) { throw 'Thaumcraft JAR differs from the selected 1.7.10-4.2.3.5 reference' }

function Copy-VerifiedInput([string]$sourcePath, [string]$destinationPath, [string]$expectedHash) {
    if (Test-Path -LiteralPath $destinationPath) {
        if ((Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash -ne $expectedHash) { throw "Existing probe input differs: $destinationPath" }
    } else { [IO.File]::Copy($sourcePath, $destinationPath, $false) }
}

foreach ($dependency in $spec.dependencies) {
    $target = Join-Path $modsRoot $dependency.file
    if ($BaublesJar) {
        if ((Get-FileHash -LiteralPath $BaublesJar -Algorithm SHA256).Hash -ne $dependency.sha256) { throw "Baubles JAR differs: $BaublesJar" }
        if (!(Test-Path -LiteralPath $target)) { [IO.File]::Copy($BaublesJar, $target, $false) }
    }
    if (!(Test-Path -LiteralPath $target)) {
        $temporaryDownload = Join-Path $modsRoot ([IO.Path]::GetRandomFileName())
        try {
            Invoke-WebRequest $dependency.url -OutFile $temporaryDownload -TimeoutSec 90
            if ((Get-FileHash -LiteralPath $temporaryDownload -Algorithm SHA256).Hash -ne $dependency.sha256) { throw "Dependency hash differs: $($dependency.file)" }
            [IO.File]::Move($temporaryDownload, $target)
        } finally { if (Test-Path -LiteralPath $temporaryDownload) { [IO.File]::Delete($temporaryDownload) } }
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $dependency.sha256) { throw "Dependency hash differs: $target" }
}
Copy-VerifiedInput $ThaumcraftJar (Join-Path $modsRoot $spec.thaumcraft.file) $spec.thaumcraft.sha256
Write-Output "Prepared isolated Thaumcraft reference inputs: $gameRoot"
if ($RunChecks) {
    if (!$LegacyRuntimeDir) { $LegacyRuntimeDir = Join-Path $workspaceRoot 'run/mfcl/runtime/1.7.10' }
    $LegacyRuntimeDir = [IO.Path]::GetFullPath($LegacyRuntimeDir)
    if (!(Test-Path -LiteralPath (Join-Path $LegacyRuntimeDir 'minecraft-forge-1.7.10-srg.jar'))) { throw "Prepared Forge 1.7.10 runtime missing: $LegacyRuntimeDir" }
    $report = Join-Path $gameRoot 'logs/mfcl/content-1.7.10-smoke.json'
    Remove-Item -LiteralPath $report -ErrorAction SilentlyContinue
    $investigationRoot = Join-Path $workspaceRoot 'build/thaumcraft-investigation'
    [IO.Directory]::CreateDirectory($investigationRoot) | Out-Null
    $log = Join-Path $investigationRoot 'host-acceptance.log'
    $previousJavaHome = $env:JAVA_HOME
    $previousPreference = $ErrorActionPreference
    Push-Location $workspaceRoot
    try {
        $env:JAVA_HOME = $JavaHome
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat runClient "-PmfclRunDir=$gameRoot" "-PlegacyRuntimeDir=$LegacyRuntimeDir" -PmfclContentSmokeTest --console=plain 2>&1 | Tee-Object -FilePath $log
        $exitCode = $LASTEXITCODE
    } finally { $env:JAVA_HOME = $previousJavaHome; $ErrorActionPreference = $previousPreference; Pop-Location }
    if ($exitCode -ne 0) { throw "Thaumcraft checks failed: $exitCode" }
    if (!(Test-Path -LiteralPath $report)) { throw "Content smoke report missing: $report" }
    if ((Get-Content -LiteralPath $report -Raw | ConvertFrom-Json).status -ne 'PASS') { throw "Content smoke did not pass: $report" }
    Write-Output 'Verified MFCL_CONTENT_WORLD_SMOKE_PASS'
}
