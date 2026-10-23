package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.nbt.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.ToolActions;
import java.util.*;

/** Lossless native tool payloads in old inventories, including Forge capabilities and Tinkers' broken tools. */
final class HostToolStacks implements LegacyHostTools.Access {
    private static final String PAYLOAD = "mfcl_tool";
    private static final String DAMAGE_BASE = "mfcl_tool_damage";
    private static final com.mojang.authlib.GameProfile MACHINE = new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("mfcl:machine_tool".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[MFCL Tool]");
    private static final Set<Item> OLD_VANILLA = Set.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE);
    private static final TagKey<Item> FORGE_PICKAXES = TagKey.create(Registries.ITEM, ResourceLocation.parse("forge:tools/pickaxes"));
    private final LegacyServices services;
    HostToolStacks(LegacyServices services) throws Exception {
        this.services = services;
        // One stable identity is installed before any old inventory is deserialized on either side.
        LegacyHostTools.install(services, this);
    }
    static boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || OLD_VANILLA.contains(stack.getItem())) return false;
        if (stack.getItem() instanceof PickaxeItem || stack.is(ItemTags.PICKAXES) || stack.is(FORGE_PICKAXES) || stack.canPerformAction(ToolActions.PICKAXE_DIG)) return true;
        // Tinkers disables actions when broken. Probe an intact copy so broken picks can still be removed/reinserted.
        if (stack.hasTag() && stack.getTag().getBoolean("tic_broken")) {
            var intact = stack.copy(); intact.getOrCreateTag().remove("tic_broken"); intact.setDamageValue(0);
            return intact.canPerformAction(ToolActions.PICKAXE_DIG);
        }
        return false;
    }
    LegacyClientData.Stack toLegacy(ItemStack stack) throws Exception {
        return inHost(() -> accepts(stack) ? encode(stack) : null);
    }
    private static <T> T inHost(java.util.concurrent.Callable<T> work) throws Exception {
        var thread = Thread.currentThread(); var previous = thread.getContextClassLoader();
        try { thread.setContextClassLoader(HostToolStacks.class.getClassLoader()); return work.call(); }
        finally { thread.setContextClassLoader(previous); }
    }
    private LegacyClientData.Stack encode(ItemStack stack) throws Exception {
        var tag = new CompoundTag(); tag.put(PAYLOAD, stack.serializeNBT());
        // Old ItemStack saves damage as a signed short. The payload retains the full native durability value.
        int metadata = Math.min(Short.MAX_VALUE, Math.max(0, stack.getDamageValue())); tag.putInt(DAMAGE_BASE, metadata);
        var enchantments = new ListTag();
        // Query effective levels: Tinkers exposes luck/silk touch through Forge's item enchantment hooks.
        for (var entry : stack.getAllEnchantments().entrySet()) {
            var key = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey());
            var id = key == null ? null : services.enchantments().id(key.toString());
            if (id == null || entry.getValue() <= 0) continue;
            var value = new CompoundTag(); value.putShort("id", id.shortValue()); value.putShort("lvl", entry.getValue().shortValue()); enchantments.add(value);
        }
        if (!enchantments.isEmpty()) tag.put("ench", enchantments);
        if (stack.hasTag() && stack.getTag().getBoolean("Unbreakable")) tag.putBoolean("Unbreakable", true);
        var bytes = new java.io.ByteArrayOutputStream(); NbtIo.writeCompressed(tag, bytes);
        return services.items().withTag(new LegacyClientData.Stack(LegacyHostTools.PICKAXE, metadata, stack.getCount(), stack.getHoverName().getString(), ""), bytes.toByteArray());
    }
    ItemStack toHost(LegacyClientData.Stack old) throws Exception {
        return inHost(() -> decode(old));
    }
    private ItemStack decode(LegacyClientData.Stack old) throws Exception {
        if (!old.key().equals(LegacyHostTools.PICKAXE)) return ItemStack.EMPTY;
        var tag = LegacyVanillaStacks.originalTag(services, old);
        if (!tag.contains(PAYLOAD, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Host pickaxe payload is missing");
        var stack = ItemStack.of(tag.getCompound(PAYLOAD));
        if (stack.isEmpty()) throw new IllegalArgumentException("Host pickaxe item is missing");
        stack.setCount(old.count());
        int baseline = tag.contains(DAMAGE_BASE, Tag.TAG_INT) ? tag.getInt(DAMAGE_BASE) : stack.getDamageValue();
        if (old.metadata() != baseline) stack.setDamageValue(Math.max(0, stack.getDamageValue() + old.metadata() - baseline));
        return stack;
    }
    private ItemStack host(Object stack) throws Exception { return toHost(services.items().snapshot(stack)); }
    @Override public int maxDamage(Object stack) throws Exception { return inHost(() -> host(stack).getMaxDamage()); }
    @Override public boolean usable(Object old) throws Exception {
        return inHost(() -> usableHost(old));
    }
    private boolean usableHost(Object old) throws Exception {
        var stack = host(old);
        return !stack.isEmpty() && !(stack.hasTag() && stack.getTag().getBoolean("tic_broken"))
                && (!stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage());
    }
    @Override public Object damage(Object old, int amount, Object world) throws Exception {
        return inHost(() -> damageHost(old, amount, world));
    }
    private Object damageHost(Object old, int amount, Object world) throws Exception {
        var stack = host(old);
        if (amount > 0 && usable(old) && stack.isDamageableItem()) {
            var level = (ServerLevel) services.worlds().host(world);
            if (level == null) throw new IllegalStateException("Host tool wear requires a bound server world");
            // Forge damageItem runs first: Tinkers consumes modifiers and sets tic_broken, returning zero vanilla wear.
            var actor = net.minecraftforge.common.util.FakePlayerFactory.get(level, MACHINE);
            var previous = actor.getMainHandItem();
            try {
                actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
                int remaining = stack.getItem().damageItem(stack, amount, actor, ignored -> {});
                if (remaining > 0 && stack.hurt(remaining, level.random, null)) {
                    stack.shrink(1); stack.setDamageValue(0);
                }
            } finally { actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, previous); }
        }
        if (stack.isEmpty()) {
            // TC4 inspects the returned count before clearing the slot, so return a zero-count stack, not null.
            old.getClass().getField("field_77994_a").setInt(old, 0); return old;
        }
        return services.items().materialize(encode(stack));
    }
}
