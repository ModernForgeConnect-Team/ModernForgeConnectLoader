# 银树节点的法杖吸取（2026-10-30）

输入：Minecraft 1.20.1 / Forge 47.4.28 / Java 17，未经修改的 Thaumcraft 4.2.3.5 与 Baubles 1.0.1.10。

## 原因与操作

揭示之护目镜和魔导透镜可以透过原木、树叶显示节点；原版法杖的充能使用方块射线，仍会被这些方块遮挡。对银树树干中的纯净节点，应清掉视线前方的外层原木与树叶，保留节点所在的中心原木，对准它并持续按住右键。单击只会短暂开始使用，不能代替持续吸取。

MFCL 还有一个独立的交互错误：所有镜像方块的客户端 `use` 都无条件返回 `SUCCESS`。即使普通原木在原版没有方块激活行为，客户端也会提前结束右键流程，不再发送物品持续使用请求。清开外层原木、瞄准含节点的中心原木后也因此无法充能；空中节点没有现代选中轮廓，走到物品右键入口，反而能够充能。

`LegacyBlockLogic.hasActivation` 现在检查原始方块是否覆写 `Block.onBlockActivated`。镜像方块对没有激活回调的原木等返回客户端 `PASS`，让原有物品使用流程继续；覆写激活回调的设备保留原有客户端预测。服务端仍执行原始回调，未修改 TC4 的射线、吸取算法和节点类型规则。

## 验收

新增 `-PmfclWandNodeSmokeTest`。使用生存模式玩家、空铁帽木杖，以及只含 20 点风灵气的固定节点；银树节点夹具在修复后设置为 `PURE`。从实际客户端方块使用结果决定是否继续物品右键，发送原生网络请求，保持右键并等待 70 个正常客户端刻。测试不手动调用 `onUseTick`、旧版吸取方法或灵气转移函数。

| 场景 | 修复前 | 修复后 |
| --- | --- | --- |
| 节点前另有一块银树原木 | 客户端方块使用 `SUCCESS`，无吸取 | 方块使用 `PASS`，射线命中外层原木，节点仍为 20、法杖为 0，符合遮挡规则 |
| 清开遮挡，直接瞄准含节点的银树原木 | 客户端方块使用 `SUCCESS`，无持续使用；节点 20、法杖 0 | 方块使用 `PASS`，物品使用 `CONSUME`；节点 20→6，法杖 0→1400 原始风灵气（14 点） |
| 露在空中的节点 | 节点 20→6，法杖 0→1400 | 同样通过，未回归 |

两个可吸取场景均验证 `节点剩余 × 100 + 法杖原始灵气 = 2000`，松手后服务器停止持续使用并清除 `IIUX` 节点目标。客户端截图已检查；对应物品使用、节点渲染与持续使用路径未报告错误。该测试不覆盖所有杖帽、杖杆、杖端及研究组合。

旧 `WandHostAuditProbe.verifyNode` 直接调用服务端物品右键，并手动驱动持续使用回调，只覆盖空中节点；它不能单独证明普通客户端点击银树原木的流程可用。此前记录中的节点充能通过应按此范围理解。

## 重现与产物

将 TC4/Baubles 原始 JAR 放在 `build/wand-node-fix/game/mods/1.7.10/`，使用私有运行时：

```powershell
.\gradlew.bat --offline --project-cache-dir build/wand-node-fix/cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/wand-node-fix/output' `
  runClient '-PmfclRunDir=build/wand-node-fix/game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' -PmfclWandNodeSmokeTest
```

测试创建并关闭自己的客户端世界。报告为 `build/wand-node-fix/game/logs/mfcl/wand-node-smoke.json`；修复前失败报告保存在 `build/wand-node-fix/before-report.json`，修复后日志为 `build/wand-node-fix/after-client.log`。截图位于游戏目录的 `screenshots/wand-node-{coveredSilverwood,exposedSilverwood,floatingNode}.png`。
