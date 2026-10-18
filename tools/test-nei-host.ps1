param(
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [switch]$World
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$gameDirectory = Join-Path $workspaceRoot 'build/nei-smoke/game'
$runtime = Join-Path $gameDirectory 'mfcl/runtime/1.7.10'
if (!(Test-Path -LiteralPath (Join-Path $runtime 'minecraft-forge-1.7.10-srg.jar'))) { throw 'Run tools/prepare-legacy-runtime.ps1 first' }
$mode = if ($World) { '-PmfclNeiWorldSmokeTest' } else { '-PmfclNeiUiSmokeTest' }
$marker = if ($World) { 'MFCL_NEI_WORLD_SMOKE_PASS' } else { 'MFCL_NEI_UI_SMOKE_PASS' }
$log = Join-Path $workspaceRoot 'build/nei-investigation/host-acceptance.log'
$previousJavaHome = $env:JAVA_HOME
Push-Location $workspaceRoot
try {
    $env:JAVA_HOME = $JavaHome
    & .\gradlew.bat runClient '-PmfclRunDir=build/nei-smoke/game' $mode --console=plain 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw "Forge host failed: $LASTEXITCODE" }
    $output = [IO.File]::ReadAllText($log)
    if (!$output.Contains($marker) -or $output.Contains('MFCL_NEI_UI_SMOKE_FAIL') -or $output.Contains('MFCL modern recipe projection failed')) { throw "Required NEI acceptance marker missing or recipe sync failed; see $log" }
    Write-Output "Verified $marker"
} finally { $env:JAVA_HOME = $previousJavaHome; Pop-Location }
