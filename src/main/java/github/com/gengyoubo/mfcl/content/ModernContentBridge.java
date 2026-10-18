package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Registration barrier: frozen old declarations are published only inside modern RegisterEvents. */
public final class ModernContentBridge {
    public static final IntegerProperty METADATA = IntegerProperty.create("legacy_meta", 0, 15);
    public static final String STACK_TAG = "mfcl_legacy";
    private static ModernContentBridge active;
    private final LegacyServices services;
    private final LegacyContent declarations;
    private final LegacyBlockRendering blockRendering;
    private final LegacyBlockShapes blockShapes;
    private final LegacyItemUse itemUse;
    private final Set<String> legacyBlockKeys;
    private final ThreadLocal<PlacementTransaction> placement = new ThreadLocal<>();
    private final Map<String, Block> blocks = new LinkedHashMap<>();
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<Item, LegacyContent.Item> reverseItems = new IdentityHashMap<>();
    private final LegacyContentPack pack;
    private final CreativeTabBridge creative;
    private boolean blocksRegistered, itemsRegistered;
    private Map<Item,LegacyClientData.Stack> vanillaStacks;
    private BlockEntityType<LegacyBlockEntity> tileType;

    private ModernContentBridge(LegacyServices services) throws Exception {
        this.services = services;
        try (var context = new LegacyInventoryContext(services)) {
            declarations = new LegacyContent(services.items());
            blockShapes = new LegacyBlockShapes(services);
            blockRendering = new LegacyBlockRendering(services, declarations);
            itemUse = new LegacyItemUse(services);
            var registry = services.loader().loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            legacyBlockKeys = Set.copyOf((Set<String>)registry.getClass().getMethod("func_148742_b").invoke(registry));
        }
        validateIds(declarations.blocks().stream().map(LegacyContent.Block::key).toList());
        validateIds(declarations.items().stream().map(LegacyContent.Item::key).toList());
        pack = new LegacyContentPack(services.gameDirectory(), declarations, blockRendering);
        creative = new CreativeTabBridge(this);
    }
    public static ModernContentBridge install(LegacyServices services, IEventBus bus) {
        try {
            var bridge = new ModernContentBridge(services); active = bridge;
            ScanSyncChannel.register();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(bridge::playerLoggedIn);
            bus.addListener(bridge::register); bus.addListener(bridge.pack::findPacks);
            bus.addListener(bridge.creative::buildVanillaContents);
            LevelWorldBridge.install(bridge);
            LogUtils.getLogger().info("MFCL content barrier: {} blocks, {} items declared before modern registration", bridge.declarations.blocks().size(), bridge.declarations.items().size());
            LogUtils.getLogger().info("MFCL legacy static models: {} items custom-rendered", bridge.declarations.customItems().size());
            for (String diagnostic : bridge.declarations.captureDiagnostics()) LogUtils.getLogger().info("MFCL capture: {}", diagnostic);
            LogUtils.getLogger().info("MFCL world ISBRH support: {}", bridge.blockRendering.supported());
            for (String diagnostic : bridge.blockRendering.diagnostics()) LogUtils.getLogger().info("MFCL world capture: {}", diagnostic);
            return bridge;
        } catch (Exception failure) { throw new IllegalStateException("Cannot freeze legacy content declarations", failure); }
    }
    public static ModernContentBridge active() { return active; }
    public LegacyContent declarations() { return declarations; }
    public LegacyBlockRendering blockRendering() { return blockRendering; }
    public LegacyBlockShapes blockShapes() { return blockShapes; }
    public LegacyServices services() { return services; }
    public LegacyContentPack pack() { return pack; }
    public CreativeTabBridge creative() { return creative; }
    public Map<String, Block> blocks() { return Collections.unmodifiableMap(blocks); }
    public Map<String, Item> items() { return Collections.unmodifiableMap(items); }
    public boolean registered() { return blocksRegistered && itemsRegistered; }
    public BlockEntityType<LegacyBlockEntity> tileType() { return Objects.requireNonNull(tileType, "Legacy block entity registry window"); }
    public static boolean canPlace(LegacyContent.Block entry) { return canPlace(entry, 0); }
    public static boolean canPlace(LegacyContent.Block entry, int metadata) {
        return entry.renderType() == 0 || active != null && active.blockRendering.supports(entry.key(), metadata);
    }
    public static final String LEGACY_ENTITY_DATA = "mfcl_legacy_entity";
    private void playerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        byte[] data = player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA);
        if (data.length == 0) return;
        try {
            itemUse.loadPersisted(services.worlds().bound(player.serverLevel()), placer(player), data);
        } catch (Exception failure) {
            LogUtils.getLogger().error("MFCL legacy player data restore failed for {}", player.getName().getString(), failure);
        }
    }
    /** Applies a client-reported legacy scan on the authoritative server thread and persists the resulting research. */
    public void applyServerScan(net.minecraft.server.level.ServerPlayer player, ScanSyncChannel.ScanMessage message) {
        try {
            var world = services.worlds().bound(player.serverLevel());
            byte[] prior = player.getPersistentData().contains(LEGACY_ENTITY_DATA) ? player.getPersistentData().getByteArray(LEGACY_ENTITY_DATA) : null;
            byte[] updated = itemUse.applyScan(world, placer(player), message.type(), message.id(), message.meta(), message.phenomena(), message.prefix(), prior);
            player.getPersistentData().putByteArray(LEGACY_ENTITY_DATA, updated);
            LogUtils.getLogger().info("MFCL legacy server scan applied for {}: type={}, id={}, meta={}", player.getName().getString(), message.type(), message.id(), message.meta());
        } catch (Exception failure) {
            LogUtils.getLogger().error("MFCL legacy server scan application failed for {}", player.getName().getString(), failure);
        }
    }
    public static ResourceLocation id(String key) {
        var parsed = ResourceLocation.tryParse(key);
        return parsed != null ? parsed : ResourceLocation.fromNamespaceAndPath("mfcl", "legacy/" + HexFormat.of().formatHex(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static void validateIds(List<String> keys) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (String key : keys) if (!ids.add(id(key))) throw new IllegalArgumentException("Legacy registry ID collision: " + key);
    }
    private void register(RegisterEvent event) {
        event.register(Registries.BLOCK, registry -> {
            if (blocksRegistered) throw new IllegalStateException("Legacy block registration repeated");
            for (var entry : declarations.blocks()) {
                if (BuiltInRegistries.BLOCK.containsKey(id(entry.key()))) throw new IllegalStateException("Host already owns block ID " + entry.key());
                var block = entry.tileEntity() ? new MirroredTileBlock(entry) : new MirroredBlock(entry);
                registry.register(id(entry.key()), block); blocks.put(entry.key(), block);
            }
            blocksRegistered = true;
        });
        event.register(Registries.ITEM, registry -> {
            if (!blocksRegistered || itemsRegistered) throw new IllegalStateException("Legacy registry window ordering violated");
            for (var entry : declarations.items()) {
                if (BuiltInRegistries.ITEM.containsKey(id(entry.key()))) throw new IllegalStateException("Host already owns item ID " + entry.key());
                var properties = new Item.Properties().stacksTo(entry.maxDamage() > 0 ? 1 : entry.stackLimit());
                if (entry.maxDamage() > 0) properties.durability(entry.maxDamage());
                Item item = entry.blockKey() != null && blocks.containsKey(entry.blockKey())
                        ? new MirroredBlockItem((MirroredBlock) blocks.get(entry.blockKey()), properties, entry)
                        : new MirroredItem(properties, entry);
                registry.register(id(entry.key()), item); items.put(entry.key(), item); reverseItems.put(item, entry);
            }
            itemsRegistered = true;
            LogUtils.getLogger().info("MFCL_CONTENT_REGISTRATION_PASS: blocks={} items={}", blocks.size(), items.size());
        });
        event.register(Registries.CREATIVE_MODE_TAB, creative::register);
        event.register(Registries.BLOCK_ENTITY_TYPE, registry -> {
            var supported = declarations.blocks().stream().filter(LegacyContent.Block::tileEntity).map(entry -> blocks.get(entry.key())).toArray(Block[]::new);
            if (supported.length != 0) {
                tileType = BlockEntityType.Builder.of((pos, state) -> new LegacyBlockEntity(this, pos, state), supported).build(null);
                registry.register(ResourceLocation.tryParse("mfcl:legacy_tile_1_7_10"), tileType);
            }
        });
    }
    public ItemStack mapStack(LegacyClientData.Stack old) {
        return items.containsKey(old.key()) ? stack(old.key(), old.metadata(), old.count(), old.tag()) : HostStackMapping.renderStack(old);
    }
    public LegacyClientData.Stack gameplayStack(ItemStack stack) throws Exception {
        if (stack.isEmpty()) return null;
        if (reverseItems.containsKey(stack.getItem())) return snapshot(stack);
        if (stack.hasTag() && stack.getTag().getAllKeys().stream().anyMatch(key -> !key.equals("Damage")))
            throw new UnsupportedOperationException("Unmapped host inventory NBT: " + stack);
        if (vanillaStacks == null) {
            var mapped = new IdentityHashMap<Item,LegacyClientData.Stack>();
            for (var original : services.items().catalog()) if (original.key().startsWith("minecraft:") && original.tag().isEmpty()) {
                var modern = HostStackMapping.renderStack(original);
                if (!modern.isEmpty()) mapped.putIfAbsent(modern.getItem(),original);
            }
            vanillaStacks = mapped;
        }
        var original = vanillaStacks.get(stack.getItem());
        if (original == null) throw new UnsupportedOperationException("No original item representation: " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return new LegacyClientData.Stack(original.key(),stack.isDamageableItem() ? stack.getDamageValue() : original.metadata(),stack.getCount(),original.name(),"");
    }
    public ItemStack gameplayStack(LegacyClientData.Stack stack) {
        if (stack == null || stack.count() <= 0) return ItemStack.EMPTY;
        if (!items.containsKey(stack.key()) && !stack.tag().isEmpty()) throw new UnsupportedOperationException("Original inventory NBT needs a host mapping: " + stack.key());
        var mapped = mapStack(stack);
        if (mapped.isEmpty()) throw new UnsupportedOperationException("Original inventory item needs a host mapping: " + stack.key());
        return mapped;
    }
    public ItemStack stack(String key, int metadata, int count, String tag) {
        Item item = items.get(key); if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, count);
        if (item.canBeDepleted()) stack.setDamageValue(metadata);
        if ((!item.canBeDepleted() && metadata != 0) || (tag != null && !tag.isEmpty())) {
            var legacy = stack.getOrCreateTagElement(STACK_TAG);
            if (!item.canBeDepleted()) legacy.putInt("metadata", metadata);
            if (tag != null && !tag.isEmpty()) legacy.putString("snbt", tag);
        }
        return stack;
    }
    public static int metadata(ItemStack stack) {
        if (stack.isDamageableItem()) return stack.getDamageValue();
        CompoundTag tag = stack.getTagElement(STACK_TAG); return tag == null ? 0 : tag.getInt("metadata");
    }
    public LegacyClientData.Stack snapshot(ItemStack stack) {
        var item = reverseItems.get(stack.getItem()); if (item == null) return null;
        if (stack.hasTag()) for (String key : stack.getTag().getAllKeys())
            if (!key.equals(STACK_TAG) && !key.equals("Damage")) throw new UnsupportedOperationException("Host item NBT needs an explicit legacy mapping: " + key);
        CompoundTag tag = stack.getTagElement(STACK_TAG);
        return new LegacyClientData.Stack(item.key(), metadata(stack), stack.getCount(), stack.getHoverName().getString(), tag == null ? "" : tag.getString("snbt"));
    }
    public HostWorldAccess.Block legacyBlock(BlockState state) {
        if (state.getBlock() instanceof MirroredBlock mirrored) return new HostWorldAccess.Block(mirrored.entry.key(), state.getValue(METADATA));
        if (!state.equals(state.getBlock().defaultBlockState())) throw new UnsupportedOperationException("Block state requires a legacy metadata mapping: " + state);
        String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return new HostWorldAccess.Block(key.equals("minecraft:grass_block") ? "minecraft:grass" : key, 0);
    }
    public BlockState modernBlock(HostWorldAccess.Block old) {
        Block block = blocks.get(old.legacyKey());
        if (block != null) {
            if (!canPlace(((MirroredBlock) block).entry, old.metadata())) throw new UnsupportedOperationException("Legacy special rendering/placement bridge is pending: " + old.legacyKey() + "@" + old.metadata());
            if (old.metadata() < 0 || old.metadata() > 15) throw new IllegalArgumentException("Legacy block metadata outside 0..15");
            return block.defaultBlockState().setValue(METADATA, old.metadata());
        }
        if (old.metadata() != 0) throw new UnsupportedOperationException("No vanilla block metadata mapping for " + old);
        String key = old.legacyKey().equals("minecraft:grass") ? "minecraft:grass_block" : old.legacyKey();
        var id = ResourceLocation.tryParse(key);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) throw new UnsupportedOperationException("No host block representation: " + old);
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }
    private Component name(LegacyContent.Item entry, ItemStack stack) {
        int metadata = metadata(stack);
        var variant = entry.variants().stream().filter(v -> v.metadata() == metadata).findFirst().orElse(entry.variants().get(0));
        return Component.translatable(variant.nameKey());
    }
    private void tooltip(LegacyContent.Item entry, ItemStack stack, List<Component> lines) {
        lines.add(Component.literal("1.7.10 · " + entry.key() + " @ " + metadata(stack)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        if (entry.blockKey() != null && blocks.get(entry.blockKey()) instanceof MirroredBlock block) {
            if (!canPlace(block.entry, metadata(stack))) lines.add(Component.translatable("mfcl.content.special_block_pending").withStyle(net.minecraft.ChatFormatting.YELLOW));
            else if (block.entry.tileEntity() && (block.entry.renderType() == 0 || blockRendering.requiresLogic(block.entry.key(),metadata(stack))))
                lines.add(Component.translatable("mfcl.content.machine_logic_pending").withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }
    private final class MirroredItem extends Item {
        private final LegacyContent.Item entry;
        MirroredItem(Properties properties, LegacyContent.Item entry) { super(properties); this.entry = entry; }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
        @Override public InteractionResult useOn(UseOnContext context) { return useLegacyItem(context); }
        @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            try {
                var result = itemUse.rightClick(services.worlds().bound(level), placer(player), snapshot(stack));
                if (!result.used()) return net.minecraft.world.InteractionResultHolder.pass(stack);
                if (result.useDuration() > 0) player.startUsingItem(hand);
                if (result.prompt() != null && level.isClientSide) player.displayClientMessage(Component.literal(result.prompt()), true);
                return net.minecraft.world.InteractionResultHolder.consume(stack);
            } catch (Exception failure) {
                LogUtils.getLogger().error("MFCL legacy item use failed for {}", entry.key(), failure);
                return net.minecraft.world.InteractionResultHolder.fail(stack);
            }
        }
        @Override public int getUseDuration(ItemStack stack) {
            try { return itemUse.useDuration(snapshot(stack)); }
            catch (Exception failure) { return 0; }
        }
        @Override public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
            if (!(living instanceof net.minecraft.world.entity.player.Player player)) return;
            try {
                var scan = itemUse.usingTick(services.worlds().bound(level), placer(player), snapshot(stack), remaining);
                if (scan != null && level.isClientSide) ScanSyncChannel.sendToServer(scan, "@");
            } catch (Exception failure) { LogUtils.getLogger().debug("MFCL legacy using tick failed for {}", entry.key(), failure); }
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
            if (!(living instanceof net.minecraft.world.entity.player.Player player)) return;
            try { itemUse.release(services.worlds().bound(level), placer(player), snapshot(stack), timeLeft); }
            catch (Exception failure) { LogUtils.getLogger().debug("MFCL legacy release failed for {}", entry.key(), failure); }
        }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(customRenderer(() -> entry == null ? null : entry.key()));
        }
    }
    private static LegacyTiles.Placer placer(net.minecraft.world.entity.player.Player player) {
        return new LegacyTiles.Placer(player.getUUID(), player.getName().getString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
    /** Item's constructor invokes this before the subclass sets its entry, so the key is resolved lazily. */
    private net.minecraftforge.client.extensions.common.IClientItemExtensions customRenderer(java.util.function.Supplier<String> key) {
        return new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            private github.com.gengyoubo.mfcl.client.LegacyItemRenderer renderer;
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                String value = key.get();
                if (value == null || !declarations.customItems().contains(value)) return null;
                if (renderer == null) renderer = new github.com.gengyoubo.mfcl.client.LegacyItemRenderer(ModernContentBridge.this, value);
                return renderer;
            }
        };
    }
    private final class MirroredBlockItem extends BlockItem {
        private final LegacyContent.Item entry;
        MirroredBlockItem(MirroredBlock block, Properties properties, LegacyContent.Item entry) { super(block, properties); this.entry = entry; }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(customRenderer(() -> entry == null ? null : entry.key()));
        }
        @Override public InteractionResult useOn(UseOnContext context) {
            if (!ModernContentBridge.canPlace(((MirroredBlock) getBlock()).entry, metadata(context.getItemInHand()))) {
                if (context.getPlayer() != null && context.getLevel().isClientSide) context.getPlayer().displayClientMessage(Component.translatable("mfcl.content.special_block_pending"), true);
                return InteractionResult.FAIL;
            }
            if (!context.getItemInHand().isDamageableItem() && (metadata(context.getItemInHand()) < 0 || metadata(context.getItemInHand()) > 15)) return InteractionResult.FAIL;
            return useLegacyItem(context);
        }
        @Override public InteractionResult place(BlockPlaceContext context) {
            if (!ModernContentBridge.canPlace(((MirroredBlock) getBlock()).entry, metadata(context.getItemInHand()))) return InteractionResult.FAIL;
            BlockPos position = context.getClickedPos(); var before = context.getLevel().getBlockState(position);
            try { return super.place(context); }
            catch (Exception failure) {
                context.getLevel().setBlock(position, before, 3);
                LogUtils.getLogger().error("MFCL legacy block placement failed for {} at {}", entry.key(), position, failure);
                if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable("mfcl.content.placement_failed"), true);
                return InteractionResult.FAIL;
            }
        }
        @Override protected BlockState getPlacementState(BlockPlaceContext context) {
            BlockState state = super.getPlacementState(context);
            if (state == null) return null;
            int meta = metadata(context.getItemInHand()) & 15;
            if (!context.getLevel().isClientSide) try {
                var position = context.getClickedPos(); var hit = context.getClickLocation().subtract(position.getX(), position.getY(), position.getZ());
                meta = blockShapes.placement(entry.blockKey(), services.worlds().bound(context.getLevel()), position.getX(),position.getY(),position.getZ(),
                        context.getClickedFace().get3DDataValue(), (float)hit.x, (float)hit.y, (float)hit.z, meta);
            } catch (Exception error) { throw new IllegalStateException("Cannot evaluate original placement metadata for " + entry.key(), error); }
            return state.setValue(METADATA, meta & 15);
        }
    }
    private class MirroredBlock extends Block {
        private final LegacyContent.Block entry;
        MirroredBlock(LegacyContent.Block entry) {
            super(properties(entry)); this.entry = entry; registerDefaultState(stateDefinition.any().setValue(METADATA, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(METADATA); }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            try {
                var pendingInventory = new ArrayList<ItemStack>();
                int selected = player.getInventory().selected;
                var outcome = logicTransaction(level,pos,() -> {
                    var inventory = new ArrayList<LegacyClientData.Stack>();
                    for (int slot=0;slot<40;slot++) inventory.add(gameplayStack(player.getInventory().getItem(slot)));
                    if (hand == net.minecraft.world.InteractionHand.OFF_HAND) inventory.set(selected,gameplayStack(player.getOffhandItem()));
                    var point = hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
                    var actor = new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.getAbilities().instabuild,player.isShiftKeyDown());
                    var result = services.logic().activate(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ(),actor,inventory,selected,hit.getDirection().get3DDataValue(),(float)point.x,(float)point.y,(float)point.z);
                    pendingInventory.addAll(result.inventory().stream().map(ModernContentBridge.this::gameplayStack).toList());
                    return result.handled() ? InteractionResult.CONSUME : InteractionResult.PASS;
                },true);
                if (!pendingInventory.isEmpty()) {
                    for (int slot=0;slot<40;slot++) {
                        var replacement = pendingInventory.get(slot);
                        var current = slot == selected && hand == net.minecraft.world.InteractionHand.OFF_HAND ? player.getOffhandItem() : player.getInventory().getItem(slot);
                        if (ItemStack.isSameItemSameTags(current,replacement)) current.setCount(replacement.getCount());
                        else {
                            current.setCount(0); // Invalidate a native useOn caller's reference to the consumed item.
                            if (slot == selected && hand == net.minecraft.world.InteractionHand.OFF_HAND) player.setItemInHand(hand,replacement);
                            else player.getInventory().setItem(slot,replacement);
                        }
                    }
                    player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
                }
                return outcome;
            } catch (Exception failure) {
                LogUtils.getLogger().error("MFCL original block interaction failed for {} at {}",entry.key(),pos,failure);
                player.displayClientMessage(Component.literal("此设备的部分交互尚未支持。"),true); return InteractionResult.FAIL;
            }
        }
        @Override public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int event, int data) {
            try { return services.logic().event(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ(),event,data); }
            catch (Exception failure) { LogUtils.getLogger().error("MFCL original block event failed for {} at {}",entry.key(),pos,failure); return false; }
        }
        @Override public void tick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
            try { logicTransaction(level,pos,() -> { services.logic().scheduled(entry.key(),services.worlds().bound(level),pos.getX(),pos.getY(),pos.getZ()); return null; },true); }
            catch (Exception failure) { LogUtils.getLogger().error("MFCL original scheduled tick failed for {} at {}",entry.key(),pos,failure); }
        }
        @Override public RenderShape getRenderShape(BlockState state) {
            return entry.renderType() < 0 && blockRendering.hasTileRenderer(entry.key(), state.getValue(METADATA)) ? RenderShape.INVISIBLE : RenderShape.MODEL;
        }
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            return shape(state, level, pos, false);
        }
        @Override public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            return shape(state, level, pos, true);
        }
        private net.minecraft.world.phys.shapes.VoxelShape shape(BlockState state, BlockGetter level, BlockPos pos, boolean collision) {
            if (!canPlace(entry, state.getValue(METADATA))) return net.minecraft.world.phys.shapes.Shapes.block();
            try {
                var original = blockShapes.shapes(entry.key(), renderView(level, pos, state));
                var boxes = collision ? original.collision() : original.outline();
                var result = net.minecraft.world.phys.shapes.Shapes.empty();
                for (var box : boxes) if (box.maxX() > box.minX() && box.maxY() > box.minY() && box.maxZ() > box.minZ())
                    result = net.minecraft.world.phys.shapes.Shapes.or(result, net.minecraft.world.phys.shapes.Shapes.box(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()));
                return result;
            } catch (Exception error) { throw new IllegalStateException("Cannot evaluate original shape " + entry.key() + " at " + pos, error); }
        }
        @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
            return itemForBlock(entry.key(), state.getValue(METADATA));
        }
        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            if (level.isClientSide) return;
            try {
                if (state.hasBlockEntity() && entry.hasTile(state.getValue(METADATA))) {
                    if (!(level.getBlockEntity(pos) instanceof LegacyBlockEntity tile)) throw new IllegalStateException("Missing host tile envelope");
                    tile.legacy();
                }
                LegacyTiles.Placer actor = placer == null ? null : new LegacyTiles.Placer(placer.getUUID(), placer.getName().getString(), placer.getX(), placer.getY(), placer.getZ(), placer.getYRot(), placer.getXRot());
                services.tiles().placed(entry.key(), services.worlds().bound(level), pos.getX(), pos.getY(), pos.getZ(), actor, snapshot(stack));
                if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.synchronize();
            } catch (Exception error) { throw new IllegalStateException("Original onBlockPlacedBy failed for " + entry.key(), error); }
        }
        @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            if (!canPlace(entry, state.getValue(METADATA))) return List.of();
            try {
                var result = new ArrayList<ItemStack>();
                for (var drop : declarations.drops(entry.key(), state.getValue(METADATA), params.getLevel().random.nextLong())) {
                    ItemStack mapped = mapStack(drop);
                    if (mapped.isEmpty()) throw new UnsupportedOperationException("Unmapped original block drop: " + drop.key());
                    result.add(mapped);
                }
                if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof LegacyBlockEntity tile) {
                    Object legacy = tile.legacyOrNull();
                    if (legacy != null) for (var drop : services.tiles().inventory(legacy)) {
                        ItemStack mapped = mapStack(drop);
                        if (mapped.isEmpty()) throw new UnsupportedOperationException("Unmapped original inventory drop: " + drop.key());
                        result.add(mapped);
                    }
                }
                return result;
            } catch (Exception failure) { throw new IllegalStateException("Cannot execute original block drops for " + entry.key(), failure); }
        }
    }
    private final class MirroredTileBlock extends MirroredBlock implements EntityBlock {
        MirroredTileBlock(LegacyContent.Block entry) { super(entry); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LegacyBlockEntity(ModernContentBridge.this, pos, state); }
        @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            return level.isClientSide || type != tileType ? null : (world,pos,current,tile) -> ((LegacyBlockEntity)tile).tickServer();
        }
    }
    public ItemStack itemForBlock(String key, int metadata) {
        for (var item : declarations.items()) if (key.equals(item.blockKey())) return stack(item.key(), metadata, 1, "");
        return ItemStack.EMPTY;
    }
    public LegacyBlockRendering.View renderView(BlockGetter level, BlockPos origin) {
        return renderView(level, origin, level.getBlockState(origin));
    }
    public LegacyBlockRendering.View renderView(BlockGetter level, BlockPos origin, BlockState center) {
        return new LegacyBlockRendering.View() {
            public HostWorldAccess.Block block(int x, int y, int z) {
                var state = x == 0 && y == 0 && z == 0 ? center : level.getBlockState(origin.offset(x,y,z));
                if (state.hasProperty(METADATA)) return legacyBlock(state);
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (state.isAir()) id = "minecraft:air";
                if (id.equals("minecraft:grass_block")) id = "minecraft:grass";
                // New vanilla blocks have no old registry entry; preserve their occlusion for old neighbor queries.
                if (!legacyBlockKeys.contains(id)) id = state.isSolidRender(level, origin.offset(x,y,z)) ? "minecraft:stone" : "minecraft:air";
                return new HostWorldAccess.Block(id, 0);
            }
            public byte[] tileData(int x, int y, int z) {
                if (x == 0 && y == 0 && z == 0 && level.getBlockState(origin).getBlock() != center.getBlock()) return null;
                return level.getBlockEntity(origin.offset(x,y,z)) instanceof LegacyBlockEntity tile ? tile.renderData() : null;
            }
            public long time() { return level instanceof Level world ? world.getGameTime() : 0; }
        };
    }
    private InteractionResult useLegacyItem(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        var level = context.getLevel(); var player = context.getPlayer();
        var stack = context.getItemInHand(); var pos = context.getClickedPos(); var hit = context.getClickLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
        try (var transaction = new PlacementTransaction(level)) {
            var actor = new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.getAbilities().instabuild,player.isShiftKeyDown());
            var result = new LegacyItemPlacement(services).use(services.worlds().bound(level),actor,snapshot(stack),pos.getX(),pos.getY(),pos.getZ(),context.getClickedFace().get3DDataValue(),(float)hit.x,(float)hit.y,(float)hit.z);
            if (!result.used()) return InteractionResult.PASS;
            transaction.commit();
            if (!player.getAbilities().instabuild) {
                if (result.stack() == null) stack.setCount(0);
                else {
                    stack.setCount(result.stack().count());
                    if (!result.stack().tag().equals(snapshot(stack).tag())) player.setItemInHand(context.getHand(),mapStack(result.stack()));
                }
            }
            return InteractionResult.CONSUME;
        } catch (Exception failure) {
            LogUtils.getLogger().error("MFCL original item placement failed for {} at {}", BuiltInRegistries.ITEM.getKey(stack.getItem()),pos,failure);
            player.displayClientMessage(Component.translatable("mfcl.content.placement_failed"),true);
            return InteractionResult.FAIL;
        }
    }
    public void beforeMutation(Level level, BlockPos pos) {
        var transaction = placement.get();
        if (transaction != null && transaction.level == level) transaction.record(pos);
    }
    public <T> T logicTransaction(Level level, BlockPos pos, java.util.concurrent.Callable<T> action, boolean immediate) throws Exception {
        if (placement.get() != null) { beforeMutation(level,pos); return action.call(); }
        try (var transaction = new PlacementTransaction(level)) {
            transaction.record(pos); var result = action.call(); transaction.commit(immediate); return result;
        }
    }
    public void afterCommit(Level level, Runnable action) {
        var transaction = placement.get();
        if (transaction != null && transaction.level == level) transaction.effects.add(action);
        else action.run();
    }
    private final class PlacementTransaction implements AutoCloseable {
        private record Before(BlockState state, CompoundTag tile) {}
        private final Level level;
        private final Map<BlockPos,Before> before = new LinkedHashMap<>();
        private final List<Runnable> effects = new ArrayList<>();
        private boolean committed;
        PlacementTransaction(Level level) {
            if (placement.get() != null) throw new IllegalStateException("Nested legacy placement");
            this.level = level; placement.set(this);
        }
        void record(BlockPos pos) {
            before.computeIfAbsent(pos.immutable(), key -> {
                var tile = level.getBlockEntity(key);
                return new Before(level.getBlockState(key),tile == null ? null : tile.saveWithFullMetadata());
            });
        }
        void commit() {
            commit(true);
        }
        void commit(boolean immediate) {
            for (var pos : before.keySet()) if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.captureChanges(immediate);
            committed = true;
            effects.forEach(Runnable::run);
        }
        @Override public void close() {
            placement.remove();
            if (committed) return;
            var positions = new ArrayList<>(before.keySet()); Collections.reverse(positions);
            for (var pos : positions) {
                var saved = before.get(pos); level.setBlock(pos,saved.state(),3);
                if (saved.tile() != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(saved.tile());
            }
        }
    }
    private static BlockBehaviour.Properties properties(LegacyContent.Block entry) {
        var properties = BlockBehaviour.Properties.of().strength(entry.hardness(), entry.resistance()).lightLevel(state -> entry.light());
        if (!entry.opaque()) properties.noOcclusion();
        return properties;
    }
}
