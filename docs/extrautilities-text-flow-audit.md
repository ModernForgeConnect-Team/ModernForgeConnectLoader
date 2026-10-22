# Extra Utilities 文字与节点状态链路审计

2026-10-22，使用原始 `extrautilities-1.2.12.jar` 和已准备的 1.7.10 私有运行时验证。未恢复已移除的 `src/test`；专项程序保存在 `tools/extrautilities-text-audit/`。

## 修复

- `PacketTempChat` 能通过原始编解码器，但接收端写入的是旧 `GuiNewChat`。现在将旧聊天输出转发到现代 HUD，保留消息 ID 替换和多行提示缩短时的删除行为。
- 液体节点的真实储罐与 GUI 显示分别走方块实体 NBT 和 `ICrafting` 容器属性。原先后者发给无连接的旧玩家后丢失，GUI 可一直显示空罐。现在捕获原始属性，沿既有宿主通道传输，再调用客户端原始 `Container.updateProgressBar`；保留窗口 ID 校验和 signed-short 值语义。首次描述继续在原生开窗消息之后发送。
- QED 的 `WidgetEFText` 收到 `{Float, Byte}`，却先检查 `Boolean` 再转换 `Byte`，导致能量文字不可达。适配器将该类型检查修正为 `Byte`，保留原始数值和工作状态判断。

取水算法没有改动。液体节点的世界交互升级仍要求正对源水，且该格至少有两个水平源水邻居。`disableTransferNodeWatergen=false` 时才产水。

## 验证结果

`build/text-audit/report.json` 的 15 组专项检查为 `PASS`：

- 153 个声明及发电机标题翻译键在中英文表中齐全，26 个原始类的声明字面量全部匹配。
- 22 个物品变种执行真实 tooltip，按英文、中文、英文切换，未缓存上一语言。
- 普通聊天、直接聊天、按接收者捕获、临时聊天编解码和宿主队列、多行仪式提示、消息替换及删除通过。
- 灵魂原始回调的生命上限降低/恢复及中文反馈、采石场边界检测反馈通过。
- 发电机 GUI 中文标题、QED 有效/无效工作标志及运行时语言切换通过。
- 液体节点有升级时产水；单水源、流水和无升级场景不产水。正常 40 tick 后节点中有 4000 mB。
- 初始容器属性捕获 11 条，后续更新 3 条；GUI 从 1000 更新为 2000 mB，错误窗口 ID 被拒绝。原始 GUI 绘制捕获到 `储存： 2,000mB Water`。
- 在 Y=-60 复现水池、节点、上方管道和相邻桶的布局，100 tick 后桶中有 9000 mB，节点中有 1000 mB。

GUI 验证执行原始绘制代码并检查捕获命令；现代聊天验证检查消息列表更新和换行重建调用。此记录不代表已完成新版客户端截图、多人语言差异或所有节点变种的实机验收。

## 重跑

使用 JDK 17。原始 JAR 放在 `run/mods/extrautilities-1.2.12.jar`，私有运行时放在 `run/mfcl/runtime/1.7.10/`，执行：

```powershell
.\gradlew.bat --offline --project-cache-dir build/gui-state-cache `
  -I tools/isolated-build.init.gradle `
  -I tools/extrautilities-text-audit/audit.init.gradle `
  -PmfclValidationBuildDir=build/gui-state `
  auditTranslationFlows auditCreativeUpgrade auditDeviceInteractions assemble
```

程序使用 `build/text-audit/game/` 和 `build/text-audit/creative-game/`，不修改玩家存档。专项断言失败时进程返回非零。当前修复构建输出为 `build/gui-state/libs/mfcl-0.2.8.jar`。

Creative Upgrade 的设计描述是防止节点缓冲区耗尽，用作物品、液体和能量的无限源。不能把下述当前环境的验证结果表述为“设计上只对能量节点生效”。

独立原始运行时验证为 `CREATIVE_UPGRADE_PASS`：没有升级时为 0，加入升级后为 10000，消耗后为 9000，再次执行节点逻辑恢复到 10000 RF。

进一步检查真实输出路径，分别给物品节点预装 1 个钻石、液体节点预装 1000 mB 水，断开输入源，保留输出接收端并执行 200 tick。未装升级和装有创世升级时，接收端都只得到 1 个物品或 1000 mB 水，没有出现无限输出。当前这份 1.2.12 JAR 的相应输出路径与设计描述不符；能量路径的结果不能作为三种节点完整实现的证明。原始缓冲区输出字节码仍执行扣减，这一差异不能未经进一步比对就归因于 MFCL。

作者发布帖也说明创世升级会阻止节点缓冲区耗尽：[原帖说明](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/wip-mods/1443963-extra-utilities-v1-1-0k)。

## 储液桶取水与 GUI/区块并发绘制

