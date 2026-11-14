# MFCL Legacy Navigation for IntelliJ IDEA

独立的 IDEA 插件，提供 Java 注解字符串中的成员补全和 Ctrl+B / Ctrl+单击跳转：

* `@LegacyType("...")` 或 `@LegacyType(type = ...class)` 下的 `@LegacyMethod(name = "...")`、`@LegacyField(name = "...")`。
* `@Mixin(Target.class)` / `@Mixin(targets = "...")` 下的 `@Inject(method = "...")` 等 Sponge Mixin injection 注解，包含方法数组。方法名后附带的描述符不会被跳转范围选中。

例如 `@Mixin(Projectile.class)` 配合 `@Inject(method = "onHit", ...)`，在字符串中按 Ctrl+Space 补全，Ctrl+B 跳转。如果方法有重载，会列出同名候选；当前版本不会按 JVM 描述符筛选重载。IDEA 没有对应目标类的索引时无法补全或跳转。旧类建议先配置 `mfcl.direct` 并运行 `directDeobfMinecraft`、同步 Gradle；插件优先选用 `minecraft-forge-1.7.10-mcp.jar`，避免选中仅包含类名的 stub。对于只有字节码的依赖，IDEA 通常会显示反编译内容。

例如 `@LegacyType("thaumcraft.common.items.wands.ItemWandCasting")` 下输入 `@LegacyMethod(name = "")`，把文本光标放在引号内，按 `Ctrl+Space` 可选择 `getMaxVis`。输入方法名前缀时 IDEA 也可以自动弹出候选，取决于其代码补全设置；鼠标悬停不会触发补全。旧 JAR 除了可通过 Gradle 作为库索引，也会在项目的 `build/mfcl-direct/*/`、`mods/`、`mods/1.7.10/`、`run/mods/` 和 `run/mfcl/legacy-maven/1.7.10/` 等常见位置查找。使用其他路径时请将 JAR 添加为 IDEA 项目库。依赖文件或构建产物需要实际存在，插件不会自动下载它们。

这不是 Mixin 本身，也不提供 Mixin 注入、运行时转换或修改游戏的能力。暂不支持带 `Lowner;` 的 Mixin 成员选择器、转义字符串、SRG/MCP 名称互相转换、按描述符筛选重载；普通 Java 代码的补全由 IDEA 自身提供。插件不会下载或安装旧版 Forge。

## 构建与安装

插件声明支持 IntelliJ IDEA 2025.3 到 2026.1（内部版本 253–261），需要 Java 插件和 JBR 21；构建使用本机 IDEA SDK。Windows 下会自动查找常见的 IDEA 安装目录。使用 JDK 17 或更高运行 Gradle，在仓库根目录执行：

```powershell
.\gradlew.bat -p idea-plugin buildPlugin
```

如果自动查找不到，或有多个版本需要指定，可以手动设置路径：

```powershell
.\gradlew.bat -p idea-plugin buildPlugin '-PideaHome=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3'
```

也可设置 `IDEA_HOME` 指向相应 IDEA 安装目录。安装 `idea-plugin/build/distributions/mfcl-idea-<版本>.zip`：IDEA 的 **Settings → Plugins → 齿轮 → Install Plugin from Disk**，选择 ZIP 后重启 IDE。插件使用本地 IDEA SDK 编译，不会修改 IDEA 的安装目录。

## 发布到本地 Maven 仓库

在仓库根目录执行：

```powershell
.\gradlew.bat -p idea-plugin publish
```

该命令先构建插件 ZIP，再将 ZIP 和 POM 发布到项目的 `mcmodsrepo/github/com/gengyoubo/mfcl/mfcl-idea/<版本>/`，坐标为 `github.com.gengyoubo.mfcl:mfcl-idea:<版本>`（扩展名 `zip`）。版本统一取自仓库根目录 `gradle.properties` 的 `mod_version`。它不发布到 JetBrains Marketplace；IDEA 仍可通过 **Install Plugin from Disk** 安装构建产物或发布出的 ZIP。
