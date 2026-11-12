package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Original structure spawner selection through real host spawner ticking and save/load. */
final class SpawnerHostSmokeProbe {
    private final ModernContentBridge content;private boolean done;
    private SpawnerHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new SpawnerHostSmokeProbe(content)::tick);}
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;done=true;
        var report=new LinkedHashMap<String,Object>();var level=event.getServer().overworld();
        try {
            var pos=new BlockPos(0,180,0);level.getChunkAt(pos);
            for(int x=-2;x<=2;x++)for(int y=-2;y<=3;y++)for(int z=-2;z<=2;z++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            level.setBlockAndUpdate(pos,Blocks.SPAWNER.defaultBlockState());
            var tile=(SpawnerBlockEntity)level.getBlockEntity(pos);var services=content.services();var loader=services.loader();var world=services.worlds().bound(level);
            services.items().inLegacy(()->{
                var oldWorld=loader.loadClass("net.minecraft.world.World");Object old=oldWorld.getMethod("func_147438_o",int.class,int.class,int.class).invoke(world,pos.getX(),pos.getY(),pos.getZ());
                Object logic=loader.loadClass("net.minecraft.tileentity.TileEntityMobSpawner").getMethod("func_145881_a").invoke(old);
                loader.loadClass("net.minecraft.tileentity.MobSpawnerBaseLogic").getMethod("func_98272_a",String.class).invoke(logic,"Thaumcraft.Wisp");return null;
            });
            var saved=tile.saveWithoutMetadata();var entity=saved.getCompound("SpawnData").getCompound("entity");
            check(entity.getString("id").equals("mfcl:legacy_entity"),"Wrong native spawner entity: "+entity);
            check(entity.getString("LegacySpawnerName").equals("Thaumcraft.Wisp"),"Original spawner identity lost");
            check(entity.getString("LegacyType").equals("thaumcraft.common.entities.monster.EntityWisp"),"Wrong original entity class");
            check(entity.getByteArray("LegacyNbt").length>0&&entity.getBoolean("LegacySpawnerTemplate"),"Missing original spawn template");
            saved.putShort("Delay",(short)0);saved.putShort("SpawnCount",(short)1);saved.putShort("SpawnRange",(short)0);tile.load(saved);
            var player=FakePlayerFactory.getMinecraft(level);player.setPos(0.5,180,2.5);level.addNewPlayer(player);
            for(int i=0;i<10&&level.getEntitiesOfClass(LegacyHostedEntity.class,new AABB(pos).inflate(8)).isEmpty();i++)tile.getSpawner().serverTick(level,pos);
            var entities=level.getEntitiesOfClass(LegacyHostedEntity.class,new AABB(pos).inflate(8));check(!entities.isEmpty(),"Native spawner created no hosted entity");
            var hosted=entities.get(0);double x=hosted.getX(),y=hosted.getY(),z=hosted.getZ();hosted.tick();
            check(!hosted.isRemoved()&&hosted.legacyOrNull()!=null,"Original wisp did not initialize");
            check(hosted.legacyOrNull().getClass().getName().equals("thaumcraft.common.entities.monster.EntityWisp"),"Spawner created wrong original entity");
            check(hosted.distanceToSqr(x,y,z)<64,"Original wisp was restored at template coordinates instead of the spawner");
            report.put("nativeSpawnerSaveLoad",true);report.put("originalWispSpawnedAndTicked",true);report.put("spawnPositionPreserved",true);
            entities.forEach(e->e.discard());level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());report.put("status","PASS");
        }catch(Throwable failure){report.put("status","FAIL");report.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL original spawner smoke failed",failure);}
        try{var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/spawner-smoke.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){throw new IllegalStateException(failure);}
        event.getServer().halt(false);
    }
}
