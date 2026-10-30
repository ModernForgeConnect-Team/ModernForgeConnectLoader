# 坩埚合成与挖掘修复

2026-10-30，宿主 Forge 1.20.1 / 47.4.28、Java 17，原始 Thaumcraft 4.2.3.5 与 Baubles 1.0.1.10。

坩埚继续执行原始 `TileCrucible.attemptSmelt` 和原始配方、研究检查。此前缺少三段桥接：原始 `ItemTossEvent` 没有收到现代投掷操作，因此 TC4 无法记录投掷者并检查其研究；掉落实体的 Forge 附加 NBT 没有进入原始碰撞回调；机器产生的物品在没有玩家交互捕获器时没有生成到宿主世界。现在转发投掷事件，传递附加 NBT，并在世界事务提交时生成原始掉落物。成品保留原始掉落实体子类标记，使 TC4 的 `EntitySpecialItem` 判定继续阻止再次溶解成品。标记和投掷者数据由原生实体 NBT 保存。

挖掘有两个问题。旧方块缺少现代有效工具标签，现代镐子使用了低效挖掘速度；更直接的故障是原生生存破坏先移除方块实体，再计算掉落，而旧桥接在此时调用 `legacyOrNull()`，触发“设备已移除”的异常。现在为工具和等级声明在全部旧元数据中一致的方块导出原生挖掘标签；未声明工具的方块按原始钻石镐、斧、锹的有效速度推导标签。材料要求工具时使用原生采集限制。不同元数据具有不同声明的方块不套用统一标签，自定义旧工具等级高于 3 时也不映射为原生等级。方块实体在失效前保存库存掉落快照，掉落计算读取快照，不重新激活已移除的设备。

## 验证

专用服务器探针使用独立测试目录，填入原始炼金煤配方所需源质，实际通过宿主投掷事件和自然实体碰撞执行合成。报告 [crucible-progress.json](crucible-progress.json) 的 21 个检查全部通过，包括自然熔岩加热、冷坩埚保留物品、未解锁研究时不产出、投掷者 NBT 保存恢复、成品准确生成一次、消耗 50 mB 水和相应源质、成品不会再次溶解、原生铁镐和原始神秘镐有效、生存模式破坏及准确掉落一个坩埚。

该探针不覆盖全部坩埚配方、全部模组组合或图形客户端操作。实体 NBT 检查是保存和读取单个实体，不代表完整世界重启验收。

```powershell
$env:JAVA_HOME = 'C:/path/to/jdk-17'
./gradlew.bat --project-cache-dir build/crucible-cache `
  -I tools/isolated-build.init.gradle `
  '-PmfclValidationBuildDir=build/crucible-validation' `
  runServer '-PmfclRunDir=build/crucible-game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  '-PmfclServerSmokeTest=crucible' --console=plain
```

目录需包含独立测试服务器配置、已接受的 EULA 和两份原始模组。运行时报告位于测试目录的 `logs/mfcl/crucible-smoke.json`。本次服务器验收临时排除了客户端可选依赖 JEI。

## IDEA 构建同步

`test` 任务原先在配置闭包中读取 `sourceSets.test.runtimeClasspath.asPath`，使 IDEA 在实现惰性任务时触发 ForgeGradle 的依赖准备任务创建，违反 Gradle 的任务容器变更限制。该系统属性现在在 `doFirst` 中设置。配置回归检查确认：实现 `test` 任务后，`testRuntimeClasspath` 仍为 `UNRESOLVED`，无需在配置任务时下载或生成 Forge、JEI 构件。
