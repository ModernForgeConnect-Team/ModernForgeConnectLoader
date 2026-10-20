param(
    [Parameter(Mandatory=$true)][string]$RuntimeDirectory
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$runtimeRoot = (Resolve-Path -LiteralPath $RuntimeDirectory).Path
$manifestPath = Join-Path $runtimeRoot 'runtime-manifest.json'
$runtimeManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

# 1.7.10 ships only en_US.lang inside the client JAR. Every other vanilla language lives in the
# launcher asset index, so the private runtime gets them as a separate, verified resource pack.
$indexUrl = 'https://launchermeta.mojang.com/v1/packages/1863782e33ce7b584fc45b037325a1964e095d3e/1.7.10.json'
$indexSha1 = '1863782e33ce7b584fc45b037325a1964e095d3e'
$packName = 'minecraft-1.7.10-languages.zip'

function Get-VerifiedBytes([string]$url, [string]$sha1) {
    $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -Uri $url
    $bytes = if ($response.Content -is [byte[]]) { $response.Content } else { [Text.Encoding]::UTF8.GetBytes($response.Content) }
    $hash = [BitConverter]::ToString([Security.Cryptography.SHA1]::Create().ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
    if ($hash -ne $sha1) { throw "Asset checksum mismatch: $url" }
    return ,$bytes
}

$indexBytes = Get-VerifiedBytes $indexUrl $indexSha1
$objects = ([Text.Encoding]::UTF8.GetString($indexBytes) | ConvertFrom-Json).objects
$wanted = @($objects.PSObject.Properties | Where-Object { $_.Name -match '^minecraft/lang/[^/]+\.lang$' -or $_.Name -eq 'pack.mcmeta' } | Sort-Object Name)
if (!($wanted | Where-Object { $_.Name -eq 'minecraft/lang/zh_CN.lang' })) { throw 'Asset index has no zh_CN language' }

$destination = Join-Path $runtimeRoot $packName
$temporary = Join-Path $runtimeRoot ([IO.Path]::GetRandomFileName())
try {
    $stream = [IO.File]::Open($temporary, [IO.FileMode]::CreateNew)
    $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $wanted) {
            $hash = $entry.Value.hash
            $bytes = Get-VerifiedBytes "https://resources.download.minecraft.net/$($hash.Substring(0, 2))/$hash" $hash
            $name = if ($entry.Name -eq 'pack.mcmeta') { 'pack.mcmeta' } else { "assets/$($entry.Name)" }
            $zipEntry = $zip.CreateEntry($name)
            $zipEntry.LastWriteTime = [DateTimeOffset]::new(2014, 6, 26, 0, 0, 0, [TimeSpan]::Zero)
            $out = $zipEntry.Open(); try { $out.Write($bytes, 0, $bytes.Length) } finally { $out.Dispose() }
        }
    } finally { $zip.Dispose(); $stream.Dispose() }
    if (Test-Path -LiteralPath $destination) { [IO.File]::Delete($destination) }
    [IO.File]::Move($temporary, $destination)
} finally { if (Test-Path -LiteralPath $temporary) { [IO.File]::Delete($temporary) } }

$resources = @(@($runtimeManifest.resources) | Where-Object { $_ -and $_.file -ne $packName })
$resources += @{file=$packName; sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()}
$runtimeManifest | Add-Member -NotePropertyName resources -NotePropertyValue $resources -Force
[IO.File]::WriteAllText($manifestPath, ($runtimeManifest | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
Write-Output "Installed legacy language pack: $packName ($($wanted.Count) files)"
