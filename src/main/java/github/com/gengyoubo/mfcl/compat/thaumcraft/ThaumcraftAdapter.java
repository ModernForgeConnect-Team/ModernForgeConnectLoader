package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** TC4-owned compatibility implementation; the core depends only on generic extension points. */
public final class ThaumcraftAdapter implements LegacyModAdapter {
    private LegacyServices services;
    @Override public String id() { return "thaumcraft4"; }
    @Override public boolean supports(Set<String> mods) { return mods.contains("Thaumcraft"); }
    @Override public Map<String,Object> initialize(LegacyServices services) {
        this.services = services;
        var loader = services.loader();
        LegacyPackets.allowServer(loader,ThaumcraftDevicePackets::prepare);
        services.playerState().register(new LegacyPlayerState.Hooks() {
            @Override public String id() { return "thaumcraft4:knowledge"; }
            @Override public AutoCloseable enter(Object world) throws Exception { return ThaumcraftKnowledge.server(loader, world); }
            @Override public void initialize(Object player) throws Exception { ThaumcraftKnowledge.initialize(loader, player); }
            @Override public void save(Object player, Object nbt) throws Exception { ThaumcraftKnowledge.nbt(loader, player, nbt, true); }
            @Override public void load(Object player, Object nbt) throws Exception { ThaumcraftKnowledge.nbt(loader, player, nbt, false); }
        });
        return Map.of("status", "READY", "playerStateHooks", "thaumcraft4:knowledge");
    }
    @Override public void installClient() {
        ThaumcraftDynamicItems.install(services);
        try {services.items().inLegacy(()->{
            var loader=services.loader();var tc=loader.loadClass("thaumcraft.common.Thaumcraft");
            Object instance=tc.getField("instance").get(null);var renderField=tc.getField("renderEventHandler");Object renderer=renderField.get(instance);
            if(renderer==null){renderer=loader.loadClass("thaumcraft.client.lib.RenderEventHandler").getConstructor().newInstance();renderField.set(instance,renderer);}
            // TC normally initializes this in its old HUD event before research GUIs render notifications.
            var notifications=renderer.getClass().getField("notifyHandler");
            if(notifications.get(renderer)==null)notifications.set(renderer,loader.loadClass("thaumcraft.client.lib.REHNotifyHandler").getConstructor().newInstance());
            return null;
        });}catch(Exception failure){throw new IllegalStateException("Cannot initialize original TC4 GUI notifications",failure);}
    }
}
