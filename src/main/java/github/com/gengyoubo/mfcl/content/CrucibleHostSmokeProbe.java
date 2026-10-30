package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Disposable dedicated-server regression using the original TC4 recipe and native item physics/tool drops. */
final class CrucibleHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
    private ServerPlayer player;
    private BlockPos pos;
    private Object tile,recipe;
    private ItemStack expected;
    private ItemEntity ingredient;
    private int step;
    private long started;
    private boolean done;
    private CrucibleHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new CrucibleHostSmokeProbe(content)::tick);}
    private void check(boolean value,String name){if(!value)throw new IllegalStateException(name);proof.put(name,true);}
    private void tick(TickEvent.ServerTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END)return;
        var level=event.getServer().overworld();
        try {
            if(System.nanoTime()>deadline)throw new IllegalStateException("Crucible acceptance timed out");
            if(player==null){setup(level);return;}
            if(step==0) {
                if(level.getGameTime()-started<210)return;
                check(tile.getClass().getField("heat").getShort(tile)>150,"naturalLavaHeating");
                var unheated=pos.east(3);level.setBlockAndUpdate(unheated,state());
                var cold=new ItemEntity(level,unheated.getX()+.5,unheated.getY()+.3,unheated.getZ()+.5,new ItemStack(Items.COAL));
                state().entityInside(level,unheated,cold);check(!cold.isRemoved()&&cold.getItem().getCount()==1,"coldCruciblePreservesIngredient");
                seed(false);ingredient=toss(level);step++;started=level.getGameTime();return;
            }
            if(step==1) {
                if(level.getGameTime()-started<15)return;
                check(ingredient.isRemoved(),"unresearchedCatalystDissolves");
                check(outputs(level).isEmpty(),"researchRequirementStillEnforced");
                seed(true);ingredient=toss(level);
                check(player.getName().getString().equals(ingredient.getPersistentData().getString("thrower")),"originalTossSubscriberRecordsPlayer");
                var saved=new net.minecraft.nbt.CompoundTag();ingredient.saveWithoutId(saved);
                var restored=new ItemEntity(level,ingredient.getX(),ingredient.getY(),ingredient.getZ(),ingredient.getItem().copy());restored.load(saved);
                check(restored.getPersistentData().equals(ingredient.getPersistentData()),"tossProvenanceSurvivesEntityNbtReload");
                step++;started=level.getGameTime();return;
            }
            if(step==2) {
                if(level.getGameTime()-started<15)return;
                check(ingredient.isRemoved(),"researchedCatalystConsumed");
                var drops=outputs(level);check(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==expected.getCount(),"originalRecipeOutputSpawnedExactlyOnce");
                check(water()==950,"originalRecipeConsumes50mbWater");
                Object aspects=tile.getClass().getField("aspects").get(tile);
                check((int)aspects.getClass().getMethod("visSize").invoke(aspects)==0,"originalRecipeConsumesRequiredAspects");
                for(var drop:drops) {
                    check(HostEntityQueries.snapshot(level,drop).legacyClass().equals("thaumcraft.common.entities.EntitySpecialItem"),"ejectedItemRetainsOriginalSubtype");
                    var stack=drop.getItem().copy();drop.setPos(pos.getX()+.5,pos.getY()+.4,pos.getZ()+.5);
                    state().entityInside(level,pos,drop);
                    check(!drop.isRemoved()&&ItemStack.matches(stack,drop.getItem()),"ejectedProductIsNotRedissolved");drop.discard();
                }
                mine(level);proof.put("status","PASS");finish(event,null);
            }
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());finish(event,failure);}
    }
    private net.minecraft.world.level.block.state.BlockState state(){return content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockMetalDevice",0));}
    private void setup(ServerLevel level)throws Exception {
        pos=new BlockPos(0,100,0);level.setChunkForced(0,0,true);
        for(int x=-2;x<=5;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.below(),Blocks.LAVA.defaultBlockState());level.setBlockAndUpdate(pos,state());
        tile=((LegacyBlockEntity)level.getBlockEntity(pos)).legacy();
        player=FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(.5,100,-1.5);player.setOnGround(true);player.getInventory().clearContent();
        var services=content.services();var loader=services.loader();
        services.items().inLegacy(()->{
            for(Object candidate:(List<?>)loader.loadClass("thaumcraft.api.ThaumcraftApi").getMethod("getCraftingRecipes").invoke(null))
                if(candidate.getClass().getName().equals("thaumcraft.api.crafting.CrucibleRecipe")&&candidate.getClass().getField("key").get(candidate).equals("ALUMENTUM")){recipe=candidate;break;}
            if(recipe==null)throw new IllegalStateException("Missing original ALUMENTUM recipe");
            expected=content.gameplayStack(services.items().snapshot(recipe.getClass().getMethod("getRecipeOutput").invoke(recipe)));return null;
        });
        seed(false);started=level.getGameTime();
    }
    @SuppressWarnings("unchecked") private void seed(boolean researched)throws Exception {
        var services=content.services();var loader=services.loader();var world=services.worlds().bound(player.serverLevel());
        services.items().inLegacy(()->{try(var scope=services.playerState().enter(world)) {
            Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
            Object knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
            ((Map<String,ArrayList<String>>)knowledge.getClass().getField("researchCompleted").get(knowledge)).put(player.getName().getString(),new ArrayList<>(researched?List.of("ALUMENTUM"):List.of()));
            Object required=recipe.getClass().getField("aspects").get(recipe);
            tile.getClass().getField("aspects").set(tile,required.getClass().getMethod("copy").invoke(required));
            Object tank=tile.getClass().getField("tank").get(tile);
            Object water=loader.loadClass("net.minecraftforge.fluids.FluidRegistry").getField("WATER").get(null);
            Object fluid=loader.loadClass("net.minecraftforge.fluids.FluidStack").getConstructor(loader.loadClass("net.minecraftforge.fluids.Fluid"),int.class).newInstance(water,1000);
            tank.getClass().getMethod("setFluid",fluid.getClass()).invoke(tank,fluid);return null;
        }});
    }
    private int water()throws Exception{Object tank=tile.getClass().getField("tank").get(tile);return (int)tank.getClass().getMethod("getFluidAmount").invoke(tank);}
    private ItemEntity toss(ServerLevel level) {
        var drop=net.minecraftforge.common.ForgeHooks.onPlayerTossEvent(player,new ItemStack(Items.COAL),false);
        check(drop!=null,"nativeTossAccepted");drop.setPos(pos.getX()+.5,pos.getY()+.65,pos.getZ()+.5);drop.setDeltaMovement(0,0,0);return drop;
    }
    private List<ItemEntity> outputs(ServerLevel level){return level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(4),drop->!drop.isRemoved()&&ItemStack.isSameItemSameTags(drop.getItem(),expected));}
    private void mine(ServerLevel level)throws Exception {
        var target=pos.east(3);level.setBlockAndUpdate(target,state());
        check(state().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),"nativePickaxeMiningTag");
        var iron=new ItemStack(Items.IRON_PICKAXE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,iron);
        check(iron.getDestroySpeed(state())>1,"nativeIronPickaxeEffectiveSpeed");
        check(iron.isCorrectToolForDrops(state()),"nativeIronPickaxeCanHarvest");
        check(!new ItemStack(Items.STICK).isCorrectToolForDrops(state())&&state().requiresCorrectToolForDrops(),"bareHandCannotHarvestMetalDevice");
        var oldPick=content.stack("Thaumcraft:ItemPickThaumium",0,1,"");check(oldPick.getDestroySpeed(state())>1&&oldPick.isCorrectToolForDrops(state()),"originalThaumiumPickaxeStillWorks");
        player.setPos(target.getX()+.5,target.getY(),target.getZ()-1.5);
        check(state().getDestroyProgress(player,level,target)>1f/100,"pickaxeBreaksInFiniteNormalTime");
        check(player.gameMode.destroyBlock(target)&&level.isEmptyBlock(target),"survivalPlayerDestroysCrucible");
        var drops=level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(target).inflate(1),drop->!drop.isRemoved()&&drop.getItem().is(content.itemForBlock("Thaumcraft:blockMetalDevice",0).getItem()));
        check(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"survivalBreakDropsOneCrucible");
    }
    private void finish(TickEvent.ServerTickEvent event,Throwable failure) {
        done=true;
        try{var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/crucible-smoke.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception error){throw new IllegalStateException(error);}
        if(failure==null)com.mojang.logging.LogUtils.getLogger().info("MFCL_CRUCIBLE_SMOKE_PASS: {}",proof);
        else com.mojang.logging.LogUtils.getLogger().error("MFCL_CRUCIBLE_SMOKE_FAIL: "+proof,failure);
        event.getServer().halt(false);
    }
}
