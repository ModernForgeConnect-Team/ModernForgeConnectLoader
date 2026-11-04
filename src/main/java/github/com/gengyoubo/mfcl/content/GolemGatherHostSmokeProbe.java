package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

/**
 * Natural Gather-core work: real original AI on hosted golems, real host drops and chests. After installing the core the
 * probe never touches a task; it only samples the items in each isolated site every tick. The invariant is that the
 * counted items on the ground, in the site's chests and carried by its golems stay exactly constant, so both copying
 * and losing an item fail at the tick they happen. Every site always runs, so one report documents all of them.
 */
final class GolemGatherHostSmokeProbe {
    private static final int OBSERVATION=1500,CORE=2,CARRY=4;
    private static final String ESSENCE_TAG="{Aspects:[{key:aer,amount:3}]}";
    private static final Predicate<ItemStack> DIAMONDS=stack->stack.is(Items.DIAMOND),ANY=stack->true;
    private final ModernContentBridge content;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Site> sites=new ArrayList<>();
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(6);
    private net.minecraftforge.common.util.FakePlayer player;
    private ServerLevel level;
    private long started=-1;
    private boolean done;
    private GolemGatherHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new GolemGatherHostSmokeProbe(content)::tick);}

    /** One isolated work area. Expected final counts of -1 are not asserted: only conservation applies to that site. */
    private final class Site {
        final String name;final BlockPos base;final Predicate<ItemStack> counted;final List<BlockPos> chests=new ArrayList<>();
        final List<LegacyHostedEntity> golems=new ArrayList<>();final List<ItemEntity> drops=new ArrayList<>();final Map<String,Object> notes=new LinkedHashMap<>();
        int expectedTotal,expectedChest=-1,expectedGround=-1,expectedCarried=-1,maxTotal=Integer.MIN_VALUE,minTotal=Integer.MAX_VALUE,ground,chest,carried;
        long firstViolation=-1;boolean partialSeen,eventDone;int initialGround;long eventTick=-1;
        java.util.function.Consumer<Site> placeDrops=site->{};
        EventRule rule;
        Site(String name,Predicate<ItemStack> counted){this.name=name;this.counted=counted;int i=sites.size();base=new BlockPos(128+(i%4)*48,99,128+(i/4)*48);chests.add(base);sites.add(this);}
        Site expect(int chest,int ground,int carried){expectedChest=chest;expectedGround=ground;expectedCarried=carried;return this;}
        Site place(java.util.function.Consumer<Site> placeDrops){this.placeDrops=placeDrops;return this;}
        Site on(EventRule rule){this.rule=rule;return this;}
        ItemEntity drop(double dx,double dz,ItemStack stack){
            var entity=new ItemEntity(level,base.getX()+dx+.5,100.1,base.getZ()+dz+.5,stack);entity.setDeltaMovement(Vec3.ZERO);entity.setNoPickUpDelay();
            if(!level.addFreshEntity(entity))throw new IllegalStateException("Drop was not spawned in "+name);drops.add(entity);return entity;
        }
    }
    /** Runs once per tick until it returns true: the only way a site changes state from outside the original AI. */
    @FunctionalInterface private interface EventRule{boolean run(Site site,long tick)throws Exception;}

    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();level=server.overworld();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Gather probe timed out");
            if(sites.isEmpty()){setup();return;}
            if(started<0){
                // A forced distant chunk can hold the entity before ServerLevel's UUID lookup exposes it to interactOn.
                for(var site:sites)for(var golem:site.golems)if(level.getEntity(golem.getUUID())!=golem)return;
                configure();started=level.getGameTime();return;
            }
            long tick=level.getGameTime()-started;
            for(var site:sites)sample(site,tick);
            if(tick<OBSERVATION)return;
            verify();finish(server,null);
        }catch(Throwable failure){
            report.put("status","FAIL");report.put("failure",failure.toString());finish(server,failure);
        }
    }

    private void setup()throws Exception{
        level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        player=FakePlayerFactory.getMinecraft(level);player.getInventory().clearContent();player.getInventory().selected=4;level.addNewPlayer(player);
        new Site("basic",DIAMONDS).expect(3,0,0).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,3)));
        new Site("multiTrip",DIAMONDS).expect(10,0,0).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,10)));
        new Site("filter",DIAMONDS.or(stack->stack.is(Items.COBBLESTONE))).expect(5,5,0).place(s->{s.drop(5,0,new ItemStack(Items.DIAMOND,5));s.drop(-5,0,new ItemStack(Items.COBBLESTONE,5));});
        // The carried stack cannot go anywhere and the two left on the ground no longer fit: nothing may change after the first load.
        new Site("chestFull",DIAMONDS).expect(0,2,CARRY).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,6)));
        // Two free places in a filled chest: partial placement, then a second partial load that fills the golem.
        new Site("chestAlmostFull",DIAMONDS).expect(64,0,CARRY).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,6)));
        new Site("targetRemoved",DIAMONDS).expect(0,0,0).place(s->s.drop(7,0,new ItemStack(Items.DIAMOND,4))).on((site,tick)->{
            if(!targeted(site))return false;
            site.drops.get(0).discard();site.expectedTotal=0;site.notes.put("removedWhileTargeted",tick);return true;
        });
        new Site("targetShrunk",DIAMONDS).expect(1,0,0).place(s->s.drop(7,0,new ItemStack(Items.DIAMOND,4))).on((site,tick)->{
            if(!targeted(site))return false;
            site.drops.get(0).setItem(new ItemStack(Items.DIAMOND,1));site.expectedTotal=1;site.notes.put("shrunkWhileTargeted",tick);return true;
        });
        var race=new Site("twoGolems",DIAMONDS).expect(16,0,0).place(s->s.drop(3,0,new ItemStack(Items.DIAMOND,16)));race.chests.add(race.base.east(6));
        new Site("killedCarrying",DIAMONDS).expect(0,6,0).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,6))).on((site,tick)->{
            var golem=site.golems.get(0);if(golem.isRemoved()){site.notes.put("golemRemoved",true);return true;}
            if(site.carried!=CARRY)return false;
            if(site.eventTick<0)site.eventTick=tick;
            if((tick-site.eventTick)%20==0)golem.hurt(level.damageSources().generic(),1000f);
            return false;
        });
        new Site("reloadCarrying",DIAMONDS).expect(6,0,0).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,6))).on((site,tick)->{
            if(site.eventTick<0){
                if(site.carried!=CARRY)return false;
                site.eventTick=tick;var old=site.golems.get(0);
                var saved=old.saveWithoutId(new CompoundTag());old.discard();
                var restored=new LegacyHostedEntity(content.legacyEntityType(),level);restored.load(saved);restored.setUUID(UUID.randomUUID());
                if(!level.addFreshEntity(restored))throw new IllegalStateException("Reloaded golem was not spawned");
                // The envelope restores its original lazily on the first tick; until then nothing can report the carried stack.
                restored.tick();site.golems.set(0,restored);return false;
            }
            if(tick-site.eventTick<5)return false;
            site.notes.put("carriedAfterReload",site.carried);
            clearFiller(site.base);return true;
        });
        new Site("originalNbt",ANY).expect(6,0,0).place(s->s.drop(5,0,content.stack("Thaumcraft:ItemEssence",1,6,ESSENCE_TAG)));
        new Site("hostOnlyNbt",ANY).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,3).setHoverName(Component.literal("gather probe"))));
        new Site("noOriginalForm",ANY).place(s->s.drop(5,0,new ItemStack(Items.NETHERITE_INGOT,3)));
        new Site("neverPickup",DIAMONDS).expect(0,3,0).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,3)).setNeverPickUp());
        new Site("pickupDelay",DIAMONDS).expect(3,0,0).place(s->s.drop(5,0,new ItemStack(Items.DIAMOND,3)).setPickUpDelay(400)).on((site,tick)->{
            if(tick<350)return false;
            site.notes.put("untouchedWhileDelayed",site.chest==0&&site.carried==0&&site.ground==3);return true;
        });
        new Site("hostMerge",DIAMONDS).expect(6,0,0).place(s->{s.drop(6,0,new ItemStack(Items.DIAMOND,3));s.drop(6.2,0,new ItemStack(Items.DIAMOND,3));});
        var services=content.services();var loader=services.loader();var world=services.worlds().bound(level);
        var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");var material=loader.loadClass("thaumcraft.common.entities.golems.EnumGolemType");var wood=material.getEnumConstants()[1];
        for(var site:sites){
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
                var at=site.base.offset(x,0,z);level.setChunkForced(at.getX()>>4,at.getZ()>>4,true);level.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
            }
            for(var at:site.chests){
                level.setBlockAndUpdate(at,Blocks.CHEST.defaultBlockState());
                Object original=services.items().inLegacy(()->{
                    Object golem=type.getConstructor(loader.loadClass("net.minecraft.world.World"),material,boolean.class).newInstance(world,wood,false);
                    type.getMethod("func_70107_b",double.class,double.class,double.class).invoke(golem,at.getX()+.5,100d,at.getZ()+.5);
                    type.getMethod("func_110171_b",int.class,int.class,int.class,int.class).invoke(golem,at.getX(),100,at.getZ(),16);
                    type.getField("homeFacing").setInt(golem,1);type.getMethod("setOwner",String.class).invoke(golem,player.getName().getString());
                    type.getMethod("setupGolem").invoke(golem);return golem;
                });
                var host=LegacyHostedEntity.host(content,level,original);
                if(!level.addFreshEntity(host))throw new IllegalStateException("Golem was not spawned in "+site.name);site.golems.add(host);
            }
        }
        for(var name:List.of("chestFull","killedCarrying","reloadCarrying"))fill(site(name).base,0);
        fill(site("chestAlmostFull").base,62);
        LogUtils.getLogger().info("MFCL_GOLEM_GATHER_TERRAIN_READY");
    }
    private Site site(String name){return sites.stream().filter(site->site.name.equals(name)).findFirst().orElseThrow();}
    private void fill(BlockPos pos,int diamonds){
        var chest=(ChestBlockEntity)level.getBlockEntity(pos);
        for(int slot=0;slot<chest.getContainerSize();slot++)chest.setItem(slot,new ItemStack(Blocks.STONE,64));
        if(diamonds>0)chest.setItem(0,new ItemStack(Items.DIAMOND,diamonds));
    }
    private void clearFiller(BlockPos pos){
        var chest=(ChestBlockEntity)level.getBlockEntity(pos);
        for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Blocks.STONE.asItem()))chest.setItem(slot,ItemStack.EMPTY);
    }
    /** The real core-insertion path, then the drops: no golem can act before its site is complete. */
    private void configure()throws Exception{
        var services=content.services();var loader=services.loader();var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");
        for(var site:sites){
            for(var golem:site.golems){
                player.setPos(golem.getX()+1,100,golem.getZ());
                player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemGolemCore",CORE,2,""));
                if(!player.interactOn(golem,InteractionHand.MAIN_HAND).consumesAction())throw new IllegalStateException("Core not accepted in "+site.name);
                if(player.getMainHandItem().getCount()!=1)throw new IllegalStateException("Core not consumed once in "+site.name);
                Object original=golem.legacyOrNull();
                byte core=services.items().inLegacy(()->(byte)type.getMethod("getCore").invoke(original));
                if(core!=CORE)throw new IllegalStateException("Core not installed in "+site.name+": "+core);
            }
            site.placeDrops.accept(site);
        }
        // Only the diamond filter separates this site from "take everything" (an all-empty original inventory).
        Object filtered=site("filter").golems.get(0).legacyOrNull();
        services.items().inLegacy(()->{
            Object inventory=type.getField("inventory").get(filtered);
            inventory.getClass().getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(inventory,0,services.items().materialize(content.gameplayStack(new ItemStack(Items.DIAMOND,16))));
            return null;
        });
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.discard();
        for(var site:sites){count(site);site.expectedTotal=site.ground+site.chest+site.carried;site.initialGround=site.ground;site.notes.put("initialTotal",site.expectedTotal);}
        LogUtils.getLogger().info("MFCL_GOLEM_GATHER_SETUP");
    }
    /** Whether the golem's original pickup task has held on to its target for at least one earlier tick. */
    private boolean targeted(Site site)throws Exception{
        Object golem=site.golems.get(0).legacyOrNull();
        return content.services().items().inLegacy(()->{
            Object tasks=golem.getClass().getField("field_70714_bg").get(golem);
            for(Object entry:(List<?>)tasks.getClass().getField("field_75782_a").get(tasks)){
                Object task=entry.getClass().getField("field_75733_a").get(entry);
                if(!task.getClass().getSimpleName().equals("AIItemPickup"))continue;
                var target=task.getClass().getDeclaredField("targetEntity");target.setAccessible(true);
                if(target.get(task)==null){site.notes.remove("targetSeenAt");return false;}
                boolean held=site.notes.containsKey("targetSeenAt");site.notes.putIfAbsent("targetSeenAt",level.getGameTime());return held;
            }
            return false;
        });
    }
    private void count(Site site)throws Exception{
        int ground=0,chest=0,carried=0;
        for(var drop:level.getEntitiesOfClass(ItemEntity.class,new AABB(site.base).inflate(16),item->!item.isRemoved()))if(site.counted.test(drop.getItem()))ground+=drop.getItem().getCount();
        for(var at:site.chests)if(level.getBlockEntity(at) instanceof ChestBlockEntity box)for(int slot=0;slot<box.getContainerSize();slot++)if(site.counted.test(box.getItem(slot)))chest+=box.getItem(slot).getCount();
        for(var golem:site.golems)carried+=carried(site,golem);
        site.ground=ground;site.chest=chest;site.carried=carried;
    }
    /** A dying golem has already dropped its load; the original keeps the field set until the entity is gone. */
    private int carried(Site site,LegacyHostedEntity golem)throws Exception{
        Object original=golem.legacyOrNull();if(golem.isRemoved()||original==null)return 0;
        var services=content.services();
        return services.items().inLegacy(()->{
            if((float)original.getClass().getMethod("func_110143_aJ").invoke(original)<=0)return 0;
            Object stack=original.getClass().getMethod("getCarried").invoke(original);if(stack==null)return 0;
            var snapshot=services.items().snapshot(stack);
            return site.counted.test(content.gameplayStack(snapshot))?snapshot.count():0;
        });
    }
    private void sample(Site site,long tick)throws Exception{
        count(site);
        if(!site.eventDone&&site.rule!=null){site.eventDone=site.rule.run(site,tick);count(site);}
        if(site.ground>0&&site.ground<site.initialGround&&site.expectedTotal==site.initialGround)site.partialSeen=true;
        int total=site.ground+site.chest+site.carried;
        site.maxTotal=Math.max(site.maxTotal,total);site.minTotal=Math.min(site.minTotal,total);
        if(total!=site.expectedTotal&&site.firstViolation<0){
            site.firstViolation=tick;site.notes.put("firstViolation",Map.of("tick",tick,"expected",site.expectedTotal,"ground",site.ground,"chest",site.chest,"carried",site.carried));
            LogUtils.getLogger().warn("MFCL_GOLEM_GATHER_VIOLATION {} at tick {}: expected {} found ground={} chest={} carried={}",site.name,tick,site.expectedTotal,site.ground,site.chest,site.carried);
        }
    }
    private void verify()throws Exception{
        var rows=new LinkedHashMap<String,Object>();var failures=new ArrayList<String>();
        for(var site:sites){
            var row=new LinkedHashMap<String,Object>(site.notes);count(site);
            row.put("ground",site.ground);row.put("chest",site.chest);row.put("carried",site.carried);row.put("expectedTotal",site.expectedTotal);row.put("maxTotal",site.maxTotal);row.put("minTotal",site.minTotal);row.put("partialGroundSeen",site.partialSeen);
            row.put("golemsAlive",site.golems.stream().filter(golem->!golem.isRemoved()).count());
            rows.put(site.name,row);
            if(site.firstViolation>=0)failures.add(site.name+": item count left "+site.expectedTotal+" at tick "+site.firstViolation+" (min "+site.minTotal+", max "+site.maxTotal+")");
            if(site.expectedChest>=0&&(site.chest!=site.expectedChest||site.ground!=site.expectedGround||site.carried!=site.expectedCarried))
                failures.add(site.name+": final chest/ground/carried "+site.chest+"/"+site.ground+"/"+site.carried+", expected "+site.expectedChest+"/"+site.expectedGround+"/"+site.expectedCarried);
        }
        extra(failures,"multiTrip",site->site.partialSeen,"carry limit never left a partial stack on the ground");
        extra(failures,"filter",site->((ChestBlockEntity)level.getBlockEntity(site.base)).countItem(Items.COBBLESTONE)==0,"filter let cobblestone through");
        extra(failures,"killedCarrying",site->Boolean.TRUE.equals(site.notes.get("golemRemoved")),"golem was not killed");
        extra(failures,"reloadCarrying",site->Integer.valueOf(CARRY).equals(site.notes.get("carriedAfterReload")),"reload changed the carried stack");
        extra(failures,"pickupDelay",site->Boolean.TRUE.equals(site.notes.get("untouchedWhileDelayed")),"pickup delay ignored");
        for(var name:List.of("targetRemoved","targetShrunk"))extra(failures,name,site->site.eventDone,"golem never targeted the drop");
        var essence=site("originalNbt");var box=(ChestBlockEntity)level.getBlockEntity(essence.base);boolean tagged=true;int stacks=0;
        for(int slot=0;slot<box.getContainerSize();slot++)if(!box.getItem(slot).isEmpty()){stacks++;var old=content.gameplayStack(box.getItem(slot));tagged&=old.key().equals("Thaumcraft:ItemEssence")&&old.metadata()==1&&old.tag().contains("aer");}
        if(!tagged||stacks==0)failures.add("originalNbt: delivered stack lost its identity or aspects");
        outcome(site("hostOnlyNbt"),rows,failures);outcome(site("noOriginalForm"),rows,failures);
        report.put("carryLimit",CARRY);report.put("observedServerTicks",level.getGameTime()-started);report.put("sites",rows);report.put("failures",failures);
        report.put("status",failures.isEmpty()?"PASS":"FAIL");
    }
    private void extra(List<String> failures,String name,Predicate<Site> test,String message){if(!test.test(site(name)))failures.add(name+": "+message);}
    /** Stacks the old world may not see: either untouched on the ground or delivered whole, with the host's data intact. */
    @SuppressWarnings("unchecked")
    private void outcome(Site site,Map<String,Object> rows,List<String> failures){
        var row=(Map<String,Object>)rows.get(site.name);
        boolean untouched=site.ground==site.initialGround&&site.chest==0&&site.carried==0;boolean delivered=site.ground==0&&site.chest==site.initialGround&&site.carried==0;
        row.put("outcome",untouched?"untouched":delivered?"delivered":"other");
        if(!untouched&&!delivered){failures.add(site.name+": neither untouched nor delivered");return;}
        if(!delivered)return;
        var box=(ChestBlockEntity)level.getBlockEntity(site.base);
        for(int slot=0;slot<box.getContainerSize();slot++)if(!box.getItem(slot).isEmpty()&&site.name.equals("hostOnlyNbt")&&!box.getItem(slot).getHoverName().getString().equals("gather probe"))failures.add(site.name+": delivered stack lost its host name");
    }
    private void finish(net.minecraft.server.MinecraftServer server,Throwable failure){
        done=true;
        for(var site:sites){site.golems.forEach(Entity::discard);site.drops.forEach(Entity::discard);}
        if(player!=null)player.discard();
        try{
            var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/golem-gather-smoke.json");Files.createDirectories(path.getParent());
            Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception unwritable){throw new IllegalStateException(unwritable);}
        if(failure!=null)LogUtils.getLogger().error("MFCL_GOLEM_GATHER_SMOKE_FAIL",failure);
        LogUtils.getLogger().info("MFCL_GOLEM_GATHER_SMOKE_DONE: {} {}",report.get("status"),report.get("failures"));server.halt(false);
    }
}
