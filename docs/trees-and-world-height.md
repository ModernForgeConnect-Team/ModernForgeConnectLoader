# 树木、树叶掉落与世界高度审计

2026-10-31。宿主 Forge 1.20.1，私有运行时 Forge 1.7.10，原始 Thaumcraft 4.2.3.5、Baubles 1.0.1.10、Extra Utilities 1.2.12。

## 修复与边界

旧世界桥接现在提供宿主维度的最低高度和最高高度（上界不含）。未绑定宿主世界时仍返回原始 `0..256`。`WorldHeightTransformer` 对有坐标数据流证据的世界回调、生成器及方块实体边界判断替换上下界，包括负高度判断和 `y == 255` 放置限制；保留数组长度、掩码及不能确认用途的常量。它作用于私有运行时中的旧类，不修改原始模组 JAR。

`World.checkChunksExist` 的立面改为检查宿主高度交集和已加载区块，不再无条件成功，也不主动加载区块。TC4 树叶腐烂依赖这个查询。

镜像方块现在读取原始 `Block.SoundType` 的音量、音高及破坏、脚步、放置事件，映射到宿主声音。木材和树叶的材质音效已通过实际客户端音频通道验证。

树叶掉落原先只读取通用 `getDrops`，会漏掉 TC4 覆盖的 `dropBlockAsItemWithChance`。现在对覆盖此回调的旧方块捕获其实际生成的物品，并转为宿主掉落。移除支撑木头或树叶时，另转发附近树叶的 `beginLeavesDecay`，使自然腐烂和后续连续破坏能看到原始腐烂标记；不重复执行完整的旧 `breakBlock`，避免重复掉落容器物品。

## TC4 原版树苗规则

原始 `BlockMagicalLeaves.func_149690_a` 要求元数据同时满足 `(meta & 8) != 0`（待检查腐烂）和 `(meta & 4) == 0`（非玩家放置的持久树叶）。随后宏伟之木以 `1/200`、银树以 `1/250` 掉落对应树苗。该回调没有时运概率加成。

因此，孤立的、没有腐烂标记的树叶被打掉不会出树苗。砍木头会标记附近树叶，移除树叶又会向相邻树叶传播标记。随机刻发现仍有木头支撑时也可能清除标记。测试和正常游戏都应区分这些状态，不能把所有自然树叶当成始终以固定概率掉落。

