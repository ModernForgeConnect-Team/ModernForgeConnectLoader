# 第二轮调查：启动、注册屏障与旧对象结构

日期：2026-10-18（Asia/Tokyo）。固定目标：Forge 1.7.10 `10.13.4.1614` → Forge 1.20.1 `47.4.28`，Java 17。

## 1. 结果与证据范围

**建议继续做受限的嵌入运行实验；首选普通现代 Forge 模组的构造阶段完成旧域加载，注册事件负责创建现代对应物。世界使用保留旧类型的对象图门面，物品栈单独制定转换和写回契约。**

这是一条有源码依据的候选路线，不是已完成的兼容实现。本轮实际执行的是发行产物检查、字节码检查和小型 JVM/依赖探针，没有完整启动旧 FML，也没有在现代 Forge 游戏内运行旧模组。

| 优先级 | 问题 | 本轮判断 | 尚需证明 |
| --- | --- | --- | --- |
| P0 | 不接管宿主，运行真实旧 FML | 旧 `Loader` 可被独立驱动，有参考实现；需重建旧启动上下文 | 真实宿主中的完整加载及失败传播 |
| P0 | 两套游戏/Forge 命名空间隔离 | JVM 允许；需要严格 owner 规则及独立工具域，不能只有 child-first | 类、资源、服务与反射路径无泄漏 |
| P0 | 旧 Init 注册赶上现代窗口 | 可以先尝试把旧域全生命周期放在现代 CONSTRUCT 内，再通过注册屏障发布计划 | 旧初始化不依赖未来现代注册；注册依赖无环 |
| P0 | World / Block / ItemStack 对应 | 三类对象应采用不同契约；直接字段访问是必须支持的独立路径 | 对象图闭合、别名、复制、重入及保存语义 |
| P1 | CoreMod / AT 只作用于旧域 | 普通转换分发可以约束；原版 AT 有额外类加载层级假设 | 完整插件协议和变换顺序；不等于任意 CoreMod 隔离 |
| P1 | RFB / FML 在 Java 17 稳定 | 有现代化参考；实测两个旧假设确实不成立 | 嵌入、线程、依赖与反复进出世界的稳定性 |

资料分三层：**发行产物事实**、**参考项目源码事实**、**本项目设计建议**。后者没有标成已实现。

## 2. 本轮固定了哪些真实输入

本轮不再仅依靠可变的 `1.7.10` 或 `1.20.x` 分支：

| 输入 | 用途 |
| --- | --- |
| Forge 1614 universal JAR | 原版 FML `ModClassLoader` 探针、Loader class 版本检查 |
| Forge 1614 userdev JAR | 随发行版附带的 FML 源码及 Forge/Minecraft 补丁 |
| Forge 47.4.28 sources、fmlcore sources、javafmllanguage sources | 构造、注册事件、冻结和执行器调用链 |
| 官方 Minecraft 1.7.10 server JAR + MCP SRG | 核实旧 World / WorldProvider / ItemStack 的真实结构 |
| LaunchWrapper 1.12、Guava 17.0、ASM 5.0.3 | 小型 JVM 探针 |
| RFB `78b687172b77a3b933f3a0f1de58fcaebf9dcbda` | 启动器及类加载器源码 |
| LWJGL3ify `4b9a170e369cb718cbe06b9e4d2ff17fe142d4cf` | 现代 Java 补丁与启动参数 |
| UniversalModBridge `b24a6378b58db5b7c4581456b38e74c02deb8a8b` | 旧运行时、对象门面、CoreMod 的实际覆盖边界 |

输入哈希与源码路径记录在 [evidence.json](evidence.json)。官方 server JAR 的 SHA-1 与版本元数据中的 `952438ac4e01b4d115c5fc38f891710c4941df29` 一致。文件检查使用 Temurin 17.0.18 的 `javap`，没有运行这个 server JAR。

Forge 1614 发行源码与 FML `1.7.10` 分支头并不完全相同，例如发行版 Loader 含额外的 ItemStack holder 注入。因此本文涉及固定版本的生命周期结论以发行包为准。

下载物和解包源码位于被 Git 忽略的 `build/research-round2/`；没有把游戏 JAR 或解包游戏源码加入项目资料目录。

