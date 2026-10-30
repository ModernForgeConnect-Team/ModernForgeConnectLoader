# 银树随机刻与节点显示

验证日期：2026-10-30。输入：Minecraft 1.20.1 / Forge 47.4.28 / Java 17，原版 Thaumcraft 4.2.3.5 与 Baubles 1.0.1.10。

## 修复

旧方块的 `Block.getTickRandomly`（SRG `func_149653_t`）此前没有映射到现代方块属性。银树树苗虽然能通过原始 `growSilverTree` 方法长成树，也能创建纯净节点，但现代随机刻调度器不会选择它，因此种下后等待不会自然生长。

`LegacyContent.Block` 现在保留原始随机刻声明，`ModernContentBridge` 按此声明启用 `Properties.randomTicks()`。现代 `BlockBehaviour.randomTick` 会调用已有的 `tick` 桥接，由事务执行原始 `updateTick`。未声明随机刻的方块保持关闭；没有给所有旧方块强行增加随机刻，也没有绕过 TC4 的成长几率、光照和空间要求。

节点显示另有一个缺口：旧 TESR 的观察者有装备快照，却没有可靠的当前手持物品快照。魔导透镜的显示判断可能依赖第一人称物品渲染之前是否更新过旧玩家；隐藏界面时可稳定复现只显示裸眼节点。`LegacyBlockEntityRenderer` 现在通过 `LegacyTileRendering.view` 显式提供当前手持物品，`LegacyPlayerInventory.held` 更新或清空旧玩家的选中槽，保留其他槽与相等物品的对象身份。切换成没有旧版对应物的现代物品也会清除先前的透镜。

显示条件仍由 TC4 原始 `TileNodeRenderer` 判断：手持魔导透镜并朝向节点，或装备揭示之护目镜时，节点光效不受前方方块深度遮挡；裸眼的微弱光效仍受遮挡。

可见不等于可以隔着原木吸取。法杖仍受方块射线遮挡，并曾存在客户端吞掉原木右键的另一个问题；操作方法、修复及充能实测见 [银树节点的法杖吸取](wand-node-charging.md)。

## 客户端验收

新增 `-PmfclSilverwoodSmokeTest`，创建独立超平坦创造世界，在高处泥土平台种植八棵银树树苗。仅将测试世界的 `randomTickSpeed` 调高到 256，以缩短等待时间；不直接调用成长方法或原始更新回调。找到自然长成的节点后将测试世界随机刻设为 0，固定观察场景。

最终一次完整客户端验收通过：

- 45 个 TC4 方块的现代随机刻状态与原始声明一致：16 个启用、29 个关闭。
- 在 621 个服务器刻后观测到银树长成，并在 `(0, 106, 0)` 生成原始 `TileNode`，类型为 `PURE`。这是单次加速调度样本，不表示正常游戏中的生长时间或节点概率。
- 客户端收到 171 字节原始节点 NBT，含纯净类型与非空灵气数据。
- 裸眼：原始渲染器输出 1 个图元 / 4 个顶点，开启深度测试。
- 手持魔导透镜、未戴护目镜：输出 4 个图元 / 16 个顶点，节点光效关闭深度测试，可见被树叶、树干挡住的节点。隐藏界面时仍通过。
- 切换成原版 1.7.10 没有的下界合金剑后，节点不再透视，验证没有残留的旧透镜状态。
- 揭示之护目镜：输出 4 个图元 / 16 个顶点，同样可透过方块观察节点。
- 使用创造模式玩家的实际物品放置入口放置 `Thaumcraft:blockAiry@0`，结果 `CONSUME`。原始放置回调初始化了 `NORMAL` 节点与 56 点总灵气，客户端同样产生 4 个图元 / 16 个顶点。
- 检查实际客户端截图，确认自然生成节点和创造模式放置节点的光效可见；相关节点渲染与随机刻路径未出现错误。

同一隔离输出的 `test build` 通过，6 项单元测试全部执行且无失败、无跳过；生成 `build/silverwood-fix/output/libs/mfcl-0.2.11-dev2.jar`。原有工作区版本设置保持不变。

本次不覆盖完整服务器重启、全部节点类型、腐化净化及魔法森林扩散，也不把此次通过视为所有 TC4 世界生成均通过。测试日志另有宏伟之木宝箱世界生成的 `HostInventoryTile` 转换到 `TileEntityChest` 错误，发生在其他区块；它不在本次修复范围。

## 重现与产物

在 `build/silverwood-fix/game/mods/1.7.10/` 放入原始 TC4 与 Baubles JAR，使用现有私有运行时：

```powershell
.\gradlew.bat --offline --project-cache-dir build/silverwood-fix/cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/silverwood-fix/output' `
  runClient '-PmfclRunDir=build/silverwood-fix/game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' -PmfclSilverwoodSmokeTest
```

每次创建新的测试存档，完成后关闭自己的客户端。报告：`build/silverwood-fix/game/logs/mfcl/silverwood-smoke.json`。截图位于同一游戏目录的 `screenshots/`：

- `silverwood-node-bare.png`
- `silverwood-node-thaumometer.png`
- `silverwood-node-goggles.png`
- `creative-node-goggles.png`

首次透镜失败的报告保存在 `build/silverwood-fix/thaumometer-before-fix.json`，修复后完整启动日志为 `build/silverwood-fix/final-client-launch.log`。
