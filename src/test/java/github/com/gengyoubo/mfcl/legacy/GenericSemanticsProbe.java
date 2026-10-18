package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import org.objectweb.asm.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Runs in a fresh diagnostic JVM. Exercises actual old types without any mod-specific adapter. */
public final class GenericSemanticsProbe {
    static void verify(EmbeddedLegacyRuntime runtime, Map<String, Object> report) throws Exception {
        if (!Boolean.TRUE.equals(report.get("legacyFmlStarted"))) throw new AssertionError(report);
        LegacyServices services = runtime.services(); ClassLoader loader = services.loader();
        if (loader.loadClass("org.w3c.dom.Node") != org.w3c.dom.Node.class) throw new AssertionError("JDK XML types were not shared");
        if (loader.loadClass("org.objectweb.asm.Type").getClassLoader() != loader) throw new AssertionError("Host ASM escaped into the private runtime");
        Class<?> reflection = loader.loadClass("github.com.gengyoubo.mfcl.legacy1710.LegacyReflection");
        var modifiers = (java.lang.reflect.Field) reflection.getMethod("declaredField", Class.class, String.class).invoke(null, java.lang.reflect.Field.class, "modifiers");
        reflection.getMethod("accessible", java.lang.reflect.AccessibleObject.class, boolean.class).invoke(null, modifiers, true);
        var keyMap = loader.loadClass("net.minecraft.client.settings.KeyBinding").getDeclaredField("field_74514_b");
        modifiers.setInt(keyMap, keyMap.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        if (java.lang.reflect.Modifier.isFinal(keyMap.getModifiers())) throw new AssertionError("Legacy Field.modifiers idiom did not work");
        try { java.lang.reflect.Field.class.getDeclaredField("modifiers"); throw new AssertionError("JDK reflection filtering was changed globally"); }
        catch (NoSuchFieldException expected) { }
        report.put("genericModernJavaAndPlatformIsolationVerified", true);
        Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft");
        Object resourceManager = minecraft.getMethod("func_110442_L").invoke(minecraft.getMethod("func_71410_x").invoke(null));
        Class<?> locationType = loader.loadClass("net.minecraft.util.ResourceLocation");
        var getResource = loader.loadClass("net.minecraft.client.resources.IResourceManager").getMethod("func_110536_a", locationType);
        Object resource = getResource.invoke(resourceManager, locationType.getConstructor(String.class).newInstance("minecraft:textures/font/ascii.png"));
        try (var stream = (java.io.InputStream) loader.loadClass("net.minecraft.client.resources.IResource").getMethod("func_110527_b").invoke(resource)) {
            if (stream.read() != 137 || stream.read() != 80) throw new AssertionError("Original resource stream was not preserved");
        }
        try { getResource.invoke(resourceManager, locationType.getConstructor(String.class).newInstance("minecraft:mfcl-missing-resource")); throw new AssertionError("Missing legacy resource was silently accepted"); }
        catch (InvocationTargetException expected) { if (!(expected.getCause() instanceof java.io.IOException)) throw expected; }
        report.put("genericOriginalResourceManagerVerified", true);
        Class<?> sys = loader.loadClass("org.lwjgl.Sys");
        long tick = (Long) sys.getMethod("getTime").invoke(null);
        long resolution = (Long) sys.getMethod("getTimerResolution").invoke(null);
        if (resolution != 1_000_000 || Math.abs(tick / 1000 - System.nanoTime() / 1_000_000) > 1000)
            throw new AssertionError("Private platform clock did not preserve elapsed-time units");
        var diamond = services.items().catalog().stream().filter(s -> s.key().equals("minecraft:diamond")).findFirst().orElseThrow();
        Object first = services.items().materialize(diamond), second = services.items().materialize(diamond);
        if (first == second) throw new AssertionError("Stacks alias across ownership boundary");
        first.getClass().getField("field_77994_a").setInt(first, 17);
        if (services.items().snapshot(second).count() != diamond.count()) throw new AssertionError("Stack copy leaked mutable count");
        // The old parser predates modern SNBT typed-array notation; preserve old NBT syntax at this boundary.
        var withNbt = new LegacyClientData.Stack(diamond.key(), 7, 11, diamond.name(), "{mfcl_probe:{value:23},label:\"bridge\",numbers:[1,2,3]}");
        Object nbtStack = services.items().materialize(withNbt);
        Object nbt = nbtStack.getClass().getField("field_77990_d").get(nbtStack);
        Object child = nbt.getClass().getMethod("func_74775_l", String.class).invoke(nbt, "mfcl_probe");
        if ((int) child.getClass().getMethod("func_74762_e", String.class).invoke(child, "value") != 23
                || services.items().snapshot(nbtStack).metadata() != 7 || services.items().snapshot(nbtStack).count() != 11)
            throw new AssertionError("Fresh legacy NBT was not materialized with original types");
        Object copiedNbt = services.items().materialize(services.items().snapshot(nbtStack));
        if (copiedNbt.getClass().getField("field_77990_d").get(copiedNbt) == nbt) throw new AssertionError("Legacy NBT aliases across stack copies");
        report.put("genericFreshNbtRoundTripVerified", true);
        Class<?> tabFixture = creativeFixture(loader);
        var tabConstructor = tabFixture.getConstructor(String.class, loader.loadClass("net.minecraft.item.ItemStack"), List.class);
        tabConstructor.newInstance("mfcl_test_category_a", nbtStack, List.of(nbtStack, second));
        tabConstructor.newInstance("mfcl_test_category_b", second, List.of(second, nbtStack));
        var originalTabs = LegacyCreativeTabs.read(services.items(), Set.of());
        var tabA = originalTabs.stream().filter(tab -> tab.label().equals("mfcl_test_category_a")).findFirst().orElseThrow();
        var tabB = originalTabs.stream().filter(tab -> tab.label().equals("mfcl_test_category_b")).findFirst().orElseThrow();
        if (tabA.key().equals(tabB.key()) || !tabA.nameKey().equals("itemGroup.mfcl_test_category_a")
                || !tabB.nameKey().equals("itemGroup.mfcl_test_category_b") || tabA.builtin() || tabB.builtin()
                || !tabA.icon().equals(services.items().snapshot(nbtStack)) || !tabB.icon().equals(services.items().snapshot(second))
                || !tabA.contents().equals(List.of(services.items().snapshot(nbtStack), services.items().snapshot(second)))
                || !tabB.contents().equals(List.of(services.items().snapshot(second), services.items().snapshot(nbtStack))))
            throw new AssertionError("Original creative tab callbacks, category isolation, icon metadata/NBT or ordering were lost");
        report.put("genericOriginalCreativeTabsVerified", true);
        final class Host implements HostWorldAccess {
            long time = 100; Block value = new Block("minecraft:dirt", 0);
            public String dimensionKey() { return "mfcl:test"; } public int legacyDimensionId() { return 7; } public boolean clientSide() { return true; }
            public long gameTime() { return time; } public long dayTime() { return time; } public void setDayTime(long time) { this.time = time; }
            public Block block(int x, int y, int z) { return value; }
            public boolean setBlock(int x, int y, int z, Block value, int flags) { this.value = value; return true; }
        }
        Host host = new Host(); Object identity = new Object(), world = services.worlds().bind(identity, host);
        if (world != services.worlds().bind(identity, host)) throw new AssertionError("World identity changed");
        var provider = loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
        if (provider.getClass().getField("field_76574_g").getInt(provider) != 7 || provider.getClass().getField("field_76579_a").get(provider) != world)
            throw new AssertionError("World/provider direct fields lost identity");
        var time = world.getClass().getMethod("func_72820_D");
        world.getClass().getMethod("func_72877_b", long.class).invoke(world, 321L);
        if ((Long) time.invoke(world) != 321L || host.time != 321) throw new AssertionError("World writes are not synchronous");
        Object registry = loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
        Object stone = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, "minecraft:stone");
        world.getClass().getMethod("func_147465_d", int.class, int.class, int.class, loader.loadClass("net.minecraft.block.Block"), int.class, int.class)
                .invoke(world, 1, 2, 3, stone, 0, 3);
        if (world.getClass().getMethod("func_147439_a", int.class, int.class, int.class).invoke(world, 1, 2, 3) != stone)
            throw new AssertionError("World read-after-write returned stale data");
        boolean rejected = CompletableFuture.supplyAsync(() -> {
            try { time.invoke(world); return false; }
            catch (Exception error) { return error instanceof InvocationTargetException && error.getCause() instanceof IllegalStateException; }
        }).get();
        if (!rejected) throw new AssertionError("Cross-thread world access was accepted");
        services.worlds().unbind(identity);
        try { time.invoke(world); throw new AssertionError("Expired world still works"); }
        catch (InvocationTargetException expected) { if (!(expected.getCause() instanceof IllegalStateException)) throw expected; }
        Object oldGui = fixture(loader).getConstructor().newInstance();
        var session = services.screens().open(oldGui, 320, 240); session.resize(640, 480); session.mousePressed(12, 34, 0); session.keyPressed('a', 30);
        if (oldGui.getClass().getField("field_146294_l").getInt(oldGui) != 640 || oldGui.getClass().getField("field_146295_m").getInt(oldGui) != 480
                || oldGui.getClass().getField("clicks").getInt(oldGui) != 1 || oldGui.getClass().getField("keys").getInt(oldGui) != 1) throw new AssertionError("Old GuiScreen lifecycle/input did not execute");
        session.close();
        if (oldGui.getClass().getField("closed").getInt(oldGui) != 1) throw new AssertionError("Old GuiScreen did not close");
        try { session.mousePressed(0, 0, 0); throw new AssertionError("Closed screen accepted input"); } catch (IllegalStateException expected) {}
        report.put("genericItemWorldGuiSemanticsVerified", true);
        try(var capture=LegacyGuiGraphics.begin(320,240,12,34,Set.of(29))) {
            Class<?> gl=loader.loadClass("org.lwjgl.opengl.GL11");gl.getMethod("glPushMatrix").invoke(null);gl.getMethod("glTranslatef",float.class,float.class,float.class).invoke(null,10f,20f,0f);
            loader.loadClass("net.minecraft.client.gui.Gui").getMethod("func_73734_a",int.class,int.class,int.class,int.class,int.class).invoke(null,1,2,4,5,0xff00ff00);
            gl.getMethod("glPopMatrix").invoke(null);
            Class<?> mc=loader.loadClass("net.minecraft.client.Minecraft");Object client=mc.getMethod("func_71410_x").invoke(null);
            Object font=mc.getField("field_71466_p").get(client);font.getClass().getMethod("func_78276_b",String.class,int.class,int.class,int.class).invoke(font,"generic",3,7,0xffffff);
            if(!capture.commands().contains(new LegacyGuiGraphics.Rect(11,22,14,25,0xff00ff00,0xff00ff00))
                    ||capture.commands().stream().noneMatch(LegacyGuiGraphics.Text.class::isInstance))throw new AssertionError("Generic old GUI drawing did not cross the render boundary");
            Class<?> keyboard=loader.loadClass("org.lwjgl.input.Keyboard");
            if(!(Boolean)keyboard.getMethod("isKeyDown",int.class).invoke(null,29)||!(Integer.valueOf(19).equals(keyboard.getMethod("getKeyIndex",String.class).invoke(null,"R"))))throw new AssertionError("Legacy keyboard semantics were not preserved");
            Object sound=loader.loadClass("net.minecraft.client.audio.PositionedSoundRecord").getMethod("func_147674_a",loader.loadClass("net.minecraft.util.ResourceLocation"),float.class)
                    .invoke(null,loader.loadClass("net.minecraft.util.ResourceLocation").getConstructor(String.class).newInstance("gui.button.press"),1f);
            Object soundHandler=mc.getMethod("func_147118_V").invoke(client);
            soundHandler.getClass().getMethod("func_147682_a",loader.loadClass("net.minecraft.client.audio.ISound")).invoke(soundHandler,sound);
            if(!capture.hasClickSound())throw new AssertionError("Old button sound did not reach the host boundary");
            try{gl.getMethod("glClear",int.class).invoke(null,0);throw new AssertionError("Unsupported native draw operation was silently accepted");}
            catch(InvocationTargetException expected){if(!(expected.getCause() instanceof UnsupportedOperationException))throw expected;}
        }
        report.put("genericGuiCaptureVerified",true);
        Class<?> privateGl = loader.loadClass("org.lwjgl.opengl.GL11");
        int displayList = (Integer) privateGl.getMethod("glGenLists", int.class).invoke(null, 1);
        privateGl.getMethod("glNewList", int.class, int.class).invoke(null, displayList, 4864);
        privateGl.getMethod("glBegin", int.class).invoke(null, 7);
        privateGl.getMethod("glColor4d", double.class, double.class, double.class, double.class).invoke(null, 1d, 0.5d, 0d, 1d);
        privateGl.getMethod("glVertex3d", double.class, double.class, double.class).invoke(null, 1.25d, 2d, 3d);
        privateGl.getMethod("glEnd").invoke(null);
        privateGl.getMethod("glEndList").invoke(null);
        if (github.com.gengyoubo.mfcl.bridge.LegacyDisplayLists.commands(privateGl, displayList).size() != 4)
            throw new AssertionError("Private GL display list lost original commands");
        privateGl.getMethod("glDeleteLists", int.class, int.class).invoke(null, displayList, 1);
        report.put("genericDisplayListCaptureVerified", true);
    }
    private static Class<?> fixture(ClassLoader loader) throws Exception {
        String name = "mfcl/fixture/Gui"; ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/client/gui/GuiScreen", null);
        for (String field : List.of("clicks", "keys", "closed")) writer.visitField(Opcodes.ACC_PUBLIC, field, "I", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/client/gui/GuiScreen", "<init>", "()V", false); constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); constructor.visitEnd();
        for (String[] method : List.of(new String[]{"func_73864_a", "(III)V", "clicks"}, new String[]{"func_73869_a", "(CI)V", "keys"}, new String[]{"func_146281_b", "()V", "closed"})) {
            var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method[0], method[1], null, null); mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitInsn(Opcodes.DUP);
            mv.visitFieldInsn(Opcodes.GETFIELD, name, method[2], "I"); mv.visitInsn(Opcodes.ICONST_1); mv.visitInsn(Opcodes.IADD); mv.visitFieldInsn(Opcodes.PUTFIELD, name, method[2], "I");
            mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(3, 4); mv.visitEnd();
        }
        writer.visitEnd(); byte[] bytes = writer.toByteArray();
        return (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class).invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
    }
    private static Class<?> creativeFixture(ClassLoader loader) throws Exception {
        String name = "mfcl/fixture/CreativeCategory", stack = "Lnet/minecraft/item/ItemStack;";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/creativetab/CreativeTabs", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "icon", stack, null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "contents", "Ljava/util/List;", null, null).visitEnd();
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;" + stack + "Ljava/util/List;)V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/creativetab/CreativeTabs", "<init>", "(Ljava/lang/String;)V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 2); constructor.visitFieldInsn(Opcodes.PUTFIELD, name, "icon", stack);
        constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 3); constructor.visitFieldInsn(Opcodes.PUTFIELD, name, "contents", "Ljava/util/List;");
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(3, 4); constructor.visitEnd();
        for (String method : List.of("func_151244_d", "func_78016_d")) {
            var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method, "()" + (method.equals("func_151244_d") ? stack : "Lnet/minecraft/item/Item;"), null, null);
            mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitFieldInsn(Opcodes.GETFIELD, name, "icon", stack);
            if (method.equals("func_78016_d")) mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "func_77973_b", "()Lnet/minecraft/item/Item;", false);
            mv.visitInsn(Opcodes.ARETURN); mv.visitMaxs(1, 1); mv.visitEnd();
        }
        var contents = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_78018_a", "(Ljava/util/List;)V", null, null);
        contents.visitCode(); contents.visitVarInsn(Opcodes.ALOAD, 1); contents.visitVarInsn(Opcodes.ALOAD, 0);
        contents.visitFieldInsn(Opcodes.GETFIELD, name, "contents", "Ljava/util/List;");
        contents.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true);
        contents.visitInsn(Opcodes.POP); contents.visitInsn(Opcodes.RETURN); contents.visitMaxs(2, 2); contents.visitEnd();
        writer.visitEnd(); byte[] bytes = writer.toByteArray();
        return (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class).invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
    }
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]);
        try (var runtime = new EmbeddedLegacyRuntime(game, Path.of(args[1]), new LegacyRuntimeService().inspect(game))) {
            var report = runtime.boot(); verify(runtime, report);
            Files.writeString(game.resolve("logs/mfcl/runtime-1.7.10.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("MFCL_GENERIC_SEMANTICS_PASS");
        }
    }
}
