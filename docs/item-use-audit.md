# Extra Utilities 物品使用审计（2026-10-22）

环境：Forge 1.20.1-47.4.28、Java 17、原始 Extra Utilities 1.2.12，通过 MFCL 私有 Forge 1.7.10 运行时执行。测试使用独立目录和新建的临时平坦世界。

## 批量回调实测

`ItemUseHostAudit` 枚举注册物品的原始方法覆盖情况，对 415 个物品/变种执行适用的空中右键、石头目标使用和背包更新回调。原生客户端、集成服务器和原始模组代码均实际运行。

| 结果 | 变种数 | 含义 |
| --- | ---: | --- |
| CALLBACKS_RETURNED | 407 | 本次调用没有抛异常或返回 FAIL，不代表物品的全部功能正常 |
| FAIL | 5 | 测试调用返回 FAIL，详见下表 |
| NO_DIRECT_USE | 3 | 没有本次检查的直接使用回调 |

| 物品 | metadata | 观察 |
| --- | ---: | --- |
| ExtraUtilities:glove | 0 | 原始 onItemUseFirst 读取旧服务器 `player.field_71133_b`，为空而抛异常 |
| ExtraUtilities:mini-soul | 0、3 | 原始右键查询玩家属性实例，返回空而抛异常 |
| ExtraUtilities:datablock | 0 | 石头目标放置返回 FAIL；尚未判定特殊放置条件与桥接问题 |
| ExtraUtilities:endConstructor | 0 | 石头目标放置返回 FAIL；尚未判定特殊放置条件与桥接问题 |

此统计不能用作“407 个物品功能通过”的结论。测试只给出一种通用目标，未覆盖材料需求、蓄力完成、工具挖掘、伤害/附魔、所有 NBT 状态、全体客户端视觉效果或放置后设备的长期运行。

## 本轮修复与回归

- 物品右键及方块使用捕获原始 FML GUI 请求，使用原生菜单同步；之前只有方块激活接入。
- 便携菜单标记随开窗数据发送，跳过方块坐标距离限制，事务锚点使用玩家位置；原始容器的有效性检查继续执行。通道版本更新为 6。
- 构造原始客户端 GUI 前投影玩家背包和选中槽位，补齐映射玩家的原始背包容器。
- 安装天使之戒的现代 Forge 监听器时使用宿主类加载上下文，避免私有旧版加载器参与现代事件类变换。

原始金色背包的隔离集成回归覆盖 GUI 捕获、非零快捷栏槽位、90 个槽位、原始点击逻辑、存入物品的 NBT 和重新投影背包后的读取。

最终原生验收也通过：

- 金色背包在第 6 个快捷栏槽位打开客户端原始界面，保持有效；经原生槽位点击存入 3 个石头，关闭重开后仍为 3 个。
- 生存模式背包中的天使之戒授予宿主飞行权限；移除后，原始到期回调收回权限。
- 金色套索对实际猪实体调用原生实体交互入口，结果为 PASS，目标未移除、套索没有捕获 NBT，确认该功能仍不可用。

最终 `test assemble` 成功：119 项测试中 109 项通过、10 项因缺少其他可选输入跳过，失败和错误均为 0。原生审计报告最终状态为 `FAILURES_FOUND`，保留前述 5 项失败；这与自动化回归通过不矛盾。
构建产物为 `build/item-audit/libs/mfcl-0.2.8.jar`。本轮没有把手套、迷你灵魂、实体交互、工具/武器和区块加载票据等剩余缺口一并补全。

## 已确认的覆盖缺口

原始实体交互回调覆盖者包括 `golden_lasso`、`scanner`、`shears`，通用 MirroredItem 尚未转发此入口。
原始命中回调覆盖者包括 `defoliageAxe`、`destructionpickaxe`、`erosionShovel`、`ethericsword`、`glove`、`lawSword`；武器命中、工具挖掘和属性仍需单独接入与验证。

批量放置期间，还观察到抽熔岩泵的后台 tick 在旧 `ForgeChunkManager.requestTicket` 抛异常。该问题不计入物品调用的 5 个 FAIL，说明放置回调返回正常不能证明设备能持续工作。

## 复现

原生实测目录需要在 `mods/1.7.10` 放入原始 Extra Utilities JAR，并在 `options.txt` 设置 `onboardAccessibility:false`。运行：

```powershell
.\gradlew.bat --offline --project-cache-dir build/item-audit-client-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/item-audit-client `
  runClient -PmfclRunDir=build/item-audit-game `
  -PlegacyRuntimeDir=build/nei-investigation/runtime -PmfclItemAudit
```

报告位于 `build/item-audit-game/logs/mfcl/item-use-audit.json`。该模式还执行金色背包客户端存取/重开、金色套索实体目标、天使之戒生存飞行授予/回收场景。完成结果须检查报告和日志，不能仅以 Gradle 进程退出成功为准。
