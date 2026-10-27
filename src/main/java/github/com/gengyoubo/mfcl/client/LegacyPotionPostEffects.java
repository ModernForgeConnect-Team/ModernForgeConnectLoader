package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import java.util.*;

/** Original potion screen filters rendered by native PostChain, without taking over the game's shader slot. */
final class LegacyPotionPostEffects {
    private static final Map<String,PostChain> ACTIVE=new LinkedHashMap<>();
    private static final Set<String> FAILED=new HashSet<>();
    private static int width,height;
    static Set<String> activeEffects(){return Set.copyOf(ACTIVE.keySet());}
    static void install(ModernContentBridge content,IEventBus bus){
        bus.addListener((RegisterClientReloadListenersEvent event)->event.registerReloadListener((ResourceManagerReloadListener)manager->{close();FAILED.clear();}));
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event)->{
            if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
            var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null){close();return;}
            var shaders=content.potions().shaders();var wanted=new LinkedHashSet<String>();
            shaders.forEach((key,resource)->{var effect=content.potions().effect(key);if(effect!=null&&mc.player.hasEffect(effect))wanted.add(key);});
            var it=ACTIVE.entrySet().iterator();while(it.hasNext()){var entry=it.next();if(!wanted.contains(entry.getKey())){entry.getValue().close();it.remove();}}
            int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();
            for(String key:wanted)if(!ACTIVE.containsKey(key)&&!FAILED.contains(key))try{
                var chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),mc.getMainRenderTarget(),shaders.get(key));chain.resize(w,h);ACTIVE.put(key,chain);
            }catch(Exception failure){FAILED.add(key);com.mojang.logging.LogUtils.getLogger().error("MFCL original potion screen filter failed: {}",key,failure);}
            if(w!=width||h!=height){ACTIVE.values().forEach(chain->chain.resize(w,h));width=w;height=h;}
            if(ACTIVE.isEmpty())return;
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();
            try{for(var chain:ACTIVE.values())chain.process(event.getPartialTick());}
            finally{mc.getMainRenderTarget().bindWrite(false);RenderSystem.enableDepthTest();}
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.ClientTickEvent event)->{if(event.phase==net.minecraftforge.event.TickEvent.Phase.END&&Minecraft.getInstance().level==null)close();});
    }
    private static void close(){ACTIVE.values().forEach(PostChain::close);ACTIVE.clear();width=height=0;}
}