`DeviceInteractionAudit` 使用独立的 `build/text-audit/device-game/` 执行原始储液桶激活回调。生存模式下，2000 mB 水配合单个空铁桶，得到水桶并剩余 1000 mB；创造模式同样扣除 1000 mB，但空铁桶保留。这是原始 `BlockDrum` 和当前 Forge `FluidUtil` 的创造模式语义。倒入水桶时，生存模式得到空桶，创造模式保留水桶，两者均向储液桶加入 1000 mB。这项原始回调测试记录上游行为，不代表创造模式交互已经满足玩家取水预期。

用户进一步指出“扣水但不给水桶”仍是使用问题。宿主流体交互现对创造模式下的原版空桶使用原版 `ItemUtils.createFilledResult` 语义：通过真实流体能力取出一桶液体，保留手中空桶，并在背包尚无对应满桶时加入一只满桶。生存模式和倒入液体仍使用原来的 Forge 交互。该修正适用于所有暴露旧流体储罐的镜像方块，不按模组名称硬编码。

`tools/fluid-container-audit/FluidContainerHostAudit.java` 通过独立 Forge 专用服务器测试原生 `ServerPlayerGameMode.useItemOn`、原版桶物品能力与旧储液桶能力。运行时仅将该工具源码加入单独的验证构建，不打入发布 JAR：

```powershell
.\gradlew.bat --offline --project-cache-dir build/fluid-container-audit/cache `
  -I tools/isolated-build.init.gradle `
  -I tools/fluid-container-audit/audit.init.gradle `
  '-PmfclValidationBuildDir=build/fluid-container-audit/output' `
  '-PmfclRunDir=build/fluid-container-audit/game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' runServer
```

独立游戏目录需放入原始 XU JAR，并沿用已有的 EULA 接受设置；测试使用自有世界，结果输出到该目录的 `fluid-container-report.json`。检查普通/强化储液桶、创造/生存、主手/副手、单桶/堆叠空桶、已有满桶、水/岩浆、倒入液体及不足一桶的情况。

原生 Forge 服务器验证为 `PASS`，共 32 个场景。每次成功取液体均扣除恰好 1000 mB；创造模式获得满桶并保留空桶，已有同类满桶时不重复生成。不足 1000 mB 的储液桶不扣液体、不生成满桶。

2026-10-22 16:19 的节点上下文渲染异常底层为 `Not tesselating!`。旧 `Tessellator` 是共享单例，原先 `LegacyWorldGraphics.begin()` 会在 GUI 尚未获取 `LegacyClientData.inLegacy` 锁时重置它，可能清掉工作线程正在提交的批次。专项用两个线程和闩锁稳定复现了这一竞争：修复前断言 `GUI initialization reset the chunk thread's active Tessellator` 失败。

恢复遗留批次的操作现仅在持有旧运行时锁的捕获入口执行。GUI 在锁外创建捕获帧时不接触共享绘制器；进入锁后仍能恢复先前失败的批次，嵌套旧运行时调用保留当前批次。专项同时验证并发 GUI/区块批次、嵌套调用和故障后的 GUI/区块恢复。这些是执行真实旧绘制器的确定性回归检查，不代表新版客户端画面的截图验收。

## 储液桶随液体着色与跨模组变种

原模组的 `LiquidColorRegistry` 读取液体图标纹理的平均颜色，并乘以该液体声明的颜色。放置后的 `BlockDrum` 从真实储罐读取颜色，物品的原始绘制器从流体 NBT 读取；普通桶和基岩筒保留各自的桶身纹理。

之前 MFCL 手动发布 `TextureStitchEvent.Post`，遗漏 `ForgeHooksClient.onTextureStitchedPost` 在事件后为原版水/岩浆调用 `Fluid.setIcons` 的步骤。专项确认两种液体的图标均为 null，液体颜色退回白色，物品与放置模型都只有灰白顶点。修复改为调用完整的原始 Forge Pre/Post 钩子，无需按模组或液体硬编码颜色。

`DrumAppearanceAudit` 检查原始流体图标、满桶 NBT、两种桶身纹理、原始物品/方块几何顶点颜色，以及抽空、非目录 NBT 的部分填充物品和重新填充后的颜色。仅 XU 时注册水/岩浆，生成 4 个满桶变种；加入实际 Baubles/Thaumcraft JAR 后注册 5 种液体，生成 10 个满桶变种（另有两个空桶）。神秘时代液体为 `fluidpure`、`fluxgoo`、`fluiddeath`。这些变种沿原始注册表目录/NEI 路径生成；不额外修改原模组对非空创造分类参数的筛选。

当前资源下，水的计算色为 `#2F43F4`，岩浆为 `#D45B12`；三种神秘时代液体也保留各自计算色。该颜色来自原始纹理与算法，不是新增的固定调色板。验证执行原始绘制器并检查捕获模型，没有将参考图片作为实现贴图，也没有声称完成新版客户端截图验收。

重跑原始运行时专项：

```powershell
.\gradlew.bat --offline --project-cache-dir build/gui-state-cache `
  -I tools/isolated-build.init.gradle `
  -I tools/extrautilities-text-audit/audit.init.gradle `
  '-PmfclValidationBuildDir=build/gui-state' `
  auditDrumAppearanceBasic auditDrumAppearanceIntegrated assemble
```
