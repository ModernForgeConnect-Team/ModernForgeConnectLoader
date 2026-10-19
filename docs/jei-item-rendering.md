# JEI 铁砧预览与旧物品 NBT 渲染

2026-10-19。用户报告 JEI 铁砧配方渲染邪教长袍时出现 `Host item NBT needs an explicit legacy mapping: RepairCost`。

着色器原先使用物品交互的严格 NBT 转换，JEI 附加的修复费用标签因此阻止了绘制。现在颜色与容器显示使用只读 `renderSnapshot`；宿主修复费用、名称和颜色在副本上转换，其他宿主标签保留在原物品上，交互路径的严格检查保持原有行为。

原 1.7.10 NBT 字符串包含 `[0:{…},1:{…}]` 形式的带索引列表，新版 `TagParser` 不接受此语法。显示用标签合并由原始 NBT 实现完成；原物品携带的要素列表等数据不经过新版解析器。

验收调用实际宿主物品颜色与 `GuiGraphics.renderFakeItem` 路径：带 `RepairCost`、自定义名称和宿主附加标签的邪教长袍正常绘制，原物品 NBT 未修改；116 个携带原始 NBT 的神秘时代物品变体正常绘制，着色与原物品一致。原运行时回归另检查带索引列表、子标签的保留，以及副本不修改源物品。结果记录在 [thaumometer-progress.json](thaumometer-progress.json)。

## 提示框覆盖物品的回归检查

`-PmfclTooltipSmokeTest` 在不安装 NEI、仅加载 Thaumcraft 和 Baubles 的环境中，直接调用 JEI 的 `ItemStackRenderer.render` 和 `renderBatch`。检查先绘制空白背景上的原生提示框，再绘制覆盖七种旧矿石图标的同一提示框，对比文字与背景区域的像素。允许半透明背景正常透出少量物品颜色，拒绝物品遮住文字或穿过背景。

2026-10-19：两条路径各检查 9,728 个像素，通过。运行报告为游戏目录下的 `logs/mfcl/jei-tooltip-smoke.json`，截图为 `screenshots/jei-tooltip-single.png` 和 `screenshots/jei-tooltip-batch.png`。这是实际 JEI 物品渲染器与原生提示框的绘制检查，不包含 JEI 完整侧栏交互。

## 名称与说明一致性

原生物品名称执行旧物品的动态命名方法，完整 tooltip 通过原始 `ItemStack.getTooltip` 生成，使用当前语言、原始 metadata/NBT 与客户端玩家。背包、设备槽位和 JEI 共享此内容；普通提示框不再显示桥接诊断行，F3+H 仍可查看来源信息。宿主自定义名称在显示副本中合并，显示操作不修改原物品。

2026-10-19：设备客户端探针核对 metadata 24 的研究笔记，原始 tooltip、现代物品 tooltip 和实际 JEI `ItemStackRenderer.getTooltip` 的中文内容逐行一致，包含“未知知识”。英文/中文切换及原始 NBT 不变检查通过。结果在 `logs/mfcl/device-smoke.json`。
