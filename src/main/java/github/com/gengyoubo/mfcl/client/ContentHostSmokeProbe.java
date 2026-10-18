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
    private long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
    private CompletableFuture<?> pending;
    private BlockPos position;
    private String blockKey, itemKey, worldName;
    private Map<String, Object> proof;
    private volatile boolean screenshotSaved;
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
                int checked = 0;
                for (var item : bridge.declarations().items()) for (var variant : item.variants()) {
                    var stack = bridge.stack(item.key(), variant.metadata(), 1, "");
                    var model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
                    if (model == mc.getModelManager().getMissingModel()) throw new IllegalStateException("Missing generated item model: " + item.key());
                    if (model.getParticleIcon().contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
                        throw new IllegalStateException("Generated model references an unstitched texture: " + item.key());
                    checked++;
                }
                proof.put("itemModelsChecked", checked); proof.put("missingIcons", bridge.pack().missingIcons());
                proof.put("stitchedTexturesVerified", true);
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
                mc.setScreen(null); mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); stage = 5;
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
                    return true;
                });
            } else if (stage == 7 && pending.isDone()) {
                pending.join(); proof.put("worldReload", true); proof.put("status", "PASS"); proof.put("world", worldName);
                Path file = mc.gameDirectory.toPath().resolve("logs/mfcl/content-1.7.10-smoke.json"); Files.createDirectories(file.getParent());
                Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
                LogUtils.getLogger().info("MFCL_CONTENT_WORLD_SMOKE_PASS: {}", proof);
                mc.level.disconnect(); mc.clearLevel(); stage = 99; mc.stop();
            }
        } catch (Throwable failure) { LogUtils.getLogger().error("MFCL_CONTENT_WORLD_SMOKE_FAIL stage=" + stage, failure); stage = 99; mc.stop(); }
    }
    private void verifyServer() throws Exception {
        var mc = Minecraft.getInstance(); var server = Objects.requireNonNull(mc.getSingleplayerServer());
        var level = server.overworld(); var player = server.getPlayerList().getPlayers().get(0);
        var selected = bridge.declarations().blocks().stream().filter(LegacyContent.Block::simple)
                .filter(block -> !bridge.itemForBlock(block.key(), 0).isEmpty()).findFirst().orElseThrow(() -> new IllegalStateException("No ordinary legacy block to verify"));
        blockKey = selected.key(); itemKey = bridge.snapshot(bridge.itemForBlock(blockKey, 0)).key();
        position = player.blockPosition().offset(4, 0, 4); level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        ItemStack placing = bridge.stack(itemKey, 3, 2, ""); player.setItemInHand(InteractionHand.MAIN_HAND, placing); player.getAbilities().instabuild = false;
        var hit = new BlockHitResult(Vec3.atCenterOf(position.below()).add(0, .5, 0), Direction.UP, position.below(), false);
        if (!placing.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()) throw new IllegalStateException("Actual BlockItem placement failed");
        if (placing.getCount() != 1 || level.getBlockState(position).getBlock() != bridge.blocks().get(blockKey)
                || level.getBlockState(position).getValue(ModernContentBridge.METADATA) != 3) throw new IllegalStateException("Placement lost identity/metadata/count");
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
            var state = bridge.modernBlock(new HostWorldAccess.Block(block.key(), 0));
            var generated = net.minecraft.world.level.block.Block.getDrops(state, level, position, null);
            if (generated.isEmpty() || generated.stream().anyMatch(drop -> bridge.snapshot(drop) == null)) throw new IllegalStateException("Unmapped original drops for " + block.key());
            ordinaryDrops.put(block.key(), generated.stream().map(drop -> bridge.snapshot(drop).key()).toList());
        }
        proof.put("ordinaryBlockDrops", ordinaryDrops);
    }
}
