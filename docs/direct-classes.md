# 直通类（Direct classes，实验原型，API v4）

MLA 要求逐个成员写出旧类名、SRG 名和描述符字符串。直通类则让附属把**需要大量调用旧 API 的代码直接写成 1.7.10 代码**：真正 `import net.minecraft.entity.player.EntityPlayer`，可以继承旧类、实现旧接口、用 lambda 和泛型。现代代码通过一个普通的 Java 接口调用它。

探测：`runtime.supports(LegacyFeatures.DIRECT)`。

## 为什么不是让现代加载器直接加载旧类

- 1.7.10 与 1.20.1 有完全同名的类（`net.minecraft.client.Minecraft`、`net.minecraft.server.MinecraftServer`、`net.minecraftforge.common.MinecraftForge`……），一个加载器里一个名字只能对应一个类。
- 类的身份 = 类名 + 定义它的加载器。现代侧再加载一份 `EntityPlayer`，与旧运行时实际运行的那份不是同一个类，旧对象无法转换。
- JVM 要求 `loadClass(N)` 返回名为 N 的类，不能做别名。

所以反过来：直通类由 MFCL 用 `DirectLegacyLoader` 定义在隔离运行时旁边，它同时看得见两边。

## 类解析规则

直通类引用名字 N 时依次查找：

1. `java.*` → JDK；
2. `github.com.gengyoubo.mfcl.api.*` → 宿主侧 MFCL API；
3. 附属 JAR 中的 `META-INF/mfcl/direct/N.class` → 定义为直通类；
4. 原始运行时（`EntityPlayer`、`ItemStack`、Thaumcraft API，以及旧版 Guava 等库）；
5. 附属自己的宿主加载器（共享接口等）。

**同名时旧版优先。** 直通类里写 `Minecraft` 得到的是 1.7.10 的那个；直通类不应引用现代 Minecraft 类型，现代对象请以 `Object` 传入并在现代侧处理。

## 写法

```java
// src/main/java —— 现代侧共享接口：签名只用 JDK、MFCL API 和附属自己的宿主类型
package com.example.addon;
public interface ArmorApi {
    String helmetName(Object legacyPlayer);
    Object helmet(Object legacyPlayer);     // 旧对象以 Object / LegacyObject 返回
}
```

```java
// src/direct/java —— 直通类：编译期类路径为 1.7.10 deobf + 共享接口
package com.example.addon.direct;

import com.example.addon.ArmorApi;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public final class ArmorLogic implements ArmorApi {
    public String helmetName(Object player) {
        ItemStack helmet = ((EntityPlayer) player).getCurrentArmor(3);
        return helmet == null ? "" : helmet.getDisplayName();
    }
    public Object helmet(Object player) { return ((EntityPlayer) player).getCurrentArmor(3); }
}
```

```java
// 现代侧调用
ArmorApi armor = runtime.direct(ArmorApi.class, "com.example.addon.direct.ArmorLogic");
String name = runtime.withPlayer(serverPlayer, player -> armor.helmetName(player));
```

`direct(...)` 每次都会新建实例；请在 `whenReady` 中创建一次并保存返回的对象。

## Gradle：直通源集

[`gradle/mfcl-direct.gradle`](../gradle/mfcl-direct.gradle) 是一个独立的脚本插件，附属复制这一个文件即可使用：

```groovy
// build.gradle（附属）
ext.mfclDirect = [
        sourceSet: 'direct',                                            // src/direct/java
        runtime  : file('run/mfcl/runtime/1.7.10'),                     // 装有 MFCL 的游戏启动一次后生成
        mods     : fileTree('run/mods') { include 'Thaumcraft-*.jar' }, // 直通类 import 的原始模组
        host     : files(sourceSets.main.java.classesDirectory),        // 直通类实现的宿主接口
]
apply from: 'gradle/mfcl-direct.gradle'
```

它会创建：

| 任务 | 作用 |
| --- | --- |
| `<sourceSet>DeobfMinecraft` | 把已准备好的 `minecraft-forge-1.7.10-srg.jar` 按 `mcp_stable-12-1.7.10`（`fields.csv` / `methods.csv`，首次从 Forge maven 下载）改成 MCP 成员名，作为编译类路径；不分发 |
| `compile<SourceSet>Java` | `--release 8`；类路径依次为 MCP 版运行时、运行时库、`mods`、`host`。同名类旧版优先，与运行时一致 |
| `<sourceSet>Reobf` | 把编译结果中的 MCP 成员名改回 SRG 名：按 owner + 名称 + 描述符沿继承链（原版、`mods`、直通类自身）查找，因此对 `getCurrentArmor` 的调用和 `extends CommandBase` 时对 `getCommandName` 的覆写都会被改名；未混淆的旧模组 API（如 `Aspect.aspects`）保持不变 |
| `processResources` | 默认把 reobf 结果放进 `META-INF/mfcl/direct/`，开发运行和最终 JAR 都包含；设 `packaged: false` 可自行处理 |

