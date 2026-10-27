package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/** Native rendering of TC4's original heartbeat/vignette and timed warp-fog packet state. */
public final class ThaumcraftWarpVisuals {
    private static volatile float fog,targetBrightness;
    private static float brightness;
    public static float fogDensity(){return fog;}
    public static boolean vignetteActive(){return targetBrightness>0;}
    static void install(LegacyServices services)throws Exception {
        var loader=services.loader();var events=loader.loadClass("thaumcraft.client.lib.RenderEventHandler");
        var vignette=loader.loadClass("thaumcraft.client.lib.ClientTickEventsFML").getField("warpVignette");
        var fogOn=events.getField("fogFiddled");var duration=events.getField("fogDuration");var density=events.getField("fogTarget");
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event)->{
            if(event.phase!=TickEvent.Phase.START)return;var mc=Minecraft.getInstance();
            try{services.items().inLegacy(()->{
                if(mc.level==null||mc.player==null){vignette.setInt(null,0);fogOn.setBoolean(null,false);duration.setInt(null,0);density.setFloat(null,0);fog=targetBrightness=brightness=0;return null;}
                if(mc.isPaused())return null;
                var content=ModernContentBridge.active();if(content!=null&&mc.player.hasEffect(content.potions().effect("thaumcraft:deathgaze")))vignette.setInt(null,10);
                int ticks=vignette.getInt(null);targetBrightness=ticks>0?1:0;if(ticks>0)vignette.setInt(null,ticks-1);
                float value=density.getFloat(null);
                if(fogOn.getBoolean(null)){
                    int left=duration.getInt(null);if(left<100)value=.1f*Math.max(0,left)/100f;else if(value<.1f)value=Math.min(.1f,value+.001f);
                    density.setFloat(null,value);duration.setInt(null,left-1);if(left-1<0)fogOn.setBoolean(null,false);
                }else value=0;
                fog=value;return null;
            });}catch(Exception failure){throw new IllegalStateException("Cannot update original warp feedback",failure);}
        });
        MinecraftForge.EVENT_BUS.addListener((ViewportEvent.RenderFog event)->{
            if(fog<=0)return;
            // Native fog shaders use a linear distance rather than the old fixed-function exponential density.
            float end=Math.min(event.getFarPlaneDistance(),3f/fog);event.setNearPlaneDistance(0);event.setFarPlaneDistance(end);event.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event)->{
            var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui)return;
            brightness+=(targetBrightness-brightness)*.01f;if(brightness<.001f)return;
            var content=ModernContentBridge.active();if(content==null)return;
            float pulse=brightness*(1+(float)Math.sin(mc.player.tickCount/2f)*.1f);int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
            var texture=ResourceLocation.parse(content.pack().worldResource("thaumcraft:textures/misc/vignette.png"));
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO,GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
            event.getGuiGraphics().setColor(pulse,pulse,pulse,1);
            event.getGuiGraphics().blit(texture,0,0,0,0f,0f,w,h,w,h);
            event.getGuiGraphics().setColor(1,1,1,1);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.defaultBlendFunc();
        });
    }
}
