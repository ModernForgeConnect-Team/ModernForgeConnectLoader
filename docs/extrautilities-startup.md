# Extra Utilities 兼容修复（2026-10-21）

截图中的 `PREINITIALIZATION` 失败来自原始 Extra Utilities 1.2.12 的
`ExtraUtilsClient.registerClientCommands`。它在玩家进入世界前调用
`Minecraft.func_110432_I().func_148256_e()`，但 MFCL 绕过旧客户端构造函数后没有设置 `Session`。

现在在发布旧客户端单例、构造模组及触发 preInit 前创建真正的旧版 `Session`。
客户端复制启动器的用户名、UUID、令牌和会话类型；Microsoft 账号映射为旧版支持的 `mojang` 类型。
跨运行时只传字符串，旧版 Session / GameProfile 仍由私有类加载器加载。
命令行和专用服务端使用确定性的离线身份；重复初始化语言资源不会覆盖已有会话。
会话不写入诊断报告，其字符串表示隐藏凭据。

## 验证

使用 Java 17、Minecraft 1.20.1 / Forge 47.4.28、私有 Forge 1.7.10 SRG 运行时：

```powershell
.\gradlew.bat --offline --project-cache-dir build/session-fix-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/session-fix `
  test assemble -PlegacySupportDir=build/nei-investigation
```

118 项测试中 108 项通过、10 项因未提供可选外部输入而跳过，失败为 0。
通用集成测试中的真实 FML preInit 回调执行截图中的调用链，并校验自定义用户名、
无连字符 UUID、令牌、会话类型、旧 authlib 隔离，以及重复初始化后的会话身份。

将原始 `extrautilities-1.2.12.jar` 复制到独立目录
`build/session-extrautils-game/mods/1.7.10/` 后执行：

```powershell
.\gradlew.bat --offline --project-cache-dir build/session-fix-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/session-fix `
  bootLegacyRuntime -PlegacyGameDir=build/session-extrautils-game `
  -PlegacyRuntimeDir=build/nei-investigation/runtime

.\gradlew.bat --offline --project-cache-dir build/session-fix-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/session-fix `
  runClient -PmfclRunDir=build/session-extrautils-game `
  -PlegacyRuntimeDir=build/nei-investigation/runtime -PmfclHostSmokeTest
```

旧 FML 启动报告为 `AVAILABLE`，Extra Utilities 完成全部初始化阶段。
宿主到达主菜单，注册 56 个旧模组方块、79 个物品，目录共 975 个堆叠。

首次 Session 修复后的完整宿主探针结果为 `FAIL`：解析普通配方
`131c42013246a94d056d49f14a83fde050e13e107d7d718e1fc5aeac0834a96f`
时无法表示 `minecraft:redstone_torch@1`。该问题由下述后续修复处理；首次测试结果保留为历史记录。
详细报告在测试目录的 `logs/mfcl/host-startup-smoke.json`，原始模组 JAR 未修改。

构建产物为 `build/session-fix/libs/mfcl-0.2.8.jar`。

## 发电机、GUI、配方和 Java 17 后续修复

- 初始化旧 `SoundManager` 的 bookkeeping，不启动旧音频引擎；原始 `ITickableSound` 在宿主音频引擎中更新，播放记录随结束、无法播放或退出世界清理。宿主音频回调恢复宿主类加载上下文，避免 Forge 在私有加载器中查找现代声音父类。
- 在旧 `FMLOutboundHandler` 的已编码边界桥接 `SERVER → PLAYER` 原始 FML 数据通道。接收端仍执行注册通道的原始编解码器和客户端处理器。原始 `SimpleNetworkWrapper` 桥及服务端数据包策略保持独立；此项不宣称实现所有旧通道路由。
- `attachViewer` 和初始化期间产生的数据先捕获，在原生打开菜单消息发送后交付，避免第一次能量条描述早于 GUI 到达。后续更新走相同宿主连接。
- 配方中的非子类型原版物品也可能显式携带非零 damage，例如 `stick@1` 和 `redstone_torch@1`。投影材料保留该值，不将其改成普通 `@0`；最终匹配仍由原始配方决定。
- 私有反射调用点将四参数 `ClassLoader.defineClass` 重定向到 MFCL 私有 `LegacyLaunchLoader` 的公开定义入口，支持 Extra Utilities 的 `RemoteCallFactory`。JDK 模块保持关闭，其他加载器不获得额外访问权限。

原始模组回归：

```powershell
.\gradlew.bat --offline --project-cache-dir build/generator-fix-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/generator-fix `
  test assemble -PlegacySupportDir=build/nei-investigation `
  '-PextrautilitiesJar=run/mods/extrautilities-1.2.12.jar'
```

`ExtraUtilitiesGeneratorIntegrationTest` 使用原始发电机、动态容器和通道编解码器，检查客户端 tick、声音去重和结束、燃料消耗与发电、初始描述捕获、能量条实时同步、旧窗口号拒绝，以及原始动态调用器生成/执行类。还检查宿主回调不继承私有类加载上下文、`java.lang` 未被全局开放。

最终全量测试共 119 项：109 项通过、10 项因缺少其他可选模组输入跳过，失败/错误均为 0；`test assemble` 成功。

实际客户端验收：

```powershell
.\gradlew.bat --offline --project-cache-dir build/generator-fix-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/generator-fix `
  runClient -PmfclRunDir=build/session-extrautils-game `
  -PlegacyRuntimeDir=build/nei-investigation/runtime -PmfclGeneratorSmokeTest
```

该模式创建独立临时测试世界、放置发电机、打开原始 GUI，通过原生槽位点击将煤炭放入机器，检查能量条从 0 更新、两端 tick 未暂停，最后退出世界检查声音记录清理并停止客户端。
报告 `logs/mfcl/generator-smoke.json` 为 `PASS`：首次描述和实时描述通过，客户端能量为 215，服务端成功运行 92 tick，退出世界后的声音清理通过。此验收不覆盖发电机全部变种、跨模组 RF 能量输出和所有 Extra Utilities 数据包。

最终构建产物位于 `build/generator-fix/libs/mfcl-0.2.8.jar`。

最终再次运行 `-PmfclHostSmokeTest`，`host-startup-smoke.json` 为 `PASS`：全部 486 个普通合成配方及 3 个熔炼配方通过原生解析；同时检查 `stick@1` / `redstone_torch@1` 投影材料不会接受普通 `@0` 堆叠。此前的配方解析失败已解决。旧类初始化失败和暂停状态会保留在运行中的 JVM，应用修复需完全重启游戏。
