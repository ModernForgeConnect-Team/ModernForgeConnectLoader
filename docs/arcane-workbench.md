# 奥术工作台合成与法杖保存

解构工作台的原始 `DECONSTRUCTOR` 奥术配方消耗 20 点熵灵气（`perditio`）。铁杖端的基础消耗倍率为 1.1；无减耗装备时实际需要 22 点。只有 20 点熵灵气的铁帽木杖会显示原版 `Insufficient vis` 提示，充满至 25 点后可以合成，合成后剩余 3 点。研究台的要素研究点不用于支付奥术合成的灵气消耗。

## 界面数值与扭曲影响

原始 `GuiArcaneWorkbench` 读取配方基础消耗，再乘以法杖的 `getConsumptionModifier(..., true)` 后绘制。额外消耗会显示，不是只显示配方基础值。现代客户端的实际文本捕获、截图与原始客户端/服务端消耗计算逐项对照：

| 条件（无减耗装备） | 显示的熵需求 | 法杖存量 | 成品判定 |
| --- | --- | --- | --- |
| 未放法杖 | 20 | — | 无成品 |
| 铁帽木杖 | 22 | 20 | 不足 |
| 金帽木杖 | 20 | 20 | 可合成 |
| 铁帽木杖 | 22 | 25 | 可合成，实际扣 22 |

扭曲值本身不参与原始 `WandManager.getTotalVisDiscount` 的计算，但扭曲事件产生的普通/传染性灵气衰竭会加入消耗惩罚。每级增加基础消耗的 10%，两种衰竭并存时取较高等级，不将两种惩罚相加。因此金帽杖合成此配方时，衰竭 I 需要 22 点，传染性衰竭 IV 需要 28 点；铁帽杖分别需要 24 和 30 点。

此前桥接中的旧菜单玩家没有随现代玩家刷新效果。在同一个工作台窗口内给持有 20 点熵的金帽杖玩家施加衰竭 I 后，实际捕获的界面文本和服务端缓存成品仍停在 20/可合成，未计入应有的 22 点需求。原始需求算法没有被修改。修复为：服务端菜单回调前刷新效果；效果增减或等级变化时，由 TC4 适配器调用原始容器的合成重算；客户端绘制、更新和输入前也刷新界面所持玩家的效果。单纯剩余时间递减不会每 tick 触发服务端合成重算。

原始 `TileMagicWorkbench` 的库存共有 11 格，索引 10 是法杖槽，保存和加载都会遍历该格。宿主使用 `LegacyBlockEntity` 保存完整的原始 TileEntity NBT。

`mfclWandAudit` 的 `ArcaneWorkbenchHostAcceptance` 使用原始 TC4/Baubles，在可丢弃的世界中补充以下检查：

- 提供 `DECONSTRUCTOR` 研究和配方材料，通过现代客户端槽位包逐步放入原始奥术工作台。
- 对照熵灵气为 20 和 25 点的铁帽木杖，检查不足提示、可合成状态、成品取出、材料消耗和恰好一次的 22 点灵气扣除。
- 对照无杖、铁帽杖和金帽杖，记录真实绘制文本、客户端与服务端的基础需求/倍率/灵气量，并保存截图。
- 注入 30 永久、30 普通、10 临时扭曲而不施加衰竭，检查需求保持不变；在菜单打开期间新增衰竭 I、换成传染性衰竭 IV、两种衰竭并存、移除及自然到期，检查界面与服务端成本同步。检查不足时点击成品不取走物品或消耗材料，以及带衰竭关闭重开。
- 关闭再打开工作台，核对法杖仍在槽位内。
- 保持菜单打开时退出世界，再加载同一个世界并登录，核对服务端库存、客户端法杖槽、完整法杖 NBT 和剩余灵气，并通过客户端槽位包取回法杖。

研究状态和法杖灵气由测试夹具提供；材料放置、合成取出、退出和重进使用宿主正常入口。这不覆盖其他模组改写的配方或独立服务器断线重连。

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-17'
.\gradlew.bat --offline --project-cache-dir build/arcane-confirm-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/arcane-confirm-validation' `
  runClient '-PmfclRunDir=build/arcane-confirm-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' -PmfclWandAudit
```

游戏目录需要原始 TC4/Baubles，运行时目录需要已准备好的私有 1.7.10 运行时。检查 `logs/mfcl/wand-host-audit.json` 的 `status` 及 `deconstructionRejects20VisWithIronCaps`、`arcaneWarpEffectsDisplayAndServerCostStaySynchronized`、`arcaneCraftThroughDelayedNativePackets`、`arcaneWandSurvivesWorldReloadAndLogin`、`arcaneSavedWandRetrievableWithVisIntact`。各组界面/服务端数据保存在 `deconstructionGui_*` / `deconstructionServer_*`，截图位于游戏目录 `screenshots/arcane-deconstructor-*.png`。

2026-10-31 首轮：现代客户端无衰竭的专项回归通过。20 点熵灵气拒绝合成，25 点时产出解构工作台并剩余 3 点；实际退出世界、重载、登录后，服务端和客户端法杖槽一致，法杖完整 NBT 保留且能够取回。报告：`build/arcane-display-retest-passed.json`。未修改配方成本和法杖保存逻辑，随后增加上述扭曲衰竭状态同步修复。

同日修复后：`build/arcane-confirm-game/logs/mfcl/wand-host-audit.json` 返回 `PASS`，含全部界面数字对照、衰竭增减/等级变化/重开/自然到期、禁止不足灵气时取出成品、正常合成和法杖世界重载断言。金帽杖的实际绘制数字和服务端需求依次为无衰竭 20、衰竭 I 22、传染性 IV/同时普通 II 28，移除或到期后恢复 20。修复前复现保存在 `build/arcane-warp-baseline.json`。

隔离输出 `build/arcane-warp-check` 的 `test build` 通过（3 项通过，6 项缺少各自外部测试样本而跳过），同一次构建执行 `mfclPotionSmokeTest` 也返回 `PASS`：覆盖原始扭曲事件节律/护盾/禁用设置、净化浴水和肥皂、真实法杖施法成本及两种衰竭取最高等级、客户端知识/视觉同步，以及全部扭曲值跨世界重载保存。该报告位于 `build/arcane-warp-potion-game/logs/mfcl/potion-smoke.json`。
