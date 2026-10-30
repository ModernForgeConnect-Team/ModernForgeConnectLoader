# 法杖功能调查（2026-10-20）

2026-10-30 补充：本页早期充能验收没有覆盖客户端点击银树节点的物品回退路径。该路径的修复及真实客户端持续使用对照见 [银树节点充能](wand-node-charging.md)。

样本：未经修改的 Thaumcraft 4.2.3.5、Baubles 1.0.1.10；宿主 Forge 1.20.1 / Java 17。机器可读证据见 [wand-functions-audit.json](wand-functions-audit.json)。原始配方在隔离旧运行时执行；宿主测试在一次性世界内调用现代工作台和 `LegacyDeviceMenu` 的实际取出入口。

## 实现后的验收

已补齐服务端菜单研究作用域、持续使用的库存栈/NBT 回写、原始库存更新、节点射线判定，以及 F/G 键和受校验的原始杖端控制包。通用网络入口还修正了旧 `ContainerPlayer` 构造签名，确保真实客户端请求能够进入处理器。

| 功能 | 当前结果 | 验收证据 |
| --- | --- | --- |
| 普通铁帽木杖 | 现代客户端通过 | 普通工作台实际取出，材料耗尽，杖杆/杖帽 NBT 正确 |
| 金帽宏伟之木法杖 | 现代客户端通过 | 原始菜单实际放料、取出；六种工具杖灵气从 10000 各减至 9190；原料耗尽 |
| 奥术合成 | 现代客户端通过 | 无杖/空杖不出成品；BANNERS 成品正常取出，水/地灵气各从 2500 减至 1950；与原始成本算法逐项比对，未重复扣费，工具杖 NBT 重载一致 |
| 节点充能、松手 | 现代客户端通过 | 实际旧射线命中节点，节点风灵气 20→10，手持杖原始风灵气 0→1000；保持现代活动栈引用；松手清除节点目标 |
| F 选择、装卸杖端 | 现代客户端通过 | 实际键盘事件打开原生选择界面，不额外交换副手；经真实网络装入后库存扣一件，卸下后归还恰好一件 |
| 杖端请求校验 | 原始运行时回归通过 | 仅从实际背包/背包内法杖袋枚举，不接受客户端物品数据；不存在的杖端请求被拒绝 |
| 杖杆恢复 | 原始库存更新桥接回归通过 | 黑曜石杖杆在第 200 tick 恢复 100 原始地灵气；现代服务端库存 tick 已接入同一回调 |
| G 模式切换 | 已接入，未单独验收各杖端模式效果 | 可重绑键发送原始 `PacketItemKeyToServer`，服务端只允许法杖模式键 |

F/G 可在控制设置中修改。F 默认选择背包和背包内法杖袋中的杖端；本次未验收饰品槽内法杖袋。研究、材料和灵气由测试夹具提供，测试未验证完整生存研究解锁流程。各杖端施法、基座充能、注魔及节点装瓶仍未逐项验收。

真实客户端报告 `status=PASS`。新增的 `ThaumcraftWandIntegrationTest` 使用原版 JAR 对九项法杖检查逐项断言，全部通过。以下保留修复前的调查基线，不能作为当前状态。

## 修复前基线

