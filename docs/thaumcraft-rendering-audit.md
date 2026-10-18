# 神秘时代 4 渲染审查

审查日期：2026-10-18。对象是用户本地的 **Thaumcraft 1.7.10-4.2.3.5**，目标宿主为 Forge 1.20.1 / Java 17。

本轮发现了先前截图之外的渲染错误，并修复了几处通用桥接问题；**没有据此认定神秘时代的全部渲染已经兼容**。

后续已接通守卫者玻璃和两种琥珀方块的世界特殊渲染，并修复光照 UV 覆盖贴图 UV，详见 [特殊方块渲染与验收](special-block-rendering.md)。以下背包审查的其余缺口仍适用。

## 输入与范围

- 原版 JAR：`run/mods/1.7.10/Thaumcraft-1.7.10-4.2.3.5.jar`。
- SHA-256：`3dba9786966974701578a658d1bb369bf35bdf5f363079f5ac9c4910a39113be`，原包未修改。
- 使用本机缓存的 Vineflower 1.10.1 反编译，得到 859 个 Java 文件。它们是本地反编译结果，不是官方发布的源码。
- 反编译输出保留在被 Git 忽略的 `build/thaumcraft-investigation/decompiled/`，没有把原版代码复制进项目源码。
- 全量文件和特征扫描后，重点对照 `ClientProxy` 的注册、165 个渲染器／模型源文件、84 个物品实现、69 个方块实现，以及 GUI、特效和 `UtilsFX`。
- `ClientProxy` 注册了 8 个自定义物品渲染器、21 个方块渲染器、50 个方块实体渲染绑定、50 个实体渲染绑定。绑定数与 Java 文件数不同，因为有复用渲染器的情况。
- 少数方法未能完整反编译，例如 `TileArcaneBoreBaseRenderer` 的一个方法；相关结论需要使用 `javap -c -p` 的字节码交叉检查，不能把反编译残缺当成原版逻辑。

可重新运行静态清单：

```powershell
./tools/audit-thaumcraft-rendering.ps1
```

输出 `build/thaumcraft-investigation/source-render-audit.json` 包含每个文件的分类、行数和渲染特征出现位置。这是扫描索引，不是“每个方法都已验证正确”的证明。

## 本轮修复

| 问题 | 原版证据与触发条件 | 桥接修复 |
| --- | --- | --- |
| 模型旋转了，光照法线没有旋转 | `ModelRenderer`、水晶和风箱等模型会旋转和非均匀缩放；`LegacyWorldGraphics.addTransformed` 原先只转换位置 | 法线使用旧矩阵的逆转置，并归一化；现代显示变换仍由现代法线矩阵处理 |
| 同一批顶点的多个 IIcon 被误认为同一张贴图 | `TileJarRenderer.renderLiquid` 等代码会在一个 Tessellator 批次中提交多个面；原解析器对整批 UV 取平均 | 新增 `LegacyIconResolver`，对独立四边形／三角形分别解析图集单元，并保持完整模型贴图的原始 UV |
| 自定义物品中的方块图集几何被当作整张图片复制 | `ItemJarFilledRenderer`、`ItemJarNodeRenderer` 混用模型图片和 `TextureMap.field_110575_b` | IItemRenderer 的结果也通过图集解析，不再尝试复制不存在的 `minecraft:textures/atlas/blocks.png` |
| 特殊模型捕获时缺少场景对象 | `TileCrystalRenderer`、`TileBellowsRenderer`、`TileJarRenderer.renderBrain` 读取玩家 ticks；钻机、法杖和节点读取视角实体；扫描器还读取玩家世界 RNG | 以 `LegacyInventoryContext` 临时提供玩家、视角实体和只读预览世界，完成后恢复原引用并解除绑定 |
| TESR 的贴图与字体管理器未设置 | 原版 `TileEntitySpecialRenderer` 通过 dispatcher 绑定贴图；先前只有普通 RenderManager 获得贴图管理器 | 初始化两个 dispatcher 所需的贴图、字体、设置；临时场景同时设置实体 RenderManager 的世界和视角 |
| 渲染中途异常仍可能发布半个模型，未结束的 primitive 会污染下一次捕获 | 旧版渲染器发生异常时可能已经提交部分顶点或处于 glBegin 内 | IItemRenderer 异常后不发布残缺结果；关闭失败的捕获也会清除 ThreadLocal，下一件物品可独立继续 |

预览世界只为执行背包渲染提供环境，不会改动玩家存档、开放特殊方块放置，或补齐设备运行逻辑。

## 尚未解决的确定缺口

### 0. 分解工作台背包模型仍捕获失败（P1，实测）

`Thaumcraft:blockTable@14` 是此次实测仍失败的一个变体。`BlockTableRenderer` 会调用 `TileDeconstructionTableRenderer`；后者除了桌体，还会通过实体 RenderManager 绘制一个嵌套的扫描器物品。当前桥接执行该路径仍报错，不能把退回的基础模型视为原版模型。

