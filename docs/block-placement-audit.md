# 神秘时代 4 方块放置调查

日期：2026-10-18。统计依据是当前放置拦截代码和最近通过客户端验收的注册清单；没有把每个被拦截条目重复点击一次。

- 注册表共 **45 类方块**，其中 **35 类**仍被放置桥接拦截。
- 创造栏实际可见 **34 类方块 / 134 个元数据变体**，其中 **28 类 / 108 个变体**不能放置。
- 单独计算颜色/NBT：创造栏 **150 个方块物品条目**里有 **124 个**被拦截；相对元数据计数多出的 16 项来自彩色旗帜。
- “允许放置”表示通过当前桥接判定，不代表全部朝向、碰撞、交互、机器运行及生成行为均已兼容。非创造栏技术方块、未声明元数据及专用放置物品分开列出，避免混算。

## 创造栏不能放置的方块

| 方块注册名 | 元数据变体数 | 含 NBT 条目数 | 名称（元数据） | 原因 |
| --- | ---: | ---: | --- | --- |
| `Thaumcraft:blockAiry` | 1 | 1 | 灵气节点（0） | 没有普通方块网格，需接通方块实体专用渲染/放置 |
| `Thaumcraft:blockCandle` | 16 | 16 | 白色油脂蜡烛（0）、橙色油脂蜡烛（1）、品红色油脂蜡烛（2）、浅蓝色油脂蜡烛（3）、黄色油脂蜡烛（4）、柠檬色油脂蜡烛（5）、粉色油脂蜡烛（6）、灰色油脂蜡烛（7）、浅灰色油脂蜡烛（8）、青色油脂蜡烛（9）、紫色油脂蜡烛（10）、蓝色油脂蜡烛（11）、棕色油脂蜡烛（12）、绿色油脂蜡烛（13）、红色油脂蜡烛（14）、黑色油脂蜡烛（15） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockChestHungry` | 1 | 1 | 饕餮箱子（0） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockCrystal` | 7 | 7 | 风之魔晶（0）、火之魔晶（1）、水之魔晶（2）、地之魔晶（3）、秩序魔晶（4）、混沌魔晶（5）、复相魔晶（6） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockCustomOre` | 8 | 8 | 朱砂矿石（0）、风之蕴魔石（1）、火之蕴魔石（2）、水之蕴魔石（3）、地之蕴魔石（4）、秩序蕴魔石（5）、混沌蕴魔石（6）、琥珀矿石（7） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockCustomPlant` | 6 | 6 | 宏伟之木树苗（0）、银树树苗（1）、水银花（2）、火焰草（3）、天域花（4）、纤毛菇（5） | 植物交叉面及生长/附着放置尚未桥接 |
| `Thaumcraft:blockEldritch` | 1 | 1 | 发光荒古石头（4） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockEssentiaReservoir` | 1 | 1 | 源质库（0） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockFluidDeath` | 1 | 1 | 死亡之水  （0） | 旧流体世界渲染及放置尚未桥接 |
| `Thaumcraft:blockFluidPure` | 1 | 1 | 净化之水（0） | 旧流体世界渲染及放置尚未桥接 |
| `Thaumcraft:blockFluxGas` | 1 | 1 | 咒波瓦斯（0） | 咒波气体世界渲染/放置尚未桥接 |
| `Thaumcraft:blockFluxGoo` | 1 | 1 | 咒波粘浆（0） | 旧流体世界渲染及放置尚未桥接 |
| `Thaumcraft:blockJar` | 3 | 3 | 源质罐子（0）、缸中之脑（1）、虚空罐子（3） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockLifter` | 1 | 1 | 奥术悬浮台（0） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockLootCrate` | 3 | 3 | 废弃箱子（0）、废弃箱子（1）、废弃箱子（2） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockLootUrn` | 3 | 3 | 古旧之瓮（0）、古旧之瓮（1）、古旧之瓮（2） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockMagicalLog` | 2 | 2 | 宏伟之木原木（0）、银树原木（1） | 原木侧面/端面及轴向元数据尚未桥接 |
| `Thaumcraft:blockMetalDevice` | 11 | 11 | 坩埚（0）、奥术蒸馏器（1）、魔力充能中继器（2）、高级炼金构材（3）、物品格栅（5）、奥术灯（7）、催生灯（8）、炼金构材（9）、记忆矩阵（12）、育种灯 （13）、魔力中继器 （14） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockMirror` | 2 | 2 | 魔镜（0）、源质之镜（6） | 没有普通方块网格，需接通方块实体专用渲染/放置 |
| `Thaumcraft:blockStairsArcaneStone` | 1 | 1 | 奥术楼梯（0） | 楼梯形状、朝向和碰撞尚未桥接 |
| `Thaumcraft:blockStairsEldritch` | 1 | 1 | 荒古楼梯（0） | 楼梯形状、朝向和碰撞尚未桥接 |
| `Thaumcraft:blockStairsGreatwood` | 1 | 1 | 宏伟之木楼梯（0） | 楼梯形状、朝向和碰撞尚未桥接 |
| `Thaumcraft:blockStairsSilverwood` | 1 | 1 | 银树楼梯（0） | 楼梯形状、朝向和碰撞尚未桥接 |
| `Thaumcraft:blockStoneDevice` | 11 | 11 | 炼金炉（0）、奥术基座（1）、符文矩阵（2）、法杖充能基座（5）、复合充能核心（8）、节点稳定器（9）、高级节点稳定器（10）、节点换能器（11）、奥术浴场（12）、核心镶饰台（13）、咒波洗涤器（14） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockTable` | 3 | 3 | 台桌（0）、解构工作台（14）、奥术工作台（15） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockTaintFibres` | 4 | 4 | 腐化草丛（0）、腐化草地（1）、腐化植物（2）、孢子梗（3） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockTube` | 8 | 8 | 源质管道（0）、源质阀门（1）、奥法离心机（2）、过滤源质管道（3）、源质缓存器（4）、限定源质管道（5）、定向源质管道（6）、源质结晶器（7） | 自定义世界渲染未通过当前支持探测，放置被拦截 |
| `Thaumcraft:blockWoodenDevice` | 8 | 24 | 奥术风箱（0）、奥术之耳（1）、奥术压力盘（2）、奥术钻探机基座（4）、奥术钻探机（5）、宏伟之树木板（6）、银树木板（7）、血腥教徒旗帜（8） | 自定义世界渲染未通过当前支持探测，放置被拦截 |

