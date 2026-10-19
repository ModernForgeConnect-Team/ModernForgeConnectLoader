# Modern Forge Connect Loader

[English](../README.md) · [简体中文](README.zh-CN.md) · [日本語](README.ja.md) · [Русский](README.ru.md) · [Deutsch](README.de.md) · [한국어](README.ko.md)

Modern Forge Connect Loader（MFCL）是实验性的兼容运行时，目标是在 Forge 1.20.1 / Java 17 宿主中运行原始 Forge 1.7.10 模组。

## 项目定位与设计目标

MFCL 在宿主同一个 JVM 的隔离类加载域中执行真实旧 FML 和原始模组代码，通过桥接连接已支持的游戏对象、注册表、渲染和交互。原始模组 JAR 保持不变。

核心负责通用机制，模组专用行为属于可选适配器。Thaumcraft 知识系统与透镜动态渲染正在拆入 TC4 兼容层。Developer API / MFCL Legacy Accessor（MLA）允许现代附属模组调用旧代码，无需编译依赖旧 Minecraft 类。

**项目仍在开发。成功初始化或能够显示物品，不代表完整游戏功能兼容。**

## 运行环境及安装

| 组件 | 当前目标 |
| --- | --- |
| 宿主 | Minecraft 1.20.1、Forge 47.4.28 |
| Java | JDK 17 |
| 旧运行时 | Minecraft 1.7.10、Forge 10.13.4.1614，私有准备的 SRG 运行时 |
| 已验收图形环境 | Windows 11；客户端及集成服务器 |

1. 使用 Java 17 构建 MFCL，或使用匹配版本的构建产物，将完整 `mfcl-0.1.0.jar` 放入现代游戏的 `mods/`。
2. 自行取得原始旧模组和必需前置，直接放入 `mods/1.7.10/`，不要放入现代模组根目录。
3. 准备私有旧运行时及已校验输入清单，参见[测试环境与准备](../docs/testing.md)。MFCL JAR 和依赖解析器均不包含、也不会自动安装完整旧 Minecraft 运行时。
4. 启动 Forge，查看 `logs/mfcl/legacy-1.7.10.json` 和 `logs/mfcl/runtime-1.7.10.json`，核对扫描结果、实际加载阶段和失败原因。

```text
<game-directory>/
  mods/
    mfcl-0.1.0.jar
    1.7.10/
      original-mod.jar
      required-legacy-dependency.jar
  mfcl/runtime/1.7.10/
    runtime-manifest.json
    minecraft-forge-1.7.10-srg.jar
    ...
```

MFCL 使用游戏自身目录，只扫描 `mods/1.7.10/` 直属 JAR。Minecraft、Forge 和第三方模组原始文件不随 MFCL 分发。

## 已实现的兼容功能

- 隔离旧域中的真实 FML 初始化，以及已支持的 CoreMod 和访问转换器。
- 现代物品 / 方块代理注册，metadata 与原始 NBT 保留，纹理和语言资源生成，以及已支持的放置、掉落和持久化。
- 已支持的原始 GUI、字体、物品、方块和方块实体渲染；部分实时物品渲染及旧音效。
- 部分世界、玩家状态、库存、红石、方块实体逻辑，以及已支持的普通工作台配方。
- 实验性 Developer API / MLA、显式对象转换、诊断及独立依赖工具。

| 参考模组 | 已验收范围 |
| --- | --- |
| NotEnoughItems 1.0.5.120 与 CodeChicken 前置 | 真实初始化、背包附着界面、搜索、原始配方 / 用途页面 |
| AcademyCraft 1.0.7 与 LambdaLib 1.2.3 | 真实初始化、基础内容注册及部分放置 / 保存检查 |
| Thaumcraft 4.2.3.5 与 Baubles 1.0.1.10 | 真实初始化、部分渲染和玩法桥接、魔导手册、持续扫描 / 奖励 / 持久化及音效 |

