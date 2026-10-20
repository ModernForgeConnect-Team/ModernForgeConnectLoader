# 魔导手册界面桥接

2026-10-19，Forge 1.20.1 / Java 17，原始 Thaumcraft 4.2.3.5。

修复原始物品右键到宿主界面的链路：旧 FML 的 `showGuiScreen` 请求进入宿主 HostedScreen；服务端使用旧 `EntityPlayerMP` 类型；原始 SimpleNetworkWrapper 注册的客户端消息经过宿主连接传输，再交给原始解码器与消息处理器。手册打开时的研究、要素同步沿用原代码。

补齐原始玩家加载流程中的初始要素和自动解锁条目。界面语言与宿主保持一致，GUI 的纹理、文字、物品缩放以及 Tessellator 图形按原顺序回放。`FontRenderer` 的普通和内部文字绘制入口都进入文字桥接，因此长提示的分行绘制不会再访问未初始化的旧纹理管理器。旧窗口焦点操作由宿主界面切换接管。

## 验证

`-PmfclBookSmokeTest` 在独立游戏目录自动新建测试世界，通过现代右键路径打开原手册。测试进入主菜单后隐藏自己的窗口，以免人工输入干扰验收；正常启动不启用此探针。

中文验收通过：原研究消息送达、4 个可见条目的悬停提示、原始 `ENCHANT` 正文、返回研究树、分类切换与 Esc 关闭。截图确认背景完整、分类图标和中文正文可见。报告归档为 [thaumonomicon-progress.json](thaumonomicon-progress.json)。

通用运行时回归同时检查服务端玩家类型、分行文字捕获，以及 GUI 图形与文字的顺序和矩阵变换。JUnit 共 53 项，49 项通过、4 项缺少外部样本跳过，失败为 0；`test assemble` 成功。

本次验证覆盖手册打开和浏览。后续透镜修复已验证扫描产生的研究、要素与扫描记录保存及重新载入，见 [thaumometer-bridge.md](thaumometer-bridge.md)。购买研究、生成研究笔记所需的旧客户端到服务器消息仍需接入并独立验收。此处的消息转发覆盖原始物品回调和已接入的扫描流程，不代表旧网络已全面兼容。

## 前置研究完成后的误锁（2026-10-20）

旧客户端的渲染 Tick 原本负责创建 `ClientTickEventsFML.researchPopup`，宿主没有运行该事件，因此其值一直为空。`PacketResearchComplete` 先更新玩家知识，再调用研究弹窗，最后才更新 `GuiResearchBrowser.completedResearch`。空弹窗导致处理器在刷新手册前抛出异常，表现为已经完成的前置研究仍被手册判定为未完成。客户端消息队列也会因此中断。

在 Thaumcraft 客户端适配器中补齐原始弹窗初始化，让原版完成消息继续执行手册状态刷新。保留原始前置判断，不主动授予任何研究。

`-PmfclBookSmokeTest` 增加独立世界中的前置研究回归：从原研究树选择一对依赖节点，确认前置未完成时锁定；只发送原始增量完成消息，确认手册保持打开时立即解锁；重新打开手册后再次检查。测试不通过全量研究同步掩盖增量消息处理错误。

使用原始 4.2.3.5 JAR 验证 `RESEARCHER1 -> RESEARCHER2`：修复前复现弹窗空指针和前置状态滞留，修复后上述三项检查及原有浏览检查全部通过，结果见 [thaumonomicon-unlock-progress.json](thaumonomicon-unlock-progress.json)。

完整 JUnit 检查共 95 项，90 项通过、4 项跳过，另有 1 项研究台集成测试失败：`ResearchTableSemanticsProbe` 检测到已占用六角格 `(-1, 0)` 的放置请求未被拒绝。该测试不调用本次修改的客户端初始化入口；此次手册回归通过不代表研究台校验也已通过。
