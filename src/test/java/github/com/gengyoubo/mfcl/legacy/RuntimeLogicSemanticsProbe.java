package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import org.objectweb.asm.*;
import java.util.*;

/** Runs against actual private 1.7.10 classes without requiring a particular mod. */
final class RuntimeLogicSemanticsProbe {
    static void verify(LegacyServices services, Map<String,Object> report) throws Exception {
        var loader = services.loader(); var notifications = new ArrayList<String>();
        var sounds=new ArrayList<HostWorldAccess.Sound>();
        var effects=new ArrayList<String>();
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
            public void playSound(Sound sound) {sounds.add(sound);}
            public void levelEvent(UUID excluded,int event,int x,int y,int z,int data) {effects.add(event+":"+x+":"+y+":"+z);}
            public void swing(UUID id) {effects.add("swing:"+id);}
        });
        try {
            if (!loader.loadClass("net.minecraft.world.WorldServer").isInstance(world)) throw new AssertionError("Server logic received a client-only world type");
            Object actor=services.tiles().actor(world,new LegacyTiles.Placer(new UUID(1,2),"ServerActor",0,64,0,0,0),null);
            if(!loader.loadClass("net.minecraft.entity.player.EntityPlayerMP").isInstance(actor))throw new AssertionError("Server item callbacks received a client player type");
            var worldType=loader.loadClass("net.minecraft.world.World");
            worldType.getMethod("func_72908_a",double.class,double.class,double.class,String.class,float.class,float.class).invoke(world,1.25,65.5,-3.75,"thaumcraft:wand",.4f,.7f);
            worldType.getMethod("func_72980_b",double.class,double.class,double.class,String.class,float.class,float.class,boolean.class).invoke(world,0d,0d,0d,"thaumcraft:cameraticks",1f,1f,false);
            loader.loadClass("net.minecraft.entity.Entity").getMethod("func_85030_a",String.class,float.class,float.class).invoke(actor,"random.orb",.2f,.9f);
            worldType.getMethod("func_85173_a",loader.loadClass("net.minecraft.entity.player.EntityPlayer"),String.class,float.class,float.class).invoke(world,actor,"random.click",.5f,1.1f);
            if(sounds.size()!=3||!sounds.get(0).equals(new HostWorldAccess.Sound(1.25,65.5,-3.75,"thaumcraft:wand",.4f,.7f,false,null))
                    ||!sounds.get(1).id().equals("random.orb")||sounds.get(1).y()!=64||!new UUID(1,2).equals(sounds.get(2).excludedPlayer()))
                throw new AssertionError("Old positional/entity sound semantics were lost: "+sounds);
            report.put("genericWorldAndEntitySoundsForwarded",true);
            worldType.getMethod("func_72889_a",loader.loadClass("net.minecraft.entity.player.EntityPlayer"),int.class,int.class,int.class,int.class,int.class).invoke(world,actor,1003,1,64,2,0);
            var door=sounds.get(3);
            if(!Set.of("random.door_open","random.door_close").contains(door.id())||door.x()!=1.5||door.y()!=64.5||door.pitch()<.9f||door.pitch()>=1||!new UUID(1,2).equals(door.excludedPlayer()))throw new AssertionError("Door auxiliary event lost position, pitch or excluded player: "+door);
            worldType.getMethod("func_72926_e",int.class,int.class,int.class,int.class,int.class).invoke(world,2004,1,64,2,0);
            actor.getClass().getMethod("func_71038_i").invoke(actor);
            if(!effects.equals(List.of("2004:1:64:2","swing:"+new UUID(1,2))))throw new AssertionError("Old particle/swing effect entered the absent legacy tracker: "+effects);
            if(!loader.loadClass("net.minecraft.entity.EntityLivingBase").getField("field_82175_bq").getBoolean(actor))throw new AssertionError("Swing bridge skipped the original animation state");
            report.put("genericAuxiliaryEventsAndSwingVerified",true);
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
