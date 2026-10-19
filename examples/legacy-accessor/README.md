# 最小现代附属 API 示例

`src/example/LegacyAddon.java` 仅依赖 `mfcl-api`，没有 Minecraft、Forge 或旧模组类型。仓库根目录执行 `gradlew compileApiExample` 验证独立编译。

在现代 Forge 模组构造器调用 `LegacyAddon.initialize()`，并在 `mods.toml` 中声明对 `mfcl` 的必需依赖。完整接入、线程规则和签名说明见 [开发 API](../../docs/developer-api.md)。

示例在 API 就绪时通过 MLA 调用真实旧 ItemStack 的复制方法与数量字段。专属模组 API 可以在自己的适配库声明类似接口；旧类名只作为字符串描述符出现，不把原始 JAR 加进现代运行时。
