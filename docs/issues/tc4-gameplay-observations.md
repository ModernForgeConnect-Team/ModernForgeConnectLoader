# TC4 游戏内问题记录

环境：MFCL 当前工作区，原始 Thaumcraft 4.2.3.5。记录日期：2026-10-21。

## 奥能之扉开关无音效

- 状态：已定位静态兼容缺口，待修复、待游戏内回归；本轮仅记录。
- 用户现象：奥能之扉没有门的音效。
- 物品资料：https://www.mcmod.cn/item/6930.html
- 原始 JAR 的 `BlockArcaneDoor.playDoorSound` 明确调用 `random.door_open` / `random.door_close`，不是有意静音；无权限时另播 `thaumcraft:doorfail`。
- `src/main/java/github/com/gengyoubo/mfcl/bridge/LegacySounds.java` 的旧原版音效映射缺少上述两个门事件，应对应现代木门开关音效。
- 原始 `onPoweredBlockChange` 使用 `World.func_72889_a` 的旧辅助事件 1003；`LegacyWorlds` 当前没有该事件转发，需一并核对压力盘路径。
- 验收：手动开关、奥术压力盘开关、无权限反馈；客户端和服务端声音应正常且不重复。

## 法杖核心：九狱无法召唤焱蝠

- 状态：用户报告无法召唤，待专项复现和定位；尚未修复。
- 用户在说明需要瞄准生物后再次反馈“召唤不了生物”。具体目标、难度、灵气是否扣除和当次运行日志尚未确认。
- 物品资料：https://www.mcmod.cn/item/5359.html
- 原始 JAR 已核对：`ItemFocusHellbat.onFocusRightClick` 通过 `EntityUtils.getPointedEntity(world, player, 32, EntityFireBat.class)` 寻找目标；目标必须是 `EntityLivingBase`，排除焱蝠；对玩家还检查 PvP。消耗灵气成功后创建并生成 `EntityFireBat`，设置攻击目标和召唤标记。
- 预期：非和平难度，装备核心、灵气足够且瞄准无遮挡的有效生物时，应生成九狱焱蝠并攻击目标。对空气或方块不生成属于原版行为。
- 当前代码有通用实体路径：`LegacyWorlds.spawn` → `LevelWorldBridge.spawnLegacyEntity` → `LegacyHostedEntity`，因此不能仅凭没有九狱专用适配就判定未实现生成。
- 待排查：旧侧目标选择结果、实际施法回调和扣费、生成提交，以及焱蝠第一次服务端更新是否异常被移除。`LegacyHostedEntity.fail` 会记录异常并移除实体；尚无当次错误证据证明命中了该分支。
- 验收：固定目标与足量灵气，验证原始右键回调、生成数量、扣费、客户端可见性及持续追踪攻击；同时验证无目标不召唤的负例。

## 奖励类方块无法使用：废弃箱子、古旧之瓮等

- 状态：用户报告无法使用；已发现自定义掉落入口兼容缺口，待游戏内复现确认具体表现，尚未修复。
- 用户资料：https://www.mcmod.cn/item/list/204-1.html
- 已核对范围：废弃箱子 `Thaumcraft:blockLootCrate`（https://www.mcmod.cn/item/37783.html）、古旧之瓮 `Thaumcraft:blockLootUrn`（https://www.mcmod.cn/item/37782.html），各有 metadata 0/1/2 三种品质。其他奖励方块尚未逐项确认。
- 原版行为：打破方块掉落相应品质的战利品；这两种方块不是右键打开界面的储物箱。原始 `BlockLoot` 没有覆盖右键激活方法，而是覆盖 Forge `getDrops(World,int,int,int,int,int)`，调用 `Utils.generateLoot(metadata, world.rand)` 生成奖励。
- 已定位静态缺口：`ModernContentBridge.MirroredBlock.getDrops` 调用 `LegacyContent.drops`；后者仅调用旧 `quantityDropped` / `damageDropped` / `getItemDropped`，没有执行方块覆盖的 Forge `getDrops`，因此绕过了 `BlockLoot` 的战利品生成逻辑。需修复通用掉落桥接，并保留世界、坐标、metadata 和工具附魔上下文。
- 历史放置审计曾将这两类方块列为自定义渲染不支持而拦截放置；这是历史证据，不代表当前版本仍不能放置。
- 验收：在生存模式、允许方块掉落的条件下，分别放置/打破两类方块的三个合法品质，验证实际奖励与原始 `getDrops` 一致且不重复；另核对自然生成样本。若用户仅指右键无界面，应归为原版行为，而非交互故障。

