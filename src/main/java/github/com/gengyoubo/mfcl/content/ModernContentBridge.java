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
                var block = new MirroredBlock(entry); registry.register(id(entry.key()), block); blocks.put(entry.key(), block);
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
            if (!((MirroredBlock) block).entry.simple()) throw new UnsupportedOperationException("Legacy tile entity/special rendering bridge is pending: " + old.legacyKey());
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
        if (entry.blockKey() != null && blocks.get(entry.blockKey()) instanceof MirroredBlock block && !block.entry.simple())
            lines.add(Component.translatable("mfcl.content.special_block_pending").withStyle(net.minecraft.ChatFormatting.YELLOW));
    }
    private final class MirroredItem extends Item {
        private final LegacyContent.Item entry;
        MirroredItem(Properties properties, LegacyContent.Item entry) { super(properties); this.entry = entry; }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
    }
    private final class MirroredBlockItem extends BlockItem {
        private final LegacyContent.Item entry;
        MirroredBlockItem(MirroredBlock block, Properties properties, LegacyContent.Item entry) { super(block, properties); this.entry = entry; }
        @Override public Component getName(ItemStack stack) { return name(entry, stack); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) { tooltip(entry, stack, lines); }
        @Override public InteractionResult useOn(UseOnContext context) {
            if (!((MirroredBlock) getBlock()).entry.simple()) {
                if (context.getPlayer() != null && context.getLevel().isClientSide) context.getPlayer().displayClientMessage(Component.translatable("mfcl.content.special_block_pending"), true);
                return InteractionResult.FAIL;
            }
            if (!context.getItemInHand().isDamageableItem() && (metadata(context.getItemInHand()) < 0 || metadata(context.getItemInHand()) > 15)) return InteractionResult.FAIL;
            return super.useOn(context);
        }
        @Override protected BlockState getPlacementState(BlockPlaceContext context) {
            BlockState state = super.getPlacementState(context);
            return state == null ? null : state.setValue(METADATA, metadata(context.getItemInHand()) & 15);
        }
    }
    private final class MirroredBlock extends Block {
        private final LegacyContent.Block entry;
        MirroredBlock(LegacyContent.Block entry) {
            super(properties(entry)); this.entry = entry; registerDefaultState(stateDefinition.any().setValue(METADATA, 0));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(METADATA); }
        @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
            return itemForBlock(entry.key(), state.getValue(METADATA));
        }
        @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
            if (!entry.simple()) return List.of();
            try {
                var result = new ArrayList<ItemStack>();
                for (var drop : declarations.drops(entry.key(), state.getValue(METADATA), params.getLevel().random.nextLong())) {
                    ItemStack mapped = stack(drop.key(), drop.metadata(), drop.count(), drop.tag());
                    if (mapped.isEmpty()) throw new UnsupportedOperationException("Unmapped original block drop: " + drop.key());
                    result.add(mapped);
                }
                return result;
            } catch (Exception failure) { throw new IllegalStateException("Cannot execute original block drops for " + entry.key(), failure); }
        }
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
