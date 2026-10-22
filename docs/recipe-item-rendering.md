# 配方中的不稳定锭渲染（2026-10-22）

环境为 Java 17、Forge 1.20.1-47.4.28、JEI 和原始 Extra Utilities 1.2.12；使用独立构建输出和新建临时世界。

## 原因与修复

原始不稳定锭的创造栏 metadata 0 堆叠带有 `{creative:1b}`，配方里的堆叠可能不带这个标签，或携带其他配方 NBT。MFCL 以物品、metadata 和原始 NBT 缓存模型；之前只有宿主堆叠带桥接标签时才尝试实时捕获，因而无标签的配方堆叠没有可绘制几何。

此外，实时捕获保留旧图标名，静态模型则已转换成生成资源包中的图集 ID。物品重放没有为实时几何转换图标，导致配方里出现紫黑缺失贴图。

现在自定义模型缓存未命中时，无论有无标签都会用请求堆叠执行原始绘制；物品重放统一解析原始图标和独立贴图，同时接受已转换的宿主资源 ID。没有给配方堆叠补上创造模式标签，也没有修改配方或合成后的物品状态。

## 复现与验收

开发模式打开实际 JEI 不稳定锭配方页，随后比较铁锭、金锭、基岩锭、不稳定锭各变种和创造栏带标签版本的原生、JEI 单格、JEI 批量渲染。逐格检查存在物品像素且没有紫黑缺失纹理，并保存实际配方页与对照图供看图检查。

```powershell
.\gradlew.bat --offline --project-cache-dir C:/Users/gengy/AppData/Local/Temp/mfcl-recipe-client-cache `
  -I tools/isolated-build.init.gradle '-PmfclValidationBuildDir=../../AppData/Local/Temp/mfcl-recipe-client-output' `
  runClient '-PmfclRunDir=C:/Users/gengy/AppData/Local/Temp/mfcl-recipe-game' `
  '-PlegacyRuntimeDir=C:/Users/gengy/AppData/Local/Temp/mfcl-validation-inputs/runtime' -PmfclRecipeItemSmokeTest
```

测试目录需要 `mods/1.7.10/extrautilities-1.2.12.jar`。结果查看 `logs/mfcl/recipe-items-smoke.json`，截图为 `screenshots/recipe-ingots-jei.png` 和 `screenshots/recipe-ingots-comparison.png`。Gradle 成功退出不代表验收通过，须同时检查报告状态和截图。

实机初次修复验收为 PASS：7 种堆叠分别通过原生、JEI 单格和批量绘制，21 个图标格均有实际物品像素且无紫黑缺失纹理。无标签的不稳定锭和 `{creative:1b}` 创造栏版本每格同为 376 个物品像素；已看图确认实际 JEI 配方页恢复显示。
