package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** TC4-owned compatibility implementation; the core depends only on generic extension points. */
public final class ThaumcraftAdapter implements LegacyModAdapter {
    private LegacyServices services;
    @Override public String id() { return "thaumcraft4"; }
    @Override public boolean supports(Set<String> mods) { return mods.contains("Thaumcraft"); }
    @Override public void beforeBoot(ClassLoader loader) throws Exception {
        LegacyModAdapter.registerBytecodeAdapters(loader,
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftBiomeAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftPortalAdapter");
    }
    @Override public Map<String,Object> initialize(LegacyServices services) throws Exception {
        this.services = services;
        var loader = services.loader();
        var generation=loader.loadClass("thaumcraft.common.lib.world.ThaumcraftWorldGenerator");
        var config=loader.loadClass("thaumcraft.common.config.Config");
        // {host id, ThaumcraftWorldGenerator field, Config weight field (null: never generated in the overworld)}
        for(String[] biome:new String[][]{{"thaumcraft:magical_forest","biomeMagicalForest","biomeMagicalForestWeight"},{"thaumcraft:tainted_land","biomeTaint","biomeTaintWeight"},
                {"thaumcraft:eerie","biomeEerie",null},{"thaumcraft:eldritch","biomeEldritchLands",null}})
            services.biomes().register(biome[0],generation.getField(biome[1]).get(null),biome[2]==null?0:config.getField(biome[2]).getInt(null));
        ThaumcraftOuterLands.install(services);
        LegacyPackets.allowServer(loader,ThaumcraftDevicePackets::prepare);
        services.playerState().register(new LegacyPlayerState.Hooks() {
            @Override public String id() { return "thaumcraft4:knowledge"; }
            @Override public AutoCloseable enter(Object world) throws Exception { return ThaumcraftKnowledge.server(loader, world); }
            @Override public void initialize(Object player) throws Exception { ThaumcraftKnowledge.initialize(loader, player); }
            @Override public void save(Object player, Object nbt) throws Exception { ThaumcraftKnowledge.nbt(loader, player, nbt, true); }
            @Override public void load(Object player, Object nbt) throws Exception { ThaumcraftKnowledge.nbt(loader, player, nbt, false); }
            @Override public void synchronize(Object player) throws Exception { ThaumcraftScanner.synchronize(loader, player); }
        });
        services.scanners().register(new ThaumcraftScanner(loader));
        ThaumcraftCrossbow.install(services);
        return Map.of("status", "READY", "playerStateHooks", "thaumcraft4:knowledge", "heldScanners", "thaumcraft4:thaumometer", "dimensions", ThaumcraftOuterLands.HOST_KEY);
    }
    @Override public void installClient() {
        ThaumcraftWandControls.install(services);
        ThaumcraftDynamicItems.install(services);
        ThaumcraftGoggles.install(services);
        ThaumcraftParticles.install(services);
        try {services.items().inLegacy(()->{initializeNotifications();return null;});}
        catch(Exception failure){throw new IllegalStateException("Cannot initialize original TC4 GUI notifications",failure);}
    }
    private void initializeNotifications() throws Exception {
        var loader=services.loader();var tc=loader.loadClass("thaumcraft.common.Thaumcraft");
        Object instance=tc.getField("instance").get(null);var renderField=tc.getField("renderEventHandler");Object renderer=renderField.get(instance);
        if(renderer==null){renderer=loader.loadClass("thaumcraft.client.lib.RenderEventHandler").getConstructor().newInstance();renderField.set(instance,renderer);}
        // TC normally initializes this in its old HUD event before research GUIs render notifications.
        var notifications=renderer.getClass().getField("notifyHandler");
        if(notifications.get(renderer)==null)notifications.set(renderer,loader.loadClass("thaumcraft.client.lib.REHNotifyHandler").getConstructor().newInstance());
        // The old render-tick event normally creates this before PacketResearchComplete uses it.
        // A missing popup aborts that handler before it updates the book's prerequisite snapshot.
        var popup=loader.loadClass("thaumcraft.client.lib.ClientTickEventsFML").getField("researchPopup");
        if(popup.get(null)==null){
            var minecraft=loader.loadClass("net.minecraft.client.Minecraft");
            popup.set(null,loader.loadClass("thaumcraft.client.gui.GuiResearchPopup").getConstructor(minecraft).newInstance(minecraft.getMethod("func_71410_x").invoke(null)));
        }
    }
}
