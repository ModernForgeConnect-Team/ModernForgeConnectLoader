param(
    [Parameter(Mandatory=$true)][string]$RuntimeDirectory,
    [Parameter(Mandatory=$true)][string]$ForgeJar
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$runtimeRoot = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
$manifestPath = Join-Path $runtimeRoot 'runtime-manifest.json'
$runtimeManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$forgeZip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ForgeJar).Path)
try {
    $versionEntry = $forgeZip.GetEntry('version.json')
    if (!$versionEntry) { throw 'Forge JAR has no version.json' }
    $reader = [IO.StreamReader]::new($versionEntry.Open())
    try { $forgeVersion = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
} finally { $forgeZip.Dispose() }

# Forge's Scala/Typesafe runtime is separate from the vanilla Minecraft library list.
# Use Forge's original versions and pinned canonical Maven artifacts. Some historical
# Forge checksums refer to repacked distributions; the canonical Scala modules also
# moved from org.scala-lang to org.scala-lang.modules without changing their version.
$pins = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../docs/legacy-language-inputs.json') -Raw | ConvertFrom-Json
$inputs = @($runtimeManifest.inputs)
foreach ($library in $forgeVersion.libraries | Where-Object { $_.name -match '^(org\.scala-lang(?:\.plugins)?|com\.typesafe(?:\.akka)?):' }) {
    $pin = @($pins.dependencies | Where-Object { $_.coordinate -eq $library.name })
    if ($pin.Count -ne 1) { throw "Unpinned Forge library: $($library.name)" }
    $pin = $pin[0]
    $fileName = $pin.file
    $destination = Join-Path $runtimeRoot $fileName
    if (!(Test-Path -LiteralPath $destination)) {
        $temporary = Join-Path $runtimeRoot ([IO.Path]::GetRandomFileName())
        try {
            Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -Uri $pin.url -OutFile $temporary
            if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $pin.sha256) { throw "Forge library checksum mismatch: $fileName" }
            [IO.File]::Move($temporary, $destination)
        } finally { if (Test-Path -LiteralPath $temporary) { [IO.File]::Delete($temporary) } }
    }
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $pin.sha256) { throw "Cached Forge library checksum mismatch: $fileName" }
    $inputs = @($inputs | Where-Object { $_.file -ne $fileName })
    $inputs += @{file=$fileName; sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()}
    Write-Output "Verified Forge runtime library: $fileName"
}
$runtimeManifest.inputs = $inputs
[IO.File]::WriteAllText($manifestPath, ($runtimeManifest | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