日志最后显示 TileEntity 的空世界访问；旧 dispatcher 在渲染异常后还会生成方块实体崩溃详情，因此这条最深层异常可能掩盖嵌套物品渲染的原始异常。需要单独保留原始异常再补齐该路径，本轮没有把它标成已修复。

### 1. 透明、发光与绘制顺序（P1）

`BlockJarRenderer`、`TileJarRenderer`、`ItemNodeRenderer`、`TileEtherealBloomRenderer` 等使用混合、alpha、深度写入控制。现有 `Primitive` 没有保存这些状态，`GuiPlatformAdapter` 对部分状态调用仅作空操作，`LegacyItemRenderer` 统一采用 `entityCutoutNoCull`，并按贴图重新分组。

因此，即使几何和贴图均已捕获，罐子玻璃、液体、节点光晕仍可能过于不透明、缺少加法发光，或出现遮挡顺序错误。后续接通的守卫者玻璃和琥珀方块按原版方块 pass 使用透明材质，但不表示这些混合多个状态的 TESR 已修复；它们仍需要保留逐批次渲染状态和顺序。

### 2. 背包快照被用于手持和掉落物（P1）

`LegacyContent` 只执行旧版 `INVENTORY` 分支，`LegacyItemRenderer` 随后对所有现代 `ItemDisplayContext` 使用这份几何。原版 `ItemWandRenderer`、`ItemThaumometerRenderer` 的 `EQUIPPED`、`EQUIPPED_FIRST_PERSON`、`ENTITY` 分支具有不同位移、缩放、动画及依赖；`ItemBowBoneRenderer` 甚至只接受 `EQUIPPED`。

因此背包显示通过，不能证明第一人称扫描仪、法杖施法姿态、骨弓拉弓和掉落物姿态正确。

### 3. 未收录的 NBT 组合与动态图标（P1）

旗帜和法杖已有按 NBT 区分的缓存，但只预捕获物品目录和创造栏提供的栈。`LegacyItemRenderer` 对未命中的 NBT 组合仍会回退基础几何。运行中组装的新杖杆／杖帽、装上核心后的法杖、新装液体和过滤标签的罐子，并不都在这份目录中。

此外，`ItemResource.getIcon` 的元数据 13 分支依赖 NBT 中的要素；当前生成模型的贴图层是从无 NBT 栈读取的。动态颜色回调已接通，但不能据此证明 NBT 选择的**图标本身**也正确。骨弓的五参数 `getIcon`、未声明元数据的研究笔记也需要独立验证。

### 4. 动画停留在捕获时刻（P1/P2）

节点、扫描仪屏幕、风箱、水晶闪烁等读取 ticks、时间和随机数。启动时捕获一次几何只代表某一时刻，不会随着宿主每帧重新运行旧版模型。某些材质自带 `.mcmeta` 的图集动画与这种代码驱动动画是两回事。

### 5. 世界、实体、粒子、盔甲渲染尚未完整桥接（P1，功能缺口）

`ClientProxy` 注册了大量实体／方块实体渲染器和旧事件处理器，但本项目目前主要发布现代物品模型和有限的方块对应物。旧世界事件、粒子引擎、盔甲模型和这些渲染器的全部状态，并不会自动接入现代渲染循环。

特殊方块“尚不能放置”的提示不是一种贴图错误；不能仅删除提示或放开放置，就宣称世界模型、碰撞和设备功能已经正确。

### 6. 纹理矩阵、自动纹理坐标与三维 GUI 操作（P2）

`ItemBowBoneRenderer` 的附魔层会切换纹理矩阵；`TileEldritchNothingRenderer`、镜面等使用纹理坐标生成。这些操作没有完整映射，不能等价为模型矩阵或静态 UV。`LegacyRenderBridge.rotate` 在 GUI 捕获中也不执行三维旋转，因此研究页面等原版 GUI 的全部视觉效果仍需逐项测试。

## 验证与证据

- JUnit：图集混合批次、完整模型贴图 UV、旋转／非均匀缩放法线、捕获异常恢复，以及真实旧运行时中的预览上下文建立和恢复。
- 客户端：隔离构建目录 `build/item-render-validation/`；创建新的测试世界，检查模型、旗帜、要素物品、翻译和存档重载。
- 运行日志：`build/thaumcraft-investigation/render-audit-client*.log`。
- 运行报告：`build/crash-render-smoke/game/logs/mfcl/content-1.7.10-smoke.json`，其中 `renderCaptureFailures` 必须单独查看。
- 本轮修改前的 `client-item-layers.log` 中有 18 条模型捕获失败；最终降至 1 条，为 `blockTable@14`。
- 最终 JUnit 共 47 项：43 项通过、4 项因缺少可选输入跳过、0 失败。客户端检查状态为 PASS，捕获了 106 个背包几何变体，存档重载通过；捕获失败清单仍包含上述分解工作台。
- 汇总结果见 [thaumcraft-rendering-audit.json](thaumcraft-rendering-audit.json)，其中包含原包与修复版 JAR 的哈希、注册数、测试数和剩余问题。

验收通过只表示该有限测试场景通过，**不表示上述所有缺口都已修复**。