## 未在创造栏展示的其他被拦截方块

- `Thaumcraft:blockAlchemyFurnace`：没有普通方块网格，需接通方块实体专用渲染/放置。
- `Thaumcraft:blockArcaneDoor`：门的朝向、上下两半及专用放置尚未桥接。
- `Thaumcraft:blockArcaneFurnace`：自定义世界渲染未通过当前支持探测，放置被拦截。
- `Thaumcraft:blockEldritchNothing`：没有普通方块网格，需接通方块实体专用渲染/放置。
- `Thaumcraft:blockManaPod`：植物交叉面及生长/附着放置尚未桥接。
- `Thaumcraft:blockPortalEldritch`：没有普通方块网格，需接通方块实体专用渲染/放置。
- `Thaumcraft:blockWarded`：自定义世界渲染未通过当前支持探测，放置被拦截。

## 原版专用物品放置入口

这些不是 BlockItem，不计入上面的 108 个变体。当前注册为普通 MirroredItem，没有转发原版 useOn / onItemUseFirst。原包源码确认至少还有以下 6 类入口，其中 4 个条目出现在当前创造栏；另外两个是装满的罐子。

| 名称 | 物品 | 创造栏条目数 | 原版放置目标 |
| --- | --- | ---: | --- |
| 奥能之扉 | `Thaumcraft:ItemArcaneDoor@0` | 1 | `Thaumcraft:blockArcaneDoor` |
| 魔豆 | `Thaumcraft:ItemManaBean@0` | 1 | `Thaumcraft:blockManaPod` |
| 闪耀之光 | `Thaumcraft:ItemResource@1` | 1 | `Thaumcraft:blockAiry@1` |
| 邪术方尖碑敷设器 | `Thaumcraft:ItemEldritchObject@4` | 1 | `Thaumcraft:blockEldritch@0,1,2` |
| 要素罐子 | `Thaumcraft:BlockJarFilledItem@0` | 0 | `Thaumcraft:blockJar` |
| 缸中节点 | `Thaumcraft:BlockJarNodeItem@0` | 0 | `Thaumcraft:blockJar` |

## 已允许的特殊方块

守卫者玻璃及两种琥珀方块（`Thaumcraft:blockCosmeticOpaque@0,1,2`）已通过世界渲染支持探测；其他 renderType 非 0 的变体仍被拦截。截图的风之魔晶 `Thaumcraft:blockCrystal@0` 属于被拦截的 7 种魔晶之一。

完整元数据、统计口径和原版专用放置源文件见 [JSON 清单](block-placement-audit.json)。重新统计：`./tools/audit-block-placement.ps1`。