官方输入入口：[Forge 1614 userdev](https://maven.minecraftforge.net/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10-userdev.jar)、[Forge 47.4.28 sources](https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.28/forge-1.20.1-47.4.28-sources.jar)、[FML core sources](https://maven.minecraftforge.net/net/minecraftforge/fmlcore/1.20.1-47.4.28/fmlcore-1.20.1-47.4.28-sources.jar)、[Java FML sources](https://maven.minecraftforge.net/net/minecraftforge/javafmllanguage/1.20.1-47.4.28/javafmllanguage-1.20.1-47.4.28-sources.jar)、[Minecraft 1.7.10 元数据](https://piston-meta.mojang.com/v1/packages/ed5d8789ed29872ea2ef1c348302b0c55e3f3468/1.7.10.json)、[MCP SRG](https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.7.10/mcp-1.7.10-srg.zip)。

## 3. P0：如何嵌入真正的旧 FML

### 3.1 可以保留状态机，替换启动环境提供方式

1614 的 `FMLServerHandler.beginServerLoading()` 调用 `Loader.loadMods()`、`preinitializeMods()`；`finishServerLoading()` 调用 `initializeMods()`。真实旧 Loader 的职责包括发现、排序、构造、容器上下文、实例/代理/holder 注入以及事件派发。

因此不需要让现代宿主执行旧 Minecraft 的 `main()`，也不需要将三次生命周期回调写成自制反射事件。但必须提供 Loader 原本从旧启动链取得的环境：

| 启动依赖 | 嵌入方式建议 |
| --- | --- |
| 旧 `Launch.classLoader`、blackboard、目录与启动标记 | 在旧工具域中建立独立上下文，不能覆盖现代宿主状态 |
| `FMLInjectionData`、版本和注入容器 | 按固定 1614 输入设置；禁止假报现代宿主为旧 Minecraft |
| Forge 补丁后的旧基础类与命名空间 | 准备一致的旧运行输入；原版 MC JAR 加 FML JAR 不等于完整旧 Forge 环境 |
| 原版旧注册内容 | 在旧模组构造/生命周期所需位置完成 bootstrap |
| `IFMLSidedHandler` | 提供旧侧处理器，明确启动阶段没有真实世界 |
| 转换链与访问规则 | 在受影响类第一次定义前安装；不能事后靠缓存补救 |
| 线程、TCCL、退出及日志 | 由嵌入控制器限定作用范围，并向宿主传播失败 |

这保留的是“真实旧 FML 状态机”，不声称完整重放了原生旧启动过程。[FMLServerHandler 源码参考](https://github.com/MinecraftForge/FML/blob/a099592d3d1418245e3af65eda195da244287188/src/main/java/cpw/mods/fml/server/FMLServerHandler.java)、[UMB LegacyDriver](https://github.com/gato001k1/UniversalModBridge/blob/b24a6378b58db5b7c4581456b38e74c02deb8a8b/umb-legacy/src/legacyside/java/dev/umb/legacy/legacyside/LegacyDriver.java)。

### 3.2 已实测的硬性类型要求

原版 `ModClassLoader` 构造器将参数强转为旧 `LaunchClassLoader`。在 Java 17 探针中，传入系统类加载器确实产生 `ClassCastException`。

这证明普通现代 ClassLoader 不能直接替代它。可以提供兼容的旧 LaunchClassLoader 实现，或对固定旧运行时适配；不能仅给它换一个 URLClassLoader 后宣称启动问题已解决。[源码参考](https://github.com/MinecraftForge/FML/blob/a099592d3d1418245e3af65eda195da244287188/src/main/java/cpw/mods/fml/common/ModClassLoader.java)。

旧 `getEffectiveSide()` 还通过线程名是否恰好为 `Server thread` 判断逻辑侧。现代加载 worker 与旧启动 worker 不满足这一假设。应在旧域建立明确的侧别上下文并验证旧 API 结果，不能随意改现代线程名作为通用方案。

## 4. P0：隔离的单位应包括工具和依赖

推荐三个责任域，而非预先规定恰好两个加载器：

```text
H：现代 Forge 自有的游戏/模组定义域
   └─ 提供唯一一份 Bridge API 类型；保留现代启动权

R0：受控旧工具域
   ├─ 旧 LaunchWrapper/RFB 启动支持、所选工具和私有依赖
   ├─ 仅向 H 请求列入白名单的 Bridge API
   └─ L：旧游戏域
       └─ 旧 Minecraft、Forge/FML、旧模组及旧游戏适配器

JDK 类型 → bootstrap/platform；Bridge 类型 → H 中的指定定义者
```

这是逻辑设计，具体父子关系和工具位置需要用固定输入验证。R0 可以经自定义委托访问 H 的 API，没必要把现代全部 classpath 暴露给旧域。

关键规则：

- `net.minecraft.*`、旧 Forge 游戏 API 等由旧域负责，查不到就报错；禁止回退取得现代同名类。
- `net.minecraft.launchwrapper.*` 与跨域工具 SPI 的类型必须各有明确唯一归属，符合旧 FML 的强转要求。
- ASM、Guava、Netty、日志等库不能因为包名相同就直接共享。跨边界的 SPI 参数必须来自同一份类定义；纯旧实现依赖可以隔离。
- `javax.*` 不能一概自行加载或一概委托；例如 JDK 平台类型与旧 `javax.vecmath` 的来源不同。
- `getResource(s)`、`getClassBytes`、服务描述、TCCL 和反射查找遵循相同 owner 计划；“类没串”并不能证明资源没串。
- 旧 `classLoaderExceptions` 与 `transformerExceptions` 含义不同。前者可能改用另一个加载器，后者只跳过转换。不能不经验证把两者当成同一张表。

UMB 的 `LegacyLoader` 在自己不拥有类时仍会回退到父加载器。因此它的 child-first 实现不自动满足本项目提出的“受保护命名空间缺失即失败”，需要更严格的规则。[LegacyLoader](https://github.com/gato001k1/UniversalModBridge/blob/b24a6378b58db5b7c4581456b38e74c02deb8a8b/umb-legacy/src/boot/java/dev/umb/legacy/boot/LegacyLoader.java)。

这里的隔离目标是正常加载/链接和转换路径的归属隔离。ClassLoader 本身不会阻止任意旧代码调用 `System`、系统类加载器或本地库；不能把它解释成任意插件的进程沙箱。

## 5. P0：Registration Barrier 的具体位置

### 5.1 固定 47.4.28 的实际调用链

从发行源码核实：

```text
现代 ModLoader.gatherAndInitializeMods()
  → 构建现代 ModContainer
  → GATHER / CONSTRUCT：并行构造模组，等待阶段完成
  → CREATE_REGISTRIES
  → OBJECT_HOLDERS
  → INJECT_CAPABILITIES
  → UNFREEZE_DATA
  → LOAD_REGISTRIES：GameData.postRegisterEvents()
       → 针对每个 registry：unfreeze → 分发 RegisterEvent → freeze
  → 后续 LOAD：CONFIG_LOAD、COMMON_SETUP、SIDED_SETUP
  → COMPLETE 阶段及整体 FREEZE_DATA
```

证据位置：47.4.28 `ForgeStatesProvider` 第 21–26 行，`ModLoader` 第 137–225 行，`GameData` 第 308–355 行，`FMLModContainer` 第 66–96 行。行号对应本轮解包文件，源码哈希见证据清单。

**每张 Forge 注册表在它的 RegisterEvent 结束后就会被冻结；不能等最后的 FREEZE_DATA 才把注册视为结束。**对应的[上游状态定义](https://github.com/MinecraftForge/MinecraftForge/blob/d13dd327bdc75c010f3358992e80e7e92f51e030/src/main/java/net/minecraftforge/common/ForgeStatesProvider.java)与[注册调用实现](https://github.com/MinecraftForge/MinecraftForge/blob/d13dd327bdc75c010f3358992e80e7e92f51e030/src/main/java/net/minecraftforge/registries/GameData.java)可作为导航，固定版本判断仍以发行包为准。

### 5.2 首选候选：先完成旧加载，再进入现代内容绑定

旧生命周期与现代生命周期不必按同名阶段对应。针对普通旧模组，建议实验：

```text
现代 MFCL 模组构造
  → 安装现代 RegisterEvent 监听器
  → 在受控旧线程完成一次旧域启动：
       loadMods → preinitializeMods → initializeMods
       （含 Init、PostInit、AVAILABLE，旧 GameData.freezeData）
  → 核对旧注册表，产生不可变计划，封闭旧侧新增声明
  → 旧加载成功且计划已发布，现代构造才返回

现代 RegisterEvent(registry K)
  → 验证旧侧计划已封闭
  → 只创建 K 对应的现代对象并完成句柄绑定
  → 事件返回；该注册表被现代 Forge 冻结

现代世界实例出现
  → 建立 World/Player 门面，开启相应游戏回调
```

这使“旧 Init 才注册方块”仍发生在现代注册事件之前；不需要重放旧模组、不需要伪造提前注册成功，也不首先要求接管 ModLauncher。

需要区分两个屏障：

| 屏障 | 保证 |
| --- | --- |
| 声明屏障 | 支持范围内旧加载已完成，注册计划不再新增/替换 |
| 绑定屏障 | 当前现代 registry 的对象已在合法事件内创建并可解析 |

“计划里已有条目”不意味着现代对象可提前读取。现代 supplier 在对应事件内执行；跨 registry 依赖按实际顺序验证，不能假定所有对象已同时可用。

还有一项固定版本事实：旧 `GameData.freezeData()` 主要建立快照；1614 的 `FMLControlledNamespacedRegistry` 检测 `GameData.isFrozen(this)` 后只是输出“添加太晚”的告警，并未在该检查处拒绝新增。**旧 FML 自带的 freeze 不是本项目所需的硬屏障。**Bridge 必须在支持的旧注册/替换入口增加明确拒绝与审计，防止后台线程、后续事件或直接注册表调用在计划封闭后继续改变内容。判据是“晚写被拒绝且原注册表未改变”，不能只检查是否打印了日志。

### 5.3 必须保留旧注册的即时语义

旧 `GameRegistry.registerBlock` 会构造旧 ItemBlock，先把旧 Block 加入旧 GameData，再注册旧 Item。旧模组当场查询注册表应拿到真实旧对象。

因此只延迟现代对应物的创建，不能把旧注册操作本身都变成 supplier。收集计划可采用“旧原版基线快照 + 加载后差异”起步，并在旧注册/替换路径加入审计。只拦截 `GameRegistry` 不够：直接旧注册表调用、TileEntity 映射、实体映射、配方列表也有自己的入口。

旧 Block 的注册与方法/属性配置可能分多步完成，现代 BlockState 结构需要读取声明屏障后的最终定义。旧数字 ID 不写入现代注册表；建立独立映射，并处理同名现代内容冲突。首版应明确拒绝尚未支持的原版替换，而不是静默覆盖。

### 5.4 哪些问题单靠“更早启动”解决不了

如果旧 Init 同步查询现代模组尚未注册的对象，而该现代模组又在注册阶段查询这批旧内容，就出现阶段依赖环。提前介入 ModLauncher 只会更早遇到“现代对象不存在”，不会自动解除依赖环。

因此候选方案先限定：旧启动阶段可使用自己的真实旧注册环境；现代对象查询在绑定屏障前明确失败；没有真实世界时不接受世界操作；旧工作线程不得把内容注册延迟到屏障以后。

出现这种环时，需要按具体 API 设计可保留语义的 handle、阶段协调或兼容补丁；不能后台重试来掩盖第一次查询失败。

只有当固定旧输入确实要求在兼容层构造之前准备，或现代类必须在首次定义前改写，才升级到受控的早期 ModLauncher 扩展。晚注册不应通过解除冻结处理。

## 6. P0：保留旧类型与字段结构的对象门面

### 6.1 World 必须携带一张一致的旧对象图

对下面的读取：

```java
world.provider.dimensionId
```

需要的并非一个 getter，而是：旧 `World` 上真正的 provider 字段 → 旧 `WorldProvider` 对象 → 对象上真正的 dimension 字段。该 provider 的 `worldObj` 还应指回同一个旧 World。

官方字节码与 SRG 检查到：`World.provider` 是 `public final` 引用；`WorldProvider.dimensionId` 是可变 `public int`；provider 还保存旧 World 引用。旧模组以旧类为静态类型访问字段时，不会读取子类新声明的同名字段。

本轮 JVM 夹具也验证：子类遮蔽 provider 后，旧基类引用仍读取基类 provider；将正确值写入基类实际持有的 provider 后，旧引用才能读到。依据：[JVMS 字段读取](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.getfield)、[字节码观察记录](bytecode-observations.txt)。

建议的 World 门面契约：

| 对象或字段 | 所需语义 |
| --- | --- |
| 旧 World | 每个现代世界实例、逻辑侧和代次对应稳定的旧门面 |
| provider | 填写旧 World 真正的字段；不能用子类同名字段替代 |
| provider.worldObj | 指回对应旧 World，避免对象图跳到另一套世界 |
| dimensionId | 使用明确的旧整数 ID ↔ 现代维度 key 映射，保存映射规则和冲突处理 |
| WorldInfo / 时间 / 天气 | 支持的查询转发至宿主；直接字段路径单独列入同步范围 |
| player/entity/tile 集合 | 返回旧类型对象及稳定别名；集合操作也需要定义宿主行为 |
| 逻辑侧 | 客户端世界与服务端世界分别绑定，不能复用一个对象 |
| 卸载与重进 | 旧句柄失效，不能因维度 key 相同而复用上一次实例 |

只构造通用 WorldProvider 无法满足检查特定 provider 子类的旧模组；自定义维度、ChunkProvider 和世界生成仍需要单独支持范围。新旧高度范围也不能硬编码成永久相同。

### 6.2 构造器、final 字段和初始化不变量

优先使用可以满足前提的真实旧构造路径。旧 World 构造器会关联 provider、保存接口、WorldInfo、区块提供器等对象，必须审计它触发的原生行为。

若真实构造路径无法受控，可以研究旧运行时构造路径的内存适配；`Unsafe.allocateInstance` 只适合作为经过完整字段审计的实验手段。它不执行字段初始化器和构造器，不能因对象已成功分配就认定可用。对 final 字段的特殊写入也需要考虑发布和内存语义。[JLS final 字段语义](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5)。

方法、字段、反射和动态生成代码是不同访问路径。方法覆写只能处理实际分派到这些方法的调用；运行时字段改写还必须覆盖其消费者，反射路径则需另外验证。

### 6.3 Block：每域保持稳定注册对象，跨域通过句柄对应

旧注册 Block 保持旧域单例；现代 registry 保存对应的现代 Block。旧 `getBlock` 反复查询同一内容应返回同一个旧 Block，使旧 `==`、`instanceof` 和旧属性访问仍有意义。

metadata 与现代 BlockState 分开映射；旧 TileEntity 状态与每个坐标绑定，不能混入全局 Block 单例。现代模组提供的新方块若需要被旧代码看到，还需旧域反向门面；首版可限制为已明确映射的内容，未支持时报告缺失语义。

### 6.4 ItemStack：不能沿用 Block 的单例映射

**1.7.10 ItemStack 是 final 类；47.4.28 的现代 ItemStack 也是 final 类。**旧版的 `stackSize`、`stackTagCompound` 是直接可读写字段；现代 count/tag 的访问结构不同。去掉 final 本身也不会让两套字段自动共享。

旧代码可能：保存同一引用、直接 `stackSize--`、修改深层 NBT、调用 `copy()`、拆分堆叠，或者替换整个库存槽。将内容转换成 DTO 再创建新对象，只提供数据传递，不提供这些引用与写入语义。

建议明确三种契约，而不是一个通用 `convertStack()`：

| 场景 | 建议契约 | 限制 |
| --- | --- | --- |
| 配方、显示、网络快照等明确的值传递 | 内容标识、count、damage、NBT 的显式复制 | 不保证引用身份；NBT 新增类型、损坏或未知字段必须有明确策略 |
| 生命周期短、可界定的交互回调 | 回调进入时建立旧工作对象；保持本次别名；返回或重入宿主前写回 | 保留引用到回调外、异步写入和并发修改不自动支持 |
| 旧机器长期持有的库存 | 明确库存 owner、槽位版本和稳定旧对象；所有已支持现代访问通过库存桥接 | 现代代码拿到物品栈后直接改变对象的路径仍需验证，不能只在 Tick 末复制 |

工作对象需要覆盖真实旧对象字段，而不是桥接子类字段。同一槽位重复读取是否返回同一对象，应遵循相应旧 API；`copy()` 必须产生独立副本；替换槽位需要使旧绑定失效或按已声明的规则更新，不能通过内容相等把两个栈合并成同一身份。

本项目尚未实现这些契约。UMB 的 `UmbItemConv.toLegacy()` 每次 `new ItemStack`，并在部分 NBT 失败时返回空标签，因此不能把它直接当成保留引用与数据的通用方案。[源码](https://github.com/gato001k1/UniversalModBridge/blob/b24a6378b58db5b7c4581456b38e74c02deb8a8b/umb-legacy/src/legacyside/java/dev/umb/legacy/legacyside/UmbItemConv.java)。

### 6.5 同步还包括重入与写回顺序

旧回调在现代服务器线程执行；旧 setBlock 完成后立即可读，返回真实成功/失败结果。碰撞、邻居更新或事件可能同步重入桥接，所以任何尚未写回的工作状态必须在重入前处理。

不能承诺所有修改都是事务：宿主已发出的邻居通知、事件和磁盘副作用未必能回滚。异常发生在有副作用的调用中时，应中止相关阶段并保留原因，不能把工作副本丢弃后宣称整个动作从未发生。

## 7. P1：CoreMod 和 AT 的范围约束

### 7.1 转换分发可以限制，插件代码本身不天然受限

旧 `IClassTransformer` 只应安装在旧域的定义链上。每次输入记录定义域、原始/映射名称、owner、转换器顺序和哈希；不要把旧 transformer 注册成现代 ModLauncher service 或无条件 javaagent transformer。

这能约束普通转换输入，不能防止任意 CoreMod 自己访问系统类加载器、反射、进程属性或启动原生代码。

加载协议也不只是调用 `getASMTransformerClass()`：固定旧 `CoreModManager` 还处理 `injectData`、setup class、AT 类、MCVersion、依赖、排序与排除规则。旧基础类必须在这些规则准备好后才允许定义。

### 7.2 原版 ModAccessTransformer 的特殊层级依赖

1614 `ModAccessTransformer` 的构造器通过“当前加载器的实现类由哪个加载器定义”，寻找另一份 `ModAccessTransformer.embedded` 静态规则表。这是原生旧启动的两层工具协议，普通 child-first 并不自动复现它。

候选处理有两种：在旧工具域重建这份规则收集/交接协议，或把固定旧 AT 入口适配成显式传递规则。两者都必须保证规则使用正确的旧命名空间，并在目标类首次定义前生效。现代 AT 与旧 AT 的配置和类型不能混用。[ModAccessTransformer 源码](https://github.com/MinecraftForge/FML/blob/a099592d3d1418245e3af65eda195da244287188/src/main/java/cpw/mods/fml/common/asm/transformers/ModAccessTransformer.java)。

UMB 的 `LegacyDriver` 明确省略 `ModAccessTransformer`；其 `LegacyCoremodLoader` 主要发现并调用 transformer 列表，缺少完整原生协议。由此只能把它视为部分实现参考，不能据其存在认定任意旧 CoreMod / FMLAT 已受支持。[LegacyDriver](https://github.com/gato001k1/UniversalModBridge/blob/b24a6378b58db5b7c4581456b38e74c02deb8a8b/umb-legacy/src/legacyside/java/dev/umb/legacy/legacyside/LegacyDriver.java)、[LegacyCoremodLoader](https://github.com/gato001k1/UniversalModBridge/blob/b24a6378b58db5b7c4581456b38e74c02deb8a8b/umb-legacy/src/legacyside/java/dev/umb/legacy/legacyside/LegacyCoremodLoader.java)。

首轮仍排除用户 CoreMod；旧 Forge 自身所需的补丁/转换不是可以全部省略的可选项。之后依次引入固定 FMLAT、一个最小 IClassTransformer，最后才增加更完整的插件协议样本。

## 8. P1：Java 17 与 RFB 的具体结论

本轮实测：原版 FML Loader class 的 major version 是 50；旧 ASM 5.0.3 能读取 `--release 8` 夹具，拒绝 `--release 17` 字节码。旧 class 的低版本与解析器能力是两回事。

因此可以让宿主和控制器使用 Java 17，同时把会经过旧 ASM 的桥接/旧侧辅助类编译成兼容 class 版本。这不要求再启动 Java 8 JVM。旧 ASM 也可能为求继承层级而读取其他 class bytes，因此“辅助类排除转换”仍须验证是否足够。

Java 17 的系统应用加载器不再是 URLClassLoader，平台加载器与强封装也改变了旧假设。[Oracle 迁移说明](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html)。

RFB 源码中既有跳过系统加载器检查的路径，也有显式 URL 输入和嵌入用 addURL hook；因此不能断言它完全不可能嵌入。但 `Main` 仍会准备自己的加载环境并调用旧 Launch.main，它不是已验证的无侵入嵌入 API。其 ASM/logging 委托和全局配置必须封闭在旧工具域，不能直接使用宿主所有依赖。[Main](https://github.com/GTNewHorizons/RetroFuturaBootstrap/blob/78b687172b77a3b933f3a0f1de58fcaebf9dcbda/src/main/java/com/gtnewhorizons/retrofuturabootstrap/Main.java)、[RfbSystemClassLoader](https://github.com/GTNewHorizons/RetroFuturaBootstrap/blob/78b687172b77a3b933f3a0f1de58fcaebf9dcbda/src/main/java/com/gtnewhorizons/retrofuturabootstrap/RfbSystemClassLoader.java)。

后续稳定性验收至少包括：不替换宿主系统类加载器、不安装旧全局 SecurityManager、不接管全局输出、无第二套图形/网络主循环；必要 opens 和依赖补丁逐项验证；第一次退出世界后再次进入，旧监听器、线程、世界引用和 Tick 不重复。

“Java 17 稳定运行”的结论仍待这些实测，不能由一次类加载或 RFB 独立启动成功代替。

## 9. 已执行的小型探针

运行方式：

```powershell
./docs/research-round2/run-probe.ps1 -JavaHome 'C:/Users/gengy/.jdks/temurin-17.0.18'
```

脚本仅在独立 JVM 中运行，核对记录的依赖哈希，编译产物放在 `build/research-round2/probe`。代码：[FmlJvmProbe.java](FmlJvmProbe.java)、[LegacyFixture.java](LegacyFixture.java)、[run-probe.ps1](run-probe.ps1)。

| 检查 | 本次观察 |
| --- | --- |
| 原版 FML ModClassLoader 接受系统加载器 | 按预期拒绝：ClassCastException |
| ASM 5.0.3 读取 Java 8 class | 接受 |
| ASM 5.0.3 读取 Java 17 class | 按预期拒绝：IllegalArgumentException |
| 子类遮蔽字段能否接管旧基类字段读取 | 不能；分别读到 42 和 0 |
| 修改基类实际 provider 的值 | 通过旧引用读到修改后的 7 |

探针另检查系统类加载器保持不变。前面三个使用真实旧发行依赖；字段实验使用自制的最小形状夹具，不能称为真实旧 World 已可用。完整输出保存于证据 JSON 的 `probe.output`。

## 10. 下一组有判定价值的实验

| 次序 | 实验 | 通过与失败的判定 |
| --- | --- | --- |
| A | 现代宿主构造阶段启动真实旧 FML；三个旧样本分别在 PreInit、Init、PostInit 注册 | 各生命周期只一次；三批内容都出现在封闭计划；无需进入世界 |
| B | 按现代 RegisterEvent 创建 A 的对应物 | 记录事件序列；窗口内绑定；事件结束后不再新增；不解除冻结 |
| C | 旧 Init 请求未来的现代注册对象 | 明确报阶段依赖错误；无等待死锁和假对象 |
| D | 同名游戏/Forge 类、资源和 SPI 冲突夹具 | 正确 owner；故意缺失的旧类不回退现代类；旧转换器输入不含现代类 |
| E | 真实旧 World 对象图探针 | provider 字段、worldObj 回指、维度值、WorldInfo 正确；保存引用后再读仍正确；重进世界后旧句柄失效 |
| F | ItemStack 别名与写回 | 重复读取、copy、split、stackSize--、深层 NBT、槽位替换和重入逐项有证据 |
| G | 最小 AT/CoreMod | FMLAT 改动旧目标，现代同名目标不变；injectData/setup/排序与失败均可观察 |
| H | Java 17 反复进出世界 | 线程、监听器、对象绑定和 Tick 次数不累积；保留完整异常链 |

A/B 是注册屏障的架构关卡，E/F 是对象互联的架构关卡。在这些实验通过之前，不应把资源生成、渲染或大型整合包兼容加入第一个实现目标。
