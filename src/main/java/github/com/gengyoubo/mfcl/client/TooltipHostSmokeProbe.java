package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import java.util.*;

/** Opt-in pixel comparison of native tooltips over JEI's actual single and batch ingredient renderers. */
final class TooltipHostSmokeProbe {
    private final ModernContentBridge content;
    private boolean complete;
    private TooltipHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new TooltipHostSmokeProbe(content)::render);}
    private void render(ScreenEvent.Render.Post event) {
        var mc=Minecraft.getInstance();
        if(complete||!(event.getScreen() instanceof TitleScreen)||mc.getOverlay()!=null)return;
        complete=true;
        var proof=new LinkedHashMap<String,Object>();
        try {
            if(content.services().items().loader().getResource("codechicken/nei/NEIClientConfig.class")!=null)
                throw new IllegalStateException("JEI acceptance must run without NEI");
            Object renderer=Class.forName("mezz.jei.library.render.ItemStackRenderer").getConstructor().newInstance();
            var single=renderer.getClass().getMethod("render",GuiGraphics.class,ItemStack.class,int.class,int.class);
            var batch=renderer.getClass().getMethod("renderBatch",GuiGraphics.class,List.class);
            var element=Class.forName("mezz.jei.api.ingredients.rendering.BatchRenderElement").getConstructor(Object.class,int.class,int.class);
            var items=new ArrayList<ItemStack>();
            for(int metadata=0;metadata<7;metadata++) {
                var stack=content.stack("Thaumcraft:blockCustomOre",metadata,1,"");
                if(stack.isEmpty())throw new IllegalStateException("Thaumcraft ore fixture missing");items.add(stack);
            }
            var graphics=event.getGuiGraphics();
            String label="Tooltip overlap";
            List<Component> lines=List.of(Component.literal(label));
            clear(graphics);graphics.renderComponentTooltip(mc.font,lines,50,58);graphics.flush();
            try(var reference=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                for(boolean batched:new boolean[]{false,true}) {
                    clear(graphics);
                    if(batched) {
                        var elements=new ArrayList<Object>();
                        for(int index=0;index<items.size();index++)elements.add(element.newInstance(items.get(index),40+index*18,40));
                        batch.invoke(renderer,graphics,elements);
                    } else for(int index=0;index<items.size();index++)single.invoke(renderer,graphics,items.get(index),40+index*18,40);
                    graphics.renderComponentTooltip(mc.font,lines,50,58);graphics.flush();
                    try(var actual=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                        int checked=compare(reference,actual,graphics,62,46,mc.font.width(label),8);
                        proof.put(batched?"batchTooltipPixels":"singleTooltipPixels",checked);
                        var screenshot=mc.gameDirectory.toPath().resolve("screenshots/jei-tooltip-"+(batched?"batch":"single")+".png");
                        java.nio.file.Files.createDirectories(screenshot.getParent());actual.writeToFile(screenshot);
                    }
                }
            }
            proof.put("neiAbsent",true);proof.put("status","PASS");
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_JEI_TOOLTIP_SMOKE_FAIL",failure);}
        try {
            var report=mc.gameDirectory.toPath().resolve("logs/mfcl/jei-tooltip-smoke.json");java.nio.file.Files.createDirectories(report.getParent());
            java.nio.file.Files.writeString(report,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        }catch(Exception failure){throw new IllegalStateException(failure);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_JEI_TOOLTIP_SMOKE_RESULT: {}",proof);mc.stop();
    }
    private static void clear(GuiGraphics graphics) {
        graphics.flush();RenderSystem.clear(256,Minecraft.ON_OSX);
        graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),0xff404040);graphics.flush();
    }
    private static int compare(NativeImage expected,NativeImage actual,GuiGraphics graphics,int x,int y,int width,int height) {
        double sx=(double)actual.getWidth()/graphics.guiWidth(),sy=(double)actual.getHeight()/graphics.guiHeight();int count=0,white=0;
        for(int py=(int)(y*sy);py<(int)((y+height)*sy);py++)for(int px=(int)(x*sx);px<(int)((x+width)*sx);px++) {
            int a=expected.getPixelRGBA(px,py),b=actual.getPixelRGBA(px,py);count++;
            if((a&255)>220&&(a>>8&255)>220&&(a>>16&255)>220)white++;
            for(int shift:new int[]{0,8,16})if(Math.abs((a>>shift&255)-(b>>shift&255))>22)
                throw new IllegalStateException("JEI ingredient occluded tooltip at "+px+","+py);
        }
        if(white==0)throw new IllegalStateException("Tooltip reference has no glyph pixels");return count;
    }
}
