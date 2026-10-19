# 功能请求：为原版隐藏物品提供独立的兼容测试创造栏（研究笔记等）

目标仓库：https://github.com/onodera2007/TC4-Modern

发布状态：GitHub 连接返回 HTTP 403 `Resource not accessible by integration`，尚未创建 issue。

## 需求

希望由 TC4: Modern / TC Modern Lib 为原版 TC4 中已注册、但未加入原版创造模式分类的物品提供独立的“兼容测试”分类，保留原版分类的内容和顺序。

这是 MFCL + 原版 TC4 联调中发现的测试可访问性需求，不是声称已在 TC4: Modern 当前版本复现了创造栏注册故障。

## 联调环境与例子

- Minecraft 1.20.1 / Forge 47.4.28 / Java 17
- Modern Forge Connect Loader（MFCL），开发版本 0.1.2
- 原版 Thaumcraft 4.2.3.5 + Baubles 1.0.1.10
- 现代侧使用 JEI 测试，保留 NEI 兼容代码

例如 `Thaumcraft:ItemResearchNotes` 已存在于旧注册表中，但原始物品没有设置创造模式分类。默认 metadata 0 的无 NBT 笔记也不等于可揭晓的未知笔记，因此只补一个默认 ItemStack 容易让测试者误以为物品失效。

原始 4.2.3.5 的右键代码识别 metadata 24/42 的未知笔记；已生成、未完成和已完成的研究笔记还依赖真实的原始 NBT，不能仅凭名称或 metadata 构造“可用”样本。

## 建议范围

- 新增独立的兼容测试创造栏，不把测试样本混入原版 TC4 分类。
- 收集未列入原版分类的已注册物品，以及原版配方实际输出的有效变体；按物品、metadata、NBT 去重。
- 对研究笔记提供有意义的测试入口，保留 metadata/NBT；区分空白样本、未知知识和具有有效研究数据的笔记。
- 不遍历任意 metadata 范围，也不自动解锁玩家研究来让样本“可用”。
- 通过 TC Modern Lib → MFCL 公开 API 实现；需要通用 API 能力时另列依赖，不引用 MFCL 的 internal/bridge/compat 实现。

研究笔记右键交互和翻译显示由 MFCL 侧继续处理，本 issue 只跟踪创造栏与测试样本整理。

## 验收建议

1. 原版创造栏条目、顺序与原始回调结果一致。
2. 新分类可通过中文名称搜索，条目保留正确 metadata/NBT，取出后不变成默认空白样本。
3. 研究笔记状态可辨认；需要原始研究数据的样本不伪装成可直接使用的笔记。
4. JEI 与创造栏展示同一物品状态，不把“原版有意隐藏”误报成“物品未注册”。

## 参考

- [MC百科：神秘时代4](https://www.mcmod.cn/class/204.html)
- [研究笔记](https://www.mcmod.cn/item/12134.html)
- [知识碎片](https://www.mcmod.cn/item/10351.html)

百科用于核对用途，具体状态和行为以所用原始 4.2.3.5 运行时为准。
