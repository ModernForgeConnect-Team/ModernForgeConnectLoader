# Extra Utilities 锥刺接触伤害审计

2026-10-22，针对原始 `extrautilities-1.2.12.jar` 和 Forge 1.20.1 宿主运行时。用户参考：[铁锥](https://www.mcmod.cn/item/2416.html)。

## 原因与修复

`ModernContentBridge.MirroredBlock.entityInside` 之前只接收 `ItemEntity`，用于神秘时代坩埚的物品溶解；生物和玩家直接返回，没有调用原始锥刺的 `onEntityCollidedWithBlock`。`LegacyBlockLogic.collide` 也固定读取 `EntityItem.getEntityItem`，不能接收其他实体。

现在将所有可映射的实体送入原始碰撞回调，继续通过既有 `LegacyEntityHooks` 转发伤害与着火等效果。只有掉落物才读取/写回物品栈，其他实体保留原始移除标志；回调改变的运动量通过宿主世界提交。没有替换锥刺原始伤害算法或按方块名实现另一份扣血。

实体快照新增可选的当前生命值，并保留原有构造函数兼容调用方。每次建立/更新生物镜像时同步生命值 DataWatcher，避免原先固定的 1 HP 替代值误导木锥的非致死判断和金属锥的致死分支。镜像中未构造完整属性表，因此直接同步原始生命值槽位，不调用依赖最大生命属性的 `setHealth`。

## 原生验证

`tools/spike-audit/SpikeHostAudit.java` 仅通过专项初始化脚本加入独立验证构建，不打入发布 JAR。测试运行在 `build/spike-audit/game/` 的独立专用服务器世界，输出 `spike-report.json`。

- 四种锥刺、六个朝向执行原生 `BlockState.entityInside`，核对当前原始版本的接触伤害。
- 真实 `Entity.move` 触发碰撞，确认不是只靠手动调用旧回调才有伤害。
- 木锥连续接触后保留生命、铁锥能击杀低生命目标。
- 无敌生物不被伤害，掉落物不被销毁，非生物实体不触发物品栈反射错误。
- 一般怪物镜像和玩家镜像均通过对应宿主伤害路径。玩家使用关闭出生保护、按游戏模式决定免疫的测试 FakePlayer；Forge 原生 FakePlayer 固定无敌，不能用于判定生存玩家受伤。

本专项覆盖接触伤害与非致死分支，不作为经验、稀有掉落或全部附魔效果的完整验收。

原生服务器本轮报告为 `PASS`，共 37 个场景；原始伤害数值、移动触发、木锥连续接触停止、铁锥致死以及实体类型分支的断言均通过。

## 重跑

沿用已有 EULA 接受设置，将原始 XU JAR 放入专项游戏目录的 `mods/1.7.10/`，使用 JDK 17：

```powershell
.\gradlew.bat --offline --project-cache-dir build/spike-audit/cache `
  -I tools/isolated-build.init.gradle -I tools/spike-audit/audit.init.gradle `
  '-PmfclValidationBuildDir=build/spike-audit/output' `
  '-PmfclRunDir=build/spike-audit/game' `
  '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' runServer
```

初始化脚本要求本轮生成的报告状态为 `PASS`，断言失败或缺少报告都会使 Gradle 任务失败。
