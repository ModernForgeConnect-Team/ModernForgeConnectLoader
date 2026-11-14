# MFCL Legacy Navigation for VS Code

此扩展为 Java 注解中的成员名称提供补全和“转到定义”：

- `@LegacyType("...")` 或 `@LegacyType(type = Target.class)` 内的 `@LegacyMethod(name = "...")` 和 `@LegacyField(name = "...")`。
- `@Mixin(Target.class)` 或 `@Mixin(targets = "...")` 内常用的 Sponge Mixin injection 注解（`Inject`、`Redirect`、`ModifyArg`、`ModifyArgs`、`ModifyVariable`、`ModifyConstant`）的 `method` 字符串，包含数组。描述符不会进入名称替换范围，同名重载会返回多个定义。

## 安装与使用

需要 VS Code 1.85 或更高版本、[Language Support for Java by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.java)，并等待 Java 项目导入和索引完成。Gradle 从仓库根目录的 `gradle.properties` 读取 `mod_version`。安装 Node.js 后，在仓库根目录运行：

```powershell
.\gradlew.bat -p vscode-extension buildPlugin
```

在 VS Code 扩展视图右上角的 `...` 菜单中选择 **从 VSIX 安装**，选择 `vscode-extension/build/distributions/` 下生成的 `.vsix`。也可以用 `code --install-extension <文件路径>` 安装。打开 Java 项目后，在注解字符串中使用 Ctrl+Space 补全或 F12 / Ctrl+单击跳转。

目标类必须能通过 Java 扩展的工作区符号和文档符号提供器找到。对于旧版 Minecraft 类，先配置 `mfcl.direct`、运行 `directDeobfMinecraft` 并重新导入 Gradle 项目；具体能否索引依赖 JAR 和显示反编译结果取决于 Java 扩展及项目配置。当前仅检索目标类自身可见于文档符号的成员，不检索继承成员；同名类可能受 Java 索引返回顺序影响。支持显式 import 和完整类名，通配符 import、转义字符串、带 `Lowner;` 的成员选择器、SRG/MCP 名称转换和按 JVM 描述符筛选重载暂不支持。此扩展不执行 Mixin 注入或安装旧版 Forge。

运行解析器测试：`npm test`。


## 发布到本地 Maven 仓库

在仓库根目录运行 `.\gradlew.bat -p vscode-extension publish`。该命令构建 VSIX 并将其和 POM 发布到 `mcmodsrepo`，坐标为 `github.com.gengyoubo.mfcl:mfcl-vscode:<版本>`（扩展名 `vsix`），不发布到 VS Code Marketplace。

