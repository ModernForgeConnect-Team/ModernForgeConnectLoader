# JEI 铁砧预览与旧物品 NBT 渲染

2026-10-19。用户报告 JEI 铁砧配方渲染邪教长袍时出现 `Host item NBT needs an explicit legacy mapping: RepairCost`。

着色器原先使用物品交互的严格 NBT 转换，JEI 附加的修复费用标签因此阻止了绘制。现在颜色与容器显示使用只读 `renderSnapshot`；宿主修复费用、名称和颜色在副本上转换，其他宿主标签保留在原物品上，交互路径的严格检查保持原有行为。

原 1.7.10 NBT 字符串包含 `[0:{…},1:{…}]` 形式的带索引列表，新版 `TagParser` 不接受此语法。显示用标签合并由原始 NBT 实现完成；原物品携带的要素列表等数据不经过新版解析器。

验收调用实际宿主物品颜色与 `GuiGraphics.renderFakeItem` 路径：带 `RepairCost`、自定义名称和宿主附加标签的邪教长袍正常绘制，原物品 NBT 未修改；116 个携带原始 NBT 的神秘时代物品变体正常绘制，着色与原物品一致。原运行时回归另检查带索引列表、子标签的保留，以及副本不修改源物品。结果记录在 [thaumometer-progress.json](thaumometer-progress.json)。
