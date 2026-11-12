package github.com.gengyoubo.mfcl.content;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Dedicated-server regression for saved and re-enqueued deferred host-dimension population. */
final class WorldgenQueueHostSmokeProbe {
    private final ModernContentBridge content;private boolean done;
    private WorldgenQueueHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new WorldgenQueueHostSmokeProbe(content)::tick);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    @SuppressWarnings("unchecked")
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;done=true;
        var report=new LinkedHashMap<String,Object>();var level=event.getServer().overworld();
        long chunk=ChunkPos.asLong(20000,-20000),unrelated=ChunkPos.asLong(20002,-20000);
        LegacyDimensionHost.Populated data=null;
        try {
            var get=LegacyDimensionHost.class.getDeclaredMethod("populated",ServerLevel.class);get.setAccessible(true);
            data=(LegacyDimensionHost.Populated)get.invoke(null,level);
            check(!data.chunks.contains(chunk)&&!data.waiting.contains(chunk),"Fixture chunk already exists");
            var enqueue=LegacyDimensionHost.class.getDeclaredMethod("enqueue",ServerLevel.class,long.class,boolean.class);enqueue.setAccessible(true);
            var tick=LegacyDimensionHost.class.getDeclaredMethod("serverTick",TickEvent.ServerTickEvent.class);tick.setAccessible(true);
            var field=LegacyDimensionHost.class.getDeclaredField("pending");field.setAccessible(true);
            var first=new LegacyDimensionHost(content);enqueue.invoke(first,level,chunk,true);
            var pending=(Map<ServerLevel,LongLinkedOpenHashSet>)field.get(first);
            check(data.waiting.contains(chunk)&&pending.get(level).contains(chunk),"New chunk not saved and queued");
            check(!LegacyDimensionHost.neighborsReady(20000,-20000,(x,z)->level.getChunkSource().getChunkNow(x,z)!=null),"Unloaded neighbors reported ready");
            tick.invoke(first,new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,event.getServer()));
            check(!pending.get(level).contains(chunk)&&data.waiting.contains(chunk)&&!data.chunks.contains(chunk),"Unloaded chunk lost its waiting marker");
            var saved=data.save(new CompoundTag());var restored=LegacyDimensionHost.Populated.load(saved);
            check(restored.waiting.contains(chunk)&&!restored.chunks.contains(chunk),"Deferred marker failed save/load");
            var reloaded=new LegacyDimensionHost(content);enqueue.invoke(reloaded,level,chunk,false);enqueue.invoke(reloaded,level,unrelated,false);
            var after=(Map<ServerLevel,LongLinkedOpenHashSet>)field.get(reloaded);
            check(after.get(level).contains(chunk)&&!after.get(level).contains(unrelated),"Only saved deferred chunks should be re-enqueued");
            // Let a genuinely loaded 3x3 area proceed through the normal scheduler, using a tracked host player.
            long ready=Long.MIN_VALUE;
            for(int x=-5;x<=5&&ready==Long.MIN_VALUE;x++)for(int z=-5;z<=5;z++){
                long candidate=ChunkPos.asLong(x,z);
                if(!data.chunks.contains(candidate)&&LegacyDimensionHost.neighborsReady(x,z,(a,b)->level.getChunkSource().getChunkNow(a,b)!=null)){ready=candidate;break;}
            }
            check(ready!=Long.MIN_VALUE,"No loaded 3x3 fixture available");
            int cx=ChunkPos.getX(ready),cz=ChunkPos.getZ(ready);
            var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
            player.setPos((cx<<4)+8.5,100,(cz<<4)+8.5);level.addNewPlayer(player);
            check(!level.getChunkSource().chunkMap.getPlayers(new ChunkPos(ready),false).isEmpty(),"Fixture player not tracked for candidate");
            enqueue.invoke(reloaded,level,ready,true);
            for(int i=0;i<20&&!data.chunks.contains(ready);i++)tick.invoke(reloaded,new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,event.getServer()));
            check(data.chunks.contains(ready)&&!data.waiting.contains(ready),"Ready candidate was not processed");
            level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            report.put("newChunkPersisted",true);report.put("unloadedChunkNotMarkedComplete",true);report.put("savedPendingRestored",true);report.put("previousChunkReloadRequeued",true);report.put("loadedCandidateProcessed",true);report.put("status","PASS");
        }catch(Throwable failure){report.put("status","FAIL");report.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL worldgen queue smoke failed",failure);}
        finally{if(data!=null){data.waiting.remove(chunk);data.setDirty();}}
        try{var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/worldgen-queue-smoke.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){throw new IllegalStateException(failure);}
        event.getServer().halt(false);
    }
}