可选项：`mappings`（默认 `'de.oceanlabs.mcp:mcp_stable:12-1.7.10@zip'`）、`packaged`（默认 `true`）。

编译用的 MCP 版运行时还有一处调整：原版只把泛型参数覆写（例如 `CommandBase.compareTo(Object)`）保留为 synthetic 方法，javac 会忽略它们，导致继承这些类时报“未覆盖抽象方法”。脚本把同名同参数下没有其他方法的 synthetic 方法设为可见，效果与当年从 MCP 源码重新编译时一样；协变返回值的桥接方法保持 synthetic，不会造成调用歧义。

## 跨边界规则

| 方向 | 规则 |
| --- | --- |
| 进入直通类的参数 | `LegacyObject` 与 MLA 访问器代理变为原始对象并检查其守卫；直通代理变为直通对象；其余值原样传入（数组、集合内的元素不转换） |
| 直通类返回原始对象 | 接口返回类型必须是 `Object`、`LegacyObject` 或 `@LegacyType` 契约，得到与参数同守卫的引用；其他返回类型会报错 |
| 直通类返回另一个直通对象 | 返回类型为接口时自动包装，之后的调用同样进入运行时 scope |
| 其他返回值 | 原样返回 |

每次通过代理调用都会进入运行时 scope（与 MLA 相同：加锁、切换上下文加载器和图形域），失败记录到 `/mfclapi` 诊断并以 `LegacyAccessException` 抛出。直通类内部再调用旧代码就是普通 Java 调用，没有额外开销。

## 限制与注意事项

- **引用寿命**：直通类内部拿到的是裸原始对象，没有守卫。`withPlayer` 借出的玩家在回调结束后不得再使用，不要存进字段。
- **成员名**：1.7.10 运行时成员是 SRG 名。用上面的 Gradle 脚本写 MCP 名即可；不用脚本时须自行 reobf。反射里写的成员名字符串不会被改写，需要直接写 SRG 名。`mcp_stable-12` 中没有 MCP 名的成员仍以 `func_` / `field_` 名出现。
- **打包**：直通类必须放在 `META-INF/mfcl/direct/` 下，不能作为普通 `.class` 进入现代 JAR，否则现代加载器会先定义它们。
- **注册时机**：直通类可以继承 `Item` 等旧类，但目前没有在旧 FML 生命周期内注册内容的钩子；本原型适合逻辑调用，不适合注册新内容。
- **旧 transformer**：直通类不经过旧 `LaunchClassLoader` 的 transformer 链（包括 MFCL 的调用点重定向）。
- **共享接口**：签名中的类型在两个加载器下必须解析为同一个类，因此只能用 JDK、MFCL API 和附属自己的宿主类型；与旧运行时同名的库类型（如 Guava）会触发加载约束错误。

## 验证状态

- 单元测试 `LegacyDirectClassesTest`：测试时用 javac 编译一个模拟旧运行时、一个宿主接口和一个真正 `import` 旧类的直通类，验证直通类看到的 `EntityPlayer` 与运行时同一个 `Class`、同名类旧版优先、返回值成为引用、返回的直通对象调用同样进入 scope、过期借用引用被拒绝、实现类校验。
- 游戏内 `-PmfclApiSmokeTest`（Forge 1.20.1 客户端 + 集成服务器，TC4/Baubles）：`src/directSmoke` 中的 `DirectSmokeLogic` / `DirectCommand` 用 MCP 名编写，经 `gradle/mfcl-direct.gradle` 编译和 reobf，仅在该测试中打包进 `META-INF/mfcl/direct/`。`DirectCommand extends CommandBase`，原始的 `CommandBase.compareTo` 字节码按 SRG 名调用它 reobf 后的 `getCommandName` 覆写（`originalCodeCallsReobfuscatedOverride`）。它在服务器线程经 `withPlayer` 被调用，`api-smoke.json` 的 `directClasses` 记录：直通类由 `DirectLegacyLoader` 定义、同名 `MinecraftServer` 解析到 `LegacyLaunchLoader`、原始 TC4 要素注册表可见（48 个）、现代头盔同步后从旧玩家读到 `minecraft:iron_helmet` 及其原始显示名、回调结束后的旧玩家被拒绝。这说明 ModLauncher 命名模块下的 `META-INF` 资源读取和直通类实现 MFCL 模块接口均可行。

尚未覆盖：独立专用服务器、带 `module-info` 的附属、直通类继承旧类并注册内容。