详细范围、原始输入哈希及带日期的验收记录见[技术文档索引](../docs/README.md)。结果仅适用于已测试的版本和功能。

## 兼容性限制

MFCL 尚不是完整的 Forge 1.7.10 客户端、通用 OpenGL 模拟器或全部旧游戏子系统的替代实现。任意模组、自定义 GUI / 渲染器、机器、世界生成、实体、玩家能力及网络协议仍有未覆盖部分。集成服务器测试不代表已验证独立专用服务器兼容性。

转换会明确拒绝未支持或会丢失数据的输入。现代独有方块 / 物品可能没有旧表示。完整事件转发与实体 / 玩家通用转换尚未完成；普通工作台配方支持不包括奥术、坩埚、注魔等模组专用配方系统。

TC4 扫描完成验证和扫描包捕获暂保留专用路径，迁移计划见[API 与适配边界](../docs/developer-api.md)。旧容器仍属于旧 FML；模组页面的合并展示不等于现代 ModList 或网络握手中注册了这些旧模组。

## Developer API / MLA 示例

独立 API 使用 `compileOnly`，不要打包进附属模组，也不要把旧 Minecraft 类放到现代运行时类路径：

```groovy
dependencies {
    compileOnly files('libs/mfcl-0.1.0-api.jar')
}
```

在附属模组内声明公开 accessor，并在初始化阶段订阅：

```java
@LegacyType("net.minecraft.item.ItemStack")
public interface OldStack {
    @LegacyMethod(name = "func_77946_l",
                  descriptor = "()Lnet/minecraft/item/ItemStack;")
    LegacyObject copy();
}

MfclApi.whenReady(runtime -> {
    LegacyObject stack = runtime.stack(
        new LegacyStack("minecraft:diamond", 0, 2, ""));
    LegacyObject copy = runtime.accessor(OldStack.class, stack).copy();
});
```

MLA 使用精确旧 SRG 名称和 JVM 描述符。旧引用保持不透明，且只属于一个运行时；玩法调用须运行在对应游戏线程。导入、字段 / 构造器、转换、生命周期及限制见[可编译示例](../examples/legacy-accessor/)和[API 指南](../docs/developer-api.md)。管理员可用 `/mfclapi` 查看诊断。

## Legacy Dependency Resolver

[独立 Gradle 插件](../tools/legacy-dependency-resolver/)复用 Gradle 依赖解析，并支持显式本地 JAR 导入。输入必须提供固定 SHA-256、来源和许可 / 分发说明。工具不自行添加远程仓库、不解析传递依赖、不把旧 JAR 安装到现代运行时类路径。

输出为内容寻址 JAR 和来源清单。配置、失败行为和测试见[工具说明](../tools/legacy-dependency-resolver/README.md)。

## 构建、测试与贡献

在仓库根目录使用 JDK 17 执行：

```powershell
.\gradlew.bat test build
.\gradlew.bat apiJar apiSourcesJar compileApiExample
.\gradlew.bat -p tools/legacy-dependency-resolver test validatePlugins
.\gradlew.bat inspectLegacyMods "-PlegacyGameDir=C:/path/to/game"
```

完整模组及 API 产物位于 `build/libs/`。非 Windows 系统使用 `./gradlew`。缺少外部输入时，对应参考检查会跳过；图形验收需要准备独立游戏目录。准备步骤、测试参数和独立构建输出见[测试与复现](../docs/testing.md)。

贡献请附目标模组 / 版本、复现步骤、日志及预期行为。通用机制放核心，专用行为放适配器；提供有针对性的回归证据，保持原始 JAR 不变，遵守现有线程、所有权和注册约定。

## MPL-2.0 许可证

MFCL 使用 [Mozilla Public License 2.0（MPL-2.0）](../LICENSE)。Minecraft、Forge 及第三方模组仍受各自许可证约束；MFCL 的许可证不授予重新分发这些文件的权利。
