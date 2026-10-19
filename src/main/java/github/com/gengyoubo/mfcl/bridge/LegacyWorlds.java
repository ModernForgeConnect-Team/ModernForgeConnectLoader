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
    private Class<?> clientFacade, serverFacade;
    private static final class Binding {
        final Object world; final HostWorldAccess host; final Thread owner; final ClassLoader loader;
        final Map<java.util.UUID,Object> queried=new LinkedHashMap<>();
        final Map<java.util.UUID,HostWorldAccess.Entity> snapshots=new LinkedHashMap<>();
        boolean motionCapture;
        Binding(Object world, HostWorldAccess host, ClassLoader loader) { this.world = world; this.host = host; this.loader = loader; owner = Thread.currentThread(); }
        void check() { if (Thread.currentThread() != owner) throw new IllegalStateException("Legacy world access must run on its host owner thread"); }
    }
    public LegacyWorlds(ClassLoader loader) { this.loader = loader; }
    public synchronized Object bound(Object hostIdentity) {
        Binding binding = worlds.get(hostIdentity);
        if (binding == null) throw new IllegalStateException("Host world has no legacy binding");
        binding.check(); return binding.world;
    }
    /** Read-only API lookup; never creates a world on an arbitrary addon thread. */
    public synchronized Object findBound(Object hostIdentity) {
        Binding binding = worlds.get(hostIdentity);
        if (binding == null) return null;
        binding.check(); return binding.world;
    }
    public synchronized Object host(Object legacyWorld) {
        for (var entry : worlds.entrySet()) if (entry.getValue().world == legacyWorld) {
            entry.getValue().check(); return entry.getKey();
        }
        return null;
    }
    public synchronized Object bind(Object hostIdentity, HostWorldAccess host) throws Exception {
        Binding existing = worlds.get(hostIdentity);
        if (existing != null) { existing.check(); return existing.world; }
        Objects.requireNonNull(host, "Host world access");
        Class<?> facade;
        if (host.clientSide()) {
            if (clientFacade == null) clientFacade = defineFacade(true);
            facade = clientFacade;
        } else {
            if (serverFacade == null) serverFacade = defineFacade(false);
            facade = serverFacade;
        }
        Object world = allocate(facade);
        Class<?> providerType = loader.loadClass("net.minecraft.world.WorldProvider");
        Object provider = loader.loadClass("net.minecraft.world.WorldProviderSurface").getConstructor().newInstance();
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73011_w", provider);
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72995_K", host.clientSide());
        set(loader.loadClass("net.minecraft.world.World"), world, "field_73012_v", new Random());
        Object info = loader.loadClass("net.minecraft.world.storage.WorldInfo").getConstructor(loader.loadClass("net.minecraft.nbt.NBTTagCompound"))
                .newInstance(loader.loadClass("net.minecraft.nbt.NBTTagCompound").getConstructor().newInstance());
        set(loader.loadClass("net.minecraft.world.World"), world, "field_72986_A", info);
        set(loader.loadClass("net.minecraft.world.World"), world, "field_96442_D", loader.loadClass("net.minecraft.scoreboard.Scoreboard").getConstructor().newInstance());
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
    public static void sound(Object world,double x,double y,double z,String id,float volume,float pitch) {
        binding(world).host.playSound(new HostWorldAccess.Sound(x,y,z,id,volume,pitch,false,null));
    }
    public static void localSound(Object world,double x,double y,double z,String id,float volume,float pitch,boolean delay) {
        var host=binding(world).host;
        // World.playSound is a client-only effect in 1.7.10; the server implementation is intentionally empty.
        if(host.clientSide())host.playSound(new HostWorldAccess.Sound(x,y,z,id,volume,pitch,delay,null));
    }
    public static void entitySound(Object world,Object entity,String id,float volume,float pitch,boolean exclude) throws ReflectiveOperationException {
        var bound=binding(world);var type=bound.loader.loadClass("net.minecraft.entity.Entity");java.util.UUID except=null;
        if(exclude) {
            Object profile=entity.getClass().getMethod("func_146103_bH").invoke(entity);
            except=(java.util.UUID)profile.getClass().getMethod("getId").invoke(profile);
        }
        bound.host.playSound(new HostWorldAccess.Sound(type.getField("field_70165_t").getDouble(entity),
                type.getField("field_70163_u").getDouble(entity)-type.getField("field_70129_M").getFloat(entity),
                type.getField("field_70161_v").getDouble(entity),id,volume,pitch,false,except));
    }
    public static void setTime(Object world, long time) { binding(world).host.setDayTime(time); }
    /** Translates a legacy projectile into host-neutral data; unsupported entities are ignored so old logic keeps running. */
    public static boolean spawn(Object world, Object entity) {
        var bound = binding(world);
        if (bound.host.clientSide()) return false;
        try {
            ClassLoader loader = bound.loader;
            Class<?> arrowType = loader.loadClass("net.minecraft.entity.projectile.EntityArrow");
            if(loader.loadClass("net.minecraft.entity.item.EntityItem").isInstance(entity))return LegacyDeviceContainers.drop(entity);
            if (!arrowType.isInstance(entity)) return false;
            Class<?> entityType = loader.loadClass("net.minecraft.entity.Entity");
            double x = entityType.getField("field_70165_t").getDouble(entity), y = entityType.getField("field_70163_u").getDouble(entity), z = entityType.getField("field_70161_v").getDouble(entity);
            double mx = entityType.getField("field_70159_w").getDouble(entity), my = entityType.getField("field_70181_x").getDouble(entity), mz = entityType.getField("field_70179_y").getDouble(entity);
            double damage = (double) arrowType.getMethod("func_70242_d").invoke(entity);
            boolean burning = (boolean) entityType.getMethod("func_70027_ad").invoke(entity);
            Object shooter = arrowType.getField("field_70250_c").get(entity);
            java.util.UUID owner = shooter == null ? null : (java.util.UUID) entityType.getMethod("func_110124_au").invoke(shooter);
            bound.host.spawnProjectile(new HostWorldAccess.Projectile(x, y, z, mx, my, mz, damage, burning, owner));
            return true;
        } catch (Exception error) { throw new IllegalStateException("Cannot translate legacy entity spawn", error); }
    }
    public static Object block(Object world, int x, int y, int z) {
        Binding binding = binding(world); var block = binding.host.block(x, y, z);
        try {
            Object registry = binding.loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            var keys = (Set<?>) registry.getClass().getMethod("func_148742_b").invoke(registry);
            if (!keys.contains(block.legacyKey())) throw new UnmappedLegacyBlockException("No old Block representation: " + block.legacyKey());
            return registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, block.legacyKey());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static int metadata(Object world, int x, int y, int z) { return binding(world).host.block(x, y, z).metadata(); }
    public static boolean metadata(Object world, int x, int y, int z, int metadata, int flags) {
        var host = binding(world).host; var old = host.block(x, y, z);
        return host.setBlock(x, y, z, new HostWorldAccess.Block(old.legacyKey(), metadata), flags);
    }
    public static Object rayTrace(Object world, Object start, Object end, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        var binding = binding(world);
        try {
            var vecType = binding.loader.loadClass("net.minecraft.util.Vec3");
            double sx = vecType.getField("field_72450_a").getDouble(start), sy = vecType.getField("field_72448_b").getDouble(start), sz = vecType.getField("field_72449_c").getDouble(start);
            double ex = vecType.getField("field_72450_a").getDouble(end), ey = vecType.getField("field_72448_b").getDouble(end), ez = vecType.getField("field_72449_c").getDouble(end);
            var hit = binding.host.rayTrace(sx, sy, sz, ex, ey, ez);
            if (hit == null) return null;
            Object point = vecType.getMethod("func_72443_a", double.class, double.class, double.class).invoke(null, hit.hitX(), hit.hitY(), hit.hitZ());
            return binding.loader.loadClass("net.minecraft.util.MovingObjectPosition")
                    .getConstructor(int.class, int.class, int.class, int.class, vecType).newInstance(hit.x(), hit.y(), hit.z(), hit.side(), point);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static Object tile(Object world, int x, int y, int z) { return binding(world).host.tileEntity(x, y, z); }
    public static void tile(Object world, int x, int y, int z, Object tile) { binding(world).host.setTileEntity(x, y, z, tile); }
    public static void removeTile(Object world, int x, int y, int z) { binding(world).host.removeTileEntity(x, y, z); }
    public static void dirty(Object world, int x, int y, int z, Object tile) { binding(world).host.markTileDirty(x, y, z, tile); }
    public static void updated(Object world, int x, int y, int z) { binding(world).host.blockUpdated(x, y, z); }
    public static boolean powered(Object world, int x, int y, int z) { return binding(world).host.powered(x,y,z); }
    public static int power(Object world, int x, int y, int z, int side) { return binding(world).host.power(x,y,z,side); }
    public static boolean loaded(Object world, int x, int y, int z) { return binding(world).host.loaded(x,y,z); }
    public static List<Object> entities(Object world,Class<?> type,Object box) {return selectedEntities(world,type,box,null);}
    public static List<Object> selectedEntities(Object world,Class<?> type,Object box,Object selector) {
        var binding=binding(world);
        try {
            var aabb=binding.loader.loadClass("net.minecraft.util.AxisAlignedBB");
            var snapshots=binding.host.entities(aabb.getField("field_72340_a").getDouble(box),aabb.getField("field_72338_b").getDouble(box),aabb.getField("field_72339_c").getDouble(box),aabb.getField("field_72336_d").getDouble(box),aabb.getField("field_72337_e").getDouble(box),aabb.getField("field_72334_f").getDouble(box));
            var result=new ArrayList<Object>();
            for(var snapshot:snapshots) {
                Object entity=binding.motionCapture?binding.queried.get(snapshot.id()):null;
                if(entity==null){entity=LegacyEntityAccess.mirror(binding.loader,world,snapshot,binding.host.existingEntity(snapshot.id()));if(binding.motionCapture){binding.queried.put(snapshot.id(),entity);binding.snapshots.put(snapshot.id(),snapshot);}}
                if(type.isInstance(entity)&&(selector==null||(boolean)binding.loader.loadClass("net.minecraft.command.IEntitySelector").getMethod("func_82704_a",binding.loader.loadClass("net.minecraft.entity.Entity")).invoke(selector,entity)))result.add(entity);
            }
            return result;
        }catch(Exception failure){throw new IllegalStateException("Cannot query host entities",failure);}
    }
    public static List<Object> excludingEntity(Object world,Object excluded,Object box) {
        var binding=binding(world);
        try {
            var entity=binding.loader.loadClass("net.minecraft.entity.Entity");
            Object id=excluded==null?null:entity.getMethod("func_110124_au").invoke(excluded);
            var result=entities(world,entity,box);if(id!=null)result.removeIf(value->{try{return id.equals(entity.getMethod("func_110124_au").invoke(value));}catch(Exception failure){throw new IllegalStateException(failure);}});return result;
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    public static void beginEntityQueries(Object world){clearEntityQueries(world);binding(world).motionCapture=true;}
    public static void clearEntityQueries(Object world){var binding=binding(world);binding.queried.clear();binding.snapshots.clear();binding.motionCapture=false;}
    public static void commitEntityMotion(Object world) throws Exception {
        var binding=binding(world);
        try {
            for(var entry:binding.queried.entrySet()) {
                var next=LegacyEntityAccess.motion(binding.loader,entry.getValue(),entry.getKey());var previous=binding.snapshots.get(entry.getKey());
                if(next.x()!=previous.motionX()||next.y()!=previous.motionY()||next.z()!=previous.motionZ()||next.fallDistance()!=previous.fallDistance())binding.host.applyMotion(next);
            }
        }finally{clearEntityQueries(world);}
    }
    public static void comparators(Object world, int x, int y, int z, Object block) { binding(world).host.updateComparators(x,y,z); }
    private static String blockKey(Binding binding, Object block) {
        try {
            Object registry = binding.loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            return (String)registry.getClass().getMethod("func_148750_c",Object.class).invoke(registry,block);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static void blockEvent(Object world, int x, int y, int z, Object block, int event, int data) {
        var binding = binding(world); binding.host.blockEvent(x,y,z,blockKey(binding,block),event,data);
    }
    public static void schedule(Object world, int x, int y, int z, Object block, int delay) {
        var binding = binding(world); binding.host.schedule(x,y,z,blockKey(binding,block),delay);
    }
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
    private Class<?> defineFacade(boolean client) throws Exception {
        String name = "github/com/gengyoubo/mfcl/legacy1710/"+(client ? "HostWorldFacade" : "HostServerWorldFacade"), bridge = Type.getInternalName(LegacyWorlds.class);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, client ? "net/minecraft/client/multiplayer/WorldClient" : "net/minecraft/world/WorldServer", null);
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
        forward(writer, Type.getInternalName(LegacyPackets.class), "func_73045_a", "(I)Lnet/minecraft/entity/Entity;", "serverEntity", "(Ljava/lang/Object;I)Ljava/lang/Object;", "net/minecraft/entity/Entity");
        forward(writer, bridge, "func_147455_a", "(IIILnet/minecraft/tileentity/TileEntity;)V", "tile", "(Ljava/lang/Object;IIILjava/lang/Object;)V", null);
        forward(writer, bridge, "func_147475_p", "(III)V", "removeTile", "(Ljava/lang/Object;III)V", null);
        forward(writer, bridge, "func_147476_b", "(IIILnet/minecraft/tileentity/TileEntity;)V", "dirty", "(Ljava/lang/Object;IIILjava/lang/Object;)V", null);
        for (String method : List.of("func_147471_g", "func_147479_m")) forward(writer, bridge, method, "(III)V", "updated", "(Ljava/lang/Object;III)V", null);
        forward(writer, bridge, "func_147437_c", "(III)Z", "air", "(Ljava/lang/Object;III)Z", null);
        forward(writer, bridge, "func_147468_f", "(III)Z", "removeBlock", "(Ljava/lang/Object;III)Z", null);
        forward(writer, bridge, "func_147472_a", "(Lnet/minecraft/block/Block;IIIZILnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Z", "canPlace", "(Ljava/lang/Object;Ljava/lang/Object;IIIZILjava/lang/Object;Ljava/lang/Object;)Z", null);
        forward(writer, bridge, "func_147447_a", "(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;ZZZ)Lnet/minecraft/util/MovingObjectPosition;", "rayTrace", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;ZZZ)Ljava/lang/Object;", "net/minecraft/util/MovingObjectPosition");
        forward(writer, bridge, "isSideSolid", "(IIILnet/minecraftforge/common/util/ForgeDirection;Z)Z", "solid", "(Ljava/lang/Object;IIILjava/lang/Object;Z)Z", null);
        forward(writer, bridge, "func_72807_a", "(II)Lnet/minecraft/world/biome/BiomeGenBase;", "biome", "(Ljava/lang/Object;II)Ljava/lang/Object;", "net/minecraft/world/biome/BiomeGenBase");
        constant(writer, "func_72802_i", "(IIII)I", 0x00F000F0);
        constant(writer, "func_72800_K", "()I", 256);
        constant(writer, "func_72806_N", "()Z", 0);
        forward(writer,bridge,"func_72879_k","(IIII)I","power","(Ljava/lang/Object;IIII)I",null);
        constant(writer, "func_72849_a", "(IIIZ)I", 15);
        forward(writer,bridge,"func_72864_z","(III)Z","powered","(Ljava/lang/Object;III)Z",null);
        forward(writer,bridge,"func_72899_e","(III)Z","loaded","(Ljava/lang/Object;III)Z",null);
        forward(writer,bridge,"func_147452_c","(IIILnet/minecraft/block/Block;II)V","blockEvent","(Ljava/lang/Object;IIILjava/lang/Object;II)V",null);
        forward(writer,bridge,"func_147464_a","(IIILnet/minecraft/block/Block;I)V","schedule","(Ljava/lang/Object;IIILjava/lang/Object;I)V",null);
        forward(writer,bridge,"func_147453_f","(IIILnet/minecraft/block/Block;)V","comparators","(Ljava/lang/Object;IIILjava/lang/Object;)V",null);
        constant(writer, "func_72904_c", "(IIIIII)Z", 1);
        forward(writer,bridge,"func_72908_a","(DDDLjava/lang/String;FF)V","sound","(Ljava/lang/Object;DDDLjava/lang/String;FF)V",null);
        forward(writer,bridge,"func_72980_b","(DDDLjava/lang/String;FFZ)V","localSound","(Ljava/lang/Object;DDDLjava/lang/String;FFZ)V",null);
        for(String method:List.of("func_72956_a","func_85173_a")) {
            var sound=writer.visitMethod(Opcodes.ACC_PUBLIC,method,"("+(method.equals("func_72956_a")?"Lnet/minecraft/entity/Entity;":"Lnet/minecraft/entity/player/EntityPlayer;")+"Ljava/lang/String;FF)V",null,null);
            sound.visitCode();sound.visitVarInsn(Opcodes.ALOAD,0);sound.visitVarInsn(Opcodes.ALOAD,1);sound.visitVarInsn(Opcodes.ALOAD,2);sound.visitVarInsn(Opcodes.FLOAD,3);sound.visitVarInsn(Opcodes.FLOAD,4);
            sound.visitInsn(method.equals("func_85173_a")?Opcodes.ICONST_1:Opcodes.ICONST_0);
            sound.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"entitySound","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;FFZ)V",false);sound.visitInsn(Opcodes.RETURN);sound.visitMaxs(6,5);sound.visitEnd();
        }
        forward(writer,bridge,"func_72872_a","(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;","entities","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)Ljava/util/List;",null);
        forward(writer,bridge,"func_82733_a","(Ljava/lang/Class;Lnet/minecraft/util/AxisAlignedBB;Lnet/minecraft/command/IEntitySelector;)Ljava/util/List;","selectedEntities","(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",null);
        forward(writer,bridge,"func_72839_b","(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;","excludingEntity","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",null);
        forward(writer, bridge, "func_72838_d", "(Lnet/minecraft/entity/Entity;)Z", "spawn", "(Ljava/lang/Object;Ljava/lang/Object;)Z", null);
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
