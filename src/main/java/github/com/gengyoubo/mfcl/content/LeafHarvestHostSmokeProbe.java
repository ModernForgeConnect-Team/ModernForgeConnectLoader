package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Mirrors the pinned FTB Ultimine 1.20.1 loop: read current state, then ServerPlayerGameMode.destroyBlock. */
final class LeafHarvestHostSmokeProbe {
    private final ModernContentBridge content;
    private boolean done;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private static final BlockPos ORIGIN=new BlockPos(0,100,0);
    private static final AABB AREA=new AABB(-4,96,-4,20,108,20);
    private LeafHarvestHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new LeafHarvestHostSmokeProbe(content)::tick);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private net.minecraft.world.level.block.state.BlockState leaves(int metadata){return content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockMagicalLeaves",metadata));}
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;done=true;
        try{
            run(event.getServer().overworld());proof.put("status","PASS");
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL leaf harvest acceptance failed",failure);}
        try{var file=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/leaf-harvest-smoke.json");Files.createDirectories(file.getParent());Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception e){throw new IllegalStateException(e);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_LEAF_HARVEST_SMOKE {}",proof);event.getServer().halt(false);
    }
    private void run(ServerLevel level)throws Exception {
        var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.setPos(.5,100,-2.5);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(0,level.getServer());
        var cases=new ArrayList<Map<String,Object>>();proof.put("cases",cases);
        for(int type=0;type<2;type++){
            clearDrops(level);
            for(int i=0;i<4096;i++){level.setBlock(ORIGIN,leaves(type),2);check(player.gameMode.destroyBlock(ORIGIN),"Unflagged leaf not destroyed");}
            int unflagged=countDrops(level,type);check(unflagged==0,"Unflagged original leaves unexpectedly dropped saplings");
            var row=new LinkedHashMap<String,Object>();row.put("species",type==0?"greatwood":"silverwood");row.put("isolatedUnflaggedBroken",4096);row.put("isolatedUnflaggedSaplings",unflagged);
            int flagged=0,plain=0;
            for(int batch=0;batch<32;batch++){
                for(int z=0;z<16;z++)for(int x=0;x<8;x++)level.setBlock(ORIGIN.offset(x,0,z),leaves(type),2);
                // Every next leaf touches a removed one. No artificial decay flag is assigned by the fixture.
                for(int z=0;z<16;z++)for(int x=0;x<8;x++){
                    var at=ORIGIN.offset(x,0,z);var state=level.getBlockState(at);
                    if((state.getValue(ModernContentBridge.METADATA)&8)!=0)flagged++;else plain++;
                    check(player.gameMode.destroyBlock(at),"Chained leaf not destroyed");
                }
            }
            int chained=countDrops(level,type);check(chained>0,"Native FTB-style harvest dropped no saplings");
            row.put("chainedBroken",4096);row.put("chainedDecayFlagged",flagged);row.put("chainedUnflagged",plain);row.put("chainedSaplings",chained);cases.add(row);
            for(int i=0;i<100;i++){level.setBlock(ORIGIN,leaves(type|4),2);player.gameMode.destroyBlock(ORIGIN);}
            check(countDrops(level,type)==0,"Persistent player-placed leaves dropped saplings");
        }
        proof.put("persistentLeafNegativeControl",true);proof.put("noSilkTouchOrShears",player.getMainHandItem().isEmpty());
    }
    private void clearDrops(ServerLevel level){for(var item:level.getEntitiesOfClass(ItemEntity.class,AREA))item.discard();}
    private int countDrops(ServerLevel level,int type)throws Exception {
        int count=0;for(var item:level.getEntitiesOfClass(ItemEntity.class,AREA)){
            var stack=content.gameplayStack(item.getItem());check(stack!=null&&stack.key().equals("Thaumcraft:blockCustomPlant")&&stack.metadata()==type,"Unexpected leaf item "+stack);
            count+=stack.count();item.discard();
        }return count;
    }
}
