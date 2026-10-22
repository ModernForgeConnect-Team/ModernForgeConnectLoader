# 旧 RF 与现代 FE 双向桥接（2026-10-21）

适用：私有 1.7.10 CoFH RF API 与 Forge 1.20.1 `ForgeCapabilities.ENERGY` / `IEnergyStorage`，按 **1 RF = 1 FE** 转换。桥接不另设能源缓存，不修改原始模组 JAR。

## 行为

- 现代机器/管线访问旧机器时，`LegacyBlockEntity` 暴露按面的 FE 能力；存量、容量、接收和提取调用原始 RF 方法。保留 `canConnectEnergy`、原方法的速率与容量限制，以及 `simulate`。
- 旧机器/管线查询现代邻居时，服务端世界提供真正的旧 `TileEntity` 代理，实现该现代机器支持的 `IEnergyReceiver`、`IEnergyProvider` 或组合接口，调用实时 FE 能力。原发电机自身的传输算法可以直接识别它。
- 方向编号 0..5 对应 DOWN、UP、NORTH、SOUTH、WEST、EAST；RF `UNKNOWN` 对应 FE 的 `null` 面。相邻机器使用对方接触面，不反转调用方已经提供的方向。
- 现代端口输入/输出配置变化后，新的邻居查询刷新代理接口类型；旧引用每次仍重新检查实际能力。拆除、替换、能力失效和区块卸载后的引用不能继续传输，也不会主动加载目标区块。
- 实际传输仅在服务端拥有世界的线程运行；客户端能力只读。跨运行时修改加入现有世界事务，失败时恢复两端通过方块实体 NBT 持久化的状态。现代能力回调使用宿主类加载上下文。
- 没有旧 RF API 时不加载、复制或伪造 CoFH 类，其他旧模组照常运行。

旧 RF 没有与 FE 完全等价的 `canReceive` / `canExtract` 标志：FE 视图根据原对象实现的 RF 接口和连接规则提供标志，最终能否传输、传输多少以原始 RF 方法的返回值为准。例如实现了双向接口的旧发电机仍可拒绝接收并返回 0。

此实现面向方块机器/管线的 RF/FE 互通，不转换 IC2 EU、BuildCraft MJ 或物品充电接口。现代模组需要按自己的持久化约定保存其 FE 能量，才能参与世界事务的 NBT 恢复。

## 验证命令

使用已有私有 SRG 运行时和原始 Extra Utilities 1.2.12：

```powershell
.\gradlew.bat --offline --project-cache-dir build/rf-bridge-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/rf-bridge `
  test assemble -PlegacySupportDir=build/nei-investigation `
  '-PextrautilitiesJar=run/mods/extrautilities-1.2.12.jar'

.\gradlew.bat --offline --project-cache-dir build/rf-bridge-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/rf-bridge `
  runClient -PmfclRunDir=build/session-extrautils-game `
  -PlegacyRuntimeDir=build/nei-investigation/runtime -PmfclGeneratorSmokeTest
```

第二条命令使用一次性测试世界。`EnergyHostAcceptance` 仅在该测试模式下给测试位置的原版箱子附加原生、可持久化的 FE 能力；正常游戏不注册这些测试能源能力。

覆盖：原始发电机 RF 提取、原始能量节点 RF 接收、旧发电机主动向原生 FE 输出、旧 RF 从 FE 源提取、现代 FE 向 RF 节点输入及从 RF 源提取；输入/输出接口、面对面方向、限速、负数、模拟、能量守恒、端口配置变更、两端 NBT 保存/恢复、故意失败时双端回滚、缓存引用和能力失效、客户端只读。

测试报告位于测试游戏的 `logs/mfcl/generator-smoke.json`，能源断言在 `energyBridge` 字段；测试版本与限制应结合实际报告阅读。

本轮实际客户端报告为 `PASS`，四种传输方向、方向/限速/模拟、原生端口重配置、跨运行时回滚、两端 NBT 保存恢复、拆除/替换失效均通过；客户端能源只读检查也通过。全量测试 119 项中 109 项通过、10 项因缺少其他可选输入跳过，失败和错误为 0。

同时修复启动预览扫描：`LegacyTiles.create` 先检查原方块的 `hasTileEntity(metadata)`，避免对 Extra Utilities 发电机未定义的 metadata 调用工厂并触发 `BlockGenerator.tiles[metadata] == null`。原始模组回归覆盖所有这些空槽。

构建产物：`build/rf-bridge/libs/mfcl-0.2.8.jar`。

2026-10-22 增加真实 Mekanism 10.4.16.80 能量单元验收，修复 Extra Utilities 管道查询的旧高度限制。负高度下，发电机直连、经能量节点、经节点和传输管道三条路径均实际传输 10000 FE 并守恒；详见[手册与 Mekanism RF 回归修复](book-and-mekanism-rf.md)。