## 稀有藏宝袋无法使用

- 状态：用户明确反馈“无法使用”，待专项复现与根因定位，尚未修复。
- 物品：`Thaumcraft:ItemLootBag`，metadata 2；资料：https://www.mcmod.cn/item/9860.html 。寻常、非凡品质是否同样受影响尚未确认。
- 原版行为已核对：`ItemLootBag.func_77659_a` 在服务端进行 8–12 次 `Utils.generateLoot(metadata, world.rand)` 抽取，对非空结果在玩家位置生成 `EntityItem`，播放 `thaumcraft:coins`，并将袋子数量减一。没有开箱界面。
- 现有路径：`ModernContentBridge.MirroredItem.use` → `LegacyItemUse.rightClick`，服务端通过 `LegacyItemDrops.capture` 接收原始掉落并在事务提交后生成现代掉落物，随后回写库存。这表明相关入口存在，不代表该物品已通过游戏内验收。
- 与废弃箱子/古旧之瓮分开跟踪：藏宝袋使用物品右键回调直接生成奖励，不走方块 `getDrops`；不能把方块掉落接口缺失直接作为本问题根因。
- 本次检索 `run/logs/latest.log` 未找到 `ItemLootBag` 或物品右键失败的对应记录；不能据此断言实际使用时没有异常。用户尚未说明袋子是否扣除、金币声及掉落是否出现。
- 待排查：右键是否进入服务端回调、奖励物品及 NBT 转换、掉落事务是否提交、袋子扣除与库存同步。
- 验收：主手持稀有藏宝袋右键，确认一次消耗一个袋子、奖励实际生成且可拾取、金币声正常；抽到原版物品和 TC4 物品都需覆盖，并回归其余两种品质及单个/堆叠袋子。

## 元素箭射出后疑似没有元素效果

- 状态：用户报告“发射的箭没有元素效果（？）”，保留为疑似兼容问题；具体箭种、发射武器、目标与缺失表现未确认，尚未专项复现或修复。
- 资料：https://www.mcmod.cn/item/37839.html ，该页面包含风、火、水、地、秩序、混沌六种元素箭，物品为 `Thaumcraft:PrimalArrow`，metadata 0–5。
- 已核对预期：风之矢主要效果是穿甲，不能只凭缺少显眼视觉效果判断失效；火之矢点燃目标，水之矢减速，地之矢提高伤害。命中效果与飞行渲染需分别检查。
- 原始 `EntityPrimalArrow` 有按元素类型分支的伤害源、点燃和药水效果逻辑，不应一律表现为普通箭。
- 当前代码：`LegacyWorlds.spawn` 仅将类恰好为原版 `EntityArrow` 的实体转换为现代普通箭，模组箭保留原始实体逻辑；`ThaumcraftCrossbow` 有现代弩替换为原始 `EntityPrimalArrow` 的路径；`LegacyEntityHooks` / `LevelWorldBridge` 有伤害、燃烧和药水效果转发。这些静态入口不等于实际命中验收通过，也不足以确定本次根因。
- 待排查：武器实际消耗的弹药、生成实体类型、元素 metadata/type 是否保留、实体碰撞命中及效果是否作用到现代目标。弓与弩需分别验证。
- 验收：同武器、同距离、同拉弓程度下对比普通箭；风之矢以有甲/无甲目标对比穿甲，火之矢检查非免火目标燃烧，水/秩序/混沌检查状态效果，地之矢检查伤害；另验客户端渲染、服务端效果及弹药消耗。
