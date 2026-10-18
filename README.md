# Modern Forge Connect Loader

目标：在 Forge 1.20.1 / Java 17 中运行原始 Forge 1.7.10 模组。首个回归样本为 `NotEnoughItems-1.7.10-1.0.5.120-universal.jar`，检查物品列表、搜索、配方和用途交互。当前 `runClient` 测试目录已切换为 AcademyCraft 1.0.7 与 LambdaLib 1.2.3；NEI 及 CodeChicken 前置已移至 `run/mfcl/disabled-mods/nei-20261018/`，独立的 NEI 回归夹具仍保留在构建测试目录。

**AcademyCraft 已完成真实旧 FML 初始化，并接通基础内容注册，完整游戏行为仍未完成。** 原始 AcademyCraft 和 LambdaLib 已通过构造、`PreInit`、`Init`、`PostInit`，状态为 `AVAILABLE`。通用注册桥在现代注册窗口内提交 23 个方块和 58 个物品；原 JAR 中的纹理和语言生成独立资源包，metadata 保存在物品标签和方块状态中。普通机械框架与 4 种矿石接入基础放置、原始掉落方法、方块选取及保存重载；需要旧 TileEntity 或特殊渲染的 18 个方块暂不开放放置。玩家能力、机器运转、旧网络、世界生成及完整世界绘制仍不可用。调查过程和实际客户端报告见 `docs/academy-progress.json`。

**Thaumcraft 4（神秘时代 4）也已完成真实旧 FML 初始化，并接通基础内容注册。** 原始 `Thaumcraft-1.7.10-4.2.3.5.jar` 及其必需前置 `Baubles-1.7.10-1.0.1.10.jar` 通过构造、`PreInit`、`Init`、`PostInit`，状态为 `AVAILABLE`。Thaumcraft 自带 CodeChicken `DepLoader` CoreMod（`thaumcraft.codechicken.core.launch.DepLoader`），会在加载时扫描 `dependancies.info` 并从不复存在的 dropbox 地址下载 Baubles；把未经修改的 Baubles 放入 `mods/1.7.10` 后该检查直接通过，不会触发下载。通用注册桥提交 45 个方块和 154 个物品（含 1 个 Baubles 物品）：普通方块与带旧 TileEntity 的方块（如 `blockMagicBox`）可放置、掉落、选取并保存重载；`blockCosmeticSolid`、`blockHole` 这类仅在部分 metadata 生成 TileEntity、或声明 `hasTileEntity` 却不生成旧 TileEntity 的技术方块不再强行创建。方块物品按原版 `getRenderType` 选择库存模型：矿石、原木、流体、机械等以逐面纹理的立方体显示，十字植物、作物、门、纤维与蜡烛保持平面贴图，且每个 metadata 使用各自的方块图标。法杖、炼金、灵气网络、研究系统、原始 GUI 与自定义渲染仍未接通。调查与实测报告见 `docs/thaumcraft-progress.json`。

注册屏障位于现代模组构造与 `RegisterEvent` 之间：先完成真实旧 FML 并冻结内容声明，再在现代注册事件中提交代理对象。保留合法原 ID；含大写等新版不允许字符的旧 ID 使用 `mfcl:legacy/<原 ID 的 UTF-8 十六进制>`，对应关系可在 `<游戏目录>/mfcl/generated-packs/content-1.7.10-*/mfcl-content-manifest.json` 查询。创造栏为“旧版模组 · 1.7.10”，沿用旧物品的创造栏可见性。原始内部占位物品仍被注册，但不会因目录扫描而出现在创造栏。

当前掉落桥执行原始 `getItemDropped`、`damageDropped`、`quantityDroppedWithBonus` 基础方法（时运等级暂为 0）；完整旧 `getDrops`、工具等级、精准采集与旧事件尚未覆盖。物品的旧 NBT 使用原格式封装，回到旧域时由真实旧 NBT 解析器恢复；额外现代 NBT 尚无映射时明确拒绝转换。客户端世界代理只读，服务端世界代理在服务器线程同步读写；时间、基础方块与 metadata 已覆盖，完整区块与方块实体接口仍待实现。

**已接通原始 NEI 的背包附着界面和配方页，完整 NEI 兼容仍未完成。**原始 NEI 在同一个 Java 17 进程内完成真实旧 FML 生命周期，并应用其 CoreMod 转换器和 AT。默认驱动旧 `GuiContainer` 上的原始 NEI 布局、物品面板和搜索框，保留现代背包；配方页执行原始 `GuiCraftingRecipe`，通过通用绘制层显示。此前的独立浏览器仅保留为开发诊断选项。

