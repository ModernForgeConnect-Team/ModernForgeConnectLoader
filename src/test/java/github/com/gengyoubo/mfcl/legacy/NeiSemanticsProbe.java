package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import github.com.gengyoubo.mfcl.compat.nei.NeiBackend;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import java.nio.file.*;
import java.util.*;

public final class NeiSemanticsProbe {
    public static void main(String[] args) throws Exception {
        Path game = Path.of(args[0]);
        try (var runtime = new EmbeddedLegacyRuntime(game, Path.of(args[1]), new LegacyRuntimeService().inspect(game))) {
            var report = runtime.boot(); GenericSemanticsProbe.verify(runtime, report);
            verifyOriginalGui(runtime,report);
            var backend = new NeiBackend(runtime.services().items());
            var table = backend.search("crafting").stream().filter(s -> s.key().equals("minecraft:crafting_table")).findFirst().orElseThrow();
            var diamond = backend.search("diamond").stream().filter(s -> s.key().equals("minecraft:diamond")).findFirst().orElseThrow();
            var items = runtime.services().items();
            var projected = new LegacyClientData.HostRecipe("mfcl:projection", "shapeless", 0, 0, List.of(List.of(diamond)), table);
            items.projectHostRecipes(List.of(projected)); items.projectHostRecipes(List.of(projected));
            var recipes = backend.recipes(table, false);
            if (recipes.size() != 1 || recipes.get(0).ingredients().stream().flatMap(i -> i.alternatives().stream()).noneMatch(s -> s.key().equals("minecraft:diamond")) || backend.recipes(diamond, true).isEmpty()) throw new AssertionError("Original NEI did not consume generic recipe projection");
            try { items.projectHostRecipes(List.of(new LegacyClientData.HostRecipe("mfcl:invalid", "unknown", 0, 0, List.of(), table))); throw new AssertionError("Invalid recipe was accepted"); }
            catch (IllegalArgumentException expected) { if (backend.recipes(table, false).size() != 1) throw new AssertionError("Failed projection mutated live recipes"); }
            report.put("recipeProjectionVerified", true);
            items.importHostItems(List.of(new LegacyClientData.HostItem("mfcl:probe_a", "MFCL probe A", 64, 0), new LegacyClientData.HostItem("mfcl:probe_b", "MFCL probe B", 64, 0)));
            var a = items.catalog().stream().filter(s -> s.key().equals("mfcl_host:mfcl/probe_a")).findFirst().orElseThrow();
            var b = items.catalog().stream().filter(s -> s.key().equals("mfcl_host:mfcl/probe_b")).findFirst().orElseThrow();
            items.projectHostRecipes(List.of(new LegacyClientData.HostRecipe("mfcl:identity", "shapeless", 0, 0, List.of(List.of(a)), b)));
            if (backend.recipes(b, false).size() != 1 || backend.recipes(a, true).isEmpty() || !backend.recipes(b, true).isEmpty()) throw new AssertionError("Host Item identity was lost");
            report.put("hostItemIdentityVerified", true);
            ClassLoader loader = runtime.services().loader(); Class<?> data = loader.loadClass("cpw.mods.fml.common.registry.GameData");
            var getMain = data.getDeclaredMethod("getMain"); getMain.setAccessible(true);
            Class<?> item = loader.loadClass("net.minecraft.item.Item"); var constructor = item.getDeclaredConstructor(); constructor.setAccessible(true);
            var register = data.getDeclaredMethod("registerItem", item, String.class); register.setAccessible(true);
            try { register.invoke(getMain.invoke(null), constructor.newInstance(), "mfcl:late_attempt"); throw new AssertionError("Late registration accepted"); }
            catch (java.lang.reflect.InvocationTargetException expected) { if (!(expected.getCause() instanceof IllegalStateException)) throw expected; report.put("lateRegistrationRejected", true); }
            Files.writeString(game.resolve("logs/mfcl/runtime-1.7.10.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("MFCL_NEI_SEMANTICS_PASS");
        }
    }
    private static void verifyOriginalGui(EmbeddedLegacyRuntime runtime,Map<String,Object> report) throws Exception {
        var services=runtime.services();var loader=services.loader();var mc=loader.loadClass("net.minecraft.client.Minecraft");Object client=mc.getMethod("func_71410_x").invoke(null);
        Object world=services.worlds().bind(new Object(),new github.com.gengyoubo.mfcl.bridge.HostWorldAccess() {
            public String dimensionKey(){return "minecraft:overworld";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return true;}
            public long gameTime(){return 100;}public long dayTime(){return 100;}public void setDayTime(long t){throw new UnsupportedOperationException();}
            public Block block(int x,int y,int z){return new Block("minecraft:air",0);}public boolean setBlock(int x,int y,int z,Block b,int flags){throw new UnsupportedOperationException();}
        });
        mc.getField("field_71441_e").set(client,world);
        mc.getField("field_71474_y").get(client).getClass().getField("field_74335_Z").setInt(mc.getField("field_71474_y").get(client),1);
        var api=github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.class;
        Object gui;
        github.com.gengyoubo.mfcl.bridge.LegacyScreens.Session session;
        try(var frame=github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.begin(854,480,0,0,Set.of())) {
            gui=services.containers().create(854,480,339,157,176,166,List.of());
            session=services.screens().open(gui,854,480);
        }
        try(session) {
            List<github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Command> commands=List.of();
            for(int i=0;i<50;i++) {
                try(var frame=github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.begin(854,480,0,0,Set.of())) {
                    session.draw(0,0,0);commands=frame.commands();
                }
                if(commands.stream().anyMatch(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::isInstance))break;
                Thread.sleep(50);
            }
            if(commands.stream().noneMatch(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Item.class::isInstance)
                    ||commands.stream().noneMatch(github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Text.class::isInstance))throw new AssertionError("Original GUI did not draw: "+commands);
            report.put("originalGuiCommands",commands.size());
            report.put("originalGuiCaptureVerified",true);
        }
    }
}
