param(
    [string]$SmokeReport = 'build/crash-render-smoke/game/logs/mfcl/content-1.7.10-smoke.json',
    [string]$OutputDirectory = 'docs'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$proofPath = [IO.Path]::GetFullPath((Join-Path $root $SmokeReport))
$proof = Get-Content -LiteralPath $proofPath -Raw | ConvertFrom-Json
if ($proof.status -ne 'PASS' -or !$proof.worldSpecialRenderSupport) { throw 'A passing client report with world-render capabilities is required' }
$gatePath = Join-Path $root 'src/main/java/github/com/gengyoubo/mfcl/content/ModernContentBridge.java'
$gate = Get-Content -LiteralPath $gatePath -Raw
if (!$gate.Contains('return entry.renderType() == 0 || active != null && active.blockRendering.supports(entry.key(), metadata);')) {
    throw 'Placement predicate has changed; update this audit before reporting counts'
}
if ((Get-Item -LiteralPath $gatePath).LastWriteTimeUtc -gt (Get-Item -LiteralPath $proofPath).LastWriteTimeUtc) {
    throw 'Placement code is newer than the client report; run a fresh content smoke test'
}
$manifestPath = Join-Path $proof.generatedPack 'mfcl-content-manifest.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$language = Get-Content -LiteralPath (Join-Path $proof.generatedPack 'assets/mfcl/lang/zh_cn.json') -Raw | ConvertFrom-Json -AsHashtable
$blocks = @{}; $items = @{}
foreach ($block in $manifest.blocks) { $blocks[$block.key] = $block }
foreach ($item in $manifest.items) { $items[$item.key] = $item }
function CanPlace($block, [int]$metadata) {
    return $block.renderType -eq 0 -or $proof.worldSpecialRenderSupport.PSObject.Properties[$block.key].Value -contains $metadata
}
function VariantName($item, [int]$metadata) {
    $variant = $item.variants | Where-Object metadata -EQ $metadata | Select-Object -First 1
    if (!$variant) { return "$($item.key)@$metadata" }
    if ($language[$variant.nameKey]) { return $language[$variant.nameKey] }
    return $variant.nameKey
}
function Reason([int]$renderType) {
    switch ($renderType) {
        -1 { return '没有普通方块网格，需接通方块实体专用渲染/放置' }
        1 { return '植物交叉面及生长/附着放置尚未桥接' }
        7 { return '门的朝向、上下两半及专用放置尚未桥接' }
        10 { return '楼梯形状、朝向和碰撞尚未桥接' }
        31 { return '原木侧面/端面及轴向元数据尚未桥接' }
        42 { return '旧流体世界渲染及放置尚未桥接' }
        43 { return '咒波气体世界渲染/放置尚未桥接' }
        default { return '自定义世界渲染未通过当前支持探测，放置被拦截' }
    }
}
$declared = @(foreach ($item in $manifest.items) {
    if (!$item.blockKey) { continue }
    $block = $blocks[$item.blockKey]
    foreach ($variant in $item.variants) {
        [pscustomobject]@{ blockKey=$block.key; itemKey=$item.key; metadata=[int]$variant.metadata;
            name=(VariantName $item $variant.metadata); renderType=[int]$block.renderType;
            tileEntity=[bool]$block.tileEntity; allowedByBridge=(CanPlace $block $variant.metadata) }
    }
})
$creative = @(foreach ($tab in $manifest.creativeTabs) {
    foreach ($stack in $tab.contents) {
        $item = $items[$stack.key]
        if (!$item -or !$item.blockKey) { continue }
        $block = $blocks[$item.blockKey]
        [pscustomobject]@{ blockKey=$block.key; itemKey=$item.key; metadata=[int]$stack.metadata;
            tag=$stack.tag; name=(VariantName $item $stack.metadata); allowedByBridge=(CanPlace $block $stack.metadata) }
    }
})
$creative = @($creative | Sort-Object itemKey,metadata,tag -Unique)
$creativeVariants = @($creative | Sort-Object blockKey,metadata -Unique)
$creativeBlocked = @($creativeVariants | Where-Object { !$_.allowedByBridge })
$groups = @(foreach ($group in ($declared | Where-Object { !$_.allowedByBridge } | Group-Object blockKey)) {
    $key = $group.Name
    $inCreative = @($creativeBlocked | Where-Object blockKey -EQ $key)
    [pscustomobject]@{ blockKey=$key; renderType=$blocks[$key].renderType;
        declaredBlockedVariants=$group.Count; creativeBlockedVariants=$inCreative.Count;
        creativeBlockedStacks=@($creative | Where-Object { $_.blockKey -eq $key -and !$_.allowedByBridge }).Count;
        reason=(Reason $blocks[$key].renderType); variants=@($group.Group | Select-Object metadata,name) }
})
# These source-confirmed Items place blocks through useOn/onItemUseFirst rather than ItemBlock.
# Mutations caused by tools, rituals, world generation and entities are outside ordinary placement counts.
$customPlacers = @(
    @{key='Thaumcraft:ItemArcaneDoor'; meta=0; target='Thaumcraft:blockArcaneDoor'; source='common/blocks/ItemArcaneDoor.java'},
    @{key='Thaumcraft:ItemManaBean'; meta=0; target='Thaumcraft:blockManaPod'; source='common/items/ItemManaBean.java'},
    @{key='Thaumcraft:ItemResource'; meta=1; target='Thaumcraft:blockAiry@1'; source='common/items/ItemResource.java'},
    @{key='Thaumcraft:ItemEldritchObject'; meta=4; target='Thaumcraft:blockEldritch@0,1,2'; source='common/items/ItemEldritchObject.java'},
    @{key='Thaumcraft:BlockJarFilledItem'; meta=0; target='Thaumcraft:blockJar'; source='common/blocks/ItemJarFilled.java'},
    @{key='Thaumcraft:BlockJarNodeItem'; meta=0; target='Thaumcraft:blockJar'; source='common/blocks/ItemJarNode.java'}
)
$extra = @(foreach ($placer in $customPlacers) {
    $item = $items[$placer.key]; if (!$item -or $item.blockKey) { continue }
    $entries = @($manifest.creativeTabs.contents | Where-Object { $_.key -eq $placer.key -and $_.metadata -eq $placer.meta } | Sort-Object key,metadata,tag -Unique)
    [pscustomobject]@{ itemKey=$placer.key; metadata=$placer.meta; name=(VariantName $item $placer.meta);
        target=$placer.target; creativeEntries=$entries.Count;
        source="build/thaumcraft-investigation/decompiled/thaumcraft/$($placer.source)";
        reason='当前 MirroredItem 未覆写 useOn，原版专用放置回调未执行' }
})
$summary = [ordered]@{
    registeredBlockTypes=$manifest.blocks.Count
    registeredBlockedBlockTypes=$groups.Count
    declaredBlockItemVariants=$declared.Count
    declaredBlockedBlockItemVariants=@($declared | Where-Object { !$_.allowedByBridge }).Count
    creativeBlockTypes=@($creativeVariants | Sort-Object blockKey -Unique).Count
    creativeBlockedBlockTypes=@($creativeBlocked | Sort-Object blockKey -Unique).Count
    creativeBlockItemVariants=$creativeVariants.Count
    creativeBlockedBlockItemVariants=$creativeBlocked.Count
    creativeBlockItemStacks=$creative.Count
    creativeBlockedBlockItemStacks=@($creative | Where-Object { !$_.allowedByBridge }).Count
    additionalSourceConfirmedCustomPlacerTypes=$extra.Count
    additionalVisibleCustomPlacerEntries=[int]($extra | Measure-Object creativeEntries -Sum).Sum
}
$report = [ordered]@{
    date=(Get-Date -Format 'yyyy-MM-dd'); target='Thaumcraft 1.7.10-4.2.3.5 / Forge 1.20.1';
    method='Current canPlace predicate + frozen runtime declarations + original creative stacks; source audit for custom Items. No repeated click test of blocked entries.'
    smokeReport=$proofPath; manifest=$manifestPath; manifestSha256=(Get-FileHash $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
    summary=$summary; worldSpecialRenderSupport=$proof.worldSpecialRenderSupport
    blockedBlockTypes=$groups; creativeVariants=$creativeVariants; additionalCustomPlacers=$extra
}
$output = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
New-Item -ItemType Directory -Path $output -Force | Out-Null
$report | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath (Join-Path $output 'block-placement-audit.json') -Encoding utf8
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# 神秘时代 4 方块放置调查')
$lines.Add('')
$lines.Add("日期：$($report.date)。统计依据是当前放置拦截代码和最近通过客户端验收的注册清单；没有把每个被拦截条目重复点击一次。")
$lines.Add('')
$lines.Add("- 注册表共 **$($summary.registeredBlockTypes) 类方块**，其中 **$($summary.registeredBlockedBlockTypes) 类**仍被放置桥接拦截。")
$lines.Add("- 创造栏实际可见 **$($summary.creativeBlockTypes) 类方块 / $($summary.creativeBlockItemVariants) 个元数据变体**，其中 **$($summary.creativeBlockedBlockTypes) 类 / $($summary.creativeBlockedBlockItemVariants) 个变体**不能放置。")
$lines.Add("- 单独计算颜色/NBT：创造栏 **$($summary.creativeBlockItemStacks) 个方块物品条目**里有 **$($summary.creativeBlockedBlockItemStacks) 个**被拦截；相对元数据计数多出的 16 项来自彩色旗帜。")
$lines.Add('- “允许放置”表示通过当前桥接判定，不代表全部朝向、碰撞、交互、机器运行及生成行为均已兼容。非创造栏技术方块、未声明元数据及专用放置物品分开列出，避免混算。')
$lines.Add('')
$lines.Add('## 创造栏不能放置的方块')
$lines.Add('')
$lines.Add('| 方块注册名 | 元数据变体数 | 含 NBT 条目数 | 名称（元数据） | 原因 |')
$lines.Add('| --- | ---: | ---: | --- | --- |')
foreach ($group in $groups | Where-Object creativeBlockedVariants -GT 0) {
    $names = ($creativeBlocked | Where-Object blockKey -EQ $group.blockKey | ForEach-Object { "$($_.name)（$($_.metadata)）" }) -join '、'
    $lines.Add("| ``$($group.blockKey)`` | $($group.creativeBlockedVariants) | $($group.creativeBlockedStacks) | $names | $($group.reason) |")
}
$lines.Add('')
$lines.Add('## 未在创造栏展示的其他被拦截方块')
$lines.Add('')
foreach ($group in $groups | Where-Object creativeBlockedVariants -EQ 0) { $lines.Add("- ``$($group.blockKey)``：$($group.reason)。") }
$lines.Add('')
$lines.Add('## 原版专用物品放置入口')
$lines.Add('')
$lines.Add("这些不是 BlockItem，不计入上面的 $($summary.creativeBlockedBlockItemVariants) 个变体。当前注册为普通 MirroredItem，没有转发原版 useOn / onItemUseFirst。原包源码确认至少还有以下 $($extra.Count) 类入口，其中 $($summary.additionalVisibleCustomPlacerEntries) 个条目出现在当前创造栏；另外两个是装满的罐子。")
$lines.Add('')
$lines.Add('| 名称 | 物品 | 创造栏条目数 | 原版放置目标 |')
$lines.Add('| --- | --- | ---: | --- |')
foreach ($entry in $extra) { $lines.Add("| $($entry.name) | ``$($entry.itemKey)@$($entry.metadata)`` | $($entry.creativeEntries) | ``$($entry.target)`` |") }
$lines.Add('')
$lines.Add('## 已允许的特殊方块')
$lines.Add('')
$lines.Add('守卫者玻璃及两种琥珀方块（`Thaumcraft:blockCosmeticOpaque@0,1,2`）已通过世界渲染支持探测；其他 renderType 非 0 的变体仍被拦截。截图的风之魔晶 `Thaumcraft:blockCrystal@0` 属于被拦截的 7 种魔晶之一。')
$lines.Add('')
$lines.Add('完整元数据、统计口径和原版专用放置源文件见 [JSON 清单](block-placement-audit.json)。重新统计：`./tools/audit-block-placement.ps1`。')
$lines | Set-Content -LiteralPath (Join-Path $output 'block-placement-audit.md') -Encoding utf8
$summary | ConvertTo-Json
