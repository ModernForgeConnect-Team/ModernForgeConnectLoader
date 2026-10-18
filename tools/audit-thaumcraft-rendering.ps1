param(
    [string]$SourceDirectory = 'build/thaumcraft-investigation/decompiled',
    [string]$OriginalJar = 'run/mods/1.7.10/Thaumcraft-1.7.10-4.2.3.5.jar',
    [string]$OutputPath = 'build/thaumcraft-investigation/source-render-audit.json'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourceRoot = [IO.Path]::GetFullPath((Join-Path $root $SourceDirectory))
$spec = Get-Content -LiteralPath (Join-Path $root 'docs/thaumcraft-reference-inputs.json') -Raw | ConvertFrom-Json
$hash = (Get-FileHash -LiteralPath (Join-Path $root $OriginalJar) -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne $spec.thaumcraft.sha256) { throw 'Thaumcraft JAR differs from the selected 4.2.3.5 reference' }
if (!(Test-Path -LiteralPath (Join-Path $sourceRoot 'thaumcraft/client/ClientProxy.java'))) { throw 'Decompiled source tree is missing' }
$patterns = [ordered]@{
    currentPlayer = 'field_71439_g|field_71451_h'
    perPassOrNbtColor = 'func_82790_a\('
    multiplePasses = 'getRenderPasses\(|func_77623_v\('
    contextSensitiveIcon = 'getIcon\(ItemStack.+EntityPlayer'
    blendState = 'glBlendFunc\('
    depthWrite = 'glDepthMask\('
    textureMatrix = 'glMatrixMode\(5890\)'
    generatedTextureCoordinates = 'glTexGen'
    animatedGeometry = 'renderAnimated|field_70173_aa|System.nanoTime\('
    decompilerFailure = 'couldn.t be decompiled|\$VF:|Exception occurred while decompiling'
}
$inventory = foreach ($file in Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java') {
    $relative = [IO.Path]::GetRelativePath($sourceRoot, $file.FullName).Replace('\','/')
    $lines = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
    $category = switch -Regex ($relative) {
        '^thaumcraft/client/renderers/([^/]+)/' { 'renderer/' + $Matches[1]; break }
        '^thaumcraft/client/(fx|gui|lib)/' { 'client/' + $Matches[1]; break }
        '^thaumcraft/common/(items|blocks)/' { 'semantics/' + $Matches[1]; break }
        default { 'other' }
    }
    $features = [ordered]@{}
    foreach ($pattern in $patterns.GetEnumerator()) {
        $matchesAt = @(for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -match $pattern.Value) { $i + 1 } })
        if ($matchesAt.Count) { $features[$pattern.Key] = $matchesAt }
    }
    [pscustomobject][ordered]@{ source = $relative; category = $category; lines = $lines.Count; features = $features }
}
$proxy = Get-Content -LiteralPath (Join-Path $sourceRoot 'thaumcraft/client/ClientProxy.java') -Raw
$summary = [ordered]@{
    target = 'Thaumcraft 1.7.10-4.2.3.5'
    jarSha256 = $hash
    sourceKind = 'User-provided original JAR, locally decompiled with Vineflower 1.10.1; not an official source release'
    scope = 'Static inventory and feature scan plus targeted source/bytecode review; not proof of full rendering compatibility'
    files = @($inventory).Count
    categories = @($inventory | Group-Object category | Sort-Object Name | ForEach-Object { [ordered]@{ category = $_.Name; files = $_.Count } })
    registrations = [ordered]@{
        item = [regex]::Matches($proxy, 'MinecraftForgeClient\.registerItemRenderer\(').Count
        block = [regex]::Matches($proxy, 'this\.registerBlockRenderer\(').Count
        tile = [regex]::Matches($proxy, 'this\.registerTileEntitySpecialRenderer\(').Count
        entity = [regex]::Matches($proxy, 'RenderingRegistry\.registerEntityRenderingHandler\(').Count
    }
    inventory = @($inventory)
}
$destination = [IO.Path]::GetFullPath((Join-Path $root $OutputPath))
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $destination -Encoding UTF8
[pscustomobject]$summary | Select-Object target, files, registrations, categories | ConvertTo-Json -Depth 5
