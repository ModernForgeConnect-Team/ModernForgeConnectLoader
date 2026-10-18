param(
    [Parameter(Mandatory=$true)][string]$NeiJar,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [string]$GameDirectory,
    [switch]$Boot
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
& (Join-Path $PSScriptRoot 'prepare-nei-probe.ps1') -NeiJar $NeiJar -JavaHome $JavaHome
$inputRoot = Join-Path $workspaceRoot 'build/research-round2/artifacts'
$runtimeRoot = Join-Path $workspaceRoot 'build/nei-investigation/runtime'
if (!$GameDirectory) { $GameDirectory = Join-Path $workspaceRoot 'build/nei-smoke/game' }
$GameDirectory = [IO.Path]::GetFullPath($GameDirectory)
[IO.Directory]::CreateDirectory($inputRoot) | Out-Null

function Get-Verified([string]$url, [string]$target, [string]$algorithm, [string]$hash) {
    if (!(Test-Path -LiteralPath $target)) {
        $temporary = Join-Path ([IO.Path]::GetDirectoryName($target)) ([IO.Path]::GetRandomFileName())
        try {
            Invoke-WebRequest $url -OutFile $temporary -TimeoutSec 120
            if ((Get-FileHash -LiteralPath $temporary -Algorithm $algorithm).Hash -ne $hash) { throw "Downloaded input hash mismatch: $url" }
            [IO.File]::Move($temporary, $target)
        } finally { if (Test-Path -LiteralPath $temporary) { [IO.File]::Delete($temporary) } }
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm $algorithm).Hash -ne $hash) { throw "Cached input hash mismatch: $target" }
}
$metadataPath = Join-Path $inputRoot 'minecraft-1.7.10-version.json'
Get-Verified 'https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json' $metadataPath 'SHA256' '96b4a83b9bd90041170b6f0bda98d8201af52ce778b1202272cb161c54658fcd'
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
Get-Verified $metadata.downloads.client.url (Join-Path $inputRoot 'minecraft-client-1.7.10.jar') 'SHA1' $metadata.downloads.client.sha1
foreach ($library in $metadata.libraries) {
    # Forge upgrades Guava and commons-lang; Log4j uses the fixed modern version from the reference specification.
    if (!$library.downloads.artifact -or $library.name -match 'guava|log4j|commons-lang3') { continue }
    $artifact = $library.downloads.artifact
    Get-Verified $artifact.url (Join-Path $runtimeRoot ([IO.Path]::GetFileName($artifact.path))) 'SHA1' $artifact.sha1
}
$mappingZip = Join-Path $inputRoot 'mcp-1.7.10-srg.zip'
# The complete mapping file is checked independently after extraction.
if (!(Test-Path -LiteralPath $mappingZip)) {
    Invoke-WebRequest 'https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.7.10/mcp-1.7.10-srg.zip' -OutFile $mappingZip -TimeoutSec 120
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$mappingPath = Join-Path $inputRoot 'joined-1.7.10.srg'
if (!(Test-Path -LiteralPath $mappingPath)) {
    $zip = [IO.Compression.ZipFile]::OpenRead($mappingZip)
    try {
        $entry = $zip.GetEntry('joined.srg')
        if (!$entry) { throw 'MCP archive has no joined.srg' }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $mappingPath, $false)
    } finally { $zip.Dispose() }
}
if ((Get-FileHash -LiteralPath $mappingPath -Algorithm SHA256).Hash -ne '43d4376110b2638213d3096f80f8395c076eb05b58d6c0dbd22c0d999b37a357') { throw 'MCP mappings differ from the pinned reference' }
$previousJavaHome = $env:JAVA_HOME
Push-Location $workspaceRoot
try {
    $env:JAVA_HOME = $JavaHome
    & .\gradlew.bat prepareLegacyRuntime --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Runtime preparation failed: $LASTEXITCODE" }
    $installedRuntime = Join-Path $GameDirectory 'mfcl/runtime/1.7.10'
    $installedMods = Join-Path $GameDirectory 'mods/1.7.10'
    [IO.Directory]::CreateDirectory($installedRuntime) | Out-Null
    [IO.Directory]::CreateDirectory($installedMods) | Out-Null
    $allowedLibraries = [Collections.Generic.HashSet[string]]::new()
    $allowedLibraries.Add('minecraft-forge-1.7.10-srg.jar') | Out-Null
    $reference = Get-Content -LiteralPath (Join-Path $workspaceRoot 'docs/nei-reference-inputs.json') -Raw | ConvertFrom-Json
    foreach ($dependency in $reference.dependencies) { if ($dependency.directory -eq 'runtime') { $allowedLibraries.Add($dependency.file) | Out-Null } }
    foreach ($library in $metadata.libraries) {
        if ($library.downloads.artifact -and $library.name -notmatch 'guava|log4j|commons-lang3') { $allowedLibraries.Add([IO.Path]::GetFileName($library.downloads.artifact.path)) | Out-Null }
    }
    $installedInputs = @()
    foreach ($runtimeInput in Get-ChildItem -LiteralPath $runtimeRoot -Filter '*.jar' -File) {
        if (!$allowedLibraries.Contains($runtimeInput.Name) -or $runtimeInput.Name -match '^forge-1.7.10-') { continue }
        $destination = Join-Path $installedRuntime $runtimeInput.Name
        if (!(Test-Path -LiteralPath $destination) -or (Get-FileHash -LiteralPath $destination).Hash -ne (Get-FileHash -LiteralPath $runtimeInput.FullName).Hash) {
            [IO.File]::Copy($runtimeInput.FullName, $destination, $true)
        }
        $installedInputs += @{file=$runtimeInput.Name; sha256=(Get-FileHash -LiteralPath $destination).Hash.ToLowerInvariant()}
    }
    [IO.File]::WriteAllText((Join-Path $installedRuntime 'runtime-manifest.json'), (@{minecraft='1.7.10'; forge='10.13.4.1614'; inputs=$installedInputs} | ConvertTo-Json -Depth 5))
    foreach ($name in @($reference.nei.file, 'CodeChickenCore-1.7.10-1.0.7.47-universal.jar', 'CodeChickenLib-1.7.10-1.1.3.138-universal.jar')) {
        $source = Join-Path (Join-Path $workspaceRoot 'build/nei-smoke/game/mods/1.7.10') $name
        $destination = Join-Path $installedMods $name
        if (!(Test-Path -LiteralPath $destination)) { [IO.File]::Copy($source, $destination, $false) }
        elseif ((Get-FileHash -LiteralPath $destination).Hash -ne (Get-FileHash -LiteralPath $source).Hash) { throw "Existing mod differs: $destination" }
    }
    Write-Output "Installed private Java 17 runtime: $installedRuntime"
    if ($Boot) {
        & .\gradlew.bat bootLegacyRuntime "-PlegacyGameDir=$GameDirectory" "-PlegacyRuntimeDir=$installedRuntime" --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Legacy FML boot failed; see build/nei-smoke/game/logs/mfcl/runtime-1.7.10.json" }
    }
} finally { $env:JAVA_HOME = $previousJavaHome; Pop-Location }
