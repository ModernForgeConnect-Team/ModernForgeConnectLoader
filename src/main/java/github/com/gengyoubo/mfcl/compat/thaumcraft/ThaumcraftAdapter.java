package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

import static github.com.gengyoubo.tcmodern.tc4.Tc4Modern.TC;

/** TC4-owned compatibility implementation; the core depends only on generic extension points. */
public final class ThaumcraftAdapter implements LegacyModAdapter {
    public static final Map<String,String> CHINESE_ENTITY_NAMES=Map.of(
            "entity.Thaumcraft.CultistLeader.name","血腥主教 %1$s，%2$s",
            "entity.Thaumcraft.EldritchWarden.name","远古守卫 %1$s，%2$s",
            "entity.Thaumcraft.EldritchGolem.name","%1$s远古构装体",
            "mfcl.thaumcraft.egg.CultistLeader","血腥主教",
            "mfcl.thaumcraft.egg.EldritchWarden","远古守卫",
            "mfcl.thaumcraft.egg.EldritchGolem","远古构装体");
    public static final Map<String,String> ENGLISH_EGG_NAMES=Map.of(
            "mfcl.thaumcraft.egg.CultistLeader","Crimson Praetor",
            "mfcl.thaumcraft.egg.EldritchWarden","Eldritch Warden",
            "mfcl.thaumcraft.egg.EldritchGolem","Eldritch Construct");
    private LegacyServices services;
    @Override public String id() { return "thaumcraft4"; }
    @Override public boolean supports(Set<String> mods) { return mods.contains(TC); }
    @Override public void beforeBoot(ClassLoader loader) throws Exception {
        LegacyModAdapter.registerBytecodeAdapters(loader,
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftNoteAspectsAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftBiomeAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftWarpAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftDamageSourceAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftPrimalArrowAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftDoorAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftBoreAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftFocusPouchAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftHoverHarnessContainerAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftHoverHarnessAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftPortalAdapter",
                "github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft.ThaumcraftTrunkInventoryAdapter");
    }
    @Override public Map<String,Object> initialize(LegacyServices services) throws Exception {
        this.services = services;
        // TC creates champion names on the server too, before any client language is selected.
        services.items().initializeContext(services.gameDirectory(),List.of("assets/thaumcraft/lang/en_US.lang"));
        services.items().override("zh_cn",CHINESE_ENTITY_NAMES);
        services.items().supplement("en_us",ENGLISH_EGG_NAMES);
        services.items().supplement("zh_cn",Map.of("item.ItemResource.18.name","金币","tile.blockMagicalLog.silverwoodknot.name","银树树瘤"));
        services.items().supplement("en_us",Map.of("tile.blockMagicalLog.silverwoodknot.name","Silverwood Knot"));
        var loader = services.loader();
        var generation=loader.loadClass("thaumcraft.common.lib.world.ThaumcraftWorldGenerator");
        var config=loader.loadClass("thaumcraft.common.config.Config");
        services.enchantments().register("thaumcraft:repair",config.getField("enchRepair").get(null));
        services.enchantments().register("thaumcraft:haste",config.getField("enchHaste").get(null));
        for(String[] potion:new String[][]{{"fluxtaint","thaumcraft.api.potions.PotionFluxTaint"},{"visexhaust","thaumcraft.api.potions.PotionVisExhaust"},
                {"blurredvision","thaumcraft.common.lib.potions.PotionBlurredVision"},{"deathgaze","thaumcraft.common.lib.potions.PotionDeathGaze"},
                {"infvisexhaust","thaumcraft.common.lib.potions.PotionInfectiousVisExhaust"},{"sunscorned","thaumcraft.common.lib.potions.PotionSunScorned"},
                {"thaumarhia","thaumcraft.common.lib.potions.PotionThaumarhia"},{"unhunger","thaumcraft.common.lib.potions.PotionUnnaturalHunger"},{"warpward","thaumcraft.common.lib.potions.PotionWarpWard"}})
            services.potions().register("thaumcraft:"+potion[0],loader.loadClass(potion[1]).getField("instance").get(null),"thaumcraft:textures/misc/potions.png");
        ThaumcraftPotionEvents.install(services);
        for(String[] shader:new String[][]{{"deathgaze","desaturatetc"},{"blurredvision","blurtc"},{"unhunger","hunger"},{"sunscorned","sunscorned"}})
            services.potions().registerScreenFilter("thaumcraft:"+shader[0],"minecraft:shaders/post/"+shader[1]+".json");
        ThaumcraftEnchantments.install(services);
        ThaumcraftElementalTools.install(services);
        // {host id, ThaumcraftWorldGenerator field, Config weight field (null: never generated in the overworld)}
        for(String[] biome:new String[][]{{"thaumcraft:magical_forest","biomeMagicalForest","biomeMagicalForestWeight"},{"thaumcraft:tainted_land","biomeTaint","biomeTaintWeight"},
                {"thaumcraft:eerie","biomeEerie",null},{"thaumcraft:eldritch","biomeEldritchLands",null}})
            services.biomes().register(biome[0],generation.getField(biome[1]).get(null),biome[2]==null?0:config.getField(biome[2]).getInt(null));
        ThaumcraftOuterLands.install(services);
        LegacyDeviceContainers.registerPredictedContainer(loader,"thaumcraft.common.container.ContainerFocusPouch");
        LegacyDeviceContainers.registerPredictedContainer(loader,"thaumcraft.common.container.ContainerHoverHarness");
        LegacyDeviceContainers.registerPlayerEffectsListener(loader,"thaumcraft.common.container.ContainerArcaneWorkbench",(container,player)->
                container.getClass().getMethod("func_75130_a",loader.loadClass("net.minecraft.inventory.IInventory")).invoke(container,LegacyObjects.declared(container,"tileEntity")));
        LegacyPackets.allowServer(loader,ThaumcraftDevicePackets::prepare);
        services.playerState().register(new ThaumcraftKnowledge(loader));
        services.scanners().register(new ThaumcraftScanner(loader));
        ThaumcraftCrossbow.install(services);
        ThaumcraftBucketInteraction.install(services);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ThaumcraftResearchCommand::register);
        return Map.of("status", "READY", "playerStateHooks", ThaumcraftKnowledge.ID, "heldScanners", "thaumcraft4:thaumometer", "dimensions", ThaumcraftOuterLands.HOST_KEY);
    }
    @Override public void installClient() {
        try{ThaumcraftWarpVisuals.install(services);}catch(Exception failure){throw new IllegalStateException("Cannot install original warp feedback",failure);}
        ThaumcraftWandControls.install(services);
        ThaumcraftWandHud.install(services);
        ThaumcraftDynamicItems.install(services);
        ThaumcraftGoggles.install(services);
        ThaumcraftParticles.install(services);
        ThaumcraftProspecting.install(services);
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

