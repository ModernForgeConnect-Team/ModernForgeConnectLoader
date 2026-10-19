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
        RuntimeLogicSemanticsProbe.verify(services,report);
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
        var withNbt = new LegacyClientData.Stack(diamond.key(), 7, 11, diamond.name(), "{mfcl_probe:{value:23},label:\"bridge\",numbers:[1,2,3],indexed:[{key:terra,amount:2},{key:aer,amount:3}]}");
        Object nbtStack = services.items().materialize(withNbt);
        Object nbt = nbtStack.getClass().getField("field_77990_d").get(nbtStack);
        Object child = nbt.getClass().getMethod("func_74775_l", String.class).invoke(nbt, "mfcl_probe");
        if ((int) child.getClass().getMethod("func_74762_e", String.class).invoke(child, "value") != 23
                || services.items().snapshot(nbtStack).metadata() != 7 || services.items().snapshot(nbtStack).count() != 11)
            throw new AssertionError("Fresh legacy NBT was not materialized with original types");
        Object copiedNbt = services.items().materialize(services.items().snapshot(nbtStack));
        if (copiedNbt.getClass().getField("field_77990_d").get(copiedNbt) == nbt) throw new AssertionError("Legacy NBT aliases across stack copies");
        var originalSnapshot=services.items().snapshot(nbtStack);
        var preview=services.items().renderTags(originalSnapshot,7,0x123456,"Anvil preview");
        Object previewStack=services.items().materialize(preview),previewTag=previewStack.getClass().getField("field_77990_d").get(previewStack);
        Object previewChild=previewTag.getClass().getMethod("func_74775_l",String.class).invoke(previewTag,"mfcl_probe");
        if((int)previewChild.getClass().getMethod("func_74762_e",String.class).invoke(previewChild,"value")!=23
                ||(int)previewTag.getClass().getMethod("func_74762_e",String.class).invoke(previewTag,"RepairCost")!=7
                ||!services.items().snapshot(nbtStack).equals(originalSnapshot))throw new AssertionError("Render NBT merge lost old data or mutated its source");
        Object numbers=previewTag.getClass().getMethod("func_150295_c",String.class,int.class).invoke(previewTag,"indexed",10);
        if((int)numbers.getClass().getMethod("func_74745_c").invoke(numbers)!=2)throw new AssertionError("Render NBT merge lost an old indexed list");
        report.put("genericRenderNbtCopyPreservesIndexedLists",true);
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
        Object stoneIcon = loader.loadClass("net.minecraft.block.Block").getMethod("func_149691_a", int.class, int.class).invoke(stone, 0, 0);
        Class<?> iconType = loader.loadClass("net.minecraft.util.IIcon");
        String iconName = (String)iconType.getMethod("func_94215_i").invoke(stoneIcon);
        float[] iconCell = services.items().iconLayout().get(iconName);
        if ((float)iconType.getMethod("func_94206_g").invoke(stoneIcon) != iconCell[1]
                || (float)iconType.getMethod("func_94210_h").invoke(stoneIcon) != iconCell[3]
                || (float)iconType.getMethod("func_94207_b", double.class).invoke(stoneIcon, 0d) != iconCell[1]
                || (float)iconType.getMethod("func_94207_b", double.class).invoke(stoneIcon, 16d) != iconCell[3])
            throw new AssertionError("Original IIcon V endpoints and interpolated V disagree");
        report.put("genericIconUvEndpointsVerified", true);
        var shapes = new LegacyBlockShapes(services);
        var stoneShape = shapes.shapes("minecraft:stone",LegacyBlockRendering.isolatedView("minecraft:stone",0));
        if (stoneShape.collision().size() != 1 || stoneShape.collision().get(0).maxY() != 1)
            throw new AssertionError("Original stone collision did not remain a full cube");
        var plantShape = shapes.shapes("minecraft:red_flower",LegacyBlockRendering.isolatedView("minecraft:red_flower",0));
        if (!plantShape.collision().isEmpty() || plantShape.outline().isEmpty()) throw new AssertionError("Plant selection and empty collision were conflated");
        var lower = shapes.shapes("minecraft:stone_stairs",LegacyBlockRendering.isolatedView("minecraft:stone_stairs",0));
        var upper = shapes.shapes("minecraft:stone_stairs",LegacyBlockRendering.isolatedView("minecraft:stone_stairs",4));
        for (var stair : List.of(lower,upper)) {
            double volume = stair.collision().stream().mapToDouble(b -> (b.maxX()-b.minX())*(b.maxY()-b.minY())*(b.maxZ()-b.minZ())).sum();
            if (Math.abs(volume-.75) > 1e-6) throw new AssertionError("Original stair multipart collision volume changed: " + stair);
        }
        if (lower.equals(upper)) throw new AssertionError("Stair collision cache lost upper/lower metadata");
        report.put("genericOriginalBlockShapesVerified", true);
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
        services.containers().create(320, 240, 20, 20, 176, 166, List.of(new LegacyContainers.Slot(8, 18, diamond)));
        Object clientPlayer = minecraft.getField("field_71439_g").get(minecraft.getMethod("func_71410_x").invoke(null));
        Class<?> living = loader.loadClass("net.minecraft.entity.EntityLivingBase");
        if ((Boolean)living.getMethod("func_82165_m", int.class).invoke(clientPlayer, 1))
            throw new AssertionError("Fresh GUI player unexpectedly has a potion effect");
        Object potion = loader.loadClass("net.minecraft.potion.PotionEffect").getConstructor(int.class, int.class).newInstance(1, 100);
        var potionField = living.getDeclaredField("field_70713_bf"); potionField.setAccessible(true);
        @SuppressWarnings("unchecked") var effects = (Map<Integer, Object>)potionField.get(clientPlayer);
        effects.put(1, potion);
        services.containers().create(320, 240, 20, 20, 176, 166, List.of(new LegacyContainers.Slot(8, 18, diamond)));
        if (!(Boolean)living.getMethod("func_82165_m", int.class).invoke(clientPlayer, 1))
            throw new AssertionError("Reopening a container discarded the player's potion state");
        report.put("genericContainerPlayerPotionStateVerified", true);
        Class<?> entityType = loader.loadClass("net.minecraft.entity.Entity");
        Object entityData = entityType.getMethod("getEntityData").invoke(clientPlayer);
        entityData.getClass().getMethod("func_74778_a", String.class, String.class).invoke(entityData, "mfcl_probe", "kept");
        byte[] dataBytes = services.playerData().read(clientPlayer);
        if (dataBytes == null || dataBytes.length == 0) throw new AssertionError("Legacy player persistent data did not serialize");
        services.playerData().write(clientPlayer, dataBytes);
        Object reloaded = entityType.getMethod("getEntityData").invoke(clientPlayer);
        if (!"kept".equals(reloaded.getClass().getMethod("func_74779_i", String.class).invoke(reloaded, "mfcl_probe")))
            throw new AssertionError("Legacy player persistent data did not round-trip");
        report.put("genericPlayerDataBridgeVerified", true);
        Object oldClient = minecraft.getMethod("func_71410_x").invoke(null);
        Object previousViewer = minecraft.getField("field_71451_h").get(oldClient);
        var playerWorld = loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p");
        Object previousWorld = playerWorld.get(clientPlayer), previewWorld;
        Class<?> managerType = loader.loadClass("net.minecraft.client.renderer.entity.RenderManager");
        Object manager = managerType.getField("field_78727_a").get(null);
        Object previousEntityWorld = managerType.getField("field_78722_g").get(manager);
        Object previousEntityViewer = managerType.getField("field_78734_h").get(manager);
        try (var context = new LegacyInventoryContext(services)) {
            previewWorld = playerWorld.get(clientPlayer);
            if (previewWorld == null || minecraft.getField("field_71451_h").get(oldClient) != clientPlayer)
                throw new AssertionError("Inventory renderers lack player/world/viewer context");
            if (managerType.getField("field_78722_g").get(manager) != previewWorld || managerType.getField("field_78734_h").get(manager) != clientPlayer)
                throw new AssertionError("Nested entity renderers lack preview world/viewer context");
            Object actual = previewWorld.getClass().getMethod("func_147439_a", int.class,int.class,int.class).invoke(previewWorld,0,0,0);
            if (!"minecraft:air".equals(registry.getClass().getMethod("func_148750_c", Object.class).invoke(registry, actual)))
                throw new AssertionError("Inventory preview world changed block identity");
            Class<?> dispatcherType = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
            Object dispatcher = dispatcherType.getField("field_147556_a").get(null);
            if (dispatcherType.getField("field_147553_e").get(dispatcher) != minecraft.getField("field_71446_o").get(oldClient))
                throw new AssertionError("Tile inventory renderers have no texture manager");
        }
        if (playerWorld.get(clientPlayer) != previousWorld || minecraft.getField("field_71451_h").get(oldClient) != previousViewer)
            throw new AssertionError("Inventory capture leaked its temporary context");
        if (managerType.getField("field_78722_g").get(manager) != previousEntityWorld || managerType.getField("field_78734_h").get(manager) != previousEntityViewer)
            throw new AssertionError("Inventory capture leaked into entity rendering");
        report.put("genericInventoryRenderContextVerified", true);
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
            var screenType=loader.loadClass("net.minecraft.client.gui.GuiScreen");Object tooltipScreen=screenType.getConstructor().newInstance();
            screenType.getMethod("func_146280_a",mc,int.class,int.class).invoke(tooltipScreen,client,320,240);
            var hovering=screenType.getDeclaredMethod("func_146283_a",List.class,int.class,int.class);hovering.setAccessible(true);
            hovering.invoke(tooltipScreen,List.of(),12,34);
            if(capture.hasTooltip())throw new AssertionError("Empty old tooltip incorrectly replaced the host tooltip");
            hovering.invoke(tooltipScreen,List.of("Original item details"),12,34);
            if(!capture.hasTooltip()||capture.commands().stream().noneMatch(command->command instanceof LegacyGuiGraphics.Text text&&text.text().equals("Original item details")))
                throw new AssertionError("Original tooltip replacement was not captured");
            gl.getMethod("glPushMatrix").invoke(null);gl.getMethod("glTranslatef",float.class,float.class,float.class).invoke(null,10f,20f,0f);
            gl.getMethod("glBegin",int.class).invoke(null,7);
            for(double[] vertex:new double[][]{{0,0},{0,4},{4,4},{4,0}})gl.getMethod("glVertex3d",double.class,double.class,double.class).invoke(null,vertex[0],vertex[1],0d);
            gl.getMethod("glEnd").invoke(null);gl.getMethod("glPopMatrix").invoke(null);
            font.getClass().getMethod("func_78276_b",String.class,int.class,int.class,int.class).invoke(font,"after mesh",0,0,0xffffff);
            var drawing=capture.commands();int meshIndex=-1;
            for(int index=0;index<drawing.size();index++)if(drawing.get(index) instanceof LegacyGuiGraphics.Mesh)meshIndex=index;
            if(meshIndex<0||!(drawing.get(meshIndex+1) instanceof LegacyGuiGraphics.Text))throw new AssertionError("Mixed old GUI geometry/text order was lost");
            var vertex=((LegacyGuiGraphics.Mesh)drawing.get(meshIndex)).primitives().get(0).vertices().get(0);
            if(vertex.x()!=10||vertex.y()!=20)throw new AssertionError("Old GUI geometry lost its matrix transform");
            int beforeSplit=capture.commands().size();
            font.getClass().getMethod("func_78279_b",String.class,int.class,int.class,int.class,int.class).invoke(font,"A long original tooltip must wrap without native glyph rendering",3,7,65,0xffffff);
            if(capture.commands().size()-beforeSplit<2||capture.commands().subList(beforeSplit,capture.commands().size()).stream().anyMatch(command->!(command instanceof LegacyGuiGraphics.Text)))
                throw new AssertionError("Original wrapped font rendering escaped the GUI text boundary");
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
        Object useIdentity = new Object();
        Object useWorld = services.worlds().bind(useIdentity, new Host());
        try {
            var useBridge = new LegacyItemUse(services);
            var bow = services.items().catalog().stream().filter(s -> s.key().equals("minecraft:bow")).findFirst().orElseThrow();
            var usePlacer = new LegacyTiles.Placer(new java.util.UUID(7,7), "Probe", 0, 0, 0, 0, 0, true, false);
            var useResult = useBridge.rightClick(useWorld, usePlacer, bow);
            if (!useResult.used() || useResult.useDuration() != 72000)
                throw new AssertionError("Legacy onItemRightClick did not open a held use");
            if (useBridge.usingTick(useWorld, usePlacer, bow, 70000) != null) throw new AssertionError("A non-scanning item reported a completed legacy scan");
        } finally { services.worlds().unbind(useIdentity); }
        report.put("genericItemUseCallbackVerified", true);
        Object rayIdentity = new Object();
        Object rayWorld = services.worlds().bind(rayIdentity, new HostWorldAccess() {
            public String dimensionKey() { return "mfcl:ray"; } public int legacyDimensionId() { return 0; } public boolean clientSide() { return true; }
            public long gameTime() { return 0; } public long dayTime() { return 0; } public void setDayTime(long time) { }
            public Block block(int x, int y, int z) { return new Block("minecraft:air", 0); }
            public boolean setBlock(int x, int y, int z, Block block, int flags) { return false; }
            public Hit rayTrace(double sx, double sy, double sz, double ex, double ey, double ez) { return new Hit(3, 4, 5, 1, 3.5, 4.5, 5.5); }
        });
        try {
            Class<?> vecType = loader.loadClass("net.minecraft.util.Vec3");
            Object from = vecType.getMethod("func_72443_a", double.class, double.class, double.class).invoke(null, 0d, 0d, 0d);
            Object to = vecType.getMethod("func_72443_a", double.class, double.class, double.class).invoke(null, 10d, 10d, 10d);
            Object hit = rayWorld.getClass().getMethod("func_147447_a", vecType, vecType, boolean.class, boolean.class, boolean.class).invoke(rayWorld, from, to, false, false, false);
            if (hit == null || hit.getClass().getField("field_72311_b").getInt(hit) != 3 || hit.getClass().getField("field_72310_e").getInt(hit) != 1)
                throw new AssertionError("Legacy world ray trace did not surface the host hit");
        } finally { services.worlds().unbind(rayIdentity); }
        report.put("genericWorldRayTraceVerified", true);
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
