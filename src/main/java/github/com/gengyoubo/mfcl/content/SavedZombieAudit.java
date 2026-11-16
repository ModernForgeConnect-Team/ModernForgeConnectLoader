package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Diagnostic only: run against a disposable copy of the user's world, never their live save. */
final class SavedZombieAudit {
    private final ModernContentBridge content;
    private final List<Map<String,Object>> samples=new ArrayList<>();
    private LegacyHostedEntity zombie;
    private Villager victim;
    private int tick; private boolean done;
    private SavedZombieAudit(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new SavedZombieAudit(content)::tick);}
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();var level=server.overworld();
        try {
            if(tick==0){level.setChunkForced(-11,2,true);level.setChunkForced(-12,3,true);}
            if(zombie==null){
                for(Entity e:level.getAllEntities())if(e instanceof LegacyHostedEntity host && host.legacyOrNull()!=null
                        && host.legacyOrNull().getClass().getName().equals("thaumcraft.common.entities.monster.EntityBrainyZombie")){zombie=host;break;}
                if(zombie==null){if(++tick>200)throw new IllegalStateException("Saved original zombie not loaded");return;}
                victim=EntityType.VILLAGER.create(level);victim.setNoAi(true);victim.setPersistenceRequired();
                victim.moveTo(zombie.getX()+5,zombie.getY(),zombie.getZ(),0,0);level.addFreshEntity(victim);tick=1;
            }
            if(tick%10==0)sample(level);
            if(++tick<301&&!zombie.isRemoved()&&victim.isAlive())return;
            var report=new LinkedHashMap<String,Object>();report.put("samples",samples);report.put("targetHealth",victim.getHealth());report.put("zombieRemoved",zombie.isRemoved());
            var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/saved-zombie-audit.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            LogUtils.getLogger().info("MFCL_SAVED_ZOMBIE_AUDIT: {}",report);done=true;server.halt(false);
        }catch(Throwable failure){done=true;LogUtils.getLogger().error("MFCL_SAVED_ZOMBIE_AUDIT failed",failure);server.halt(false);}
    }
    private void sample(ServerLevel level)throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("tick",tick);row.put("position",List.of(zombie.getX(),zombie.getY(),zombie.getZ()));row.put("yaw",zombie.getYRot());row.put("victimHealth",victim.getHealth());
        content.services().items().inLegacy(()->{
            Object old=zombie.legacyOrNull();if(old==null){row.put("legacy","absent");return null;}
            var loader=content.services().loader();var living=loader.loadClass("net.minecraft.entity.EntityLiving");
            Object target=living.getMethod("func_70638_az").invoke(old);row.put("target",target==null?"none":target.getClass().getName());
            Object nav=living.getMethod("func_70661_as").invoke(old);row.put("noPath",nav.getClass().getMethod("func_75500_f").invoke(nav));
            for(String f:List.of("field_70714_bg","field_70715_bh")){
                Object tasks=living.getField(f).get(old);var names=new ArrayList<String>();
                for(Object entry:(List<?>)tasks.getClass().getField("field_75782_a").get(tasks))names.add(entry.getClass().getField("field_75733_a").get(entry).getClass().getSimpleName());
                row.put(f,names);
            }
            return null;
        });samples.add(row);
    }
}
