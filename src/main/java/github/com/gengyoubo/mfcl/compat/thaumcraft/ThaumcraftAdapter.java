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
    @Override public void installClient() { ThaumcraftDynamicItems.install(services); }
}
