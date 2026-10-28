# Forge 1.7.10 通用注册与行为桥接审计

审计范围：`VillagerRegistry`、统计/成就、属性、`addSpawn`、世界生成器、AI、GUI、SimpleNetworkWrapper、ChestGenHooks、自定义渲染。运行环境为旧 Forge 10.13.4.1614 的隔离运行时和现代 Forge 1.20.1-47.4.28。

本次使用独立旧模组 `tools/registry-audit/legacy/RegistryFixture.java`，经过真正的 FML 初始化和注册，再由现代专用服务器验证。它不依赖 TC4/XU 的专用适配器，不打入发布 jar。测试入口、报告位置和边界如下。

| 项目 | 现有实现与本次补接 | 仍需区分的边界 |
| --- | --- | --- |
| 村民职业、交易 | 采集职业 ID 的模组归属，注册现代职业；动态查询原始 `IVillageTradeHandler`，生成原始配方后转成现代 `MerchantOffer`，接现代交易菜单与存档；旧代码生成的普通村民转为现代村民 | 现代村庄的职业分配仍由现代 POI 决定；不把任意旧职业变成现代工作站。旧村庄房屋生成 `IVillageCreationHandler` 属于另外的结构兼容范围。自定义 EntityVillager 子类保留原始实体行为，不强制转换 |
| 成就、统计 | 原始 `StatBase` 发布为现代 custom stat；旧成就生成现代进度及父子关系；原始玩家统计读取、增加、撤销写入现代玩家数据；事务内连续增加可见，回滚不提交 | 冻结注册表后新建的统计会报告未映射；旧自定义成就页面排版/进度对象的自定义 JSON 数据不是现代进度树的一部分。原版现代事件不会自动触发所有旧版原版成就条件 |
| 自定义属性 | 采集 `RangedAttribute` 声明及 `BaseAttributeMap.registerAttribute`，发布属性、范围、默认值、同步标志；添加至现代玩家，回传原始基础值和 UUID 修饰符，使用原生属性 NBT | 当前自动挂载目标为玩家；托管旧实体仍使用原始属性表。冻结之后第一次声明的属性无法补进现代静态注册表 |
| 实体生成规则 | 捕获 Class 和 String 调用链的 add/remove；按群系、类别、实体落实覆盖/移除，保留权重、最小/最大群体数量 | 只注入有对应现代 Mob 的旧原版实体。任意模组实体目前使用 Entity 封装，尚不是可供 Native NaturalSpawner 构造的 Mob 类型；保留注册并明确诊断，不声称这些实体已能自然生成 |
| 世界生成器 | 原始 `GameRegistry.generateWorld` 发现注册实例并按权重排序；新生成的普通维度也执行模组回调；IChunkProvider 指向宿主区块；已执行区块用 SavedData 标记 | 不对已有区块追溯生成；回调在区块加载后的服务器线程执行，每 tick 至多一个、回调之间软预算 8ms。要求具体旧 ChunkProvider 实现或完整旧地形流水线的生成器仍可能不兼容。镜像旧维度继续原有地形/装饰路径 |
| 实体 AI | 托管实体执行真实旧 `Entity.onUpdate`，因此 EntityAITasks 的判断、开始、持续执行和停止使用旧代码 | 不是独立注册表。寻路、攻击、查询等仍依赖世界/实体行为桥接；一个任务能执行不代表任意模组整套 AI 都完整兼容 |
| GUI 处理器 | 打开时查询旧 NetworkRegistry 的当前 handler，动态注册无需固定适配器列表；原始服务端 Container 和客户端 GuiContainer 继续参与 | GUI 自己调用的任意额外 API、直接 OpenGL 和专用网络协议需要相应支持；不能由 handler 注册成功推断所有按钮/绘制行为都已兼容 |
| 网络消息 | Class/instance 两种注册入口；方向、单字节 discriminator、原始编解码器；补接处理器回复、sendToAll、sendToDimension、sendToAllAround 和客户端上下文/发送 | 服务端接收仍必须通过认证玩家及已登记策略，不自动信任任意客户端消息；原始 NetworkManager 不是另一个 TCP 连接，直接操作底层旧连接仍不受支持。Raw embedded/event channels 是另一条桥接路径 |
| 战利品、地牢箱 | 捕获旧模组 add/remove，生成原生 GlobalLootModifier；以旧池总权重和次数抽选模组新增项，调用原始 Item.getChestGenBase 和 WeightedRandomChestContent 生成数量/NBT，再追加现代箱子结果；移除可表示的对应条目 | 保留现代原版箱子底池，因此是模组增量兼容，不宣称整个 1.20 箱子概率与完整 1.7 表完全相同。旧版没有对应物品的现代掉落不参与旧移除匹配。自定义旧类别由原始旧生成器使用，不凭空对应任意现代 loot table |
| 自定义渲染处理器 | 原始 RenderingRegistry 中 ISBRH、实体 Render，ClientRegistry 的 TESR，以及 ForgeClient 的 IItemRenderer 有各自采集/重放路径 | 顶点、纹理、矩阵、模型和受支持 GL 状态可重放；任意 shader、Framebuffer、原生 GL 扩展、特殊渲染顺序及所有 RenderGameOverlay 事件没有通用完整翻译。运行中新增未采集模型也不能视为已支持 |

