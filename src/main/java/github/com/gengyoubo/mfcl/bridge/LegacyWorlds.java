package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import java.lang.reflect.*;
import java.util.*;

/** Maintains typed old World/WorldProvider objects, stable identity, direct fields and synchronous forwarding. */
public final class LegacyWorlds implements AutoCloseable {
    private static final Map<Object, Binding> DISPATCH = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Object, Binding> worlds = new IdentityHashMap<>();
    private final ClassLoader loader;
    private Class<?> facade;
    private static final class Binding {
        final Object world; final HostWorldAccess host; final Thread owner; final ClassLoader loader;
        Binding(Object world, HostWorldAccess host, ClassLoader loader) { this.world = world; this.host = host; this.loader = loader; owner = Thread.currentThread(); }
        void check() { if (Thread.currentThread() != owner) throw new IllegalStateException("Legacy world access must run on its host owner thread"); }
    }
    public LegacyWorlds(ClassLoader loader) { this.loader = loader; }
    public synchronized Object bound(Object hostIdentity) {
        Binding binding = worlds.get(hostIdentity);
        if (binding == null) throw new IllegalStateException("Host world has no legacy binding");
        binding.check(); return binding.world;
    }
    public synchronized Object bind(Object hostIdentity, HostWorldAccess host) throws Exception {
        Binding existing = worlds.get(hostIdentity);
        if (existing != null) { existing.check(); return existing.world; }
        Objects.requireNonNull(host, "Host world access");
        if (facade == null) facade = defineFacade();
        Object world = allocate(facade);
        Class<?> providerType = loader.loadClass("net.minecraft.world.WorldProvider");
        Object provider = loader.loadClass("net.minecraft.world.WorldProviderSurface").getConstructor().newInstance();
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73011_w", provider);
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72995_K", host.clientSide());
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73012_v", new Random());
        Object info = loader.loadClass("net.minecraft.world.storage.WorldInfo").getConstructor(loader.loadClass("net.minecraft.nbt.NBTTagCompound"))
                .newInstance(loader.loadClass("net.minecraft.nbt.NBTTagCompound").getConstructor().newInstance());
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72986_A", info);
        set(providerType, provider, "field_76574_g", host.legacyDimensionId());
        set(providerType, provider, "field_76579_a", world);
        var binding = new Binding(world, host, loader); worlds.put(hostIdentity, binding); DISPATCH.put(world, binding);
        return world;
    }
    public synchronized void unbind(Object hostIdentity) {
        Binding binding = worlds.get(hostIdentity);
        if (binding != null) { binding.check(); worlds.remove(hostIdentity); DISPATCH.remove(binding.world); }
    }
    @Override public synchronized void close() { for (Binding binding : worlds.values()) DISPATCH.remove(binding.world); worlds.clear(); }
    private static Binding binding(Object world) {
        Binding binding = DISPATCH.get(world); if (binding == null) throw new IllegalStateException("Legacy world binding has expired"); binding.check(); return binding;
    }
    public static long time(Object world, boolean total) { var binding = binding(world); return total ? binding.host.gameTime() : binding.host.dayTime(); }
    public static void setTime(Object world, long time) { binding(world).host.setDayTime(time); }
    public static Object block(Object world, int x, int y, int z) {
        Binding binding = binding(world); var block = binding.host.block(x, y, z);
        try {
            Object registry = binding.loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            var keys = (Set<?>) registry.getClass().getMethod("func_148742_b").invoke(registry);
            if (!keys.contains(block.legacyKey())) throw new UnsupportedOperationException("No old Block representation: " + block.legacyKey());
            return registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, block.legacyKey());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static int metadata(Object world, int x, int y, int z) { return binding(world).host.block(x, y, z).metadata(); }
    public static boolean metadata(Object world, int x, int y, int z, int metadata, int flags) {
        var host = binding(world).host; var old = host.block(x, y, z);
        return host.setBlock(x, y, z, new HostWorldAccess.Block(old.legacyKey(), metadata), flags);
    }
    public static Object tile(Object world, int x, int y, int z) { return binding(world).host.tileEntity(x, y, z); }
    public static void tile(Object world, int x, int y, int z, Object tile) { binding(world).host.setTileEntity(x, y, z, tile); }
    public static void removeTile(Object world, int x, int y, int z) { binding(world).host.removeTileEntity(x, y, z); }
    public static void dirty(Object world, int x, int y, int z, Object tile) { binding(world).host.markTileDirty(x, y, z, tile); }
    public static void updated(Object world, int x, int y, int z) { binding(world).host.blockUpdated(x, y, z); }
    public static boolean air(Object world, int x, int y, int z) { return binding(world).host.block(x, y, z).legacyKey().equals("minecraft:air"); }
    public static Object biome(Object world, int x, int z) {
        var binding = binding(world);
        try { return binding.loader.loadClass("net.minecraft.world.biome.BiomeGenBase").getField("field_76772_c").get(null); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static boolean canPlace(Object world, Object target, int x, int y, int z, boolean ignoreCollision, int side, Object entity, Object stack) {
        var binding = binding(world);
        try {
            var type = binding.loader.loadClass("net.minecraft.block.Block");
            Object registry = type.getField("field_149771_c").get(null);
            String key = (String)registry.getClass().getMethod("func_148750_c",Object.class).invoke(registry,target);
            Object existing = block(world,x,y,z);
            return (boolean)type.getMethod("isReplaceable",binding.loader.loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class).invoke(existing,world,x,y,z)
                    && binding.host.canPlaceBlock(new HostWorldAccess.Block(key,0),x,y,z)
                    && (boolean)type.getMethod("func_149707_d",binding.loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class,int.class).invoke(target,world,x,y,z,side);
        } catch (Exception error) { throw new IllegalStateException("Cannot evaluate old placement conditions",error); }
    }
    public static boolean removeBlock(Object world, int x, int y, int z) { return binding(world).host.setBlock(x,y,z,new HostWorldAccess.Block("minecraft:air",0),3); }
    public static boolean solid(Object world, int x, int y, int z, Object direction, boolean fallback) {
        var binding = binding(world);
        try { return (boolean)binding.loader.loadClass("net.minecraft.block.Block").getMethod("isSideSolid",binding.loader.loadClass("net.minecraft.world.IBlockAccess"),int.class,int.class,int.class,binding.loader.loadClass("net.minecraftforge.common.util.ForgeDirection"))
                .invoke(block(world,x,y,z),world,x,y,z,direction); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public static boolean setBlock(Object world, int x, int y, int z, Object oldBlock, int metadata, int flags) {
        Binding binding = binding(world);
        try {
            Class<?> type = binding.loader.loadClass("net.minecraft.block.Block");
            if (!type.isInstance(oldBlock)) throw new IllegalArgumentException("Block belongs to another runtime");
            Object registry = type.getField("field_149771_c").get(null);
            String key = (String) registry.getClass().getMethod("func_148750_c", Object.class).invoke(registry, oldBlock);
            if (key == null) throw new UnsupportedOperationException("Unregistered legacy block");
            return binding.host.setBlock(x, y, z, new HostWorldAccess.Block(key, metadata), flags);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }
    private void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        loader.loadClass("github.com.gengyoubo.mfcl.legacy1710.ModernEnumSupport").getMethod("setFailsafeFieldValue", Field.class, Object.class, Object.class)
                .invoke(null, owner.getDeclaredField(name), target, value);
    }
    private Class<?> defineFacade() throws Exception {
        String name = "github/com/gengyoubo/mfcl/legacy1710/HostWorldFacade", bridge = Type.getInternalName(LegacyWorlds.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "net/minecraft/client/multiplayer/WorldClient", null);
        for (String method : List.of("func_82737_E", "func_72820_D")) {
            var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method, "()J", null, null); mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(method.equals("func_82737_E") ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, "time", "(Ljava/lang/Object;Z)J", false); mv.visitInsn(Opcodes.LRETURN); mv.visitMaxs(2, 1); mv.visitEnd();
        }
        forward(writer, bridge, "func_72877_b", "(J)V", "setTime", "(Ljava/lang/Object;J)V", null);
        forward(writer, bridge, "func_147439_a", "(III)Lnet/minecraft/block/Block;", "block", "(Ljava/lang/Object;III)Ljava/lang/Object;", "net/minecraft/block/Block");
        forward(writer, bridge, "func_72805_g", "(III)I", "metadata", "(Ljava/lang/Object;III)I", null);
        forward(writer, bridge, "func_147465_d", "(IIILnet/minecraft/block/Block;II)Z", "setBlock", "(Ljava/lang/Object;IIILjava/lang/Object;II)Z", null);
        forward(writer, bridge, "func_72921_c", "(IIIII)Z", "metadata", "(Ljava/lang/Object;IIIII)Z", null);
        forward(writer, bridge, "func_147438_o", "(III)Lnet/minecraft/tileentity/TileEntity;", "tile", "(Ljava/lang/Object;III)Ljava/lang/Object;", "net/minecraft/tileentity/TileEntity");
        forward(writer, bridge, "func_147455_a", "(IIILnet/minecraft/tileentity/TileEntity;)V", "tile", "(Ljava/lang/Object;IIILjava/lang/Object;)V", null);
        forward(writer, bridge, "func_147475_p", "(III)V", "removeTile", "(Ljava/lang/Object;III)V", null);
        forward(writer, bridge, "func_147476_b", "(IIILnet/minecraft/tileentity/TileEntity;)V", "dirty", "(Ljava/lang/Object;IIILjava/lang/Object;)V", null);
        for (String method : List.of("func_147471_g", "func_147479_m")) forward(writer, bridge, method, "(III)V", "updated", "(Ljava/lang/Object;III)V", null);
        forward(writer, bridge, "func_147437_c", "(III)Z", "air", "(Ljava/lang/Object;III)Z", null);
        forward(writer, bridge, "func_147468_f", "(III)Z", "removeBlock", "(Ljava/lang/Object;III)Z", null);
        forward(writer, bridge, "func_147472_a", "(Lnet/minecraft/block/Block;IIIZILnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Z", "canPlace", "(Ljava/lang/Object;Ljava/lang/Object;IIIZILjava/lang/Object;Ljava/lang/Object;)Z", null);
        forward(writer, bridge, "isSideSolid", "(IIILnet/minecraftforge/common/util/ForgeDirection;Z)Z", "solid", "(Ljava/lang/Object;IIILjava/lang/Object;Z)Z", null);
        forward(writer, bridge, "func_72807_a", "(II)Lnet/minecraft/world/biome/BiomeGenBase;", "biome", "(Ljava/lang/Object;II)Ljava/lang/Object;", "net/minecraft/world/biome/BiomeGenBase");
        constant(writer, "func_72802_i", "(IIII)I", 0x00F000F0);
        constant(writer, "func_72800_K", "()I", 256);
        constant(writer, "func_72806_N", "()Z", 0);
        constant(writer, "func_72879_k", "(IIII)I", 0);
        constant(writer, "func_72849_a", "(IIIZ)I", 15);
        constant(writer, "func_72864_z", "(III)Z", 0);
        constant(writer, "func_72899_e", "(III)Z", 1);
        constant(writer, "func_72904_c", "(IIIIII)Z", 1);
        var sound = writer.visitMethod(Opcodes.ACC_PUBLIC,"func_72908_a","(DDDLjava/lang/String;FF)V",null,null);
        sound.visitCode(); sound.visitInsn(Opcodes.RETURN); sound.visitMaxs(0,10); sound.visitEnd();
        writer.visitEnd(); byte[] bytes = writer.toByteArray();
        return (Class<?>) loader.getClass().getMethod("defineLegacyClass", String.class, byte[].class, int.class, int.class).invoke(loader, name.replace('/', '.'), bytes, 0, bytes.length);
    }
    private static void constant(ClassWriter writer, String name, String descriptor, int value) {
        var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null); mv.visitCode();
        mv.visitLdcInsn(value); mv.visitInsn(Opcodes.IRETURN); mv.visitMaxs(1, 6); mv.visitEnd();
    }
    private static void forward(ClassWriter writer, String bridge, String method, String descriptor, String callback, String targetDescriptor, String cast) {
        var mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method, descriptor, null, null); mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0);
        int slot = 1; for (Type type : Type.getArgumentTypes(descriptor)) { mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), slot); slot += type.getSize(); }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, bridge, callback, targetDescriptor, false);
        if (cast != null) mv.visitTypeInsn(Opcodes.CHECKCAST, cast);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)); mv.visitMaxs(8, slot); mv.visitEnd();
    }
}
