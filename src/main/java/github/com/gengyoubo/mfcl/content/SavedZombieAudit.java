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
            if(tick==0){
                int x=Integer.getInteger("mfcl.test.zombieAuditChunkX",level.getSharedSpawnPos().getX()>>4);
                int z=Integer.getInteger("mfcl.test.zombieAuditChunkZ",level.getSharedSpawnPos().getZ()>>4);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.setChunkForced(x+dx,z+dz,true);
            }
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
            report.put("creativeTransition",creativeTransition(level));
            report.put("combat",combat(level));
            report.put("spawnEggAndOrb",spawnEggAndOrb(level));
            var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/saved-zombie-audit.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
            LogUtils.getLogger().info("MFCL_SAVED_ZOMBIE_AUDIT: {}",report);done=true;server.halt(false);
        }catch(Throwable failure){done=true;LogUtils.getLogger().error("MFCL_SAVED_ZOMBIE_AUDIT failed",failure);server.halt(false);}
    }
    private Map<String,Object> spawnEggAndOrb(ServerLevel level)throws Exception {
        return content.services().items().inLegacy(()->{
            var services=content.services();var loader=services.loader();var items=services.items();
            Class<?> egg=loader.loadClass("thaumcraft.common.entities.ItemSpawnerEgg");var list=egg.getDeclaredField("spawnList");list.setAccessible(true);
            var entries=(List<?>)list.get(null);int meta=-1;
            for(int i=0;i<entries.size();i++){Object entry=entries.get(i);var field=entry.getClass().getDeclaredField("name");field.setAccessible(true);if("Thaumcraft.EldritchGolem".equals(field.get(entry))){meta=i;break;}}
            if(meta<0)throw new IllegalStateException("Eldritch construct egg missing");
            var stack=new github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack("Thaumcraft:ItemSpawnerEgg",meta,1,"","");
            String name=items.displayName(stack);var tooltip=items.tooltip(stack,null,false);
            if(name.contains("%s")||tooltip.get(0).contains("%s")||!items.isSpawnEgg(stack))throw new IllegalStateException("Egg display or classification failed: "+name+" / "+tooltip);
            // Dedicated servers do not initialize client translations: exercise the actual TC4 placeholder explicitly.
            Object original=items.materialize(stack);var fix=items.getClass().getDeclaredMethod("eggName",Object.class,String.class);fix.setAccessible(true);
            String corrected=(String)fix.invoke(items,original,"Spawn %s Eldritch Construct");
            if(!corrected.equals("Spawn Eldritch Construct"))throw new IllegalStateException("Egg format placeholder remained: "+corrected);
            Object renamed=items.copyLegacyStack(original);loader.loadClass("net.minecraft.item.ItemStack").getMethod("func_151001_c",String.class).invoke(renamed,"Custom %s");
            if(!"Custom %s".equals(fix.invoke(items,renamed,"Custom %s")))throw new IllegalStateException("Custom name was altered");
            var tab=content.declarations().tabs().stream().filter(t->t.builtin()&&t.contents().stream().anyMatch(s->s.key().equals(stack.key())&&s.metadata()==stack.metadata())).findFirst().orElseThrow();
            var target=content.creative().target(tab,content.mapStack(stack));
            if(target!=net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS.location()))throw new IllegalStateException("Egg not routed to spawn eggs");
            var snapshot=new github.com.gengyoubo.mfcl.bridge.HostWorldAccess.Entity(UUID.randomUUID(),"net.minecraft.client.entity.EntityClientPlayerMP","Audit",zombie.getX(),zombie.getY(),zombie.getZ(),0,0,0,0,.6f,1.8f,false,false);
            Class<?> access=Class.forName("github.com.gengyoubo.mfcl.bridge.LegacyEntityAccess");
            var mirror=access.getDeclaredMethod("mirror",github.com.gengyoubo.mfcl.bridge.LegacyClientData.class,Object.class,github.com.gengyoubo.mfcl.bridge.HostWorldAccess.Entity.class,Object.class);mirror.setAccessible(true);
            Object player=mirror.invoke(null,items,services.worlds().bound(level),snapshot,null);
            var aspect=loader.loadClass("thaumcraft.api.aspects.Aspect");Object air=aspect.getField("AIR").get(null);
            var check=loader.loadClass("thaumcraft.common.lib.utils.InventoryUtils").getMethod("isWandInHotbarWithRoom",aspect,int.class,loader.loadClass("net.minecraft.entity.player.EntityPlayer"));
            int empty=(int)check.invoke(null,air,1,player);var hotbar=new ArrayList<github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack>(Collections.nCopies(36,null));
            hotbar.set(5,new github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack("Thaumcraft:WandCasting",0,1,"","{rod:\"wood\",cap:\"iron\"}"));services.playerInventory().write(player,hotbar);
            int withWand=(int)check.invoke(null,air,1,player);
            if(empty!=-1||withWand!=5)throw new IllegalStateException("Orb hotbar lookup failed: "+empty+" / "+withWand);
            var translate=loader.loadClass("net.minecraft.util.StatCollector").getMethod("func_74838_a",String.class);
            String english=(String)translate.invoke(null,"entity.Thaumcraft.CultistLeader.name");
            String formatted=String.format(java.util.Locale.ROOT,english,"Alberic","Fierce");
            if(!formatted.equals("Praetor Alberic the Fierce"))throw new IllegalStateException("Server lost champion name arguments: "+formatted);
            var oldLiving=loader.loadClass("net.minecraft.entity.EntityLiving");
            oldLiving.getMethod("func_94058_c",String.class).invoke(zombie.legacyOrNull(),formatted);
            if(!zombie.getName().getString().equals(formatted))throw new IllegalStateException("Hosted entity name was not transferred: "+zombie.getName().getString());
            items.language("zh_cn");
            try {
                String chinese=(String)translate.invoke(null,"entity.Thaumcraft.CultistLeader.name");
                if(!String.format(java.util.Locale.ROOT,chinese,"Alberic","凶暴").equals("血腥主教 Alberic，凶暴"))throw new IllegalStateException("Chinese champion name arguments were lost: "+chinese);
                String eggName=items.displayName(stack);
                if(!eggName.contains("远古构装体")||eggName.contains("%"))throw new IllegalStateException("Chinese egg name failed: "+eggName);
            } finally { items.language("en_us"); }
            return Map.of("metadata",meta,"displayName",name,"tooltip",tooltip.get(0),"placeholderName",corrected,"customNamePreserved",true,"spawnEggTab",true,"emptyHotbar",empty,"wandSlot",withWand);
        });
    }
    private List<Map<String,Object>> combat(ServerLevel level)throws Exception {
        var results=new ArrayList<Map<String,Object>>();var loader=content.services().loader();var world=content.services().worlds().bound(level);
        var attacker=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);attacker.moveTo(zombie.getX(),zombie.getY(),zombie.getZ(),0,0);
        for(String name:List.of("thaumcraft.common.entities.monster.EntityMindSpider","thaumcraft.common.entities.monster.EntityTaintChicken","thaumcraft.common.entities.monster.boss.EntityTaintacleGiant")){
            var row=new LinkedHashMap<String,Object>();row.put("class",name);
            try {
                Object original=content.services().items().inLegacy(()->{
                    Object value=loader.loadClass(name).getConstructor(loader.loadClass("net.minecraft.world.World")).newInstance(world);
                    content.services().entityHosting().position(value,zombie.getX()+2,zombie.getY(),zombie.getZ());
                    loader.loadClass("net.minecraft.entity.EntityLivingBase").getMethod("func_70606_j",float.class).invoke(value,1f);return value;
                });
                var host=LegacyHostedEntity.host(content,level,original);level.addFreshEntity(host);
                row.put("hurt",host.hurt(level.damageSources().playerAttack(attacker),10f));row.put("removedImmediately",host.isRemoved());
                if(!host.isRemoved()){
                    host.tick();
                    row.put("deathTime",loader.loadClass("net.minecraft.entity.EntityLivingBase").getField("field_70725_aQ").getInt(original));
                    row.put("removedAfterTick",host.isRemoved());
                }
                host.discard();
            }catch(Throwable failure){while(failure.getCause()!=null)failure=failure.getCause();row.put("error",failure.toString());}
            results.add(row);
        }
        return results;
    }
    private Map<String,Object> creativeTransition(ServerLevel level)throws Exception {
        return content.services().items().inLegacy(()->{
            var loader=content.services().loader();var uuid=UUID.randomUUID();var world=content.services().worlds().bound(level);
            var survival=new github.com.gengyoubo.mfcl.bridge.LegacyTiles.Placer(uuid,"Audit",zombie.getX()+1,zombie.getY(),zombie.getZ(),0,0,false,false);
            var creative=new github.com.gengyoubo.mfcl.bridge.LegacyTiles.Placer(uuid,"Audit",zombie.getX()+1,zombie.getY(),zombie.getZ(),0,0,true,false);
            Object player=content.itemUse().clientActor(world,survival,null);var creature=loader.loadClass("net.minecraft.entity.EntityCreature");
            var livingBase=loader.loadClass("net.minecraft.entity.EntityLivingBase");var living=loader.loadClass("net.minecraft.entity.EntityLiving");
            living.getMethod("func_70624_b",livingBase).invoke(zombie.legacyOrNull(),player);
            Object goal=loader.loadClass("net.minecraft.entity.ai.EntityAIHurtByTarget").getConstructor(creature,boolean.class).newInstance(zombie.legacyOrNull(),false);
            boolean before=(boolean)goal.getClass().getMethod("func_75253_b").invoke(goal);
            Object updated=content.itemUse().clientActor(world,creative,null);
            boolean after=(boolean)goal.getClass().getMethod("func_75253_b").invoke(goal);
            return Map.of("sameActor",player==updated,"continuesInSurvival",before,"continuesInCreative",after);
        });
    }
    private void sample(ServerLevel level)throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("tick",tick);row.put("position",List.of(zombie.getX(),zombie.getY(),zombie.getZ()));row.put("yaw",zombie.getYRot());row.put("victimHealth",victim.getHealth());
        content.services().items().inLegacy(()->{
            Object old=zombie.legacyOrNull();if(old==null){row.put("legacy","absent");return null;}
            var loader=content.services().loader();var living=loader.loadClass("net.minecraft.entity.EntityLiving");
            Object target=living.getMethod("func_70638_az").invoke(old);row.put("target",target==null?"none":target.getClass().getName());
            Object nav=living.getMethod("func_70661_as").invoke(old);row.put("noPath",nav.getClass().getMethod("func_75500_f").invoke(nav));
            var entity=loader.loadClass("net.minecraft.entity.Entity");
            row.put("brightness",entity.getMethod("func_70013_c",float.class).invoke(old,1f));
            row.put("legacyBurning",entity.getMethod("func_70027_ad").invoke(old));
            for(String f:List.of("field_70714_bg","field_70715_bh")){
                Object tasks=living.getField(f).get(old);var names=new ArrayList<String>();
                for(Object entry:(List<?>)tasks.getClass().getField("field_75782_a").get(tasks))names.add(entry.getClass().getField("field_75733_a").get(entry).getClass().getSimpleName());
                row.put(f,names);
            }
            return null;
        });samples.add(row);
    }
}