核心提供通用旧 `Item` / `ItemStack`、`World` / `WorldProvider`、`GuiScreen` / `GuiContainer` 类型桥接；CodeChicken 与 NEI 通过可选适配器接入。GUI 桥覆盖生命周期、槽位镜像、输入、矩形、文字、原始纹理和物品绘制，使用现代渲染器重放，不创建旧 OpenGL 上下文。尚未支持的底层绘制调用会明确失败；这不是完整 OpenGL 模拟器。World 当前仅覆盖时间、部分方块读写及对象身份，实际客户端绑定拒绝修改服务端状态。

当前 NEI 使用其自带的配方模式：给予物品、时间天气、旧网络操作尚未接入。现代背包仍由宿主处理操作；旧槽位仅镜像读取。复杂 NBT 槽位、自定义渲染及所有旧模组兼容均未完成。部分图标使用原始 2D 贴图回退；目录条目数不代表全部具有完整游戏行为。

## 正式目录约定

```text
<游戏目录>/
  mods/
    mfcl-0.1.0.jar
    1.7.10/
      AcademyCraft-1.0.7.jar
      LambdaLib-1.2.3.jar
  mfcl/runtime/1.7.10/
    runtime-manifest.json
    minecraft-forge-1.7.10-srg.jar
    ...旧运行时依赖
```

MFCL 使用 Forge 的 `FMLPaths.GAMEDIR`，自动建立 `mods/1.7.10`，只读取该目录直属的 JAR。不会改写原始输入。报告写入 `<游戏目录>/logs/mfcl/legacy-1.7.10.json`。

Forge 的模组页面会合并显示旧模组，支持原有搜索和排序。选择旧模组后显示“游戏版本：1.7.10”、来源 JAR 及实际加载状态；仅扫描到文件的条目不会标为已加载。旧模组容器仍由隔离的旧 FML 持有；基础内容代理另由通用注册桥提交。

启动完成提示和主菜单计数使用同一份运行时目录，只统计旧 FML 实际完成加载的模组。NEI 回归环境已验证总数为 5：现代环境 3 个，加上 1.7.10 的 CodeChickenCore 和 NEI；CodeChickenLib 依赖库及旧 FML 内置组件不重复计数。主菜单同时标明两个游戏版本的数量。AcademyCraft 环境现在为 6 个：现代 3 个，加上 AcademyCraft、LambdaLib 和 LambdaLib Core 容器。旧模组仍属于隔离的旧 FML，现代 `ModList` 的查询和网络握手不因此包含旧模组。启动提示和主菜单品牌缓存适配当前 Forge 47 的内部字段，升级 Forge 时需重新验证。

通用准备步骤还会安装 Forge 1.7.10 原本附带的 Scala 2.11.1 / Typesafe 库；版本来自 Forge 的 `version.json`，规范 Maven 分发文件的固定校验值列于 `docs/legacy-language-inputs.json`。旧 CoreMod 提供的 `DummyModContainer` 常量元数据可被被动识别，包括带 `|` 的旧模组 ID。已覆盖的旧 GL 显示列表命令可在 `GL_COMPILE` 阶段缓存，保留原始顶点、颜色等参数，按旧 GL 类身份隔离；世界绘制重放尚未接通，调用实际重放会明确报错。

运行时报告为 `<游戏目录>/logs/mfcl/runtime-1.7.10.json`，记录真实 FML 阶段、转换器和适配器结果。通用 FML 初始化成功时状态为 `FML_AVAILABLE`，NEI 可选适配器就绪时为 `CLIENT_DATA_AVAILABLE`；二者均有 `legacyFmlStarted=true`。`legacyClientReady=false` 仍保留，以免误报为完整旧客户端支持。

通用原生渲染桥按旧 GL 类身份绑定，每个运行时拥有独立纹理句柄；删除后重新绑定同一旧编号会创建新的宿主纹理。着色器与纹理调用会恢复宿主的程序、纹理、pixel-store 和像素传输缓冲状态。旧 `GL_CLAMP` 暂映射为 `GL_CLAMP_TO_EDGE`，边界采样行为存在差异。Java 17 反射兼容只适配旧域中的 `Field.modifiers` 调用，不修改 JDK 全局反射过滤或启动参数。JDK XML 模块可共享，ASM 等宿主库仍严格隔离。

