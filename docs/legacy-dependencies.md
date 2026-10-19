# 现代附属模组声明旧版前置

MFCL 0.1.4 起，现代附属模组可以在自己的 `META-INF/mods.toml` 中使用 `mfclDependencies` 声明原始 Forge 1.7.10 前置，对应 [issue #2](https://github.com/ModernForgeConnect-Team/ModernForgeConnectLoader/issues/2)。

```toml
# exampleaddon 必须与本文件 [[mods]] 中的现代 modId 一致。
[[dependencies.exampleaddon]]
modId="mfcl"
mandatory=true
versionRange="[0.1.4,)"
ordering="AFTER"
side="BOTH"

[[mfclDependencies.exampleaddon]]
modId="Thaumcraft"
mandatory=true
versionRange="[4.2.3.5,5.0)"
side="BOTH"
```

完整 TOML 示例见 [thaumcraft.mods.toml.example](../examples/legacy-accessor/thaumcraft.mods.toml.example)。多个旧前置重复声明 `[[mfclDependencies.exampleaddon]]`；一个 JAR 包含多个现代模组时，分别使用各自的现代 ID。

| 字段 | 含义 |
| --- | --- |
| `modId` | 必填，原始旧 FML 的 ID，区分大小写，如 `Thaumcraft`、`Baubles`、`NotEnoughItems` |
| `mandatory` | 默认 `true`；`false` 允许缺失，但已加载时仍检查版本 |
| `versionRange` | 默认空字符串，接受任意版本；支持 Maven 区间 `[4.2.3.5,5.0)`、精确范围 `[4.2.3.5]` 或精确字符串 `4.2.3.5` |
| `side` | 默认 `BOTH`；也可为 `CLIENT` 或 `SERVER`，按现代宿主的物理侧检查 |

MFCL 在自身构造阶段启动旧 FML 后检查所有现代模组文件中的声明。只有成功完成旧 FML 初始化的模组能够满足依赖；仅发现 JAR、前置缺失、运行时输入未准备好或旧 FML 启动失败均不满足必需依赖。版本来自真实旧 FML 容器，包含动态 CoreMod 容器；未知版本只能满足不限制版本的声明。错误列出附属模组、旧 ID、版本要求和日志位置，并通过 Forge 的模组构造错误阻止启动。

`side="SERVER"` 表示独立专用服务器；集成服务器运行在物理客户端中。此选项不代表 MFCL 已建立完整的专用服务器兼容性。

旧模组仍属于 MFCL 的隔离域。不要将 `Thaumcraft` 写入现代 Forge 的普通 `[[dependencies.exampleaddon]]`，也不要使用现代 `ModList.isLoaded("Thaumcraft")` 查询。普通 Forge 依赖用于要求 MFCL 的版本及现代加载顺序；`mfclDependencies` 内不支持 `ordering`。

## 程序查询

仅查询旧模组是否加载、版本和来源 Minecraft 时，无需访问旧类或等待游戏对象 API：

```java
// 在 FMLCommonSetupEvent 或更晚阶段调用；不要在静态初始化/附属构造器中假定 MFCL 已完成。
boolean loaded = MfclApi.isModLoaded("Thaumcraft");
Optional<LegacyModInfo> tc4 = MfclApi.mod("Thaumcraft");
tc4.ifPresent(mod -> {
    String version = mod.version();             // "4.2.3.5"
    String source = mod.sourceMinecraft();      // "1.7.10"
});
Map<String, LegacyModInfo> originals = MfclApi.loadedMods(); // 不可变快照
```

API 类型位于 `github.com.gengyoubo.mfcl.api`，只依赖 Java 17。MFCL 构造完成前、没有 provider、没有成功加载的旧模组或已发布运行时关闭后，查询为空 / `false`。`mcp`、`FML`、`Forge` 内部运行时组件不作为旧模组列出。普通现代模组也不会出现在此查询中。

需要 MLA、物品或世界转换时继续使用 `MfclApi.whenReady(...)`。就绪回调的异常按现有约定记录到诊断，不能替代必需前置检查；应使用上面的声明让缺失前置阻止启动。完整 API 说明见 [开发 API](developer-api.md)。
