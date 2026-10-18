package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import org.objectweb.asm.*;
import java.util.*;

/** Runs against actual private 1.7.10 classes without requiring a particular mod. */
final class RuntimeLogicSemanticsProbe {
    static void verify(LegacyServices services, Map<String,Object> report) throws Exception {
        var loader = services.loader(); var notifications = new ArrayList<String>();
        Object identity = new Object();
        Object world = services.worlds().bind(identity,new HostWorldAccess() {
            public String dimensionKey() { return "mfcl:logic-test"; } public int legacyDimensionId() { return 0; }
            public boolean clientSide() { return false; } public long gameTime() { return 123; } public long dayTime() { return 45; }
            public void setDayTime(long time) { } public Block block(int x,int y,int z) { return new Block("minecraft:stone",0); }
            public boolean setBlock(int x,int y,int z,Block block,int flags) { return false; }
            public boolean powered(int x,int y,int z) { return x == 1; } public int power(int x,int y,int z,int side) { return 7; }
            public boolean loaded(int x,int y,int z) { return x == 1; }
            public void blockEvent(int x,int y,int z,String block,int event,int data) { notifications.add("event:"+block+":"+event+":"+data); }
            public void schedule(int x,int y,int z,String block,int delay) { notifications.add("schedule:"+block+":"+delay); }
        });
        try {
            if (!loader.loadClass("net.minecraft.world.WorldServer").isInstance(world)) throw new AssertionError("Server logic received a client-only world type");
            Object actor=services.tiles().actor(world,new LegacyTiles.Placer(new UUID(1,2),"ServerActor",0,64,0,0,0),null);
            if(!loader.loadClass("net.minecraft.entity.player.EntityPlayerMP").isInstance(actor))throw new AssertionError("Server item callbacks received a client player type");
            var oldWorld = loader.loadClass("net.minecraft.world.World"); var oldTile = loader.loadClass("net.minecraft.tileentity.TileEntity");
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            String name = "mfcl/fixture/LogicTile";
            writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/tileentity/TileEntity",null);
            writer.visitField(Opcodes.ACC_PUBLIC,"ticks","I",null,null).visitEnd();
            var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null); constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD,0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/tileentity/TileEntity","<init>","()V",false); constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1,1); constructor.visitEnd();
            var tick = writer.visitMethod(Opcodes.ACC_PUBLIC,"func_145845_h","()V",null,null); tick.visitCode(); tick.visitVarInsn(Opcodes.ALOAD,0); tick.visitInsn(Opcodes.DUP);
            tick.visitFieldInsn(Opcodes.GETFIELD,name,"ticks","I"); tick.visitInsn(Opcodes.ICONST_1); tick.visitInsn(Opcodes.IADD); tick.visitFieldInsn(Opcodes.PUTFIELD,name,"ticks","I");
            tick.visitInsn(Opcodes.RETURN); tick.visitMaxs(3,1); tick.visitEnd(); writer.visitEnd(); byte[] bytes = writer.toByteArray();
            var type = (Class<?>)loader.getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class).invoke(loader,name.replace('/','.'),bytes,0,bytes.length);
            Object tile = type.getConstructor().newInstance(); oldTile.getMethod("func_145834_a",oldWorld).invoke(tile,world);
            if (!services.logic().tick(tile) || !services.logic().tick(tile) || type.getField("ticks").getInt(tile) != 2) throw new AssertionError("Original updateEntity was not dispatched");
            services.tiles().invalidate(tile);
            if (services.logic().tick(tile) || type.getField("ticks").getInt(tile) != 2) throw new AssertionError("Invalidated tiles were still ticked");
            if (!(boolean)oldWorld.getMethod("func_72864_z",int.class,int.class,int.class).invoke(world,1,2,3)
                    || (int)oldWorld.getMethod("func_72879_k",int.class,int.class,int.class,int.class).invoke(world,1,2,3,4) != 7
                    || (boolean)oldWorld.getMethod("func_72899_e",int.class,int.class,int.class).invoke(world,2,2,3)) throw new AssertionError("Host power/chunk queries were replaced by constants");
            var block = loader.loadClass("net.minecraft.block.Block"); Object registry = block.getField("field_149771_c").get(null);
            Object stone = registry.getClass().getMethod("func_82594_a",String.class).invoke(registry,"minecraft:stone");
            oldWorld.getMethod("func_147452_c",int.class,int.class,int.class,block,int.class,int.class).invoke(world,1,2,3,stone,4,5);
            oldWorld.getMethod("func_147464_a",int.class,int.class,int.class,block,int.class).invoke(world,1,2,3,stone,6);
            if (!notifications.equals(List.of("event:minecraft:stone:4:5","schedule:minecraft:stone:6"))) throw new AssertionError(notifications);
            Object furnace = loader.loadClass("net.minecraft.tileentity.TileEntityFurnace").getConstructor().newInstance();
            var access = services.inventories(); var coal = new LegacyClientData.Stack("minecraft:coal",0,3,"","");
            if (!Arrays.equals(access.slots(furnace,1),new int[]{0}) || !access.allows(furnace,1,coal,null,true)) throw new AssertionError("Original sided inventory rules were not retained");
            access.set(furnace,1,coal);
            if (access.remove(furnace,1,1).count() != 1 || access.get(furnace,1).count() != 2 || access.get(furnace,0) != null) throw new AssertionError("Original inventory callbacks lost counts or empty slots");
            report.put("genericOriginalGameplayCallbacksVerified",true);
        } finally { services.worlds().unbind(identity); }
    }
}
