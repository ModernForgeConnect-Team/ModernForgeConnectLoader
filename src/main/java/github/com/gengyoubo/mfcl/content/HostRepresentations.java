package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.*;

/** Distinct old registry objects, used by the original mod's targeting, tags and discovery hashes. */
public final class HostRepresentations implements LegacyHostRepresentations {
    private final ModernContentBridge content;
    private final Map<Item, LegacyStack> items = new IdentityHashMap<>();
    private final Map<net.minecraft.world.level.block.Block, LegacyStack> blocks = new IdentityHashMap<>();
    private final Map<String, String> entities = new HashMap<>();
    private final Map<String, Class<?>> entityClasses = new HashMap<>();
    private final Map<String, Item> reverseItems = new HashMap<>();
    private final Map<String, net.minecraft.world.level.block.Block> reverseBlocks = new HashMap<>();

    HostRepresentations(ModernContentBridge content) { this.content = content; }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    private static String key(String kind, String value) { return "mfcl_host:" + kind + "/" + value.replace(':', '/'); }
    private <T> T legacy(java.util.concurrent.Callable<T> work) {
        try { return content.services().items().inLegacy(work); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Cannot register host representation", failure); }
    }
    @Override public synchronized LegacyStack item(String value) {
        var name = id(value);
        if (!BuiltInRegistries.ITEM.containsKey(name)) throw new IllegalArgumentException("Unknown host item: " + value);
        Item item = BuiltInRegistries.ITEM.get(name);
        if (item instanceof BlockItem block) return block(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString());
        if (items.containsKey(item)) return items.get(item);
        try {
            var original = content.gameplayStack(new ItemStack(item));
            return new LegacyStack(original.key(), original.metadata(), 1, "");
        } catch (UnsupportedOperationException absent) { /* Register an independent identity below. */ }
        catch (Exception failure) { throw new IllegalStateException(failure); }
        return legacy(() -> {
            var loader = content.services().loader();
            var type = loader.loadClass("net.minecraft.item.Item");
            Object old = type.getConstructor().newInstance();
            String key = key("item", value);
            type.getMethod("func_77655_b", String.class).invoke(old, key);
            LegacyHostRegistry.registerItem(loader, key, old);
            LegacyHostRegistry.label(loader, "item." + key + ".name", () -> new ItemStack(item).getHoverName().getString());
            var stack = new LegacyStack(key, 0, 1, "");
            items.put(item, stack); reverseItems.put(key, item);
            return stack;
        });
    }
    @Override public synchronized LegacyStack block(String value) {
        var name = id(value);
        if (!BuiltInRegistries.BLOCK.containsKey(name)) throw new IllegalArgumentException("Unknown host block: " + value);
        var block = BuiltInRegistries.BLOCK.get(name);
        if (blocks.containsKey(block)) return blocks.get(block);
        try {
            var original = content.legacyBlock(block.defaultBlockState());
            return legacy(() -> {
                var loader = content.services().loader();
                Object registry = loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
                Object old = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, original.legacyKey());
                Object item = loader.loadClass("net.minecraft.item.Item").getMethod("func_150898_a", loader.loadClass("net.minecraft.block.Block")).invoke(null, old);
                if (item == null) return null;
                return new LegacyStack(original.legacyKey(), original.metadata(), 1, "");
            });
        } catch (UnmappedLegacyBlockException absent) { /* Register an independent identity below. */ }
        return legacy(() -> {
            var loader = content.services().loader();
            var type = loader.loadClass("net.minecraft.block.Block");
            var material = loader.loadClass("net.minecraft.block.material.Material");
            var constructor = type.getDeclaredConstructor(material); constructor.setAccessible(true);
            Object old = constructor.newInstance(material.getField("field_151576_e").get(null));
            String key = key("block", value);
            type.getMethod("func_149663_c", String.class).invoke(old, key);
            Object item = loader.loadClass("net.minecraft.item.ItemBlock").getConstructor(type).newInstance(old);
            LegacyHostRegistry.registerBlock(loader, key, old, item);
            LegacyHostRegistry.label(loader, "tile." + key + ".name", () -> block.getName().getString());
            var stack = new LegacyStack(key, 0, 1, "");
            blocks.put(block, stack); reverseBlocks.put(key, block);
            if (block.asItem() != Items.AIR) { items.put(block.asItem(), stack); reverseItems.put(key, block.asItem()); }
            return stack;
        });
    }
    @Override public synchronized String entity(String value) {
        var name = id(value);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(name)) throw new IllegalArgumentException("Unknown host entity: " + value);
        if (entities.containsKey(value)) return entities.get(value);
        return legacy(() -> {
            var loader = content.services().loader();
            var list = loader.loadClass("net.minecraft.entity.EntityList");
            var names = (Map<String, Class<?>>) list.getField("field_75625_b").get(null);
            String requested = Map.ofEntries(Map.entry("experience_orb", "XPOrb"), Map.entry("zombified_piglin", "PigZombie"),
                    Map.entry("mooshroom", "MushroomCow"), Map.entry("snow_golem", "SnowMan"), Map.entry("iron_golem", "VillagerGolem"),
                    Map.entry("item", "Item"), Map.entry("tnt", "PrimedTnt"), Map.entry("falling_block", "FallingSand"))
                    .getOrDefault(name.getPath(), name.getPath().replace("_", ""));
            if (name.getNamespace().equals("minecraft")) for (var entry : names.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(requested)) continue;
                entities.put(value, entry.getKey()); entityClasses.put(value, entry.getValue()); return entry.getKey();
            }
            String key = key("entity", value);
            String binary = "github/com/gengyoubo/mfcl/legacy1710/host/Entity_" + java.util.HexFormat.of().formatHex(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, binary, null, "net/minecraft/entity/EntityLiving", null);
            var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/World;)V", null, null);
            constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/EntityLiving", "<init>", "(Lnet/minecraft/world/World;)V", false);
            constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
            // Inherit EntityLiving's watcher bootstrap; its constructor initializes health through those slots.
            for (String method : List.of("func_70037_a", "func_70014_b")) {
                var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method, "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null);
                mv.visitCode(); mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0, 2); mv.visitEnd();
            }
            writer.visitEnd(); byte[] bytes = writer.toByteArray();
            var type = (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class)
                    .invoke(loader, binary.replace('/', '.'), bytes, 0, bytes.length);
            names.put(key, type); ((Map<Class<?>, String>) list.getField("field_75626_c").get(null)).put(type, key);
            LegacyHostRegistry.label(loader, "entity." + key + ".name", () -> BuiltInRegistries.ENTITY_TYPE.get(name).getDescription().getString());
            entities.put(value, key); entityClasses.put(value, type); return key;
        });
    }
    public synchronized String entityClass(String id) { var type = entityClasses.get(id); return type == null ? null : type.getName(); }
    public synchronized LegacyClientData.Stack stack(ItemStack stack) {
        var old = items.get(stack.getItem());
        return old == null ? null : new LegacyClientData.Stack(old.key(), old.metadata(), stack.getCount(), stack.getHoverName().getString(), "");
    }
    public synchronized ItemStack stack(LegacyClientData.Stack stack) {
        var item = reverseItems.get(stack.key()); return item == null ? ItemStack.EMPTY : new ItemStack(item, stack.count());
    }
    public synchronized HostWorldAccess.Block block(BlockState state) {
        var old = blocks.get(state.getBlock()); return old == null ? null : new HostWorldAccess.Block(old.key(), 0);
    }
    public synchronized BlockState block(HostWorldAccess.Block old) {
        var block = reverseBlocks.get(old.legacyKey()); return block == null ? null : block.defaultBlockState();
    }
}
