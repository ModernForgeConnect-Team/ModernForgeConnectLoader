# MFCL 开发 API / Legacy Accessor（实验版 v1）

对应 [issue #1](https://github.com/gengyoubo/ModernForgeConnectLoader/issues/1) 的第一阶段实现。目标是让现代附属模组访问**已经由 MFCL 启动的原始旧模组**，不修改、重打包或把旧 Minecraft 类放进现代类路径。

## 当前范围

| 能力 | 本版状态 |
| --- | --- |
| 独立 Java 17 API JAR，无 Minecraft / Forge 编译依赖 | 已实现 |
| 运行时就绪回调、实际加载的 mod ID、关闭失效 | 已实现 |
| MLA 精确签名的方法、字段、构造器访问 | 已实现 |
| 旧物品 / 方块注册表查询 | 已实现，只读；不绕过注册屏障添加内容 |
| ItemStack 和旧 NBT 的复制 / 快照 | 已实现，保留原始 1.7.10 SNBT |
| 现代 ItemStack 双向转换 | 已实现，复用严格的游戏数据转换 |
| 已绑定的现代世界双向查询 | 已实现，检查拥有线程及世界卸载 |
| 附属适配库注册命名转换器 | 已实现，禁止覆盖已有转换器 |
| 通用动态物品绘制和玩家状态钩子（宿主适配 SPI） | 已实现，TC4 实现按需安装 |
| 诊断历史及 `/mfclapi` 命令 | 已实现 |
| 玩家、任意实体、任意现代 NBT 的通用双向转换 | 待实现 |
| 通用旧事件转发、现代事件同步、网络协议自动转换 | 待实现 |
| Thaumcraft 等模组专属 API 适配库 | 后续独立实现，不放入 MFCL 核心 |
| 自动 MCP/SRG 名称转换、Mixin 注入 / 重定向 | 不在本版 MLA 范围内 |

本 API 仍为实验版；`API_VERSION = 1` 表示当前契约版本，不能理解为 issue 全部完成。当前宿主验收使用 Forge 1.20.1 客户端及其集成服务器，不代表所有旧模组或独立专用服务器均已兼容。

## TC4 专用逻辑的归属

按照当前拆分计划，MFCL 核心提供机制，模组兼容层提供具体知识、扫描和渲染规则：

| 当前功能 | 归属 / 本轮处理 | 后续边界 |
| --- | --- | --- |
| `ThaumcraftKnowledge` | 位于 `compat.thaumcraft`；由可选 `ThaumcraftAdapter` 通过 `LegacyPlayerState.Hooks` 安装。核心玩家保存、加载和物品使用已取消对该实现的直接调用 | TC Modern Lib 兼容实现层逐步迁出；当前仍随 MFCL 打包 |
| `ItemThaumometer.doScan()` 专用处理 | 暂时保留现有服务端验证路径 | 在 TC4 适配层继续迁移，不成为通用 API 的扫描规则 |
| `PacketScannedToServer` 特殊捕获 | 暂时保留，维持客户端完成通知和服务端验证 | 通用网络桥完成后由 TC4 适配层替换 |
| 透镜特定动态渲染 | `LegacyItemRenderer` 只调用通用 `LegacyDynamicItems.Renderer`；TC4 适配层选择透镜 / 显示上下文，并处理不可映射目标的回退 | 可在独立 TC4 客户端适配库安装相同实现 |

`LegacyDynamicItems` 在每帧按旧物品键和旧显示上下文选择 renderer，返回捕获的几何和文字，空结果继续使用已冻结模型。不同 ID 的 renderer 同时认领同一个物品上下文会明确报冲突。通用 `LiveLegacyItemCapture` 提供真实世界 / 玩家上下文下的原始第一人称捕获，不包含 TC4 物品 ID；捕获结束恢复临时相机和装备进度。

`LegacyPlayerState` 在原始 NBT 序列化前后调用适配器钩子。世界作用域按逆序恢复，部分进入失败也回滚已进入的作用域，避免集成服务器的知识状态泄漏到客户端。没有 TC4 的旧运行时不会安装这些知识和动态渲染实现。

这两个 `bridge` SPI 面向 MFCL 宿主适配层，尚未放入独立 API JAR；不要把它们误认为已稳定的外部 TC Modern Lib API。

## 编译与依赖

仓库根目录执行：

```powershell
.\gradlew.bat apiJar apiSourcesJar compileApiExample
```

产物为 `build/libs/mfcl-0.1.0-api.jar` 和 `mfcl-0.1.0-api-sources.jar`。完整 MFCL JAR 内也包含同一份 API。

附属模组使用 `compileOnly`，**不要把 API 打包进自己的 JAR**：

```groovy
dependencies {
    compileOnly files('libs/mfcl-0.1.0-api.jar')
}
```

也可执行 `publishDeveloperApiPublicationToMavenRepository`，发布到本仓库 `mcmodsrepo` 的坐标为 `github.com.gengyoubo.mfcl:mfcl-api:0.1.0`，POM 不引入旧 Minecraft、Forge 或旧模组依赖。此仓库尚未发布到公共 Maven 服务。

运行游戏时安装完整 MFCL，并按现有运行时约定准备原始旧模组及旧运行时。在现代附属模组的 `mods.toml` 声明对 `mfcl` 的必需依赖。旧 Forge / Minecraft JAR 不能作为现代附属模组的普通 `implementation` 或 `runtimeOnly` 依赖。

`examples/legacy-accessor/src/example/LegacyAddon.java` 是可编译示例，`compileApiExample` 的类路径**只有 API 输出**，证明示例不需要引用任何一个版本的 Minecraft 类。

## 生命周期

在现代附属模组构造器调用示例的 `LegacyAddon.initialize()`，或注册自己的回调：

```java
AutoCloseable subscription = MfclApi.whenReady(runtime -> {
    if (!runtime.loadedMods().contains("Thaumcraft")) return;
    // 此处建立专属适配器 / 查询旧注册表。
});
```

旧 FML 成功启动、现代内容注册完成后，MFCL 在 `FMLCommonSetupEvent.enqueueWork` 发布 API。提前订阅的回调执行一次；已经就绪时同步执行在订阅者线程。关闭订阅可取消尚未触发的回调。单个附属模组的回调异常会记录日志和诊断，不阻止其他回调。

`MfclApi.runtime()` 在发布前、没有旧运行时、启动失败或运行时关闭后返回空。只有独立 API JAR 而没有完整 MFCL provider 时，查询为空、订阅返回空操作句柄。不要在静态初始化中假定已经启动，也不要阻塞模组加载线程等待 API。

就绪回调不是世界事件；此时可能还没有世界或玩家。游戏修改应放到对应逻辑服务器 / 客户端拥有线程。MLA 的串行执行及上下文类加载器切换**不等于**自动切换游戏线程。

## MLA：按精确旧签名访问

```java
@LegacyType("net.minecraft.item.ItemStack")
public interface OldStack {
    @LegacyMethod(name = "func_77946_l",
                  descriptor = "()Lnet/minecraft/item/ItemStack;")
    LegacyObject copy();

    @LegacyField(name = "field_77994_a", descriptor = "I")
    int count();

    @LegacyField(name = "field_77994_a", descriptor = "I")
    void count(int count);
}

LegacyObject value = runtime.stack(new LegacyStack("minecraft:diamond", 0, 2, ""));
OldStack stack = runtime.accessor(OldStack.class, value);
LegacyObject copied = stack.copy();
stack.count(1); // 不影响 copied
```

接口必须是 `public`，并显式声明 `@LegacyType`。抽象方法必须且只能声明一种成员注解；可使用 Java 默认方法组合调用。

- `@LegacyMethod` 指定准确名称、完整 JVM descriptor 和 `isStatic`；返回类型也是签名的一部分。静态成员使用 `runtime.accessor(Contract.class)`。
- `@LegacyField` 的零参数方法读取字段；单参数、返回 `void` 的方法写字段；类型必须匹配，禁止写 `final` 字段。
- `@LegacyConstructor(descriptor = "(I)V")` 调用目标类构造器，API 方法返回 `LegacyObject`。描述符末尾必须为 `V`。
- 可定位非公开成员、继承方法和字段。成员不存在、重载签名错误、静态/实例声明不符、接收对象类型错误，会在绑定阶段失败。

使用**实际旧 SRG 运行时**里的名称：Minecraft 成员通常为 `func_...` / `field_...`，模组自己的成员通常保留原名。不要用现代 Mojang 映射名称，也不要把同名旧类当成现代类。`@LegacyType` 目标必须属于隔离旧域，不能借此访问宿主或 JDK 类型。

参数和返回值只允许精确匹配的基本类型、`String`、`LegacyObject` 及这些类型的数组。任意旧类 / Java 集合用 `LegacyObject` 持有，再绑定旧域的具体 accessor；`Object`、现代 Minecraft 类和原始旧 Minecraft 类均不能直接出现在签名中。数组跨界时复制，引用元素包装成 opaque handle。

`LegacyObject` 没有 `unwrap()`。MFCL 检查引用来自当前运行时，拒绝伪造 / 跨运行时引用。运行时关闭后，旧 accessor 的实际调用和转换都失败。MLA 不提供注入、重定向、覆盖旧方法，也不是隔离不可信附属代码的安全沙箱。

## 注册表、快照与转换

```java
Optional<LegacyObject> item = runtime.find(LegacyRuntime.Registry.ITEM, "minecraft:diamond");
Set<String> blocks = runtime.registryKeys(LegacyRuntime.Registry.BLOCK);
LegacyStack snapshot = runtime.snapshot(value);
```

查询保留原始命名大小写，例如 `Thaumcraft:ItemEssence`。缺失的旧方块返回空，避免旧默认注册表把未知 ID 当成空气。查询不注册新物品 / 方块，现代映射仍使用原有注册屏障。

`LegacyStack` 是独立值：旧 ID、metadata、数量、原始 SNBT。`runtime.stack` 创建旧堆的副本，`snapshot` 读取快照；SNBT 中旧 indexed-list 语法不能交给现代 `TagParser` 解析。它不是任意现代 `CompoundTag` 的通用转换器。

现代附属模组可以使用自己依赖的现代类型：

```java
LegacyObject old = runtime.mappings()
    .toLegacy(LegacyMappings.ITEM_STACK, modernStack).orElseThrow();
ItemStack copy = runtime.mappings()
    .toHost(LegacyMappings.ITEM_STACK, old, ItemStack.class).orElseThrow();
```

物品转换在两个方向复制。空堆返回空；没有等价旧物品或包含尚未映射的现代额外 NBT 时明确失败，不会用渲染占位或删除标签冒充成功。

`LegacyMappings.WORLD` 对应现代 `Level` 与已存在的旧 World facade。它不创建、加载或调度世界；未绑定的世界返回空。借出的世界引用及其 MLA 调用派生的引用都检查原线程和绑定是否仍有效；服务端引用不能拿到渲染线程用，卸载后也不能继续调用。反向转换保留同一个现代世界身份。

专属适配库可注册自己的转换：

```java
AutoCloseable registration = runtime.mappings().register(
    "myaddon:domain_value", DomainValue.class,
    host -> adapter.makeLegacyValue(host),
    old -> adapter.readLegacyValue(old));
```

适配器通过 MLA / 现有命名转换器产生和读取引用，不接触内部 raw 对象。空返回值表示没有对应表示。注册不允许覆盖已有键，关闭注册句柄可移除本次转换器。列表通过 `supported()` 查询；缺失转换器、错误宿主类型或错误运行时引用会明确失败。

## 诊断和验收

运行 `/mfclapi`（权限等级 2）查看 API 版本、实际旧 mod ID、支持的转换器、旧注册表数量和最近 5 条 API 故障。`runtime.diagnostics()` 保留最近 64 条结构化故障；`LegacyAccessException.code()` 区分 `MLA_BIND`、`MLA_INVOKE`、`REGISTRY`、`STACK_IMPORT`、`STACK_EXPORT`、`MAPPING_IMPORT`、`MAPPING_EXPORT`。原始异常作为 cause 保留以便定位调用路径。

测试覆盖隔离域同名类、重载 / 继承 / 私有成员、final 保护、数组、错误描述符、跨运行时 / 伪造引用、线程和生命周期、就绪回调、诊断容量，以及真实 1.7.10 ItemStack / 注册表 / 原始 NBT。客户端 `-PmfclApiSmokeTest` 验收独立 API ServiceLoader、真实现代堆转换、客户端和集成服务器世界身份、跨线程拒绝及卸载失效，报告写入游戏目录 `logs/mfcl/api-smoke.json`。

并行运行游戏时，应使用独立的 Gradle 输出及 project cache，避免覆盖正在运行的开发类。独立依赖工具的配置、验证和来源记录见 [Legacy Dependency Resolver](../tools/legacy-dependency-resolver/README.md)。
