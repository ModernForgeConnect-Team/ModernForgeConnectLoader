param(
    [Parameter(Mandatory=$true)][string]$NeiJar,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [switch]$RunChecks
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$spec = Get-Content -LiteralPath (Join-Path $workspaceRoot 'docs/nei-reference-inputs.json') -Raw | ConvertFrom-Json
$supportRoot = Join-Path $workspaceRoot 'build/nei-investigation'
$gameRoot = Join-Path $workspaceRoot 'build/nei-smoke/game'
$modsRoot = Join-Path $gameRoot 'mods/1.7.10'
[System.IO.Directory]::CreateDirectory($supportRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($modsRoot) | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'JavaHome must point to a Java 17 JDK' }
if ((Get-FileHash -LiteralPath $NeiJar -Algorithm SHA256).Hash -ne $spec.nei.sha256) { throw 'NEI JAR differs from the selected 1.0.5.120 reference' }

function Copy-VerifiedInput([string]$sourcePath, [string]$destinationPath, [string]$expectedHash) {
    if (Test-Path -LiteralPath $destinationPath) {
        if ((Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash -ne $expectedHash) { throw "Existing probe input differs: $destinationPath" }
    } else { [System.IO.File]::Copy($sourcePath, $destinationPath, $false) }
}

foreach ($dependency in $spec.dependencies) {
    $dependencyRoot = Join-Path $supportRoot $dependency.directory
    [System.IO.Directory]::CreateDirectory($dependencyRoot) | Out-Null
    $target = Join-Path $dependencyRoot $dependency.file
    if (!(Test-Path -LiteralPath $target)) {
        $temporaryDownload = Join-Path $dependencyRoot ([System.IO.Path]::GetRandomFileName())
        try {
            Invoke-WebRequest $dependency.url -OutFile $temporaryDownload -TimeoutSec 90
            if ((Get-FileHash -LiteralPath $temporaryDownload -Algorithm SHA256).Hash -ne $dependency.sha256) { throw "Dependency hash differs: $($dependency.file)" }
            [System.IO.File]::Move($temporaryDownload, $target)
        } finally { if (Test-Path -LiteralPath $temporaryDownload) { [System.IO.File]::Delete($temporaryDownload) } }
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $dependency.sha256) { throw "Dependency hash differs: $target" }
    if ($dependency.directory -eq '') { Copy-VerifiedInput $target (Join-Path $modsRoot $dependency.file) $dependency.sha256 }
}
Copy-VerifiedInput $NeiJar (Join-Path $modsRoot $spec.nei.file) $spec.nei.sha256
Write-Output "Prepared isolated NEI reference inputs: $gameRoot"
if ($RunChecks) {
    $previousJavaHome = $env:JAVA_HOME
    Push-Location $workspaceRoot
    try {
        $env:JAVA_HOME = $JavaHome
        & (Join-Path $workspaceRoot 'gradlew.bat') test build inspectLegacyMods probeLegacyCoremod "-PneiJar=$NeiJar" "-PlegacySupportDir=$supportRoot" "-PlegacyGameDir=$gameRoot" '-PlegacyModId=NotEnoughItems' --console=plain
        if ($LASTEXITCODE -ne 0) { throw "NEI checks failed: $LASTEXITCODE" }
    } finally { $env:JAVA_HOME = $previousJavaHome; Pop-Location }
}
