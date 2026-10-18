package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.world.item.ItemStack;

/** Client entry point for the common stack mapping used by GUI and creative-tab bridges. */
public final class ModernItemMapping {
    private ModernItemMapping() {}
    public static ItemStack renderStack(LegacyClientData.Stack old) {
        var content = ModernContentBridge.active();
        return content == null ? HostStackMapping.renderStack(old) : content.mapStack(old);
    }
}
