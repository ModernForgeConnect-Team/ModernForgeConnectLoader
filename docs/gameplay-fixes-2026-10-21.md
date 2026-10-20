# TC4 游戏行为修复（2026-10-21）

环境：Minecraft 1.20.1、Forge 47.4.28、Java 17、原始 Thaumcraft 4.2.3.5 / Baubles 1.0.1.10。原始模组 JAR 不修改；必要的 TC4 行为补丁只在可选适配器加载的私有运行时中生效。

## 实现

| 问题 | 原因与修复 |
| --- | --- |
| 奥能之扉开关无声 | 补全 `random.door_open/close` 的现代木门声音映射；旧辅助事件 1003 转为带坐标、音量、音高和排除玩家的声音。保留原版两种门样本随机选择的行为。 |
| 奥能之扉放置无声 | 原始 `ItemArcaneDoor` 绕过 `ItemBlock` 的放置声音。只在其成功放置分支补发一次放置音效，使用方块原始声音材质和音量/音高；服务端事务提交后广播，失败分支不播放。 |
| 九狱无法召唤 | 原始挥手调用不存在的旧 `EntityTracker`，导致整个施法事务回滚。保留原始挥手状态和物品回调，将动画交给宿主。另初始化旧世界的碰撞缓存和难度，避免召唤物在第一次移动时异常移除。原始 AI 留存的目标镜像在更新前刷新，目标移动、死亡和离开世界可被识别；实体更新中的运动变化也回写宿主。 |
| 奖励箱、古旧之瓮不出奖励 | 现代掉落入口改为调用原方块覆盖的 Forge `getDrops`，传入真实旧世界、坐标、metadata 和工具时运等级，执行原始战利品生成器。 |
| 藏宝袋无法开启 | 抽中有 NBT 的原版装备/附魔书，以及旧 metadata 药水时，旧物品无法转换，开启事务被回滚。新增显式转换，保留耐久、原版附魔、附魔书、显示信息和药水效果；通过二进制 NBT 读取旧列表，避免旧 SNBT 的索引格式无法被现代解析器接受。 |
| 元素箭效果不完整 | TC4 自定义伤害源的 setter 写入私有同名字段，基类的 getter 读不到；可选适配器改为调用继承的穿甲/火伤 setter。原始元素箭复制了完整箭矢更新，但又先运行父类普通箭更新，造成重复移动及普通命中抢先结算；改为保留一次基础实体更新，再执行一次原始元素箭移动和命中逻辑。 |
| 元素箭命中玩家崩溃、箭贴图缺失 | 旧玩家的 `canAttackPlayer` 访问不存在的旧服务器；绑定服务器的玩家改由宿主执行 PvP 和队伍友伤规则。查询镜像补齐创造/无敌能力，保留原始箭的免伤目标检查。旧 `minecraft:textures/entity/arrow.png` 显式映射到现代 `textures/entity/projectiles/arrow.png`。 |
| 宏伟之木、银树叶显示屏障图案 | 旧采集器只探测创造物品公开的 metadata 0、1，忽略放置后的状态 4、5 及其他状态位。现安全探测全部 16 个世界状态，原始图标方法仍决定树种；保留旧注册 ID 和存档 metadata，不拆分方块。 |
| 金币英文且不能堆叠 | 原始中文语言文件缺少 `item.ItemResource.18.name`，由 TC4 可选适配器补译“金币”。原采集器把各变体的最小堆叠上限作为整个物品的上限，导致 metadata 15 的限制 1 波及金币。现保留原始默认上限，并在现代每次查询具体堆叠上限时调用原始 `ItemStack.getMaxStackSize`，包括 metadata 与 NBT；金币为 64，原始护符仍为 1。 |

## 复现和验证

专用服务端测试模式 `-PmfclServerSmokeTest=gameplay` 使用一次性游戏目录，报告写入该目录的 `logs/mfcl/gameplay-smoke.json`。测试独立记录每项失败，最后写入汇总状态并退出，不能只由 Gradle 成功判断功能通过。

