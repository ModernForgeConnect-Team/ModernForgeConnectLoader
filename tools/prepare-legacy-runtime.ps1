param(
    [Parameter(Mandatory=$true)][string]$NeiJar,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [switch]$Boot
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
& (Join-Path $PSScriptRoot 'prepare-nei-probe.ps1') -NeiJar $NeiJar -JavaHome $JavaHome
$inputRoot = Join-Path $workspaceRoot 'build/research-round2/artifacts'
$runtimeRoot = Join-Path $workspaceRoot 'build/nei-investigation/runtime'
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
    if ($Boot) {
        & .\gradlew.bat bootLegacyRuntime --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Legacy FML boot failed; see build/nei-smoke/game/logs/mfcl/runtime-1.7.10.json" }
    }
} finally { $env:JAVA_HOME = $previousJavaHome; Pop-Location }
