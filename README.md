# Modern Forge Connect Loader

目标：在 Forge 1.20.1 / Java 17 中运行原始 Forge 1.7.10 模组。第一阶段验收样本固定为 `NotEnoughItems-1.7.10-1.0.5.120-universal.jar`，验收包括物品列表、搜索、配方和用途交互。

**当前是第一批加载基础设施实现，尚未达到 NEI 游戏内可用。**已接通现代 Forge 入口、版本目录发现、旧元数据/CoreMod/AT 识别和前置校验，实现了隔离加载器、注册屏障状态机，并提供原始 NEI 核心插件的独立执行探针。屏障尚未接入真实旧注册路径；完整旧 FML 生命周期、旧基础类准备、AT/NEITransformer 的实际应用及客户端桥接仍待实现。

## 正式目录约定

```text
<游戏目录>/
  mods/
    mfcl-0.1.0.jar
    1.7.10/
      NotEnoughItems-1.7.10-1.0.5.120-universal.jar
      CodeChickenCore-1.7.10-1.0.7.47-universal.jar
      CodeChickenLib-1.7.10-1.1.3.138-universal.jar
```

MFCL 使用 Forge 的 `FMLPaths.GAMEDIR`，自动建立 `mods/1.7.10`，只读取该目录直属的 JAR。不会改写原始输入。报告写入 `<游戏目录>/logs/mfcl/legacy-1.7.10.json`。

当前报告会明确标记 `NO_LEGACY_MODS`、`DEPENDENCY_ERRORS` 或 `RUNTIME_NOT_READY`，并保持 `legacyFmlStarted=false`、`legacyClientReady=false`。发现和插件探针结果不等于 NEI 已运行。

## 构建与检查

在 Java 17 环境运行：

```powershell
./gradlew.bat test build
./gradlew.bat inspectLegacyMods -PlegacyGameDir=<游戏目录>
```

产物为 `build/libs/mfcl-0.1.0.jar`。仅运行 `test` 时，未提供外部 NEI 样本的参考测试会明确跳过。

要复现真实 NEI 的全部参考检查：

```powershell
./tools/prepare-nei-probe.ps1 `
  -NeiJar 'C:/Users/gengy/Desktop/NotEnoughItems-1.7.10-1.0.5.120-universal.jar' `
  -JavaHome 'C:/Users/gengy/.jdks/temurin-17.0.18' `
  -RunChecks
```

脚本从项目/库发布地址准备并核对 [固定输入哈希](docs/nei-reference-inputs.json)，只在 `build/nei-investigation` 和 `build/nei-smoke/game` 中建立测试输入。NEI 原始文件由用户提供，不随产物分发。

原始核心插件探针在独立诊断 JVM 中执行构造、`injectData`、版本检查、容器/转换器声明和 setup hook，记录原始 CodeSource。该进程隔离只用于开发检查，不是正式双运行时的架构；旧启动器的静态状态和资源缓存不能据此宣称可在宿主内卸载。

真实现代宿主启动检查：

```powershell
./gradlew.bat runClient -PmfclRunDir=build/nei-smoke/game -PmfclHostSmokeTest
```

此开发选项在到达主菜单后打印 `MFCL_HOST_SMOKE_PASS` 并正常关闭客户端；正常启动不启用自动退出。这只验证现代宿主与版本目录入口，不能作为 NEI 功能验收。

## 第一阶段剩余验收

- [x] 使用指定版本目录与原始 NEI 样本。
- [x] 识别 NEI 的 CoreMod 路径、自定义 `neimod.info` 和前置链。
- [x] 在 Java 17 独立诊断进程执行原始 NEICorePlugin 握手。
- [ ] 在现代宿主内准备真实旧 Forge/Minecraft 输入并完成旧 FML 生命周期。
- [ ] 应用 CodeChickenCore/NEI 的变换和 AT，保持现代同名类不变。
- [ ] 接通旧客户端、GuiContainer、字体、纹理、输入与渲染。
- [ ] 映射真实现代物品和配方，验证 NEI 列表、搜索、R/U 及界面返回。
- [ ] 验证进出世界、资源重载、库存操作及关闭过程。

技术依据：[第一轮](docs/research-forge-1710-to-1201.md)、[第二轮](docs/research-round2/README.md)。

本批实现的测试计数、原始 NEI 插件执行结果和构建哈希见 [验证记录](docs/nei-progress.json)。该记录明确保留 `phase1Complete=false`。
