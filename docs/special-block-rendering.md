# 特殊方块世界渲染

2026-10-18，Forge 1.20.1 / Thaumcraft 4.2.3.5。

截图中的守卫者玻璃 `Thaumcraft:blockCosmeticOpaque@2` 已接通世界渲染、放置和原版 TileOwned 数据保存。同一方块的元数据 0、1（两种琥珀方块）也可放置。

## 实现范围

- 调用原版 `ISimpleBlockRenderingHandler.renderWorldBlock`，由原版决定连接贴图、面剔除及两个渲染 pass，不将背包模型直接用作世界模型。
- 区块只读视图提供邻居方块身份及元数据。捕获结果保存为不可变 ModelData，在现代区块模型中绘制，pass 1 使用 translucent，pass 0 使用 cutout。主线程与区块线程执行旧代码时共用串行边界，避免原版静态 Tessellator 和方块渲染状态相互覆盖。
- 把全部已注册图标作为生成资源包的图集输入，连接贴图不会因未出现在背包里而漏贴。资源重载会重建模型及回退几何缓存。
- 修复旧版 lightmap UV 数组覆盖主贴图 UV 的问题；`OpenGlHelper` 的 client active texture 会传入捕获层，第二纹理单元不再改写主纹理坐标或启用状态。
- 修正 IIcon 的 minV / maxV 映射，使端点与插值方法一致。
- 放置判定按元数据生效。仅接受原版报告为普通立方体、没有 TESR、没有需要 tick 的旧方块实体且已成功捕获有效图集四边形的特殊变体。当前 TC4 实际通过该探测的是上述 3 个变体。
- 对这批透明方块，背包自定义模型也使用透明材质。玻璃放置仍执行原版 `onBlockPlacedBy`，保留 owner 并通过已有的二进制 NBT 信封同步和保存。

## 验证

通过隔离构建目录 `build/item-render-validation` 验证，不替换用户正在运行的 `build/classes`。

```powershell
$env:JAVA_HOME='C:/Users/gengy/.jdks/temurin-17.0.18'
./gradlew.bat -I build/item-render-validation.init.gradle test assemble '-PlegacySupportDir=build/nei-investigation' --console=plain
./gradlew.bat -I build/item-render-validation.init.gradle runClient '-PmfclRunDir=build/crash-render-smoke/game' '-PlegacyRuntimeDir=run/mfcl/runtime/1.7.10' -PmfclContentSmokeTest --console=plain
```

- JUnit：48 项，44 通过、4 跳过、0 失败；包括光照 UV 与主 UV 分离的回归测试，以及真实旧运行时的 IIcon 端点检查。
- 集成客户端：6 个方向 × 256 种邻居组合，各方向均得到原版 47 张连接贴图，并检查所有贴图已进入现代图集。
- 相邻同种玻璃剔除内部面；完全包围时无面；不同元数据保留分界面；3×3 玻璃墙中央只绘制两个透明外表面。
- 实际 BlockItem 放置检查 0、1、2 三个元数据；检查玻璃完整碰撞、TileOwned.owner、客户端 NBT 同步及保存后重载。
- 截图：`build/crash-render-smoke/game/screenshots/mfcl-warded-glass-world.png`。
- 机器可读验收：`build/crash-render-smoke/game/logs/mfcl/content-1.7.10-smoke.json`。

## 仍未包含

此支持不包含需要动态 TESR 的设备、节点光晕、流体动画、非立方体碰撞/朝向桥接，以及守卫方块的权限判定和击打特效。这些行为没有因移除玻璃的放置限制而自动获得支持；其他尚未验证的特殊方块继续保留限制。

旧的分解工作台背包捕获失败也仍保留在验收诊断中。其余背包渲染缺口见 [渲染审查](thaumcraft-rendering-audit.md)。原版 JAR 未修改。
