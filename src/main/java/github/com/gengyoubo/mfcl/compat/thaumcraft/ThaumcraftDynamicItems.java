package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.client.LiveLegacyItemCapture;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import java.util.*;

/** TC4 chooses when its original scanner renderer needs the current target/knowledge. */
final class ThaumcraftDynamicItems {
    private ThaumcraftDynamicItems() { }
    static void install(LegacyServices services) {
        var content = ModernContentBridge.active();
        if (content == null || content.services() != services) throw new IllegalStateException("TC4 client content is unavailable");
        services.dynamicItems().register(new LegacyDynamicItems.Renderer() {
            private final Set<String> unmappedTargets = new HashSet<>();
            @Override public String id() { return "thaumcraft4:thaumometer"; }
            @Override public boolean supports(String item, String context) {
                return item.equals("Thaumcraft:ItemThaumometer") && context.equals("EQUIPPED_FIRST_PERSON");
            }
            @Override public Optional<LegacyDynamicItems.Drawing> capture(LegacyClientData.Stack stack, String context) throws Exception {
                var mc = Minecraft.getInstance();
                if (mc.level == null || mc.player == null) return Optional.empty();
                try { return Optional.of(LiveLegacyItemCapture.firstPerson(content, stack)); }
                catch (Exception failure) {
                    var unmapped = UnmappedLegacyBlockException.find(failure);
                    if (unmapped == null) throw failure;
                    if (unmappedTargets.add(unmapped.getMessage())) com.mojang.logging.LogUtils.getLogger().warn("MFCL TC4 scanner overlay unavailable: {}", unmapped.getMessage());
                    return Optional.empty(); // Keep the frozen idle lens; never invent aspects for a post-1.7 block.
                }
            }
        });
    }
}