```powershell
$env:JAVA_HOME = 'C:/Users/gengy/.gradle/jdks/eclipse_adoptium-17-amd64-windows/jdk-17.0.17+10'
.\gradlew.bat --offline --project-cache-dir build/gameplay-cache `
  -I tools/isolated-build.init.gradle -PmfclValidationBuildDir=build/gameplay-validation `
  runServer -PmfclRunDir=build/gameplay-game `
  -PlegacyRuntimeDir=build/nei-investigation/runtime -PmfclServerSmokeTest=gameplay
```

目录需具备已接受的 EULA、独立端口，以及 `mods/1.7.10/` 中的原始 TC4/Baubles。测试会清理测试区域内先前遗留的猪、掉落物和原始投射物，不能在用户存档中运行。

覆盖范围：

- 实际门物品放置上下两半、消耗一件、按原版声明的金属材质只产生一次放置音效；阻挡时不扣物品、不发声；手动和压力盘各开关一次各发声一次，无权限时保持关闭并播放拒绝声音。
- 三种品质藏宝袋的真实现代右键入口、扣除和掉落；387 个确定性奖励样本的旧→现代→旧往返，检查物品、metadata、数量及 NBT，包含附魔书、装备和药水。
- 六种奖励方块品质的实际现代 `Block.getDrops` 入口，检查奖励生成且不掉落箱/瓮自身。
- 九狱无目标不扣费、有目标召唤、原始实体连续更新、追踪移动目标并造成伤害，以及目标移除后的召唤物清理。负例与正例之间用原版公开的 `WandManager.setCooldown(actor,0)` 隔离测试，避免连续同 tick 施法被真实冷却拒绝；生产逻辑保留原始冷却。
- 实际弓/弩射击全部六种元素箭、正常消耗弹药；用现代伤害事件检查风之矢穿甲，用真实目标检查点燃、减速、额外伤害、虚弱及凋零。伤害比较禁用暴击随机数；普通箭作为对照。
- 两种树叶分别实际放置，检查 metadata 4、5；核对全部 16 个状态的原始图标、生成模型和有效贴图。检查原始箭渲染路径转换。
- 金币中文名称、原生背包合并 20+30 为一叠 50、上限 64；逐个资源变体对比原始上限，并保留 metadata 15 的上限 1。
- 玩家命中使用真实 `ServerPlayer` 规则和空网络连接，过完出生保护后测试 PvP 开/关；Forge `FakePlayer` 自带无敌且禁止攻击玩家，不能用作此项伤害验收的替代。
- 通用旧运行时回归检查声音参数、排除玩家、辅助事件与原始挥手状态；另保留原始 TC4 法杖制作、充能、扣费、装卸核心和按键审计。

声音验收观察现代声音事件；客户端最终可听性、粒子与实体渲染仍需在实际客户端复核。本轮没有扩展旧模组专用附魔的现代效果；其原始附魔数据保留在物品来源 NBT 中，缺失映射进入诊断，不因抽中此类装备回滚整袋奖励。

创造模式元素箭消耗沿用原版：`EventHandlerEntity.bowShot` 只在创造模式且命中无限附魔的 33% 随机分支时跳过消耗；创造模式持未附无限的弓仍扣箭。这是原始 TC4 的条件逻辑，本轮未改变此规则。现代弩走现代原版装填规则。

## 本轮结果

最终专用服务端报告为 `PASS`，包括金币中文/合并、两种树叶放置状态、门声音、奖励、九狱、六种元素的弓/弩命中，以及 PvP 开/关的真实玩家命中。记录见 [验收数据](gameplay-fixes-progress.json)。

`test assemble -PlegacySupportDir=build/nei-investigation` 成功：97 项测试中 90 项通过、7 项跳过、0 失败。最终源代码再次运行 `auditThaumcraftWand`，10 项功能检查全部通过。构建使用上面相同的 Java 17 和隔离输出约定，测试/打包输出为 `build/gameplay-unit`，法杖测试目录为 `build/gameplay-wand-game`。

交付文件：`build/libs/mfcl-0.2.5.jar`，从完成测试的隔离输出复制并核对 SHA-256；已检查内嵌旧运行时包含三个新增 TC4 字节码适配器。JAR 校验值在验收数据中。原始 TC4 模组 JAR 的 SHA-256 保持 `3dba9786966974701578a658d1bb369bf35bdf5f363079f5ac9c4910a39113be`，未改动原始归档。
