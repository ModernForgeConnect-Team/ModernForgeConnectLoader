# 旧版物品和世界音效

2026-10-19，原始 Thaumcraft 4.2.3.5 / Forge 1.20.1。

缺音效的两个原因是世界立面声音入口为空，以及生成资源包未包含原模组的声音定义和 OGG 文件。现在将原音频与 `sounds.json` 纳入派生资源包；为声音文件名补全原命名空间并转换新版要求的小写名称，保留原有权重、音量、音高和流式选项。

旧 `World.playSoundEffect`、客户端 `World.playSound`、`playSoundAtEntity` 和排除指定玩家的声音入口进入宿主声音系统，保留位置、音量和音高。客户端调用本地播放，服务端广播给附近玩家；服务端事务中的声音延迟到提交后发送。原本仅在客户端生效的 `playSound` 在服务端仍不广播，避免扫描快门重复播放。原来的 `random.orb`、`random.bow`、`game.neutral.swim` 等常用原版声音映射为新版事件名称。

本轮验证：

- 一次原始透镜扫描产生 10 次 `thaumcraft:cameraticks`，进入实际音频通道；扫描奖励的经验球提示声也进入音频通道。结果见 [thaumometer-progress.json](thaumometer-progress.json)。
- 原运行时测试检查世界和实体声音的参数转发、排除玩家 UUID，以及服务端不播放客户端专用声音的语义。
- 声音定义转换测试检查原命名空间、大小写、事件引用、权重及播放选项。
- 服务端发出的 `thaumcraft:wand` 及原手册按钮的 `thaumcraft:cameraclack` 进入实际音频通道；手册打开、正文、分类切换和退出回归通过，见 [thaumonomicon-progress.json](thaumonomicon-progress.json)。

JUnit 51 项通过、4 项缺少外部样本跳过，`test assemble` 成功。打包输出与游戏验收的 192 个宿主类一致，产物已更新至 `build/libs/mfcl-0.1.0.jar`。

此改动覆盖以上世界、实体声音入口。完整的旧循环声音对象生命周期、唱片播放和所有旧原版声音名称仍需各自适配，不代表旧音频引擎已完整移植。
