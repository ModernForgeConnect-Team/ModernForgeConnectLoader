package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;

/** Exercises real legacy side tests with host grass and grass-block neighbour states. */
final class GrassFaceHostSmokeProbe {
    private final ModernContentBridge content;private int stage;private boolean done;
    private GrassFaceHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new GrassFaceHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||done)return;var mc=Minecraft.getInstance();
        try{
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);stage=1;
                mc.createWorldOpenFlows().createFreshLevel("mfcl-grass-face-"+System.currentTimeMillis(),new LevelSettings("Grass face",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),a->a.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null){
                String key="Thaumcraft:blockCustomOre";var origin=mc.player.blockPosition().above(8);var adjacent=origin.east();
                mc.level.setBlock(origin,content.blocks().get(key).defaultBlockState().setValue(ModernContentBridge.METADATA,0),2);
                var counts=new ArrayList<Integer>();var mapped=new ArrayList<String>();
                for(var block:List.of(Blocks.AIR,Blocks.GRASS,Blocks.GRASS_BLOCK)){
                    mc.level.setBlock(adjacent,block.defaultBlockState(),2);var view=content.renderView(mc.level,origin);mapped.add(view.block(1,0,0).toString());
                    counts.add(content.blockRendering().capture(key,0,(x,y,z)->x==0&&y==0&&z==0?new HostWorldAccess.Block(key,0):x==1&&y==0&&z==0?view.block(x,y,z):new HostWorldAccess.Block("minecraft:air",0)).stream().mapToInt(layer->layer.geometry().size()).sum());
                }
                if(!counts.equals(List.of(6,6,5)))throw new IllegalStateException("Incorrect face counts "+counts+" mapped "+mapped);
                finish(mc,Map.of("status","PASS","faceCounts",counts,"mappedNeighbours",mapped));
            }
        }catch(Throwable failure){finish(mc,Map.of("status","FAIL","failure",failure.toString()));}
    }
    private void finish(Minecraft mc,Map<String,?> result){
        done=true;try{Path file=mc.gameDirectory.toPath().resolve("logs/mfcl/grass-face-smoke.json");Files.createDirectories(file.getParent());Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception failure){throw new IllegalStateException(failure);}
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
