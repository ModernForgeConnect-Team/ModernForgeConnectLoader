# Legacy Dependency Resolver

[issue #1](https://github.com/gengyoubo/ModernForgeConnectLoader/issues/1#issuecomment-6257158895) 的独立 Gradle 工具。Java 17 / Gradle 8.8 验收，不依赖 MFCL、Minecraft 或 Forge。它把**明确声明的**旧依赖交给 Gradle 解析，或从用户提供的本地文件导入，再校验 SHA-256 并生成来源清单。

## 接入

在使用工具的项目 `settings.gradle` 中加入（路径按本地检出位置调整）：

```groovy
pluginManagement {
    includeBuild('../ModernForgeConnectLoader/tools/legacy-dependency-resolver')
}
```

`build.gradle`：

```groovy
plugins { id 'github.com.gengyoubo.mfcl.legacy-dependencies' }

repositories {
    // 由开发者明确选择可信仓库，工具本身不添加远程仓库。
    maven { url = uri('local-legacy-maven') }
}

legacyDependencies {
    artifact('legacy-library', 'example:legacy-library:1.0',
             providers.gradleProperty('legacyLibrarySha256').get(),
             '作者原始发布页或仓库地址', '记录适用许可及分发限制')

    local('legacy-mod', file('private-inputs/original-mod.jar'),
          providers.gradleProperty('legacyModSha256').get(),
          '原作者发布页；由开发者本地导入', '仅供本地兼容开发，按原许可处理')
}

tasks.named('resolveLegacyDependencies') {
    outputDirectory = layout.buildDirectory.dir('mfcl/legacy-dependencies')
}
```

所有条目都要求完整 64 位 SHA-256、来源和许可 / 分发说明。来源说明由调用者提供，工具不替调用者认定分发许可。优先使用可信发布者提供的校验值；对本地已有文件，可用 PowerShell `Get-FileHash -Algorithm SHA256` 检查当前内容。

执行 `gradlew resolveLegacyDependencies`。已缓存的 Gradle 依赖及本地导入可使用 `--offline`。使用 Gradle 自身的仓库、认证、代理和缓存机制；版本需固定，支持 Gradle 坐标中的 classifier。每条依赖独立解析，关闭传递依赖，其他旧前置需单独声明和锁定。

本工具不自动探测 / 下载旧 Forge 或模组，不扫描任意镜像，不执行原 JAR，不修改原文件，不把旧文件加入现代 `compileClasspath` 或 `runtimeClasspath`，也不自动安装到游戏目录。输出是受控输入，仍需按 MFCL 的运行时准备流程处理；它不能代替旧 Forge 二进制补丁和 SRG 准备步骤。

## 输出和失败行为

```text
build/mfcl/legacy-dependencies/
  <sha256>.jar
  legacy-dependencies.json
  .resolver.lock
```

清单包含格式版本，以及每条输入的 `id`、`requested` 坐标 / 本地标识、`sha256`、`origin`、`license` 和内容寻址文件名。无时间戳和机器绝对路径，相同声明产生相同清单。

全部输入通过校验并确认为非空 JAR 后才更新已接受的输出。任何哈希不符、重复 ID、无来源 / 许可说明、非法 JAR 或缺失依赖均使任务失败；保留原清单。更新使用文件锁及临时文件替换。已导入同哈希文件内容异常时明确失败。修改依赖声明后可能保留未再引用的旧内容文件，消费者应以清单为准。

## 验证

在 MFCL 仓库根目录执行：

```powershell
.\gradlew.bat -p tools/legacy-dependency-resolver test validatePlugins
```

TestKit 使用本地 Maven 夹具与本地 JAR，实际解析阶段加 `--offline`，验证固定版本、禁止传递解析、来源与哈希记录、重复执行、错误哈希保留清单、重复标识和无效 JAR 拒绝。测试框架依赖首次需要由 Gradle 获取。

当前工具不承诺 Gradle configuration cache 支持，也不内置依赖许可识别或旧运行时安装逻辑。
