# Modern Forge Connect Loader

目标：在 Forge 1.20.1 / Java 17 中运行原始 Forge 1.7.10 模组。第一阶段验收样本固定为 `NotEnoughItems-1.7.10-1.0.5.120-universal.jar`，验收包括物品列表、搜索、配方和用途交互。

**已接通原始 NEI 的背包附着界面和配方页，完整 NEI 兼容仍未完成。**原始 NEI 在同一个 Java 17 进程内完成真实旧 FML 生命周期，并应用其 CoreMod 转换器和 AT。默认驱动旧 `GuiContainer` 上的原始 NEI 布局、物品面板和搜索框，保留现代背包；配方页执行原始 `GuiCraftingRecipe`，通过通用绘制层显示。此前的独立浏览器仅保留为开发诊断选项。

核心提供通用旧 `Item` / `ItemStack`、`World` / `WorldProvider`、`GuiScreen` / `GuiContainer` 类型桥接；CodeChicken 与 NEI 通过可选适配器接入。GUI 桥覆盖生命周期、槽位镜像、输入、矩形、文字、原始纹理和物品绘制，使用现代渲染器重放，不创建旧 OpenGL 上下文。尚未支持的底层绘制调用会明确失败；这不是完整 OpenGL 模拟器。World 当前仅覆盖时间、部分方块读写及对象身份，实际客户端绑定拒绝修改服务端状态。

当前 NEI 使用其自带的配方模式：给予物品、时间天气、旧网络操作尚未接入。现代背包仍由宿主处理操作；旧槽位仅镜像读取。复杂 NBT 槽位、自定义渲染及所有旧模组兼容均未完成。部分图标使用原始 2D 贴图回退；目录条目数不代表全部具有完整游戏行为。

## 正式目录约定

```text
<游戏目录>/
  mods/
    mfcl-0.1.0.jar
    1.7.10/
      NotEnoughItems-1.7.10-1.0.5.120-universal.jar
      CodeChickenCore-1.7.10-1.0.7.47-universal.jar
      CodeChickenLib-1.7.10-1.1.3.138-universal.jar
  mfcl/runtime/1.7.10/
    runtime-manifest.json
    minecraft-forge-1.7.10-srg.jar
    ...旧运行时依赖
```

MFCL 使用 Forge 的 `FMLPaths.GAMEDIR`，自动建立 `mods/1.7.10`，只读取该目录直属的 JAR。不会改写原始输入。报告写入 `<游戏目录>/logs/mfcl/legacy-1.7.10.json`。

Forge 的模组页面会合并显示旧模组，支持原有搜索和排序。选择旧模组后显示“游戏版本：1.7.10”、来源 JAR 及实际加载状态；仅扫描到文件的条目不会标为已加载。该展示不向现代 FML 的容器和注册表插入旧模组。

运行时报告为 `<游戏目录>/logs/mfcl/runtime-1.7.10.json`，记录真实 FML 阶段、转换器和适配器结果。成功时发现报告为 `CLIENT_DATA_AVAILABLE`，`legacyFmlStarted=true`；`legacyClientReady=false` 仍保留，以免将当前浏览功能误报为完整旧客户端支持。

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

准备默认 `runClient` 使用的目录并启动：

```powershell
./tools/prepare-legacy-runtime.ps1 `
  -NeiJar 'C:/Users/gengy/Desktop/NotEnoughItems-1.7.10-1.0.5.120-universal.jar' `
  -JavaHome 'C:/Users/gengy/.jdks/temurin-17.0.18' `
  -GameDirectory "$PWD/run"
$env:JAVA_HOME = 'C:/Users/gengy/.jdks/temurin-17.0.18'
./gradlew.bat runClient
```

进入世界后打开背包，NEI 自动显示在背包周围。使用底部原版搜索框筛选，左键查看配方，右键查看用途，`Esc` 返回。原始 NEI 的配置与键位逻辑仍保留。`-PmfclLegacyBrowser` 可启用此前的独立诊断浏览器。

自动游戏验收使用独立目录，新建独立平坦世界，完成查询、截图、返回和退出后自动关闭：

```powershell
./tools/prepare-legacy-runtime.ps1 -NeiJar 'C:/Users/gengy/Desktop/NotEnoughItems-1.7.10-1.0.5.120-universal.jar' -JavaHome $env:JAVA_HOME
./tools/test-nei-host.ps1 -JavaHome $env:JAVA_HOME -World
```

原版界面实测记录见 `build/nei-investigation/original-gui-world.log`，截图为 `build/nei-smoke/game/screenshots/mfcl-original-nei-overlay.png` 与 `mfcl-original-nei-recipe.png`。现代配方投影支持的数量取决于当前映射；特殊配方与复杂 NBT 仍有限制。原始 JAR 保持不变。

## 第一阶段剩余验收

- [x] 使用指定版本目录与原始 NEI 样本。
- [x] 识别 NEI 的 CoreMod 路径、自定义 `neimod.info` 和前置链。
- [x] 在 Java 17 独立诊断进程执行原始 NEICorePlugin 握手。
- [x] 在现代宿主内准备真实旧 Forge/Minecraft 输入并完成旧 FML 生命周期。
- [x] 应用 CodeChickenCore/NEI 的变换和 AT，保持现代同名类不变。
- [x] 接通旧 GuiContainer、基础字体/纹理/物品绘制及输入，驱动原始 NEI 布局与配方界面。
- [ ] 完整旧客户端、任意自定义 GUI 与渲染兼容。
- [x] 基础现代物品与配方映射，以及 NEI 列表、搜索、配方／用途查询和界面返回。
- [x] 实际进入世界、打开背包入口并退出世界。
- [ ] 验证进出世界、资源重载、库存操作及关闭过程。

技术依据：[第一轮](docs/research-forge-1710-to-1201.md)、[第二轮](docs/research-round2/README.md)。

测试计数、运行时阶段和客户端验收结果见 [验证记录](docs/nei-progress.json)。该记录明确保留 `phase1Complete=false`。
