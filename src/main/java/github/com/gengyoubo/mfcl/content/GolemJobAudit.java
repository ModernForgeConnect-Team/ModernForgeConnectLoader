package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.util.*;

/** Natural server AI: resources are prepared once, then no task callbacks are invoked by this probe. */
final class GolemJobAudit {
    private final ModernContentBridge content;
    private final int observationTicks;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final List<LegacyHostedEntity> hosts=new ArrayList<>();
    private final List<Entity> targets=new ArrayList<>();
    private final List<Entity> cleanup=new ArrayList<>();
    private final Set<Integer> actionSeen=new HashSet<>(),bobberSeen=new HashSet<>();
    private net.minecraftforge.common.util.FakePlayer player;
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private long started=-1;private boolean done;
    private GolemJobAudit(ModernContentBridge content,int ticks){this.content=content;this.observationTicks=ticks;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new GolemJobAudit(content,1200)::tick);}
    static void installReload(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new GolemJobAudit(content,10)::tick);}
    private static Throwable cause(Throwable t){while(t.getCause()!=null)t=t.getCause();return t;}
    private BlockPos base(int core){return new BlockPos(128+(core%4)*32,99,128+(core/4)*32);}
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();var level=server.overworld();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Golem job audit timed out");
            if(hosts.isEmpty()){setup(level);return;}
            if(started<0){
                // New distant forced chunks can hold entities before ServerLevel's UUID lookup exposes them.
                // The real interaction path resolves its target through that lookup.
                if(hosts.stream().anyMatch(host->level.getEntity(host.getUUID())!=host))return;
                configure(level);started=level.getGameTime();return;
            }
            sample(level);
            if(level.getGameTime()-started<observationTicks)return;
            verify(level);report.put("observedServerTicks",level.getGameTime()-started);report.put("status","COMPLETE");finish(server,null);
        }catch(Throwable t){report.put("status","FAIL");report.put("failure",cause(t).toString());finish(server,t);}
    }
    private Object tile(ServerLevel level,BlockPos pos,String key,int meta)throws Exception{
        level.setBlockAndUpdate(pos,content.modernBlock(new HostWorldAccess.Block(key,meta)));
        return ((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();
    }
    private void chest(ServerLevel level,BlockPos pos,int count){
        level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
        if(count>0)((ChestBlockEntity)level.getBlockEntity(pos)).setItem(0,new ItemStack(Items.DIAMOND,count));
    }
    private int diamonds(ServerLevel level,BlockPos pos){
        if(!(level.getBlockEntity(pos)instanceof ChestBlockEntity chest))return 0;
        int count=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(Items.DIAMOND))count+=chest.getItem(i).getCount();return count;
    }
    private void setup(ServerLevel level)throws Exception{
        var services=content.services();var loader=services.loader();var world=services.worlds().bound(level);
        var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");
        var material=loader.loadClass("thaumcraft.common.entities.golems.EnumGolemType");var wood=material.getEnumConstants()[1];
        player=FakePlayerFactory.getMinecraft(level);player.getInventory().clearContent();player.getInventory().selected=4;level.addNewPlayer(player);
        report.put("jobs",rows);report.put("material","WOOD");report.put("advanced",false);report.put("reloadWithPlayer",observationTicks==10);
        level.setDayTime(18000);level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
        for(int core=0;core<12;core++){
            final int c=core;BlockPos b=base(core);var row=new LinkedHashMap<String,Object>();row.put("core",core);rows.add(row);
            for(int x=-8;x<=16;x++)for(int z=-8;z<=8;z++){
                var p=b.offset(x,0,z);level.setChunkForced(p.getX()>>4,p.getZ()>>4,true);level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
            }
            chest(level,b,core==1||core==10?16:0);chest(level,b.east(6),core==0?16:core==10?1:0);
            Entity target=null;
            if(core==2){var drop=new ItemEntity(level,b.getX()+4.5,100,b.getZ()+0.5,new ItemStack(Items.DIAMOND,4));drop.setNoPickUpDelay();level.addFreshEntity(drop);target=drop;cleanup.add(drop);}
            if(core==3){for(int x=3;x<=9;x++)for(int z=-3;z<=3;z++){var crop=b.offset(x,0,z);level.setBlockAndUpdate(crop,Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7));level.setBlockAndUpdate(crop.above(),Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7));}row.put("initialRipeCrops",49);}
            if(core==4){var mob=EntityType.ZOMBIE.create(level);mob.setPos(b.getX()+4.5,100,b.getZ()+0.5);mob.setNoAi(true);mob.setPersistenceRequired();level.addFreshEntity(mob);target=mob;cleanup.add(mob);}
            if(core==5){tile(level,b,"Thaumcraft:blockMetalDevice",0);level.setBlockAndUpdate(b.east(5),Blocks.WATER.defaultBlockState());}
            if(core==6){
                Object alembic=tile(level,b,"Thaumcraft:blockMetalDevice",1),jar=tile(level,b.east(6),"Thaumcraft:blockJar",0);
                services.items().inLegacy(()->{Object air=loader.loadClass("thaumcraft.api.aspects.Aspect").getField("AIR").get(null);alembic.getClass().getField("aspect").set(alembic,air);alembic.getClass().getField("amount").setInt(alembic,16);jar.getClass().getField("aspectFilter").set(jar,air);return null;});
            }
            if(core==7){for(int y=0;y<4;y++)level.setBlockAndUpdate(b.east(4).above(y+1),Blocks.OAK_LOG.defaultBlockState());level.setBlockAndUpdate(b.east(4).above(5),Blocks.OAK_LEAVES.defaultBlockState());}
            if(core==9){for(int n=0;n<3;n++){var cow=EntityType.COW.create(level);cow.setPos(b.getX()+4.5+n*.6,100,b.getZ()+0.5);cow.setNoAi(true);cow.setPersistenceRequired();level.addFreshEntity(cow);cleanup.add(cow);if(n==0)target=cow;}}
            if(core==11){for(int x=3;x<=9;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=0;y++)level.setBlockAndUpdate(b.offset(x,y,z),Blocks.WATER.defaultBlockState());}
            targets.add(target);player.setPos(b.getX()+1.5,100,b.getZ()+.5);
            Object original=services.items().inLegacy(()->{
                Object golem=type.getConstructor(loader.loadClass("net.minecraft.world.World"),material,boolean.class).newInstance(world,wood,false);
                type.getMethod("func_70107_b",double.class,double.class,double.class).invoke(golem,b.getX()+.5,100d,b.getZ()+.5);
                type.getMethod("func_110171_b",int.class,int.class,int.class,int.class).invoke(golem,b.getX(),100,b.getZ(),16);
                type.getField("homeFacing").setInt(golem,1);type.getMethod("setOwner",String.class).invoke(golem,player.getName().getString());
                type.getMethod("setupGolem").invoke(golem);return golem;
            });
            var host=LegacyHostedEntity.host(content,level,original);if(!level.addFreshEntity(host))throw new IllegalStateException("Golem was not spawned: "+c);hosts.add(host);cleanup.add(host);
        }
        LogUtils.getLogger().info("GOLEM_JOB_AUDIT_TERRAIN_READY");
    }
    private void configure(ServerLevel level)throws Exception{
        var services=content.services();var loader=services.loader();var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");
        var marker=loader.loadClass("thaumcraft.common.entities.golems.Marker");
        for(int core=0;core<12;core++){
            final int c=core;var b=base(c);var row=rows.get(c);var host=hosts.get(c);Object original=host.legacyOrNull();
            player.setPos(b.getX()+1.5,100,b.getZ()+.5);
            player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemGolemCore",core,2,""));
            row.put("installConsumedAction",player.interactOn(host,InteractionHand.MAIN_HAND).consumesAction());row.put("coreItemCountAfter",player.getMainHandItem().getCount());
            row.put("installedCore",services.items().inLegacy(()->type.getMethod("getCore").invoke(original)));
            if(core==7||core==8)continue;
            services.items().inLegacy(()->{
                var markers=new ArrayList<Object>();var pos=c==5?b.east(5):b.east(6);
                markers.add(marker.getConstructor(int.class,int.class,int.class,int.class,byte.class,byte.class).newInstance(pos.getX(),pos.getY(),pos.getZ(),0,(byte)1,(byte)-1));
                type.getMethod("setMarkers",ArrayList.class).invoke(original,markers);
                if(c==0||c==1||c==2||c==10){Object inv=type.getField("inventory").get(original);inv.getClass().getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(inv,0,services.items().materialize(content.gameplayStack(new ItemStack(Items.DIAMOND,16))));}
                if(c==5){Object inv=type.getField("inventory").get(original);inv.getClass().getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(inv,0,services.items().materialize(content.gameplayStack(new ItemStack(Items.WATER_BUCKET))));}
                return null;
            });
        }
        if(observationTicks==10)player.setPos(100,100,100);else player.discard();
        LogUtils.getLogger().info("GOLEM_JOB_AUDIT_SETUP");
    }
    private void sample(ServerLevel level)throws Exception{
        content.services().items().inLegacy(()->{
            for(int c=0;c<12;c++){
                Object original=hosts.get(c).legacyOrNull();if(original==null)continue;
                if(original.getClass().getField("action").getInt(original)>0)actionSeen.add(c);
                if(c==11){Object tasks=original.getClass().getField("field_70714_bg").get(original);for(Object entry:(List<?>)tasks.getClass().getField("field_75782_a").get(tasks)){
                    Object ai=entry.getClass().getField("field_75733_a").get(entry);if(ai.getClass().getSimpleName().equals("AIFish")){var f=ai.getClass().getDeclaredField("bobber");f.setAccessible(true);if(f.get(ai)!=null)bobberSeen.add(c);}
                }}
            }return null;
        });
    }
    private void verify(ServerLevel level)throws Exception{
        var services=content.services();
        for(int c=0;c<12;c++){
            var row=rows.get(c);var host=hosts.get(c);var b=base(c);Object original=host.legacyOrNull();
            row.put("alive",!host.isRemoved()&&original!=null);row.put("position",List.of(host.getX(),host.getY(),host.getZ()));row.put("actionSeen",actionSeen.contains(c));
            row.put("homeDiamonds",diamonds(level,b));row.put("markedDiamonds",diamonds(level,b.east(6)));
            Entity target=targets.get(c);if(target!=null){row.put("targetRemoved",target.isRemoved());if(target instanceof LivingEntity living)row.put("targetHealth",living.getHealth());if(target instanceof ItemEntity item)row.put("dropCount",item.getItem().getCount());}
            if(c==3){int ripe=0;for(int x=3;x<=9;x++)for(int z=-3;z<=3;z++){var state=level.getBlockState(b.offset(x,1,z));if(state.is(Blocks.WHEAT)&&state.getValue(BlockStateProperties.AGE_7)==7)ripe++;}row.put("ripeCropsRemaining",ripe);}
            if(c==3)services.items().inLegacy(()->{
                Object world=services.worlds().bound(level);var crop=b.offset(4,1,0);
                row.put("legacyCropBlock",world.getClass().getMethod("func_147439_a",int.class,int.class,int.class).invoke(world,crop.getX(),crop.getY(),crop.getZ()).getClass().getName());
                row.put("legacyCropMetadata",world.getClass().getMethod("func_72805_g",int.class,int.class,int.class).invoke(world,crop.getX(),crop.getY(),crop.getZ()));
                row.put("legacyCropRecognizedAsGrown",services.loader().loadClass("thaumcraft.common.lib.utils.CropUtils").getMethod("isGrownCrop",services.loader().loadClass("net.minecraft.world.World"),int.class,int.class,int.class).invoke(null,world,crop.getX(),crop.getY(),crop.getZ()));return null;
            });
            if(c==5){Object crucible=((LegacyBlockEntity)level.getBlockEntity(b)).legacy();Object tank=crucible.getClass().getField("tank").get(crucible);row.put("crucibleWater",tank.getClass().getMethod("getFluidAmount").invoke(tank));}
            if(c==6){Object jar=((LegacyBlockEntity)level.getBlockEntity(b.east(6))).legacy(),source=((LegacyBlockEntity)level.getBlockEntity(b)).legacy();row.put("jarEssentia",jar.getClass().getField("amount").get(jar));row.put("alembicEssentia",source.getClass().getField("amount").get(source));}
            if(c==11)row.put("bobberSeen",bobberSeen.contains(c));
            if(c==9){var cows=level.getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class,new net.minecraft.world.phys.AABB(b).inflate(12));row.put("cowsRemaining",cows.size());row.put("cowHealthTotal",cows.stream().mapToDouble(LivingEntity::getHealth).sum());}
            if(c==11){var drops=level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(b).inflate(16));row.put("nearbyDrops",drops.stream().map(drop->drop.getItem().toString()).toList());}
            if(original!=null)services.items().inLegacy(()->{
                Object carried=original.getClass().getMethod("getCarried").invoke(original);row.put("carried",carried==null?"empty":String.valueOf(services.items().snapshot(carried)));
                Object fluid=original.getClass().getField("fluidCarried").get(original);row.put("fluidCarried",String.valueOf(fluid));
                row.put("essentiaCarried",original.getClass().getField("essentiaAmount").get(original));return null;
            });
            // Real envelope save/load, including lazy old NBT restoration. Separate from the natural task observation.
            var saved=host.saveWithoutId(new CompoundTag());host.discard();var restored=new LegacyHostedEntity(content.legacyEntityType(),level);restored.load(saved);restored.setUUID(UUID.randomUUID());level.addFreshEntity(restored);restored.tick();
            row.put("reloadAlive",!restored.isRemoved()&&restored.legacyOrNull()!=null);cleanup.add(restored);
            if(restored.legacyOrNull()!=null)services.items().inLegacy(()->{
                Object reloaded=restored.legacyOrNull(),tasks=reloaded.getClass().getField("field_70714_bg").get(reloaded);var names=new ArrayList<String>();
                for(Object entry:(List<?>)tasks.getClass().getField("field_75782_a").get(tasks))names.add(entry.getClass().getField("field_75733_a").get(entry).getClass().getSimpleName());
                row.put("reloadedTasks",names);return null;
            });
        }
    }
    private void finish(net.minecraft.server.MinecraftServer server,Throwable error){
        done=true;cleanup.forEach(Entity::discard);if(player!=null)player.discard();
        try{var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/"+(observationTicks==10?"golem-reload-audit.json":"golem-job-audit.json"));Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){throw new IllegalStateException(failure);}
        if(error!=null)LogUtils.getLogger().error("GOLEM_JOB_AUDIT_FAIL",error);else LogUtils.getLogger().info("GOLEM_JOB_AUDIT_COMPLETE");server.halt(false);
    }
}
