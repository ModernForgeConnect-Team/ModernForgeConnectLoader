package github.com.gengyoubo.mfcl.client;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in acceptance on a fresh, disposable integrated-server world; never touches existing saves. */
final class ContentHostSmokeProbe {
    private final ModernContentBridge bridge;
    private int stage, frames;
    private long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
    private CompletableFuture<?> pending;
    private BlockPos position;
    private String blockKey, itemKey, worldName;
    private Map<String, Object> proof;
    private volatile boolean screenshotSaved;
    private int itemGallery;
    private static final String[] GALLERY_ITEMS = {"Thaumcraft:ItemEssence", "Thaumcraft:ItemWispEssence", "Thaumcraft:blockLootCrate"};
    private static final String[] GALLERY_IMAGES = {"essence", "wisp-essence", "loot-crates"};
    private record TileCheck(String key, BlockPos position, int metadata, byte[] nbt, Object serverIdentity) {}
    private final List<TileCheck> tileChecks = new ArrayList<>();
    private BlockPos glassPosition;
    private ContentHostSmokeProbe(ModernContentBridge bridge) { this.bridge = bridge; }
    static void install(ModernContentBridge bridge) { MinecraftForge.EVENT_BUS.addListener(new ContentHostSmokeProbe(bridge)::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Content smoke timed out at stage " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                if (!bridge.registered()) throw new IllegalStateException("Content missed modern registration windows");
                worldName = "mfcl-content-smoke-" + System.currentTimeMillis();
                mc.options.renderDistance().set(2); mc.options.simulationDistance().set(5);
                stage = 1;
                var settings = new LevelSettings("MFCL content verification", GameType.CREATIVE, false, net.minecraft.world.Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(worldName, settings,
                        new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false), access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                stage = 2; pending = mc.getSingleplayerServer().submit(() -> { try { verifyServer(); return true; } catch (Exception failure) { throw new IllegalStateException(failure); } });
            } else if (stage == 2 && pending.isDone()) {
                pending.join();
                for (var check : tileChecks) {
                    var clientTile = mc.level.getBlockEntity(check.position());
                    if (!(clientTile instanceof github.com.gengyoubo.mfcl.content.LegacyBlockEntity tile)) return;
                    Object old = tile.legacy();
                    if (old == check.serverIdentity()) throw new IllegalStateException("Client and server share one mutable old TileEntity");
                    if (!Arrays.equals(bridge.services().tiles().save(old), check.nbt())) return;
                }
                proof.put("clientTileStateSynchronized", true);
                verifyPlacedTileRendering(mc);
                verifySpecialBlockModels(mc);
                verifyItemContextModels();
                int checked = 0;
                for (var item : bridge.declarations().items()) for (var variant : item.variants()) {
                    var stack = bridge.stack(item.key(), variant.metadata(), 1, "");
                    var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                    if (model == mc.getModelManager().getMissingModel()) throw new IllegalStateException("Missing generated item model: " + item.key());
                    if (bridge.declarations().customItems().contains(item.key())) { checked++; continue; } // builtin/entity is rendered by the captured old geometry
                    if (model.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
                        throw new IllegalStateException("Generated model references an unstitched texture: " + item.key());
                    checked++;
                }
                proof.put("itemModelsChecked", checked); proof.put("missingIcons", bridge.pack().missingIcons());
                proof.put("stitchedTexturesVerified", true);
                verifyContainerMirrors(mc);
                verifyStackRendering(mc);
                proof.put("renderCaptureFailures", bridge.declarations().captureDiagnostics().stream()
                        .filter(line -> line.contains("failed") || line.contains("unresolved") || line.contains("unavailable")).toList());
                proof.put("capturedInventoryVariants", bridge.pack().geometry().size());
                if (bridge.pack().missingIcons().stream().anyMatch(icon -> icon.contains("textures/atlas/")))
                    throw new IllegalStateException("A legacy atlas was incorrectly copied as a standalone model texture");
                CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
                if (net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(net.minecraft.resources.ResourceLocation.tryParse("mfcl:legacy_1_7_10")))
                    throw new IllegalStateException("Loader-owned merged category must not replace original tabs");
                var tabProof = new ArrayList<Object>(); int displayed = 0;
                for (var original : bridge.declarations().tabs()) {
                    var target = bridge.creative().target(original); var expected = bridge.creative().stacks(original);
                    if (!original.builtin()) {
                        var actual = new ArrayList<>(target.getDisplayItems());
                        if (actual.size() != expected.size()) throw new IllegalStateException("Original tab membership changed: " + original.key());
                        for (int index = 0; index < expected.size(); index++) if (!ItemStack.isSameItemSameTags(actual.get(index), expected.get(index)))
                            throw new IllegalStateException("Original tab order/metadata/NBT changed: " + original.key() + " #" + index);
                        if (!target.getDisplayName().equals(net.minecraft.network.chat.Component.translatable(original.nameKey()))
                                || !ItemStack.isSameItemSameTags(target.getIconItem(), bridge.creative().icon(original)))
                            throw new IllegalStateException("Original tab title/icon changed: " + original.key());
                        if (target.showTitle() != original.showTitle() || target.canScroll() != original.scroll() || target.hasSearchBar() != original.searchBar())
                            throw new IllegalStateException("Original tab settings changed: " + original.key());
                    }
                    for (var stack : expected) if (!target.contains(stack) || !CreativeModeTabs.searchTab().contains(stack))
                        throw new IllegalStateException("Original tab/search lost entry: " + original.key());
                    tabProof.add(Map.of("originalKey", original.key(), "label", original.label(), "nameKey", original.nameKey(),
                            "displayName", target.getDisplayName().getString(), "icon", original.icon(), "entries", expected.size(), "builtin", original.builtin()));
                    displayed += expected.size();
                }
                var original = bridge.declarations().tabs().stream().filter(tab -> !tab.builtin() && !tab.contents().isEmpty()).findFirst().orElseThrow();
                var tab = bridge.creative().target(original);
                var screenType = net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class;
                var selected = screenType.getDeclaredField("selectedTab"); selected.setAccessible(true); selected.set(null, tab);
                var inventory = new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true);
                mc.setScreen(inventory);
                if (inventory.getMenu().items.size() != tab.getDisplayItems().size()) throw new IllegalStateException("Native creative inventory did not show original tab");
                proof.put("nativeCreativeItems", displayed); proof.put("nativeCreativeSearch", true); proof.put("originalCreativeTabs", tabProof);
                proof.put("originalTabOrderMetadataNbt", true); proof.put("originalTabNamesAndIcons", true);
                stage = 3; frames = 0;
            } else if (stage == 3 && ++frames > 15) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mfcl-content-creative.png", mc.getMainRenderTarget(), message -> screenshotSaved = true);
                stage = 4;
            } else if (stage == 4 && screenshotSaved) {
                if (scrollToBannerVariants(mc)) { screenshotSaved = false; frames = 0; stage = 30; return; }
                stage = 31;
            } else if (stage == 30 && ++frames > 15) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mfcl-content-banners.png", mc.getMainRenderTarget(), message -> screenshotSaved = true);
                stage = 31;
            } else if (stage == 31 && screenshotSaved) {
                while (itemGallery < GALLERY_ITEMS.length) {
                    int index = itemGallery++;
                    if (scrollToItem(mc, GALLERY_ITEMS[index])) { screenshotSaved = false; frames = 0; stage = 40; return; }
                }
                mc.setScreen(null);
                if (glassPosition != null) {
                    mc.player.setPos(glassPosition.getX()+.5, glassPosition.getY()-1, glassPosition.getZ()-5);
                    mc.player.setYRot(0); mc.player.yRotO = 0; mc.player.setXRot(0); mc.player.xRotO = 0;
                    mc.options.hideGui = true; screenshotSaved = false; frames = 0; stage = 50; return;
                }
                mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); stage = 5;
            } else if (stage == 40 && ++frames > 15) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mfcl-content-" + GALLERY_IMAGES[itemGallery - 1] + ".png", mc.getMainRenderTarget(), message -> screenshotSaved = true);
                stage = 31;
            } else if (stage == 50 && ++frames > 60) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mfcl-warded-glass-world.png", mc.getMainRenderTarget(), message -> screenshotSaved = true);
                stage = 51;
            } else if (stage == 51 && screenshotSaved) {
                mc.options.hideGui = false; screenshotSaved = false; frames = 0;
                mc.setScreen(new ItemContextSmokeScreen(bridge)); stage = 60;
            } else if (stage == 60 && ++frames > 20) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-item-context-inventory.png",mc.getMainRenderTarget(),message -> screenshotSaved = true); stage = 61;
            } else if (stage == 61 && screenshotSaved) {
                mc.setScreen(null); mc.player.getInventory().selected = 1;
                mc.player.getInventory().setItem(1,bridge.stack("Thaumcraft:ItemThaumometer",0,1,""));
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.options.hideGui = true; screenshotSaved = false; frames = 0; stage = 62;
            } else if (stage == 62 && ++frames > 40) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-item-context-first-person.png",mc.getMainRenderTarget(),message -> screenshotSaved = true); stage = 63;
            } else if (stage == 63 && screenshotSaved) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); screenshotSaved = false; frames = 0; stage = 64;
            } else if (stage == 64 && ++frames > 30) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-item-context-third-person-back.png",mc.getMainRenderTarget(),message -> screenshotSaved = true); stage = 65;
            } else if (stage == 65 && screenshotSaved) {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); screenshotSaved = false; frames = 0; stage = 66;
            } else if (stage == 66 && ++frames > 30) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"mfcl-item-context-third-person-front.png",mc.getMainRenderTarget(),message -> screenshotSaved = true); stage = 67;
            } else if (stage == 67 && screenshotSaved) {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.options.hideGui = false;
                mc.player.getInventory().selected = 0;
                mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); stage = 5;
            } else if (stage == 5 && mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen) {
                stage = 6; mc.createWorldOpenFlows().loadLevel(mc.screen, worldName);
            } else if (stage == 6 && mc.player != null && mc.level != null && mc.screen == null) {
                stage = 7;
                pending = mc.getSingleplayerServer().submit(() -> {
                    var server = mc.getSingleplayerServer(); var level = server.overworld();
                    var state = level.getBlockState(position);
                    if (state.getBlock() != bridge.blocks().get(blockKey) || state.getValue(ModernContentBridge.METADATA) != 5)
                        throw new IllegalStateException("Legacy block identity/metadata did not survive world reload: " + state);
                    var player = server.getPlayerList().getPlayers().get(0);
                    var stack = player.getInventory().getItem(0);
                    if (bridge.snapshot(stack) == null || ModernContentBridge.metadata(stack) != 7 || !"{mfcl_probe:1}".equals(bridge.snapshot(stack).tag()))
                        throw new IllegalStateException("Legacy stack metadata/NBT did not survive player save reload: " + stack);
                    for (var check : tileChecks) {
                        var stateAt = level.getBlockState(check.position());
                        if (stateAt.getBlock() != bridge.blocks().get(check.key()) || stateAt.getValue(ModernContentBridge.METADATA) != check.metadata())
                            throw new IllegalStateException("Placed tile block metadata did not persist: " + check.key());
                        if (!(level.getBlockEntity(check.position()) instanceof github.com.gengyoubo.mfcl.content.LegacyBlockEntity tile))
                            throw new IllegalStateException("Tile envelope did not persist: " + check.key());
                        try {
                            Object old = tile.legacy();
                            if (old == check.serverIdentity() || !Arrays.equals(bridge.services().tiles().save(old), check.nbt()))
                                throw new IllegalStateException("Original TileEntity NBT did not persist: " + check.key());
                        } catch (Exception error) { throw new IllegalStateException(error); }
                    }
                    return true;
                });
            } else if (stage == 7 && pending.isDone()) {
                pending.join();
                for (var check : tileChecks) if (mc.level.getBlockEntity(check.position()) == null) return;
                verifyPlacedTileRendering(mc);
                proof.put("worldReload", true); proof.put("status", "PASS"); proof.put("world", worldName);
                Path file = mc.gameDirectory.toPath().resolve("logs/mfcl/content-1.7.10-smoke.json"); Files.createDirectories(file.getParent());
                Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
                LogUtils.getLogger().info("MFCL_CONTENT_WORLD_SMOKE_PASS: {}", proof);
                mc.level.disconnect(); mc.clearLevel(); stage = 99; mc.stop();
            }
        } catch (Throwable failure) {
            LogUtils.getLogger().error("MFCL_CONTENT_WORLD_SMOKE_FAIL stage=" + stage, failure);
            try {
                Path file = mc.gameDirectory.toPath().resolve("logs/mfcl/content-1.7.10-smoke.json");
                Files.createDirectories(file.getParent());
                Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(
                        new java.util.LinkedHashMap<>(Map.of("status", "FAIL", "stage", stage, "failure", String.valueOf(failure)))));
            } catch (Throwable ignored) { }
            stage = 99; mc.stop();
        }
    }
    private void verifyItemContextModels() {
        String key = "Thaumcraft:ItemThaumometer@0";
        var contexts = new LinkedHashMap<String,Object>();
        for (String type : List.of("INVENTORY","EQUIPPED","EQUIPPED_FIRST_PERSON","ENTITY")) {
            var geometry = bridge.declarations().contextGeometry().get(type+"|"+key);
            if (geometry == null || geometry.isEmpty()) throw new IllegalStateException("Original context capture missing: " + type+"|"+key);
            contexts.put(type,Map.of("bounds",LegacyItemLayout.bounds(geometry,LegacyVertexStream.identity()),"vertices",geometry.stream().mapToInt(p -> p.vertices().size()).sum()));
            if (type.equals("INVENTORY")) {
                var bounds = LegacyItemLayout.bounds(geometry,LegacyVertexStream.identity());
                if (bounds.maxX()-bounds.minX() < .6 || bounds.maxY()-bounds.minY() < .6)
                    throw new IllegalStateException("Thaumometer inventory face is edge-on or too small: " + bounds);
                if (geometry.stream().noneMatch(p -> p.state().blend())) throw new IllegalStateException("Thaumometer lens lost its blend state");
            }
            if (type.equals("EQUIPPED_FIRST_PERSON") && geometry.stream().noneMatch(p -> p.texture().equals(LegacyItemRendering.PLAYER_SKIN)))
                throw new IllegalStateException("Original first-person hands were not captured");
        }
        var table = bridge.declarations().geometry().get("Thaumcraft:blockTable@14");
        if (table == null || table.stream().noneMatch(p -> p.texture().contains("scanner.png")))
            throw new IllegalStateException("Deconstruction table lost its embedded thaumometer");
        proof.put("originalItemRenderContexts",contexts);
    }
    private void verifyPlacedTileRendering(Minecraft mc) throws Exception {
        var tiles = bridge.blockRendering().tiles();
        var camera = mc.gameRenderer.getMainCamera(); var eye = camera.getPosition();
        tiles.view(bridge.services().worlds().bound(mc.level), eye.x,eye.y,eye.z,camera.getYRot(),camera.getXRot(),mc.player.tickCount);
        int checked = 0;
        for (var check : tileChecks) {
            var tile = (github.com.gengyoubo.mfcl.content.LegacyBlockEntity)mc.level.getBlockEntity(check.position());
            Object original = tile.legacy(); // Includes client NBT callbacks such as the bore's markDirty().
            if (!bridge.blockRendering().hasTileRenderer(check.key(),check.metadata())) continue;
            var pos = check.position();
            var geometry = tiles.capture(original,.5f,pos.getX()-eye.x,pos.getY()-eye.y,pos.getZ()-eye.z);
            for (var primitive : geometry) for (var vertex : primitive.vertices())
                if (!Double.isFinite(vertex.x()) || !Double.isFinite(vertex.y()) || !Double.isFinite(vertex.z())
                        || !Float.isFinite(vertex.u()) || !Float.isFinite(vertex.v()))
                    throw new IllegalStateException("Non-finite placed tile geometry: " + check.key());
            checked++;
        }
        proof.put("placedTileRenderersChecked", checked);
        proof.put("clientTileNbtLoadAndRender", true);
    }
    private void verifyContainerMirrors(Minecraft mc) throws Exception {
        var inventory = new net.minecraft.world.SimpleContainer(27);
        inventory.setItem(0, new ItemStack(Items.OAK_WOOD));
        var tagged = new ItemStack(Items.COMPASS); tagged.getOrCreateTag().putString("mfcl_probe", "host-only");
        inventory.setItem(1, tagged);
        var menu = net.minecraft.world.inventory.ChestMenu.threeRows(0, mc.player.getInventory(), inventory);
        var screen = new net.minecraft.client.gui.screens.inventory.ContainerScreen(menu, mc.player.getInventory(), net.minecraft.network.chat.Component.literal("MFCL container verification"));
        mc.setScreen(screen);
        ClientContainerBridge.active().commands(screen); // Requires successful initialization even with unmapped host items.
        ClassLoader loader = bridge.services().loader();
        Class<?> minecraft = loader.loadClass("net.minecraft.client.Minecraft");
        Object player = minecraft.getField("field_71439_g").get(minecraft.getMethod("func_71410_x").invoke(null));
        int tooltips = 0;
        for (var tab : bridge.declarations().tabs()) for (var stack : tab.contents()) {
            if (!stack.key().endsWith(":WandCasting")) continue;
            Object old = bridge.services().items().materialize(stack);
            var lines = (List<?>)old.getClass().getMethod("func_82840_a", loader.loadClass("net.minecraft.entity.player.EntityPlayer"), boolean.class).invoke(old, player, false);
            if (lines.isEmpty()) throw new IllegalStateException("Original wand tooltip is empty");
            tooltips++;
        }
        if (inventory.getItem(0).getItem() != Items.OAK_WOOD || !ItemStack.isSameItemSameTags(tagged, inventory.getItem(1)))
            throw new IllegalStateException("Mirroring changed host inventory items");
        proof.put("unmappedHostContainerVerified", true);
        proof.put("originalWandTooltipsChecked", tooltips);
        mc.setScreen(null);
    }
    private void verifySpecialBlockModels(Minecraft mc) throws Exception {
        String key = "Thaumcraft:blockCosmeticOpaque";
        if (!bridge.blocks().containsKey(key)) return;
        if (!bridge.blockRendering().supports(key, 2)) throw new IllegalStateException("Warded glass world rendering is unavailable");
        var rendering = bridge.blockRendering();
        var isolated = rendering.isolated(key, 2);
        if (isolated.size() != 1 || isolated.get(0).pass() != 1 || isolated.get(0).geometry().size() != 6)
            throw new IllegalStateException("Warded glass must have six transparent faces");
        int[][] offsets = {{-1,1},{0,1},{1,1},{-1,0},{1,0},{-1,-1},{0,-1},{1,-1}};
        var connected = new ArrayList<Object>();
        for (int side = 0; side < 6; side++) {
            final int face = side; var icons = new HashSet<String>();
            for (int mask = 0; mask < 256; mask++) {
                final int bits = mask;
                var layers = rendering.capture(key, 2, (x,y,z) -> {
                    if (x == 0 && y == 0 && z == 0) return new HostWorldAccess.Block(key, 2);
                    for (int i = 0; i < 8; i++) {
                        int u = offsets[i][0], v = offsets[i][1];
                        boolean matches = face < 2 ? x == u && y == 0 && z == v
                                : face < 4 ? x == u && y == v && z == 0 : x == 0 && y == v && z == u;
                        if (matches && (bits & 1 << i) != 0) return new HostWorldAccess.Block(key, 2);
                    }
                    return new HostWorldAccess.Block("minecraft:air", 0);
                });
                for (var layer : layers) for (var primitive : layer.geometry()) {
                    boolean onFace = primitive.vertices().stream().allMatch(v -> Math.abs((face < 2 ? v.y() : face < 4 ? v.z() : v.x()) - (face % 2)) < .00001);
                    if (onFace) icons.add(primitive.texture());
                }
            }
            if (icons.size() != 47) throw new IllegalStateException("Connected glass lost texture variants on face " + face + ": " + icons.size());
            for (String icon : icons) {
                var sprite = mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(net.minecraft.resources.ResourceLocation.parse(bridge.pack().worldTexture(icon)));
                if (sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
                    throw new IllegalStateException("Missing connected glass sprite: " + icon);
            }
            connected.add(Map.of("face", face, "masks", 256, "textures", icons.size()));
        }
        var adjacent = rendering.capture(key, 2, (x,y,z) -> new HostWorldAccess.Block(x == 0 && y == 0 && (z == 0 || z == -1) ? key : "minecraft:air", 2));
        if (adjacent.stream().mapToInt(l -> l.geometry().size()).sum() != 5) throw new IllegalStateException("Internal glass face was not culled");
        if (!rendering.capture(key, 2, (x,y,z) -> new HostWorldAccess.Block(key, 2)).isEmpty()) throw new IllegalStateException("Enclosed glass emitted hidden faces");
        var different = rendering.capture(key, 2, (x,y,z) -> new HostWorldAccess.Block(x == 0 && y == 0 && (z == 0 || z == -1) ? key : "minecraft:air", z == -1 ? 0 : 2));
        if (different.stream().mapToInt(l -> l.geometry().size()).sum() != 6) throw new IllegalStateException("Different glass metadata incorrectly culled");
        var state = mc.level.getBlockState(glassPosition);
        var model = mc.getBlockRenderer().getBlockModel(state);
        if (!(model instanceof LegacyBlockModel)) throw new IllegalStateException("World model wrapper missing");
        var modelData = model.getModelData(mc.level, glassPosition, state, net.minecraftforge.client.model.data.ModelData.EMPTY);
        var random = net.minecraft.util.RandomSource.create(0);
        if (!model.getRenderTypes(state, random, modelData).contains(net.minecraft.client.renderer.RenderType.translucent())
                || model.getQuads(state, null, random, modelData, net.minecraft.client.renderer.RenderType.translucent()).size() != 2)
            throw new IllegalStateException("Connected wall was not baked into two transparent exterior faces");
        var tooltip = bridge.itemForBlock(key, 2).getTooltipLines(mc.player, TooltipFlag.NORMAL);
        if (tooltip.stream().anyMatch(line -> line.getString().contains("暂不能放置"))) throw new IllegalStateException("Supported glass still has the placement warning");
        proof.put("wardedGlassConnectedTextures", connected);
        proof.put("wardedGlassTransparencyAndFaceCulling", true);
        proof.put("worldSpecialRenderSupport", rendering.supported());
    }
    private boolean scrollToBannerVariants(Minecraft mc) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen inventory)) return false;
        var items = inventory.getMenu().items;
        for (int i = 0; i < items.size(); i++) {
            var old = bridge.snapshot(items.get(i));
            if (old == null || !old.key().equals("Thaumcraft:blockWoodenDevice") || old.metadata() != 8) continue;
            var variants = bridge.declarations().tabs().stream().flatMap(tab -> tab.contents().stream())
                    .filter(s -> s.key().equals(old.key()) && s.metadata() == old.metadata()).toList();
            var distinct = new HashSet<List<LegacyVertexStream.Primitive>>();
            var boundsProof = new ArrayList<Object>();
            for (var variant : variants) {
                String key = LegacyContent.geometryKey(variant.key(), variant.metadata(), variant.tag());
                var geometry = bridge.pack().geometry().get(key);
                if (geometry == null || geometry.isEmpty()) throw new IllegalStateException("Missing banner variant geometry: " + key);
                if (geometry.stream().noneMatch(p -> p.texture().contains("textures/"))) throw new IllegalStateException("Banner lost its standalone model texture: " + key);
                distinct.add(geometry);
                var fit = LegacyItemLayout.fitGui(geometry);
                var bounds = fit.apply(LegacyItemLayout.guiBounds(geometry));
                if (bounds.minX() < -.50001 || bounds.maxX() > .50001 || bounds.minY() < -.50001 || bounds.maxY() > .50001)
                    throw new IllegalStateException("Banner overflows its inventory slot: " + key + " " + bounds);
                boundsProof.add(Map.of("tag", variant.tag(), "fit", fit, "bounds", bounds));
            }
            if (distinct.size() != variants.size()) throw new IllegalStateException("Banner variants share the same captured appearance");
            proof.put("bannerVariantsChecked", variants.size());
            proof.put("bannerDistinctModels", distinct.size());
            proof.put("bannerGuiBounds", boundsProof);
            int rows = Math.max(1, (items.size() + 8) / 9 - 5);
            inventory.getMenu().scrollTo(Math.min(1f, Math.max(0, i / 9 - 1) / (float)rows));
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 0, 0);
            return true;
        }
        return false;
    }
    private boolean scrollToItem(Minecraft mc, String key) {
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen inventory)) return false;
        var items = inventory.getMenu().items;
        for (int i = 0; i < items.size(); i++) {
            var old = bridge.snapshot(items.get(i));
            if (old == null || !old.key().equals(key)) continue;
            int rows = Math.max(1, (items.size() + 8) / 9 - 5);
            inventory.getMenu().scrollTo(Math.min(1f, i / 9 / (float)rows));
            return true;
        }
        return false;
    }
    private void verifyStackRendering(Minecraft mc) throws Exception {
        for (String key : List.of("Thaumcraft:ItemEssence", "Thaumcraft:ItemWispEssence")) {
            var declaration = bridge.declarations().items().stream().filter(item -> item.key().equals(key)).findFirst();
            if (declaration.isEmpty()) continue;
            var colors = new HashSet<Integer>(); int checked = 0;
            for (var tab : bridge.declarations().tabs()) for (var old : tab.contents()) {
                if (!old.key().equals(key) || old.tag().isEmpty()) continue;
                var modern = bridge.mapStack(old);
                int pass = key.endsWith(":ItemEssence") ? 1 : 0;
                int expected = bridge.services().items().itemColor(old, pass) & 0xFFFFFF;
                int actual = mc.getItemColors().getColor(modern, pass) & 0xFFFFFF;
                if (expected != actual) throw new IllegalStateException("NBT item tint changed: " + old);
                colors.add(actual); checked++;
                if (pass == 1) {
                    var model = mc.getItemRenderer().getModel(modern, mc.level, mc.player, 0);
                    var layers = model.getQuads(null, null, net.minecraft.util.RandomSource.create()).stream().map(net.minecraft.client.renderer.block.model.BakedQuad::getTintIndex).collect(java.util.stream.Collectors.toSet());
                    if (!layers.containsAll(Set.of(0, 1)) || (mc.getItemColors().getColor(modern, 0) & 0xFFFFFF) != 0xFFFFFF)
                        throw new IllegalStateException("Phial glass and liquid must use separate tint layers");
                }
            }
            if (colors.size() < 6) throw new IllegalStateException("Aspect item variants lost their colors: " + key);
            proof.put(key + "Rendering", Map.of("variants", checked, "distinctColors", colors.size()));
        }
        var crate = bridge.declarations().blocks().stream().filter(block -> block.key().equals("Thaumcraft:blockLootCrate")).findFirst();
        if (crate.isPresent()) {
            if (crate.get().blockModel()) throw new IllegalStateException("Loot crate ignored original flat inventory renderer");
            var icons = new HashSet<Object>();
            for (int meta = 0; meta < 3; meta++) {
                var stack = bridge.itemForBlock(crate.get().key(), meta);
                var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                if (model.isGui3d()) throw new IllegalStateException("Loot crate became an invented cube inventory model");
                icons.add(model.getParticleIcon().contents().name());
            }
            if (icons.size() != 3) throw new IllegalStateException("Loot crate variants share an inventory icon");
            proof.put("originalLootCrateIcons", icons.size());
        }
        Class<?> translations = bridge.services().loader().loadClass("net.minecraft.util.StatCollector");
        for (String key : List.of("item.ItemEssence.1.name", "tc.aspect.unknown")) {
            String modern = net.minecraft.client.resources.language.I18n.get(key);
            String original = (String)translations.getMethod("func_74838_a", String.class).invoke(null, key);
            if (!modern.equals(original)) throw new IllegalStateException("Legacy tooltip language differs from host: " + key + " = " + original);
        }
        proof.put("legacyTooltipLanguageSynchronized", true);
    }
    private void verifyServer() throws Exception {
        var mc = Minecraft.getInstance(); var server = Objects.requireNonNull(mc.getSingleplayerServer());
        var level = server.overworld(); var player = server.getPlayerList().getPlayers().get(0);
        var selected = bridge.declarations().blocks().stream().filter(LegacyContent.Block::simple)
                .filter(block -> !bridge.itemForBlock(block.key(), 0).isEmpty()).findFirst().orElseThrow(() -> new IllegalStateException("No ordinary legacy block to verify"));
        blockKey = selected.key(); itemKey = bridge.snapshot(bridge.itemForBlock(blockKey, 0)).key();
        position = player.blockPosition().offset(4, 0, 4); level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        ItemStack placing = bridge.stack(itemKey, 0, 2, ""); player.setItemInHand(InteractionHand.MAIN_HAND, placing); player.getAbilities().instabuild = false;
        var hit = new BlockHitResult(Vec3.atCenterOf(position.below()).add(0, .5, 0), Direction.UP, position.below(), false);
        if (!placing.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()) throw new IllegalStateException("Actual BlockItem placement failed");
        if (placing.getCount() != 1 || level.getBlockState(position).getBlock() != bridge.blocks().get(blockKey)
                || level.getBlockState(position).getValue(ModernContentBridge.METADATA) != 0) throw new IllegalStateException("Placement lost identity/metadata/count");
        var worlds = bridge.services().worlds();
        // Verify the production binding installed by LevelEvent.Load, without creating a test-only world adapter.
        Object old = worlds.bound(level);
        {
            Class<?> world = bridge.services().loader().loadClass("net.minecraft.world.World");
            Object oldBlock = world.getMethod("func_147439_a", int.class, int.class, int.class).invoke(old, position.getX(), position.getY(), position.getZ());
            Class<?> oldBlockType = bridge.services().loader().loadClass("net.minecraft.block.Block");
            world.getMethod("func_147465_d", int.class, int.class, int.class, oldBlockType, int.class, int.class)
                    .invoke(old, position.getX(), position.getY(), position.getZ(), oldBlock, 5, 3);
            if ((int) world.getMethod("func_72805_g", int.class, int.class, int.class).invoke(old, position.getX(), position.getY(), position.getZ()) != 5
                    || level.getBlockState(position).getValue(ModernContentBridge.METADATA) != 5) throw new IllegalStateException("Old World read-after-write is inconsistent");
            if (world.getMethod("func_147439_a", int.class, int.class, int.class).invoke(old, position.getX(), position.getY(), position.getZ()) != oldBlock)
                throw new IllegalStateException("Old Block identity changed across roundtrip");
        }
        var picked = level.getBlockState(position).getBlock().getCloneItemStack(level, position, level.getBlockState(position));
        if (bridge.snapshot(picked) == null || ModernContentBridge.metadata(picked) != 5) throw new IllegalStateException("Pick block lost legacy metadata");
        if (!level.destroyBlock(position, true, player)) throw new IllegalStateException("Actual block break failed");
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(position).inflate(2));
        if (drops.isEmpty() || drops.stream().anyMatch(drop -> bridge.snapshot(drop.getItem()) == null)) throw new IllegalStateException("Original block drops were not mapped");
        level.setBlockAndUpdate(position, bridge.modernBlock(new HostWorldAccess.Block(blockKey, 5)));
        var stack = bridge.stack(itemKey, 7, 11, "{mfcl_probe:1}"); var restored = ItemStack.of(stack.save(new CompoundTag()));
        if (!Objects.equals(bridge.snapshot(stack), bridge.snapshot(restored))) throw new IllegalStateException("Stack NBT roundtrip failed");
        Object originalStack = bridge.services().items().materialize(bridge.snapshot(restored));
        Object originalTag = originalStack.getClass().getField("field_77990_d").get(originalStack);
        if ((int) originalTag.getClass().getMethod("func_74762_e", String.class).invoke(originalTag, "mfcl_probe") != 1)
            throw new IllegalStateException("Opaque legacy NBT did not materialize into an actual old ItemStack");
        player.getInventory().setItem(0, stack); player.getAbilities().instabuild = true;
        server.saveEverything(false, true, true);
        proof = new LinkedHashMap<>(); proof.put("blocksRegistered", bridge.blocks().size()); proof.put("itemsRegistered", bridge.items().size());
        proof.put("block", blockKey); proof.put("item", itemKey); proof.put("placement", true); proof.put("breakDrops", drops.stream().map(drop -> bridge.snapshot(drop.getItem()).key()).toList());
        proof.put("oldWorldSynchronous", true); proof.put("oldObjectIdentity", true); proof.put("stackNbtRoundTrip", true); proof.put("generatedPack", bridge.pack().directory().toString());
        proof.put("actualOldStackNbt", true);
        proof.put("pickBlockMetadata", true);
        var ordinaryDrops = new LinkedHashMap<String, Object>();
        for (var block : bridge.declarations().blocks()) if (block.simple()) {
            if (bridge.declarations().drops(block.key(), 0, level.random.nextLong()).isEmpty()) continue;
            var state = bridge.modernBlock(new HostWorldAccess.Block(block.key(), 0));
            var generated = net.minecraft.world.level.block.Block.getDrops(state, level, position, null);
            if (generated.isEmpty() || generated.stream().anyMatch(drop -> bridge.snapshot(drop) == null)) throw new IllegalStateException("Unmapped original drops for " + block.key());
            ordinaryDrops.put(block.key(), generated.stream().map(drop -> bridge.snapshot(drop).key()).toList());
        }
        proof.put("ordinaryBlockDrops", ordinaryDrops);
        var placements = new ArrayList<Object>(); int index = 0;
        Class<?> oldInventory = bridge.services().loader().loadClass("net.minecraft.inventory.IInventory");
        Class<?> oldTileType = bridge.services().loader().loadClass("net.minecraft.tileentity.TileEntity");
        var placementFailures = new ArrayList<String>();
        var restrictions = new ArrayList<String>();
        for (var declaration : bridge.declarations().blocks()) for (int requestedMetadata : bridge.declarations().items().stream()
                .filter(item -> declaration.key().equals(item.blockKey())).flatMap(item -> item.variants().stream()).mapToInt(LegacyContent.Variant::metadata).distinct().toArray()) {
            if (!ModernContentBridge.canPlace(declaration, requestedMetadata)) continue;
            try {
            // Keep every fixture in the subscribed client chunks, including those verified after save/reload.
            BlockPos target = player.blockPosition().offset(-18 + (index % 12) * 3, 0, -18 + (index / 12) * 3); index++;
            if (Math.abs(target.getX()-player.getX()) < 2 && Math.abs(target.getZ()-player.getZ()) < 2) target = target.offset(1,0,1);
            if (target.equals(position)) throw new IllegalStateException("Placement fixtures overlap");
            var ground = declaration.key().equals("Thaumcraft:blockCustomPlant") && requestedMetadata <= 3
                    ? (requestedMetadata == 3 ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState() : Blocks.STONE.defaultBlockState();
            if (declaration.key().equals("Thaumcraft:blockMetalDevice") && requestedMetadata == 12)
                ground = bridge.modernBlock(new HostWorldAccess.Block(declaration.key(), 10));
            level.setBlockAndUpdate(target.below(), ground); level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            var item = bridge.itemForBlock(declaration.key(), requestedMetadata); if (item.isEmpty()) throw new IllegalStateException("No original block item for " + declaration.key());
            item.setCount(2); player.setItemInHand(InteractionHand.MAIN_HAND, item); player.setYRot(90); player.getAbilities().instabuild = false;
            var placeHit = new BlockHitResult(Vec3.atCenterOf(target.below()).add(0, .5, 0), Direction.UP, target.below(), false);
            var result = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, placeHit));
            if (declaration.key().equals("Thaumcraft:blockManaPod") || declaration.key().equals("Thaumcraft:blockTaintFibres")) {
                if (result.consumesAction() || !level.getBlockState(target).isAir() || item.getCount() != 2)
                    throw new IllegalStateException("Original biome placement restriction was bypassed: " + declaration.key());
                restrictions.add(declaration.key() + "@" + requestedMetadata + ": original biome/support restriction");
                continue;
            }
            if (!result.consumesAction()
                    || level.getBlockState(target).getBlock() != bridge.blocks().get(declaration.key()) || item.getCount() != 1)
                throw new IllegalStateException("Original tile block could not be placed: " + declaration.key());
            int metadata = level.getBlockState(target).getValue(ModernContentBridge.METADATA);
            String tileClass = "";
            if (declaration.tileEntity()) {
                var envelope = (github.com.gengyoubo.mfcl.content.LegacyBlockEntity) level.getBlockEntity(target);
                Object original = envelope.legacyOrNull();
                if (original != null) {
                    tileClass = original.getClass().getName();
                    if ((int) oldTileType.getMethod("func_145832_p").invoke(original) != metadata)
                        throw new IllegalStateException("Original tile cached metadata disagrees with host block state: " + declaration.key());
                    try {
                        Object owner = original.getClass().getMethod("getPlacerName").invoke(original);
                        if (!player.getName().getString().equals(owner)) throw new IllegalStateException("Original placement callback lost the player name: " + declaration.key());
                    } catch (NoSuchMethodException ignored) { }
                    if (declaration.key().equals("Thaumcraft:blockCosmeticOpaque") && requestedMetadata == 2
                            && !player.getName().getString().equals(original.getClass().getField("owner").get(original)))
                        throw new IllegalStateException("Warded glass lost its original owner");
                    Object throughWorld = old.getClass().getMethod("func_147438_o", int.class, int.class, int.class).invoke(old, target.getX(), target.getY(), target.getZ());
                    if (original != throughWorld || envelope.legacy() != original) throw new IllegalStateException("Original tile identity was not stable");
                    // Grates and active machines can eject/process writes immediately; placement verification
                    // only injects inventory contents into the passive storage fixture used by this probe.
                    if (declaration.key().equals("Thaumcraft:blockMagicBox") && oldInventory.isInstance(original) && (int) oldInventory.getMethod("func_70302_i_").invoke(original) > 0) {
                        Object content = bridge.services().items().materialize(new LegacyClientData.Stack(itemKey, 2, 3, "", "{mfcl_inventory:1}"));
                        oldInventory.getMethod("func_70299_a", int.class, bridge.services().loader().loadClass("net.minecraft.item.ItemStack")).invoke(original, 0, content);
                        var dropStacks = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(target), level, target, envelope);
                        if (dropStacks.stream().noneMatch(drop -> bridge.snapshot(drop) != null && bridge.snapshot(drop).tag().contains("mfcl_inventory")))
                            throw new IllegalStateException("Original tile inventory NBT missing from block drops: " + declaration.key());
                    }
                    oldTileType.getMethod("func_70296_d").invoke(original);
                    tileChecks.add(new TileCheck(declaration.key(), target, metadata, bridge.services().tiles().save(original), original));
                }
            }
            placements.add(Map.of("key", declaration.key(), "metadata", metadata, "oldTileClass", tileClass));
            } catch (Exception failure) {
                Throwable cause = failure; while (cause.getCause() != null) cause = cause.getCause();
                placementFailures.add(declaration.key() + "@" + requestedMetadata + ": " + cause);
                LogUtils.getLogger().error("MFCL placement fixture failed: {}@{}",declaration.key(),requestedMetadata,failure);
            }
        }
        if (!placementFailures.isEmpty()) throw new IllegalStateException("Placement failures: " + placementFailures);
        if (bridge.blockRendering().supports("Thaumcraft:blockCosmeticOpaque", 2)) {
            glassPosition = player.blockPosition().offset(-5, 1, 24);
            var glass = bridge.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockCosmeticOpaque", 2));
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) level.setBlockAndUpdate(glassPosition.offset(x,y,0), glass);
            for (int x = -2; x <= 2; x++) for (int y = -1; y <= 1; y++)
                level.setBlockAndUpdate(glassPosition.offset(x,y,2), ((x+y)%2 == 0 ? Blocks.ORANGE_CONCRETE : Blocks.BLUE_CONCRETE).defaultBlockState());
            if (!net.minecraft.world.level.block.Block.isShapeFullBlock(glass.getCollisionShape(level, glassPosition)))
                throw new IllegalStateException("Warded glass collision is not a full cube");
            proof.put("wardedGlassCollision", true);
        }
        player.getInventory().setItem(0, stack); player.getAbilities().instabuild = true;
        server.saveEverything(false, true, true);
        proof.put("allSupportedBlockPlacements", placements); proof.put("originalTilesPersisted", tileChecks.size());
        proof.put("originalPlacementRestrictions", restrictions);
        proof.put("tileCachedMetadataAndPlacer", true);
    }
}
