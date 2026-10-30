package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Opt-in replay benchmark of a captured original research table with every aspect discovered. */
final class ResearchGuiPerformanceProbe {
    static Map<String,Object> measure(LegacyServices services,Minecraft mc) throws Exception {
        var commands=ClientContainerBridge.active().commands(mc.screen);
        var result=new LinkedHashMap<String,Object>();
        result.put("commands",commands.size());
        result.put("text",commands.stream().filter(c->c instanceof LegacyGuiGraphics.Text).count());
        var primitives=commands.stream().filter(c->c instanceof LegacyGuiGraphics.Mesh).flatMap(c->((LegacyGuiGraphics.Mesh)c).primitives().stream()).toList();
        result.put("primitives",primitives.size());
        result.put("vertices",primitives.stream().mapToInt(p->p.vertices().size()).sum());
        result.put("textures",primitives.stream().map(LegacyVertexStream.Primitive::texture).distinct().toList());
        var renderer=new LegacyGuiRenderer(services);
        var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
        long[] samples=new long[100];
        for(int i=-30;i<samples.length;i++) {
            graphics.fill(0,0,mc.screen.width,mc.screen.height,0xff404040);graphics.flush();
            long start=System.nanoTime();renderer.draw(graphics,commands);
            if(i>=0)samples[i]=System.nanoTime()-start;
        }
        Arrays.sort(samples);
        result.put("replayCpuMedianMs",samples[50]/1_000_000.0);
        result.put("replayCpuP95Ms",samples[95]/1_000_000.0);
        var path=mc.gameDirectory.toPath().resolve("screenshots/research-gui-performance.png");
        java.nio.file.Files.createDirectories(path.getParent());
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_RESEARCH_GUI_PERFORMANCE: {}",result);
        return result;
    }
}