三维旧渲染进入通用桥接阶段：`LegacyWorldGraphics` 在旧域捕获真正的旧 Tessellator 顶点数组（`glVertexPointer`/`glTexCoordPointer`/`glColorPointer`/`glNormalPointer`/`glDrawArrays`）与立即模式调用，连同 `glPushMatrix`/`glTranslate`/`glScale`/`glRotate` 的变换和纹理绑定，产出与宿主无关的顶点流（位置、UV、法线、颜色、纹理）。`LegacyVertexStream` 负责矩阵数学与重放，`LegacyDisplayLists` 的 `glCallList` 会把已编译的旧显示列表展开进当前捕获，而不是写入宿主 OpenGL。`LegacyRenderBridge` 在 2D GUI 捕获与 3D 世界捕获之间分派；现代侧 `LegacyWorldRenderer` 用 `PoseStack`/`VertexConsumer` 重放三角化后的几何。静态旧方块模型已接通：内容冻结阶段用真实旧 `RenderingRegistry.renderInventoryBlock` 执行原始 `ISimpleBlockRenderingHandler`（`LegacyBlockRenderer`），把几何捕获进顶点流。图标身份通过合成图集单元还原——旧 `IIcon` 代理把每张纹理分配到一个虚拟图集格，捕获的四边面 UV 落在某一格即可反查原图标，再把局部 UV 映射到现代方块图集 `TextureAtlasSprite`（`sprite.getU(local*16)`）后重放。`builtin/entity` 物品模型经 `Item.initializeClient` 注册的自定义渲染器 `LegacyItemRenderer` 重放捕获几何，纹理为独立图集精灵而非 Sprite ID。真实旧 Tessellator 的 `BlockRenderer.drawFaces` 会先 `glTranslatef(-0.5)` 使几何居中，重放时按现代方块物品约定平移并套用标准显示变换。Thaumcraft 的矿石、装饰方块等已能显示正确的逐面/叠加纹理；依赖 TileEntity 或世界上下文的渲染器（水晶、罐子等）暂回退为立方体。动态 `TileEntitySpecialRenderer` 的调用与缓存仍是下一步。

原生 GPU 与模组列表复测使用 `runClient -PmfclOpenGlSmokeTest -PmfclModListSmokeTest`。`mfclOpenGlSmokeTest` 会通过真实旧 LWJGL 入口验证上传读回、着色器 uniform、纹理重建和宿主状态恢复；它需要图形客户端，独立无图形旧运行时不提供 GPU 服务。

AcademyCraft 内容验收使用独立游戏目录（其中 `mods/1.7.10` 应只有原始 AcademyCraft 和 LambdaLib），自动新建平坦世界，测试放置、掉落、旧 World 同步读写、真实旧 ItemStack NBT、纹理图集与原生创造栏，再保存退出并重进验证：

```powershell
./gradlew.bat runClient `
  '-PmfclRunDir=build/academy-smoke/game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' `
  -PmfclContentSmokeTest --offline --console=plain
```

验收报告为该游戏目录的 `logs/mfcl/content-1.7.10-smoke.json`。正常启动不启用测试参数。

Thaumcraft 内容验收复用同一套内容桥，独立游戏目录的 `mods/1.7.10` 中只需原始 Thaumcraft 与 Baubles。脚本会核对 [固定输入哈希](docs/thaumcraft-reference-inputs.json)，Baubles 可由 `-BaublesJar` 提供，缺失时从记录的镜像地址下载并校验：

```powershell
./tools/prepare-thaumcraft-probe.ps1 `
  -ThaumcraftJar 'C:/Users/gengy/Desktop/Thaumcraft-1.7.10-4.2.3.5.jar' `
  -JavaHome 'C:/Users/gengy/.jdks/temurin-17.0.18' `
  -LegacyRuntimeDir 'run/mfcl/runtime/1.7.10' `
  -RunChecks
```

该运行随后向 `runClient` 传入 `-PmfclContentSmokeTest`，通过时输出 `MFCL_CONTENT_WORLD_SMOKE_PASS`，报告写入 `build/thaumcraft-smoke/game/logs/mfcl/content-1.7.10-smoke.json`。

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

当前默认 `runClient` 目录已准备好旧运行时，`mods/1.7.10` 中为 AcademyCraft 与 LambdaLib。启动：

```powershell
$env:JAVA_HOME = 'C:/Users/gengy/.jdks/temurin-17.0.18'
./gradlew.bat runClient
```

AcademyCraft 测试中可在创造模式的“旧版模组 · 1.7.10”页取得内容。以下 NEI 操作只适用于独立 NEI 回归目录：进入世界后打开背包，NEI 自动显示在背包周围；使用底部原版搜索框筛选，左键查看配方，右键查看用途，`Esc` 返回。原始 NEI 的配置与键位逻辑仍保留。`-PmfclLegacyBrowser` 可启用此前的独立诊断浏览器。

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
