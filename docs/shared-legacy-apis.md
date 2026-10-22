# 旧模组捆绑 API 与创造栏顺序（2026-10-22）

Extra Utilities 1.2.12 捆绑了 `baubles.api.BaublesApi`、`BaubleType` 和 `IBauble`。独立安装 Baubles 后，这些类会同时出现在两个 JAR 中，其中 `BaublesApi` 字节码还不相同，不能仅按文件名或“字节完全相同”处理。

MFCL 现在被动读取原始 `package-info.class` 中的 Forge `@API(owner=...)`。只有声明者同时包含对应 owner 模组、且实际提供该类时，才允许该 API 类的捆绑副本共存。私有旧类加载器将这些类的资源固定到 owner JAR，保证三个 Baubles API 类型来自同一来源，不依赖文件名和扫描顺序。

未安装 owner、两个 owner 声明冲突、普通实现类重复、重复模组 ID 和重复 JAR 继续走原有错误检查。不会删改原始模组 JAR，也不会通过禁用 Baubles 或跳过其他模组来绕过冲突。

回归包括合成 API 包的被动发现与普通实现类冲突，以及真实 Baubles + Thaumcraft + Extra Utilities 联合启动；真实用例特意把 Baubles 文件排到最后，检查实际加载类的来源。

创造栏的另一个独立问题是缺少原版末尾锚点，并把 Forge `withTabsAfter(previous)` 的含义用反。该方法表示“previous 应在当前标签后面”。现在使用 `withTabsBefore(previous)`，并以原版刷怪蛋/管理员分类作为前置约束，保留原始旧标签之间的顺序。默认布局下旧分类从后续页面开始；用户资源包仍可通过 Forge 排序配置覆盖最终顺序。

最终验收：三个共享 API 规则测试和真实三模组联合启动均通过；所有 Baubles API 类型确认来自排在最后的独立 Baubles JAR。原生客户端报告 `creativeTabsAfterVanillaInOriginalOrder: true`，同次配方物品像素验收为 PASS。

全量回归共 123 项：116 通过、6 跳过、1 失败。失败为 `ThaumcraftResearchIntegrationTest.originalNotesCanBeInsertedEditedAndCompleted`，研究操作 `-1,0:lux` 被接受，未在本批 API/渲染/创造栏修复中处理。不能把本次全量结果记为通过。