用户报告使用 FTB Ultimine。核对的官方 1.20.1 分支提交为 `2d6a57e81a1003724c4ceac26797ad991ac2a9b2`：[原始破坏循环](https://github.com/FTBTeam/FTB-Ultimine/blob/2d6a57e81a1003724c4ceac26797ad991ac2a9b2/common/src/main/java/dev/ftb/mods/ftbultimine/FTBUltimine.java)。它逐块读取当前状态，并调用 `player.gameMode.destroyBlock`；可选加速腐烂分支则调用 `world.destroyBlock`。此前关于批量缓存旧树叶状态的猜测不适用于这里。

本次未安装 FTB Ultimine；下面是参照其主循环的原生入口验证，不是完整 FTB 集成验收。用户的工具附魔状态也未确认。没有据此宣称 FTB 本身有 bug。

## 游戏验收

摘要数据：[trees-height-progress.json](trees-height-progress.json)。原始报告在隔离游戏目录 `build/wand-node-fix/game/logs/mfcl/`。

| 检查 | 结果 |
| --- | --- |
| TC4 两种树苗，根部 Y=-40、100、280 | 六棵均由宿主自然随机刻长成；提高测试世界随机刻速度以缩短等待 |
| 原始旧版橡树、云杉生成器，Y=-32、280 | 四种组合均生成成功 |
| 原始 TileEntity NBT Y=-40、280 | 保存、读取保留有符号坐标 |
| 原始 PacketNote / PacketFXVisDrain，Y=-64、-1、256、319 | 八次实际编码、解码往返通过 |
| 各破坏 4096 片带腐烂标记的树叶 | 宏伟之木 22 棵、银树 15 棵树苗 |
| 移除木头后的标记与无支撑树叶随机刻 | 标记传播、自然消失均通过 |
| 客户端实际音频 | `minecraft:block.wood.break`、`minecraft:block.grass.break` |

新增独立服务器 `leaf-harvest` 验收使用生存模式、空手玩家，按原生玩家破坏入口运行。关闭该测试世界的随机刻，以单独观察移除时的标记传播：

| 初始状态 / 顺序 | 宏伟之木 | 银树 |
| --- | --- | --- |
| 4096 次孤立未标记树叶 | 0 棵树苗 | 0 棵树苗 |
| 32 批连续树冠，各 128 片，初始均未标记 | 21 棵树苗 | 15 棵树苗 |
| 连续破坏时读取到待腐烂标记 | 4064 / 4096 | 4064 / 4096 |
| 100 次玩家持久树叶负对照 | 0 棵树苗 | 0 棵树苗 |

掉落计数是单次随机样本，不保证每棵树必掉树苗。以上原生路径已恢复树苗掉落，但不能由此断言用户的完整模组组合与工具附魔全部通过。

## 0–255 审计

扫描四个原始 JAR 的 4273 个类，收集 619 条候选；转换器干跑实际改动 39 个类、40 个方法。候选是启发式清单，不是 619 个已确认 bug，也不能证明未列出的代码没有高度问题。数据在 [world-height-audit.json](world-height-audit.json)，扫描器在 [tools/HeightAudit.java](../tools/HeightAudit.java)。

| 位置 | 分类与处理 |
| --- | --- |
| 旧版树木、花草、蘑菇等生成器的 `0/255/256` 判断 | 有坐标证据的上下界改为宿主维度范围；橡树、云杉和两种 TC4 树完成实际游戏验证 |
| TC4 `BlockArcaneDoor`、`ItemJarNode`、`ItemJarFilled`、`BlockMetalDeviceItem` 等放置回调 | 旧上限改为宿主范围；关键门、节点罐判断另有原始字节码回归，不表示所有物品放置都做过游戏验收 |
| TC4 `TileNode.checkLock`、`TileResearchTable.recalculateBonus` | 固定高度判断被全局转换器覆盖；未逐项做完整玩法验收 |
| Extra Utilities `ItemBuildersWand`、`ItemBlockEnderLily`、`ItemSwordSpike`、`TNHelper` | 有证据的高度判断被覆盖；`TNHelper` 还受已有专用高度适配保护 |
| TC4 `PacketNote`、挖掘 / 源质 / 注魔特效包的绝对 Y | `int`，无需改成更宽类型；包内其他 `byte` 是音符、颜色或相对位移 |
| TC4 `PacketFXVisDrain` 的绝对坐标 | 有符号 `short`，可容纳当前 `[-64,319]`；不是 0–255 字段。超出 short 的自定义维度仍有限制 |
| TC4 方块实体坐标 NBT | `int`，本次验证负高度及 255 以上往返 |
| Extra Utilities `TileEntityEnderThermicLavaPump.pump_y >= 0` | **仍有旧下限**。它是自定义绝对高度字段，全局坐标推断未覆盖；本次没有验收或修复该泵的完整采集流程 |
| Extra Utilities `PacketParticleEvent.doParticleEvent` | **仍有固定 256 的竖向特效循环**。方法没有可供转换器使用的世界参数；未做完整特效验收 |
| Extra Utilities `PacketUseItemAlt.doStuffServer` | **仍有截断风险**。本包的 Y 是 int，但内部重建旧 C08 包时写 byte；本次没有新增专用包处理或该功能验收 |
| 原版 1.7.10 C08 / S23、Chunk 与 Section 存储 | 旧协议和旧区块格式含 byte Y / 固定数组布局；宿主常规交互和区块由现代路径管理。不能全局放大这些字段，也没有声称每个模组内联旧包流程都已接管 |
| `255` 色彩掩码、区块内 16×16 索引、相对位移 | 保持原含义；如泵中的 `chunk_no=255`、`b=256` 属于区块水平遍历，不是世界 Y 上界 |

扫描器可用 Java 17 直接运行；类路径需要 ASM 5、LaunchWrapper 1.12 和构建后的私有运行时：

```powershell
java -Daudit.transform=true --class-path '<asm-debug-all.jar>;<launchwrapper.jar>;<mfcl-legacy-runtime.jar>' `
  tools/HeightAudit.java build/height-range-audit.json `
  run/mfcl/runtime/1.7.10/minecraft-forge-1.7.10-srg.jar `
  run/mods/Thaumcraft-1.7.10-4.2.3.5.jar `
  run/mods/Baubles-1.7.10-1.0.1.10.jar run/mods/extrautilities-1.2.12.jar
```

## 重现

使用独立游戏目录，放入 TC4 / Baubles 和已准备好的原始运行时。服务器目录还需要已接受的 `eula.txt`。两项探针会修改自己的测试世界并在结束时关闭自己的游戏进程。

```powershell
.\gradlew.bat --offline --project-cache-dir build/wand-node-fix/cache `
  -I tools/isolated-build.init.gradle '-PmfclValidationBuildDir=build/wand-node-fix/output' `
  runClient '-PmfclRunDir=build/wand-node-fix/game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' -PmfclTreeSmokeTest

.\gradlew.bat --offline --project-cache-dir build/wand-node-fix/cache `
  -I tools/isolated-build.init.gradle '-PmfclValidationBuildDir=build/wand-node-fix/output' `
  runServer '-PmfclRunDir=build/wand-node-fix/game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' '-PmfclServerSmokeTest=leaf-harvest'
```

服务器测试默认 `level-name` 决定存档位置，务必使用独立测试目录。报告分别为 `tree-smoke.json` 和 `leaf-harvest-smoke.json`，需要核对其中的 `status`，不能只看 Gradle 退出状态。

最终 `test build` 通过，9 项单元测试全部执行、无失败、无跳过；包含 3 项世界高度转换回归。构建产物为 `build/wand-node-fix/output/libs/mfcl-0.2.11-dev2.jar`。保留现有版本号。