## 实现入口

- 归属和注册采集：`RegistryCaptureTransformer`、`LegacyRegistrations`。
- 现代发布：`LegacyVillagerBridge`、`LegacyStatisticsBridge`、`LegacyAttributeBridge`、`LegacySpawnBridge`、`LegacyChestLoot`。
- 原始行为执行：`LegacyGeneration`/`LegacyChunkProvider`、`LegacyEntityHosting`、`LegacyDeviceContainers`、`LegacyPackets`、`LegacyBlockRendering`/`LegacyTileRendering`。

职业没有旧式 POI：已赋予自定义职业的现代村民保留职业，离开交易状态后缺货达 40 tick 可补货。这是适应现代商人的策略，不能等同旧版本精确的补货时间规律。交易转换追加模组 handler 的结果，保留现代原版职业交易；重复打开或读取存档不会不断追加同一批交易。

ChestGenHooks 标准映射：dungeon→simple_dungeon、mineshaft→abandoned_mineshaft、desert/jungle pyramid、jungle dispenser、三类 stronghold、bonus chest、旧 blacksmith→现代 village_weaponsmith。其他现代村庄职业箱不会一律冒充旧 blacksmith。

## 重现验证

PowerShell（JDK 17，先停止上一次审计服务器，再重建 fixture）：

```powershell
./tools/registry-audit/prepare.ps1
./gradlew.bat --offline --console=plain --project-cache-dir build/registry-audit-cache `
  -I tools/isolated-build.init.gradle -I tools/registry-audit/audit.init.gradle `
  '-PmfclValidationBuildDir=build/registry-audit-validation' `
  '-PmfclRunDir=build/registry-audit-game' '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' runServer
```

报告：`build/registry-audit-game/registry-report.json`。Gradle 在报告缺失或任一用例失败时失败，避免游戏加载失败但正常退出造成假阳性。

2026-10-28 验收结果：独立旧模组的 13 项用例全部 PASS，覆盖归属、属性/修饰符 NBT、成就与统计保存读取、事务回滚、村民交易重载去重、生成权重与移除、生成器排序和重复执行保护、晚注册 GUI、两种消息注册和回复、维度/范围广播及客户端上下文、箱子战利品、真实 AI tick、ISBRH 几何。

另以 TC4 4.2.3.5 + Baubles 1.0.1.10 + Extra Utilities 1.2.12 启动现代专用服务器，两次独立进程的存档写入/重启读取均 PASS：`build/registry-tc4-regression/logs/mfcl/server-1.7.10-smoke-write.json`、`server-1.7.10-smoke-verify.json`。2 项 LegacyDisplayAdapterTest 在提供真实旧运行时后通过，未跳过。

该验证检查真实旧注册、原始 callback 及现代端结果；专用服务器的几何采集验证不替代客户端画面逐像素验收。
