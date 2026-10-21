package github.com.gengyoubo.mfcl.compat.ic2;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** IC2's own GUI protocol, hosted through the shared original-container bridge. */
public final class Ic2Adapter implements LegacyModAdapter {
    public String id() { return "ic2"; }
    public boolean supports(Set<String> mods) { return mods.contains("IC2"); }
    public void beforeBoot(ClassLoader loader) throws Exception {
        LegacyModAdapter.registerBytecodeAdapters(loader,"github.com.gengyoubo.mfcl.legacy1710.compat.ic2.Ic2GuiAdapter");
    }
    public Map<String,Object> initialize(LegacyServices services) throws Exception {
        var loader=services.loader();var source=loader.loadClass("ic2.core.IHasGui");
        var player=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        var container=source.getMethod("getGuiContainer",player);
        var screen=source.getMethod("getGui",player,boolean.class);
        LegacyDeviceContainers.registerProvider(loader,"ic2",new LegacyDeviceContainers.GuiProvider() {
            public Object container(Object actor,Object tile) throws Exception { return container.invoke(tile,actor); }
            public Object screen(Object actor,Object tile) throws Exception { return screen.invoke(tile,actor,false); }
        });
        return Map.of("status","READY");
    }
}
