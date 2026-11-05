package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/**
 * Native ServerPlayerGameMode.destroyBlock against original blocks whose drops are computed in onBlockHarvested,
 * because their getDrops needs the tile that the host has already removed when it evaluates native loot.
 * Every case runs even after a failure, so the report also documents a regression precisely.
 */
final class HarvestDropsHostSmokeProbe {
    private static final BlockPos ORIGIN=new BlockPos(0,100,0);
    private static final AABB AREA=new AABB(ORIGIN).inflate(6);
    private static final String JAR="Thaumcraft:blockJar",FILLED="Thaumcraft:BlockJarFilledItem",NODE_JAR="Thaumcraft:BlockJarNodeItem";
    @FunctionalInterface private interface Case { void run() throws Exception; }
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>(),cases=new LinkedHashMap<>();
    private ServerLevel level;
    private ServerPlayer player;
    private boolean done;
    private HarvestDropsHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new HarvestDropsHostSmokeProbe(content)::tick);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;done=true;
        try{run(event.getServer().overworld());}
        catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL harvest drop acceptance failed",failure);}
        try{var file=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/harvest-drops-smoke.json");Files.createDirectories(file.getParent());Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception e){throw new IllegalStateException(e);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_HARVEST_DROPS_SMOKE {}",proof);event.getServer().halt(false);
    }
    private void run(ServerLevel level){
        this.level=level;level.setChunkForced(0,0,true);proof.put("cases",cases);
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(ORIGIN.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.setPos(.5,100,-2.5);
        scenario("ordinarySilverwoodWithCopiedEmptyTileData",()->{
            String key="Thaumcraft:blockMagicalLog";
            var state=content.modernBlock(new HostWorldAccess.Block(key,1));
            level.setBlockAndUpdate(ORIGIN,state);
            check(level.getBlockEntity(ORIGIN)==null,"Ordinary silverwood has a tile envelope");
            var stack=content.itemForBlock(key,1);var data=new net.minecraft.nbt.CompoundTag();
            data.putString("mfcl_legacy_block",key);data.putByteArray("mfcl_legacy_nbt",new byte[0]);
            net.minecraft.world.item.BlockItem.setBlockEntityData(stack,content.tileType(),data);
            level.setBlockAndUpdate(ORIGIN,Blocks.AIR.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var below=ORIGIN.below();var hit=new BlockHitResult(Vec3.atCenterOf(below).add(0,.5,0),Direction.UP,below,false);
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
            check(!stack.onItemUseFirst(context).consumesAction(),"First-use unexpectedly consumed ordinary log");
            check(stack.useOn(context).consumesAction(),"Copied ordinary silverwood could not be placed");
            check(content.legacyBlock(level.getBlockState(ORIGIN)).metadata()==1,"Ordinary silverwood metadata changed");
            check(level.getBlockEntity(ORIGIN)==null,"Placed ordinary silverwood has a tile envelope");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        });
        scenario("silverwoodKnotNameAndBlockEntityPlacement",()->{
            String key="Thaumcraft:blockMagicalLog";
            Object node=place(key,2);
            legacy(()->{node.getClass().getMethod("setAspects",loader("thaumcraft.api.aspects.AspectList")).invoke(node,aspects("AIR",20));return null;});
            var stack=content.itemForBlock(key,2);
            try {content.services().items().language("zh_cn");check(stack.getHoverName().getString().equals("银树树瘤"),"Missing knot translation: "+stack.getHoverName().getString());}
            finally {content.services().items().language("en_us");}
            var data=level.getBlockEntity(ORIGIN).saveWithoutMetadata();
            net.minecraft.world.item.BlockItem.setBlockEntityData(stack,content.tileType(),data);
            level.setBlockAndUpdate(ORIGIN,Blocks.AIR.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var below=ORIGIN.below();var hit=new BlockHitResult(Vec3.atCenterOf(below).add(0,.5,0),Direction.UP,below,false);
            var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit);
            check(!stack.onItemUseFirst(context).consumesAction(),"First-use unexpectedly consumed placement");
            check(stack.useOn(context).consumesAction(),"Knot with block entity data could not be placed");
            Object restored=((LegacyBlockEntity)level.getBlockEntity(ORIGIN)).legacy();
            int amount=legacy(()->{Object list=restored.getClass().getMethod("getAspects").invoke(restored);return (int)list.getClass().getMethod("getAmount",loader("thaumcraft.api.aspects.Aspect")).invoke(list,aspect("AIR"));});
            check(amount==20,"Node aspects were not restored: "+amount);
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        });
        scenario("emptyJarDropsPlainJar",()->{place(JAR,0);breakBlock();single(JAR,0);});
        scenario("filledJarKeepsEssentia",()->{
            fill(place(JAR,0),"AIR",16);breakBlock();
            assertJar(placeBack(single(FILLED,0)),"AIR",16,null);
        });
        scenario("labelledEmptyJarKeepsFilter",()->{
            filter(place(JAR,0),"FIRE");breakBlock();
            assertJar(placeBack(single(FILLED,0)),null,0,"FIRE");
        });
        scenario("labelledFilledJarKeepsBoth",()->{
            Object tile=place(JAR,0);fill(tile,"FIRE",8);filter(tile,"FIRE");breakBlock();
            assertJar(placeBack(single(FILLED,0)),"FIRE",8,"FIRE");
        });
        scenario("voidJarKeepsEssentiaAndType",()->{
            fill(place(JAR,3),"AIR",16);breakBlock();
            Object tile=placeBack(single(FILLED,3));assertJar(tile,"AIR",16,null);
            check(tile.getClass().getName().equals("thaumcraft.common.tiles.TileJarFillableVoid"),"Void jar placed back as "+tile.getClass().getName());
        });
        // The original breakBlock reads the world's metadata after Chunk stored the replacement, so it never sees md==1.
        scenario("brainJarDropsJarWithoutOriginalXpSpill",()->{
            Object tile=place(JAR,1);tile.getClass().getField("xp").setInt(tile,40);breakBlock();single(JAR,1);
            check(level.getEntitiesOfClass(ExperienceOrb.class,AREA).isEmpty(),"Original player break does not spill brain XP");
        });
        scenario("nodeJarKeepsNode",()->{
            Object tile=place(JAR,2);legacy(()->{tile.getClass().getMethod("setAspects",loader("thaumcraft.api.aspects.AspectList")).invoke(tile,aspects("AIR",20));return null;});breakBlock();
            var drop=single(NODE_JAR,0);
            int amount=legacy(()->{
                Object stack=content.services().items().materialize(content.gameplayStack(drop));
                Object list=stack.getClass().getMethod("func_77973_b").invoke(stack).getClass().getMethod("getAspects",stack.getClass()).invoke(stack.getClass().getMethod("func_77973_b").invoke(stack),stack);
                return (int)list.getClass().getMethod("getAmount",loader("thaumcraft.api.aspects.Aspect")).invoke(list,aspect("AIR"));
            });
            check(amount==20,"Node jar kept "+amount+" Aer");
        });
        scenario("mirrorDropsOnce",()->{place("Thaumcraft:blockMirror",0);breakBlock();single(key("Thaumcraft:blockMirror",0),0);});
        scenario("bannerKeepsColorAndDropsOnce",()->{
            Object tile=place("Thaumcraft:blockWoodenDevice",8);legacy(()->{tile.getClass().getMethod("setColor",byte.class).invoke(tile,(byte)3);return null;});breakBlock();
            var drop=single(key("Thaumcraft:blockWoodenDevice",8),8);
            byte color=legacy(()->{
                Object stack=content.services().items().materialize(content.gameplayStack(drop)),tag=stack.getClass().getField("field_77990_d").get(stack);
                check(tag!=null,"Banner dropped without NBT");return (byte)tag.getClass().getMethod("func_74771_c",String.class).invoke(tag,"color");
            });
            check(color==3,"Banner color "+color);
        });
        scenario("auraNodeReleasesOriginalWispEssence",()->{
            Object tile=place("Thaumcraft:blockAiry",0);legacy(()->{tile.getClass().getMethod("setAspects",loader("thaumcraft.api.aspects.AspectList")).invoke(tile,aspects("AIR",20));return null;});breakBlock();
            int count=0;for(var item:drops()){var stack=content.gameplayStack(item.getItem());check(stack.key().equals("Thaumcraft:ItemWispEssence"),"Unexpected node drop "+stack);count+=stack.count();}
            check(count==3,"20 Aer should release 3 wisp essences, got "+count);
        });
        scenario("creativeBreakMatchesOriginalCallback",()->{
            player.setGameMode(GameType.CREATIVE);
            try{fill(place(JAR,0),"AIR",16);breakBlock();assertJar(placeBack(single(FILLED,0)),"AIR",16,null);}
            finally{player.setGameMode(GameType.SURVIVAL);}
        });
        scenario("hostDoTileDropsRuleIsRespected",()->{
            var rule=level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);rule.set(false,level.getServer());
            try{fill(place(JAR,0),"AIR",16);breakBlock();check(drops().isEmpty(),"Drops ignored doTileDrops=false: "+drops().size());}
            finally{rule.set(true,level.getServer());}
        });
        // Controls: no player callback runs here, and blocks without the callback keep their native loot path.
        scenario("nonPlayerDestroyKeepsSingleFilledJar",()->{
            fill(place(JAR,0),"AIR",16);check(level.destroyBlock(ORIGIN,true),"Native destroyBlock failed");
            assertJar(placeBack(single(FILLED,0)),"AIR",16,null);
        });
        scenario("blockWithoutCallbackKeepsSingleNativeDrop",()->{
            place("Thaumcraft:blockMetalDevice",0);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
            breakBlock();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);single(key("Thaumcraft:blockMetalDevice",0),0);
        });
        proof.put("status",cases.values().stream().allMatch("PASS"::equals)?"PASS":"FAIL");
    }
    private void scenario(String name,Case body){
        for(var item:drops())item.discard();
        level.setBlockAndUpdate(ORIGIN,Blocks.AIR.defaultBlockState());
        try{body.run();cases.put(name,"PASS");}
        catch(Throwable failure){cases.put(name,failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL harvest drop case {} failed",name,failure);}
    }
    private <T> T legacy(java.util.concurrent.Callable<T> action)throws Exception{return content.services().items().inLegacy(action);}
    private Class<?> loader(String name)throws Exception{return content.services().loader().loadClass(name);}
    private Object aspect(String name)throws Exception{return loader("thaumcraft.api.aspects.Aspect").getField(name).get(null);}
    private Object aspects(String name,int amount)throws Exception{
        Class<?> type=loader("thaumcraft.api.aspects.AspectList");Object list=type.getConstructor().newInstance();
        type.getMethod("add",loader("thaumcraft.api.aspects.Aspect"),int.class).invoke(list,aspect(name),amount);return list;
    }
    private Object place(String key,int metadata)throws Exception{
        level.setBlockAndUpdate(ORIGIN,content.modernBlock(new HostWorldAccess.Block(key,metadata)));
        return level.getBlockEntity(ORIGIN) instanceof LegacyBlockEntity tile?tile.legacy():null;
    }
    private void fill(Object tile,String aspect,int amount)throws Exception{
        Object value=legacy(()->aspect(aspect));tile.getClass().getField("aspect").set(tile,value);tile.getClass().getField("amount").setInt(tile,amount);
    }
    private void filter(Object tile,String aspect)throws Exception{tile.getClass().getField("aspectFilter").set(tile,legacy(()->aspect(aspect)));}
    private void breakBlock(){check(player.gameMode.destroyBlock(ORIGIN),"destroyBlock refused");check(level.isEmptyBlock(ORIGIN),"Block still present after break");}
    private List<ItemEntity> drops(){return level.getEntitiesOfClass(ItemEntity.class,AREA,item->!item.isRemoved());}
    private String key(String block,int metadata)throws Exception{return content.gameplayStack(content.itemForBlock(block,metadata)).key();}
    /** Exactly one entity holding one item of the expected identity; consumes the entity. */
    private ItemStack single(String key,int metadata)throws Exception{
        var items=drops();int total=0;for(var item:items)total+=item.getItem().getCount();
        check(total==1,"Expected exactly one "+key+"@"+metadata+", got "+total);
        var stack=content.gameplayStack(items.get(0).getItem());
        check(stack.key().equals(key)&&stack.metadata()==metadata,"Expected "+key+"@"+metadata+", got "+stack);
        var result=items.get(0).getItem().copy();items.get(0).discard();return result;
    }
    /** The real item-use path proves the data the drop carries, not just its identity. */
    private Object placeBack(ItemStack stack)throws Exception{
        check(level.isEmptyBlock(ORIGIN),"Placement target occupied");
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var below=ORIGIN.below();var hit=new BlockHitResult(Vec3.atCenterOf(below).add(0,.5,0),Direction.UP,below,false);
        check(stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Dropped item could not be placed back");
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        check(level.getBlockEntity(ORIGIN) instanceof LegacyBlockEntity,"Placed item has no original tile");
        return ((LegacyBlockEntity)level.getBlockEntity(ORIGIN)).legacy();
    }
    private void assertJar(Object tile,String aspect,int amount,String filter)throws Exception{
        Object expectedAspect=aspect==null?null:legacy(()->aspect(aspect)),expectedFilter=filter==null?null:legacy(()->aspect(filter));
        check(tile.getClass().getField("amount").getInt(tile)==amount,"Jar amount "+tile.getClass().getField("amount").getInt(tile));
        if(amount>0)check(tile.getClass().getField("aspect").get(tile)==expectedAspect,"Jar aspect changed");
        check(tile.getClass().getField("aspectFilter").get(tile)==expectedFilter,"Jar filter changed");
    }
}
