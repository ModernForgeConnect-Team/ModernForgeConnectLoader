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
        var diamond = services.items().catalog().stream().filter(s -> s.key().equals("minecraft:diamond")).findFirst().orElseThrow();
        Object first = services.items().materialize(diamond), second = services.items().materialize(diamond);
        if (first == second) throw new AssertionError("Stacks alias across ownership boundary");
        first.getClass().getField("field_77994_a").setInt(first, 17);
        if (services.items().snapshot(second).count() != diamond.count()) throw new AssertionError("Stack copy leaked mutable count");
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
            try{gl.getMethod("glClear",int.class).invoke(null,0);throw new AssertionError("Unsupported native draw operation was silently accepted");}
            catch(InvocationTargetException expected){if(!(expected.getCause() instanceof UnsupportedOperationException))throw expected;}
        }
        report.put("genericGuiCaptureVerified",true);
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
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]);
        try (var runtime = new EmbeddedLegacyRuntime(game, Path.of(args[1]), new LegacyRuntimeService().inspect(game))) {
            var report = runtime.boot(); verify(runtime, report);
            Files.writeString(game.resolve("logs/mfcl/runtime-1.7.10.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("MFCL_GENERIC_SEMANTICS_PASS");
        }
    }
}
