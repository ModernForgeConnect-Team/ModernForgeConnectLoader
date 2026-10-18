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
    private final Map<String, Block> blocks = new LinkedHashMap<>();
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<Item, LegacyContent.Item> reverseItems = new IdentityHashMap<>();
    private final LegacyContentPack pack;
    private final CreativeTabBridge creative;
    private boolean blocksRegistered, itemsRegistered;
    private BlockEntityType<LegacyBlockEntity> tileType;

    private ModernContentBridge(LegacyServices services) throws Exception {
        this.services = services; declarations = new LegacyContent(services.items());
        validateIds(declarations.blocks().stream().map(LegacyContent.Block::key).toList());
        validateIds(declarations.items().stream().map(LegacyContent.Item::key).toList());
        pack = new LegacyContentPack(services.gameDirectory(), declarations);
        creative = new CreativeTabBridge(this);
    }
    public static ModernContentBridge install(LegacyServices services, IEventBus bus) {
        try {
            var bridge = new ModernContentBridge(services); active = bridge;
            bus.addListener(bridge::register); bus.addListener(bridge.pack::findPacks);
            bus.addListener(bridge.creative::buildVanillaContents);
            LevelWorldBridge.install(bridge);
            LogUtils.getLogger().info("MFCL content barrier: {} blocks, {} items declared before modern registration", bridge.declarations.blocks().size(), bridge.declarations.items().size());
            LogUtils.getLogger().info("MFCL legacy static models: {} items custom-rendered", bridge.declarations.customItems().size());
            for (String diagnostic : bridge.declarations.captureDiagnostics()) LogUtils.getLogger().info("MFCL capture: {}", diagnostic);
            return bridge;
        } catch (Exception failure) { throw new IllegalStateException("Cannot freeze legacy content declarations", failure); }
    }
    public static ModernContentBridge active() { return active; }
    public LegacyContent declarations() { return declarations; }
    public LegacyServices services() { return services; }
    public LegacyContentPack pack() { return pack; }
    public CreativeTabBridge creative() { return creative; }
    public Map<String, Block> blocks() { return Collections.unmodifiableMap(blocks); }
    public Map<String, Item> items() { return Collections.unmodifiableMap(items); }
    public boolean registered() { return blocksRegistered && itemsRegistered; }
    public BlockEntityType<LegacyBlockEntity> tileType() { return Objects.requireNonNull(tileType, "Legacy block entity registry window"); }
    public static boolean canPlace(LegacyContent.Block entry) { return entry.renderType() == 0; }
    public static ResourceLocation id(String key) {
        var parsed = ResourceLocation.tryParse(key);
        return parsed != null ? parsed : new ResourceLocation("mfcl", "legacy/" + HexFormat.of().formatHex(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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
            if (!canPlace(((MirroredBlock) block).entry)) throw new UnsupportedOperationException("Legacy special rendering/placement bridge is pending: " + old.legacyKey());
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
            if (!canPlace(block.entry)) lines.add(Component.translatable("mfcl.content.special_block_pending").withStyle(net.minecraft.ChatFormatting.YELLOW));
            else if (block.entry.tileEntity()) lines.add(Component.translatable("mfcl.content.machine_logic_pending").withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }
    private final class MirroredItem extends Item {
        private final LegacyContent.Item entry;
        MirroredItem(Properties properties, LegacyContent.Item entry) { super(properties); this.entry = entry; }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(customRenderer(() -> entry == null ? null : entry.key()));
        }
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
            if (!ModernContentBridge.canPlace(((MirroredBlock) getBlock()).entry)) {
                if (context.getPlayer() != null && context.getLevel().isClientSide) context.getPlayer().displayClientMessage(Component.translatable("mfcl.content.special_block_pending"), true);
                return InteractionResult.FAIL;
            }
            if (!context.getItemInHand().isDamageableItem() && (metadata(context.getItemInHand()) < 0 || metadata(context.getItemInHand()) > 15)) return InteractionResult.FAIL;
            return super.useOn(context);
        }
        @Override public InteractionResult place(BlockPlaceContext context) {
            if (!ModernContentBridge.canPlace(((MirroredBlock) getBlock()).entry)) return InteractionResult.FAIL;
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
            return state == null ? null : state.setValue(METADATA, metadata(context.getItemInHand()) & 15);
        }
    }
    private class MirroredBlock extends Block {
        private final LegacyContent.Block entry;
        MirroredBlock(LegacyContent.Block entry) {
            super(properties(entry)); this.entry = entry; registerDefaultState(stateDefinition.any().setValue(METADATA, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(METADATA); }
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
            if (!canPlace(entry)) return List.of();
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
    }
    public ItemStack itemForBlock(String key, int metadata) {
        for (var item : declarations.items()) if (key.equals(item.blockKey())) return stack(item.key(), metadata, 1, "");
        return ItemStack.EMPTY;
    }
    private static BlockBehaviour.Properties properties(LegacyContent.Block entry) {
        var properties = BlockBehaviour.Properties.of().strength(entry.hardness(), entry.resistance()).lightLevel(state -> entry.light());
        if (!entry.opaque()) properties.noOcclusion();
        return properties;
    }
}
