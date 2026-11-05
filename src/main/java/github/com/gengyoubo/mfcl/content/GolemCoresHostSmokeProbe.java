package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/**
 * Acceptance of all twelve functional golem cores: real core insertion, then only the original AI runs, with a host
 * player registered as in normal play. Each core gets a verdict from what its work left in the host world. Other than
 * Fish, whose catch is random and which therefore runs on a Thaumium golem for up to 6000 ticks, this is an ordinary wood golem.
 * {@code golem-gather} covers the Gather core's edge cases; this probe only checks that each core does its job.
 */
final class GolemCoresHostSmokeProbe {
    private static final int WORK=1500,FISH_WORK=6000,GATHER=2,HARVEST=3,GUARD=4,LIQUID=5,ESSENTIA=6,CHOP=7,USE=8,BUTCHER=9,SORT=10,FISH=11,CROPS_PER_KIND=5,TRUNK=4,SEEDS=3;
    private static final String[] NAMES={"fill","empty","gather","harvest","guard","liquid","essentia","chop","use","butcher","sort","fish"};
    private static final String[] TASKS={"AIFillTake","AIEmptyPlace","AIItemPickup","AIHarvestCrops","AIGolemAttackOnCollide","AILiquidGather","AIEssentiaGather","AIHarvestLogs","AIUseItem","AIGolemAttackOnCollide","AISortingPlace","AIFish"};
    private static final List<Block> RIPE=List.of(Blocks.WHEAT,Blocks.CARROTS,Blocks.POTATOES);
    private static final java.util.function.UnaryOperator<BlockPos> MELON=b->b.offset(0,1,-3),PUMPKIN=b->b.offset(0,1,3);
    private final ModernContentBridge content;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final LegacyHostedEntity[] hosts=new LegacyHostedEntity[12];
    private final Entity[] targets=new Entity[12];
    private final int[] conservedTotal=new int[12];
    private final Long[] firstViolation=new Long[12];
    private final List<Map<String,Integer>> peakGround=new ArrayList<>();
    private final Set<Integer> actionSeen=new HashSet<>();
    private final Set<UUID> fishDrops=new HashSet<>();
    private final List<Entity> cleanup=new ArrayList<>();
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(9);
    private net.minecraftforge.common.util.FakePlayer player;
    private ServerLevel level;
    private boolean bobberSeen,done;
    private long started=-1;
    private GolemCoresHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new GolemCoresHostSmokeProbe(content)::tick);}
    private static BlockPos base(int core){return new BlockPos(128+(core%4)*48,99,128+(core/4)*48);}

    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();level=server.overworld();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Golem core probe timed out");
            if(rows.isEmpty()){setup();return;}
            if(started<0){
                // A forced distant chunk can hold the entity before ServerLevel's UUID lookup exposes it to interactOn.
                for(var host:hosts)if(level.getEntity(host.getUUID())!=host)return;
                configure();started=level.getGameTime();return;
            }
            long tick=level.getGameTime()-started;
            for(int core=0;core<12;core++)sample(core,tick);
            if(tick<WORK||(fishDrops.isEmpty()&&tick<FISH_WORK))return;
            verify();finish(server,null);
        }catch(Throwable failure){report.put("status","FAIL");report.put("failure",failure.toString());finish(server,failure);}
    }

    private void setup()throws Exception{
        var services=content.services();var loader=services.loader();var world=services.worlds().bound(level);
        level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        // Farmland stays hydrated, immature crops stay immature and nothing but the golem removes a block.
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
        player=FakePlayerFactory.getMinecraft(level);player.getInventory().clearContent();player.getInventory().selected=4;player.setPos(.5,100,.5);level.getChunkAt(new BlockPos(0,100,0));level.addNewPlayer(player);
        var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");var material=loader.loadClass("thaumcraft.common.entities.golems.EnumGolemType");
        for(int core=0;core<12;core++){
            var row=new LinkedHashMap<String,Object>();row.put("core",core);row.put("name",NAMES[core]);rows.add(row);peakGround.add(new HashMap<>());
            var b=base(core);
            for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
                var at=b.offset(x,0,z);level.setChunkForced(at.getX()>>4,at.getZ()>>4,true);level.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
            }
            prepare(core,b);
            Object kind=material.getEnumConstants()[core==FISH?7:1];row.put("material",core==FISH?"THAUMIUM":"WOOD");
            Object original=services.items().inLegacy(()->{
                Object golem=type.getConstructor(loader.loadClass("net.minecraft.world.World"),material,boolean.class).newInstance(world,kind,false);
                type.getMethod("func_70107_b",double.class,double.class,double.class).invoke(golem,b.getX()+.5,100d,b.getZ()+.5);
                type.getMethod("func_110171_b",int.class,int.class,int.class,int.class).invoke(golem,b.getX(),100,b.getZ(),16);
                type.getField("homeFacing").setInt(golem,1);type.getMethod("setOwner",String.class).invoke(golem,player.getName().getString());
                type.getMethod("setupGolem").invoke(golem);return golem;
            });
            hosts[core]=LegacyHostedEntity.host(content,level,original);cleanup.add(hosts[core]);
            if(!level.addFreshEntity(hosts[core]))throw new IllegalStateException("Golem was not spawned: "+NAMES[core]);
        }
        LogUtils.getLogger().info("MFCL_GOLEM_CORES_TERRAIN_READY");
    }
    private void prepare(int core,BlockPos b)throws Exception{
        switch(core){
            case 0->{chest(b,Items.DIAMOND,0);chest(b.east(6),Items.DIAMOND,16);}
            case 1->{chest(b,Items.DIAMOND,16);chest(b.east(6),Items.DIAMOND,0);}
            case GATHER->{chest(b,Items.DIAMOND,0);var drop=new ItemEntity(level,b.getX()+5.5,100.1,b.getZ()+.5,new ItemStack(Items.DIAMOND,4));drop.setNoPickUpDelay();level.addFreshEntity(drop);cleanup.add(drop);}
            case HARVEST->{
                for(int kind=0;kind<RIPE.size();kind++)for(int z=-2;z<CROPS_PER_KIND-2;z++)crop(b.offset(2+kind,0,z),RIPE.get(kind),7);
                crop(b.offset(2,0,3),Blocks.WHEAT,3);
                for(int z=-1;z<=1;z++)wart(b.offset(-2,0,z),3);
                wart(b.offset(-2,0,2),1);
                var log=b.offset(-3,1,-3);level.setBlockAndUpdate(log,Blocks.JUNGLE_LOG.defaultBlockState());
                cocoa(log.south(),Direction.NORTH,2);cocoa(log.east(),Direction.WEST,0);
                level.setBlockAndUpdate(MELON.apply(b),Blocks.MELON.defaultBlockState());level.setBlockAndUpdate(PUMPKIN.apply(b),Blocks.PUMPKIN.defaultBlockState());
                var sand=b.offset(-4,0,3);level.setBlockAndUpdate(sand.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(sand,Blocks.SAND.defaultBlockState());
                level.setBlockAndUpdate(sand.above(),Blocks.CACTUS.defaultBlockState());level.setBlockAndUpdate(sand.above(2),Blocks.CACTUS.defaultBlockState());
                // Beyond this core's reach (range / 4): only the original metadata writes in configure touch them.
                crop(b.offset(8,0,-2),Blocks.WHEAT,0);wart(b.offset(8,0,0),0);
                level.setBlockAndUpdate(b.offset(8,1,2),Blocks.JUNGLE_LOG.defaultBlockState());cocoa(b.offset(8,1,3),Direction.NORTH,0);
            }
            case GUARD->{var mob=EntityType.ZOMBIE.create(level);mob.setPos(b.getX()+5.5,100,b.getZ()+.5);mob.setNoAi(true);mob.setPersistenceRequired();level.addFreshEntity(mob);targets[core]=mob;cleanup.add(mob);}
            case LIQUID->{tile(b,"Thaumcraft:blockMetalDevice",0);level.setBlockAndUpdate(b.east(5).below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(b.east(5),Blocks.WATER.defaultBlockState());}
            case ESSENTIA->{
                Object alembic=tile(b,"Thaumcraft:blockMetalDevice",1),jar=tile(b.east(6),"Thaumcraft:blockJar",0);var loader=content.services().loader();
                content.services().items().inLegacy(()->{Object air=loader.loadClass("thaumcraft.api.aspects.Aspect").getField("AIR").get(null);alembic.getClass().getField("aspect").set(alembic,air);alembic.getClass().getField("amount").setInt(alembic,16);jar.getClass().getField("aspectFilter").set(jar,air);return null;});
            }
            case CHOP->{for(int y=1;y<=TRUNK;y++)level.setBlockAndUpdate(b.east(4).above(y),Blocks.OAK_LOG.defaultBlockState());level.setBlockAndUpdate(b.east(4).above(TRUNK+1),Blocks.OAK_LEAVES.defaultBlockState());}
            case USE->{chest(b,Items.WHEAT_SEEDS,SEEDS);for(int z=-1;z<=1;z++)level.setBlockAndUpdate(b.offset(3,0,z),Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7));}
            case BUTCHER->{for(int n=0;n<3;n++){var cow=EntityType.COW.create(level);cow.setPos(b.getX()+5.5+n*.6,100,b.getZ()+.5);cow.setNoAi(true);cow.setPersistenceRequired();level.addFreshEntity(cow);cleanup.add(cow);}}
            case SORT->{chest(b,Items.DIAMOND,16);chest(b.east(6),Items.DIAMOND,1);}
            case FISH->{
                for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=-2;y<=-1;y++)level.setBlockAndUpdate(b.offset(x,y,z),Blocks.STONE.defaultBlockState());
                for(int x=3;x<=8;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=0;y++)level.setBlockAndUpdate(b.offset(x,y,z),Blocks.WATER.defaultBlockState());
            }
            default->{}
        }
    }
    private void chest(BlockPos pos,Item item,int count){
        level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
        if(count>0)((ChestBlockEntity)level.getBlockEntity(pos)).setItem(0,new ItemStack(item,count));
    }
    private Object tile(BlockPos pos,String key,int meta)throws Exception{
        level.setBlockAndUpdate(pos,content.modernBlock(new HostWorldAccess.Block(key,meta)));
        return ((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();
    }
    private void crop(BlockPos farmland,Block crop,int age){
        level.setBlockAndUpdate(farmland,Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7));
        level.setBlockAndUpdate(farmland.above(),crop.defaultBlockState().setValue(BlockStateProperties.AGE_7,age));
    }
    private void wart(BlockPos soulSand,int age){
        level.setBlockAndUpdate(soulSand,Blocks.SOUL_SAND.defaultBlockState());
        level.setBlockAndUpdate(soulSand.above(),Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3,age));
    }
    private void cocoa(BlockPos pos,Direction log,int age){
        level.setBlockAndUpdate(pos,Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,log).setValue(BlockStateProperties.AGE_2,age));
    }
    /** World.setBlockMetadataWithNotify, as old growth logic (bone meal) uses it; expects the host state afterwards. */
    private boolean metadataWrite(Object world,BlockPos pos,int metadata,net.minecraft.world.level.block.state.BlockState expected)throws Exception{
        try{content.services().items().inLegacy(()->world.getClass().getMethod("func_72921_c",int.class,int.class,int.class,int.class,int.class).invoke(world,pos.getX(),pos.getY(),pos.getZ(),metadata,2));}
        catch(java.lang.reflect.InvocationTargetException refused){LogUtils.getLogger().warn("MFCL_GOLEM_CORES_METADATA_WRITE_REFUSED {}@{}: {}",level.getBlockState(pos),metadata,refused.getCause().toString());return false;}
        return level.getBlockState(pos).equals(expected);
    }
    private void configure()throws Exception{
        var services=content.services();var loader=services.loader();var type=loader.loadClass("thaumcraft.common.entities.golems.EntityGolemBase");
        var marker=loader.loadClass("thaumcraft.common.entities.golems.Marker");
        for(int core=0;core<12;core++){
            final int c=core;var b=base(c);var host=hosts[c];var row=rows.get(c);Object original=host.legacyOrNull();
            player.setPos(host.getX()+1,100,host.getZ());
            player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemGolemCore",c,2,""));
            row.put("installConsumedAction",player.interactOn(host,InteractionHand.MAIN_HAND).consumesAction());
            row.put("coreConsumedOnce",player.getMainHandItem().getCount()==1);
            row.put("installedCore",services.items().inLegacy(()->(int)(byte)type.getMethod("getCore").invoke(original)));
            var marked=markers(c,b);
            ItemStack filter=c==USE?new ItemStack(Items.WHEAT_SEEDS,16):c==LIQUID?new ItemStack(Items.WATER_BUCKET):c==0||c==1||c==GATHER||c==SORT?new ItemStack(Items.DIAMOND,16):ItemStack.EMPTY;
            services.items().inLegacy(()->{
                if(!marked.isEmpty()){
                    var list=new ArrayList<Object>();
                    for(var pos:marked)list.add(marker.getConstructor(int.class,int.class,int.class,int.class,byte.class,byte.class).newInstance(pos.getX(),pos.getY(),pos.getZ(),0,(byte)1,(byte)-1));
                    type.getMethod("setMarkers",ArrayList.class).invoke(original,list);
                }
                if(!filter.isEmpty()){Object inventory=type.getField("inventory").get(original);inventory.getClass().getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(inventory,0,services.items().materialize(content.gameplayStack(filter)));}
                return null;
            });
            conservedTotal[c]=conserved(c);row.put("conservedAtStart",conservedTotal[c]);
        }
        // What the original crop logic sees of a ripe host crop explains a failed harvest better than the empty field does.
        var crop=base(HARVEST).offset(2,1,0);Object world=services.worlds().bound(level);var view=new LinkedHashMap<String,Object>();
        var h=base(HARVEST);
        rows.get(HARVEST).put("metadataWrites",Map.of(
                "wheat",metadataWrite(world,h.offset(8,1,-2),5,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,5)),
                "netherWart",metadataWrite(world,h.offset(8,1,0),2,Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3,2)),
                // Old cocoa metadata: the direction to its log (north = 2) in the low bits and the age above them.
                "cocoa",metadataWrite(world,h.offset(8,1,3),2|1<<2,Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH).setValue(BlockStateProperties.AGE_2,1))));
        for(var pos:List.of(crop,h.offset(3,1,0),h.offset(4,1,0),h.offset(-2,1,0),h.offset(-3,1,-2),MELON.apply(h),PUMPKIN.apply(h),h.offset(-4,2,3))){
            var at=pos;
            view.put(BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).getPath(),services.items().inLegacy(()->Map.of(
                    "block",world.getClass().getMethod("func_147439_a",int.class,int.class,int.class).invoke(world,at.getX(),at.getY(),at.getZ()).getClass().getName(),
                    "metadata",world.getClass().getMethod("func_72805_g",int.class,int.class,int.class).invoke(world,at.getX(),at.getY(),at.getZ()),
                    "grown",loader.loadClass("thaumcraft.common.lib.utils.CropUtils").getMethod("isGrownCrop",loader.loadClass("net.minecraft.world.World"),int.class,int.class,int.class).invoke(null,world,at.getX(),at.getY(),at.getZ()))));
        }
        rows.get(HARVEST).put("legacyViewOfRipeCrops",view);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setPos(.5,100,.5);
        LogUtils.getLogger().info("MFCL_GOLEM_CORES_SETUP");
    }
    private List<BlockPos> markers(int core,BlockPos b){
        return switch(core){
            case 0,1,ESSENTIA,SORT->List.of(b.east(6));
            case LIQUID->List.of(b.east(5));
            case USE->List.of(b.offset(3,0,-1),b.offset(3,0,0),b.offset(3,0,1));
            default->List.of();
        };
    }
    /** The items or essentia a core may move but not create; -1 for cores whose work legitimately produces or destroys them. */
    private int conserved(int core)throws Exception{
        var b=base(core);
        return switch(core){
            case 0,1,GATHER,SORT->chestCount(b,Items.DIAMOND)+chestCount(b.east(6),Items.DIAMOND)+ground(core,Items.DIAMOND)+carried(core);
            case USE->chestCount(b,Items.WHEAT_SEEDS)+ground(core,Items.WHEAT_SEEDS)+carried(core)+planted();
            case ESSENTIA->essentia(b)+essentia(b.east(6))+carriedEssentia();
            default->-1;
        };
    }
    private int chestCount(BlockPos pos,Item item){return level.getBlockEntity(pos) instanceof ChestBlockEntity chest?chest.countItem(item):0;}
    private List<ItemEntity> drops(int core){return level.getEntitiesOfClass(ItemEntity.class,new AABB(base(core)).inflate(14),item->!item.isRemoved());}
    private int ground(int core,Item item){int count=0;for(var drop:drops(core))if(drop.getItem().is(item))count+=drop.getItem().getCount();return count;}
    private int essentia(BlockPos pos)throws Exception{Object tile=((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();return tile.getClass().getField("amount").getInt(tile);}
    private int carriedEssentia()throws Exception{Object golem=hosts[ESSENTIA].legacyOrNull();return golem==null?0:golem.getClass().getField("essentiaAmount").getInt(golem);}
    private int planted(){int count=0;for(int z=-1;z<=1;z++)if(level.getBlockState(base(USE).offset(3,1,z)).is(Blocks.WHEAT))count++;return count;}
    private int carried(int core)throws Exception{
        Object golem=hosts[core].legacyOrNull();if(golem==null||hosts[core].isRemoved())return 0;
        var items=content.services().items();
        return items.inLegacy(()->{Object stack=golem.getClass().getMethod("getCarried").invoke(golem);return stack==null?0:items.snapshot(stack).count();});
    }
    private void sample(int core,long tick)throws Exception{
        var peak=peakGround.get(core);var now=new HashMap<String,Integer>();
        for(var drop:drops(core)){
            now.merge(BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).getPath(),drop.getItem().getCount(),Integer::sum);
            if(core==FISH&&fishDrops.add(drop.getUUID())&&fishDrops.size()==1)rows.get(FISH).put("firstCatchTick",tick);
        }
        now.forEach((key,count)->peak.merge(key,count,Math::max));
        int total=conserved(core);
        if(total!=conservedTotal[core]&&firstViolation[core]==null){
            firstViolation[core]=tick;rows.get(core).put("firstViolation",Map.of("tick",tick,"expected",conservedTotal[core],"found",total));
            LogUtils.getLogger().warn("MFCL_GOLEM_CORES_VIOLATION {} at tick {}: expected {} found {}",NAMES[core],tick,conservedTotal[core],total);
        }
        Object golem=hosts[core].legacyOrNull();if(golem==null)return;
        content.services().items().inLegacy(()->{
            if(golem.getClass().getField("action").getInt(golem)>0)actionSeen.add(core);
            if(core==FISH){var ai=task(golem,"AIFish");if(ai!=null){var field=ai.getClass().getDeclaredField("bobber");field.setAccessible(true);if(field.get(ai)!=null)bobberSeen=true;}}
            return null;
        });
    }
    private Object task(Object golem,String name)throws Exception{
        Object tasks=golem.getClass().getField("field_70714_bg").get(golem);
        for(Object entry:(List<?>)tasks.getClass().getField("field_75782_a").get(tasks)){
            Object task=entry.getClass().getField("field_75733_a").get(entry);
            if(task.getClass().getSimpleName().equals(name))return task;
        }
        return null;
    }
    private void verify()throws Exception{
        var failed=new ArrayList<String>();
        for(int core=0;core<12;core++){
            var row=rows.get(core);var checks=new LinkedHashMap<String,Boolean>();
            checks.put("installed",Integer.valueOf(core).equals(row.get("installedCore"))&&Boolean.TRUE.equals(row.get("installConsumedAction"))&&Boolean.TRUE.equals(row.get("coreConsumedOnce")));
            checks.put("aliveAfterWork",!hosts[core].isRemoved()&&hosts[core].legacyOrNull()!=null);
            if(conservedTotal[core]>=0)checks.put("conserved",firstViolation[core]==null);
            work(core,row,checks);
            row.put("actionSeen",actionSeen.contains(core));row.put("peakGround",peakGround.get(core));row.put("carried",carried(core));
            reload(core,row,checks);
            row.put("checks",checks);
            boolean pass=!checks.containsValue(false);row.put("verdict",pass?"PASS":"FAIL");
            if(!pass){var missed=new ArrayList<String>();checks.forEach((name,value)->{if(!value)missed.add(name);});failed.add(NAMES[core]+": "+missed);}
        }
        report.put("observedServerTicks",level.getGameTime()-started);report.put("cores",rows);report.put("failed",failed);report.put("status",failed.isEmpty()?"PASS":"FAIL");
    }
    private void work(int core,Map<String,Object> row,Map<String,Boolean> checks)throws Exception{
        var b=base(core);var peak=peakGround.get(core);
        switch(core){
            case 0->{row.put("home",chestCount(b,Items.DIAMOND));row.put("marked",chestCount(b.east(6),Items.DIAMOND));checks.put("markedEmptiedIntoHome",chestCount(b,Items.DIAMOND)==16&&chestCount(b.east(6),Items.DIAMOND)==0&&carried(core)==0);}
            case 1->{row.put("home",chestCount(b,Items.DIAMOND));row.put("marked",chestCount(b.east(6),Items.DIAMOND));checks.put("homeEmptiedIntoMarked",chestCount(b,Items.DIAMOND)==0&&chestCount(b.east(6),Items.DIAMOND)==16&&carried(core)==0);}
            case GATHER->{row.put("home",chestCount(b,Items.DIAMOND));checks.put("groundGatheredIntoHome",chestCount(b,Items.DIAMOND)==4&&ground(core,Items.DIAMOND)==0&&carried(core)==0);}
            case HARVEST->{
                var left=new LinkedHashMap<String,Integer>();
                for(int kind=0;kind<RIPE.size();kind++){int count=0;for(int z=-2;z<CROPS_PER_KIND-2;z++)if(level.getBlockState(b.offset(2+kind,1,z)).is(RIPE.get(kind)))count++;left.put(BuiltInRegistries.BLOCK.getKey(RIPE.get(kind)).getPath(),count);}
                row.put("ripeLeft",left);
                int wartLeft=0;for(int z=-1;z<=1;z++)if(level.getBlockState(b.offset(-2,1,z)).is(Blocks.NETHER_WART))wartLeft++;
                left.put("nether_wart",wartLeft);
                left.put("cocoa",level.getBlockState(b.offset(-3,1,-2)).is(Blocks.COCOA)?1:0);
                left.put("melon",level.getBlockState(MELON.apply(b)).is(Blocks.MELON)?1:0);
                left.put("pumpkin",level.getBlockState(PUMPKIN.apply(b)).is(Blocks.PUMPKIN)?1:0);
                left.put("cactusTop",level.getBlockState(b.offset(-4,2,3)).is(Blocks.CACTUS)?1:0);
                row.put("ripeLeft",left);
                left.forEach((kind,count)->checks.put(kind+"Harvested",count==0));
                checks.put("immatureCropsUntouched",level.getBlockState(b.offset(2,1,3)).is(Blocks.WHEAT)&&level.getBlockState(b.offset(-2,1,2)).is(Blocks.NETHER_WART)&&level.getBlockState(b.offset(-3,1,-2).offset(1,0,-1)).is(Blocks.COCOA));
                checks.put("cactusBaseUntouched",level.getBlockState(b.offset(-4,1,3)).is(Blocks.CACTUS));
                for(var drop:Map.of("wheat",CROPS_PER_KIND,"carrot",CROPS_PER_KIND,"potato",CROPS_PER_KIND,"nether_wart",3,"cocoa_beans",1,"melon_slice",1,"pumpkin",1,"cactus",1).entrySet())
                    checks.put(drop.getKey()+"Dropped",peak.getOrDefault(drop.getKey(),0)>=drop.getValue());
                ((Map<?,?>)row.get("metadataWrites")).forEach((kind,value)->checks.put(kind+"MetadataWrite",Boolean.TRUE.equals(value)));
            }
            case GUARD->{var mob=(LivingEntity)targets[core];row.put("targetHealth",mob.getHealth());checks.put("hostileKilled",mob.isRemoved()||mob.getHealth()<=0);}
            case LIQUID->{Object crucible=((LegacyBlockEntity)level.getBlockEntity(b)).legacy();Object tank=crucible.getClass().getField("tank").get(crucible);int water=(int)tank.getClass().getMethod("getFluidAmount").invoke(tank);row.put("crucibleWater",water);checks.put("crucibleFilled",water==1000);}
            case ESSENTIA->{row.put("jar",essentia(b.east(6)));row.put("alembic",essentia(b));checks.put("essentiaMoved",essentia(b.east(6))==16&&essentia(b)==0&&carriedEssentia()==0);}
            case CHOP->{
                int logs=0;for(int y=1;y<=TRUNK;y++)if(level.getBlockState(b.east(4).above(y)).is(Blocks.OAK_LOG))logs++;
                row.put("logsLeft",logs);checks.put("trunkFelled",logs==0);checks.put("eachLogDroppedOnce",peak.getOrDefault("oak_log",0)==TRUNK);
            }
            case USE->{row.put("planted",planted());row.put("chest",chestCount(b,Items.WHEAT_SEEDS));checks.put("seedsPlantedOnMarkers",planted()==SEEDS&&chestCount(b,Items.WHEAT_SEEDS)==0&&carried(core)==0);}
            case BUTCHER->{int cows=level.getEntitiesOfClass(Cow.class,new AABB(b).inflate(14),cow->!cow.isRemoved()&&cow.getHealth()>0).size();row.put("cowsLeft",cows);checks.put("surplusButchered",cows==2);checks.put("meatDropped",peak.containsKey("beef")||peak.containsKey("leather"));}
            case SORT->{row.put("home",chestCount(b,Items.DIAMOND));row.put("marked",chestCount(b.east(6),Items.DIAMOND));checks.put("sortedIntoMarked",chestCount(b.east(6),Items.DIAMOND)==17&&chestCount(b,Items.DIAMOND)==0&&carried(core)==0);}
            case FISH->{row.put("catches",fishDrops.size());row.put("bobberSeen",bobberSeen);checks.put("bobberCast",bobberSeen);checks.put("catchLanded",!fishDrops.isEmpty());}
            default->throw new IllegalStateException("Unknown core "+core);
        }
    }
    /** The real envelope round trip with a player online: the core's task must come back with the golem. */
    private void reload(int core,Map<String,Object> row,Map<String,Boolean> checks)throws Exception{
        var host=hosts[core];if(host.isRemoved()||host.legacyOrNull()==null){checks.put("reloaded",false);return;}
        var saved=host.saveWithoutId(new CompoundTag());host.discard();
        var restored=new LegacyHostedEntity(content.legacyEntityType(),level);restored.load(saved);restored.setUUID(UUID.randomUUID());
        if(!level.addFreshEntity(restored))throw new IllegalStateException("Reloaded golem was not spawned: "+NAMES[core]);
        cleanup.add(restored);restored.tick();
        Object original=restored.legacyOrNull();
        checks.put("reloaded",!restored.isRemoved()&&original!=null&&content.services().items().inLegacy(()->task(original,TASKS[core])!=null));
    }
    private void finish(net.minecraft.server.MinecraftServer server,Throwable failure){
        done=true;cleanup.forEach(Entity::discard);if(player!=null)player.discard();
        try{
            var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/golem-cores-smoke.json");Files.createDirectories(path.getParent());
            Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception unwritable){throw new IllegalStateException(unwritable);}
        if(failure!=null)LogUtils.getLogger().error("MFCL_GOLEM_CORES_SMOKE_FAIL",failure);
        LogUtils.getLogger().info("MFCL_GOLEM_CORES_SMOKE_DONE: {} {}",report.get("status"),report.get("failed"));server.halt(false);
    }
}