| 功能 | 调查结果 | 证据范围 |
| --- | --- | --- |
| 普通铁帽木杖制作 | 宿主通过 | 现代工作台显示结果，实际取出得到 `cap=iron, rod=wood` 的法杖，三个材料槽耗尽 |
| 金帽宏伟之木法杖组装 | 原版算法通过；宿主完整流程未验收 | 原始奥术容器产出 `cap=gold, rod=greatwood`，工具杖六种灵气分别由 10000 减至 9190；研究和满灵气工具杖由测试夹具提供 |
| 普通奥术配方 | 宿主存在缺口 | 使用真实 BANNERS 旗帜配方；服务端已有研究、材料正确且法杖充满，普通菜单仍不显示成品 |
| 奥术取出与扣料、扣灵气 | 原版算法通过；宿主上下文错误 | 旧容器正确拒绝无杖、空杖和未研究配方，合成旗帜耗尽输入，水/地灵气分别从 2500 降至 1950，NBT 重载保留余额。宿主控制实验中，显式进入服务端知识上下文后成品出现，退出上下文再按普通菜单取出时，成品被取走而杖内灵气未扣 |
| 吸取节点充能 | 失败 | 实际执行 `LegacyItemUse.rightClick` 和 100 次 `usingTick`：节点风灵气 20→0，手持杖风灵气仍为 0 |
| F 切换杖端及相关网络请求 | 未接入 | 原版 `PacketFocusChangeToServer` 经现有服务器分派被明确拒绝；没有宿主键位及对应旧玩家 tick 转发 |
| 原始杖杆自动恢复 | 静态检查发现缺口，未做端到端验收 | 原版由 `ItemWandCasting.onUpdate` 调杖杆 `IWandRodOnUpdate`；当前普通镜像物品没有转发原始库存更新回调，只有装备物品转发了装备更新 |
| 各杖端施法、充能基座、节点装瓶、注魔全流程 | 未专项验收 | 部分原始方法可被调用，不能据此认定现代世界中的效果、扣费、生成实体及保存全部可用 |

## 修复前确认的断点

`LegacyDeviceMenu.clicked` 和 `broadcastChanges` 没有进入 `services.playerState().enter(world)`，原始 TC4 的静态知识对象在普通菜单调用中可能指向客户端知识。相同菜单的按钮和设备包路径已经有此作用域。宿主控制实验只切换知识作用域并重算配方，就能让旗帜成品出现；取出路径同样需要一致的服务端知识上下文，不能只修成品显示。

`LegacyItemUse.usingTick` 将手持快照重新物化为临时旧栈，玩家库存内又是另一份栈；`onUsingTick` 对临时栈的灵气/NBT 修改没有返回给宿主。该方法只返回扫描完成请求，现代 `MirroredItem.onUseTick` 也没有回收修改后的库存。节点本身已被旧逻辑扣除灵气，因此这是可复现的资源丢失。

原版 F/G 功能依赖 `KeyHandler.playerTick` 和原始按键状态；当前适配器转发粒子客户端 tick，不等同于转发玩家按键 tick。杖端切换包也没有受支持的服务器处理策略。

## 复现

先在 `build/wand-audit-game/mods/1.7.10/` 和 `build/wand-host-game/mods/1.7.10/` 放入上述原始模组，使用已经准备好的私有旧运行时。以下命令使用不同输出，避免替换运行中客户端的类：

```powershell
$env:JAVA_HOME='C:/Users/gengy/.gradle/jdks/eclipse_adoptium-17-amd64-windows/jdk-17.0.17+10'

.\gradlew.bat --project-cache-dir build/wand-unit-cache `
  -I tools/isolated-build.init.gradle '-PmfclValidationBuildDir=build/wand-unit' `
  auditThaumcraftWand '-PlegacyGameDir=build/wand-audit-game' `
  '-PlegacyRuntimeDir=build/nei-investigation/runtime'

.\gradlew.bat --project-cache-dir build/wand-validation-cache `
  -I tools/isolated-build.init.gradle '-PmfclValidationBuildDir=build/wand-validation' `
  runClient '-PmfclRunDir=build/wand-host-game' `
  '-PlegacyRuntimeDir=build/nei-investigation/runtime' -PmfclWandAudit
```

原始报告分别写入游戏目录下的 `logs/mfcl/wand-functions-audit.json` 和 `logs/mfcl/wand-host-audit.json`。诊断任务成功退出表示已完成调查并写出结果；各功能是否通过必须查看报告字段，不能由 Gradle 的 `BUILD SUCCESSFUL` 推断。

本轮基础 JUnit 回归为 89 项通过、6 项因可选外部输入未启用而跳过。此前显式启用 TC4 原始研究台回归时，固定端点拒绝断言失败；该测试未使用上述法杖回调，需另外处理。

右键交互的补充宿主探针已通过炼药锅转换、普通神秘桌转换及法杖移入、潜行状态刷新、客户端 Shift 右键清空服务端坩埚，以及原有设备 200 tick 运行检查。报告保留 `WORLD_CHECKS_PASS_RELOAD_PENDING`：保存重载阶段未完成，随后停止该测试客户端，不能把这部分记为完整探针 `PASS`。
