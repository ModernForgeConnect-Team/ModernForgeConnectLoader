package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.LegacyObjects;
import github.com.gengyoubo.mfcl.bridge.LegacyPlayerState;
import java.util.Map;

/**
 * TC research rules on MFCL's generic player lifecycle: original player-load defaults (normally applied by its old
 * player-data loading event), NBT save/load, and per-server-world knowledge maps.
 */
final class ThaumcraftKnowledge implements LegacyPlayerState.Hooks {
    static final String ID = "thaumcraft4:knowledge";
    /** NBT section -> CommonProxy map that the original load*NBT methods add to. */
    private static final String[][] SECTIONS={{"THAUMCRAFT.RESEARCH","getCompletedResearch"},{"THAUMCRAFT.ASPECTS","getKnownAspects"},
            {"THAUMCRAFT.SCAN.OBJECTS","getScannedObjects"},{"THAUMCRAFT.SCAN.ENTITIES","getScannedEntities"},{"THAUMCRAFT.SCAN.PHENOMENA","getScannedPhenomena"}};
    private final ClassLoader loader;
    private final LegacyPlayerState.ServerScoped server;
    ThaumcraftKnowledge(ClassLoader loader) throws Exception {
        this.loader=loader;
        var knowledge=loader.loadClass("thaumcraft.common.lib.research.PlayerKnowledge").getConstructor();
        server=new LegacyPlayerState.ServerScoped(()->ThaumcraftReflection.proxy(loader),loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge"),knowledge::newInstance);
    }
    @Override public String id() { return ID; }
    /** Called inside the runtime lock: an integrated server must not share its research maps with the client. */
    @Override public AutoCloseable enter(Object world) throws Exception { return server.enter(world); }
    @Override public void save(Object player,Object nbt) throws Exception { nbt(player,nbt,true); }
    @Override public void load(Object player,Object nbt) throws Exception { nbt(player,nbt,false); }
    @Override public void synchronize(Object player) throws Exception { ThaumcraftScanner.synchronize(loader,player); }
    private void nbt(Object player,Object nbt,boolean save) throws Exception {
        // The original load*NBT methods only add entries. Writing saved data replaces the player's state, so a rolled-back
        // transaction must not keep scans or research it completed; sections absent from the NBT keep their current entries.
        if(!save) {
            Object proxy=ThaumcraftReflection.proxy(loader);
            String name=LegacyObjects.playerName(player);
            var has=nbt.getClass().getMethod("func_74764_b",String.class);
            for(String[] section:SECTIONS)
                if((boolean)has.invoke(nbt,section[0]))((Map<?,?>)proxy.getClass().getMethod(section[1]).invoke(proxy)).remove(name);
        }
        var manager=loader.loadClass("thaumcraft.common.lib.research.ResearchManager");
        Class<?> nbtType=loader.loadClass("net.minecraft.nbt.NBTTagCompound"),playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        for(String section:new String[]{"Research","Aspect","Scanned"})manager.getMethod((save?"save":"load")+section+"NBT",nbtType,playerType).invoke(null,nbt,player);
        // TC4 saves these in savePlayerData, outside saveResearchNBT. MFCL persists the actor's entity NBT instead.
        Object knowledge=ThaumcraftReflection.proxy(loader).getClass().getMethod("getPlayerKnowledge").invoke(ThaumcraftReflection.proxy(loader));
        String playerName=LegacyObjects.playerName(player);
        for(String[] warp:new String[][]{{"Thaumcraft.eldritch","WarpPerm"},{"Thaumcraft.eldritch.sticky","WarpSticky"},{"Thaumcraft.eldritch.temp","WarpTemp"},{"Thaumcraft.eldritch.counter","WarpCounter"}}) {
            if(save)nbtType.getMethod("func_74768_a",String.class,int.class).invoke(nbt,warp[0],knowledge.getClass().getMethod("get"+warp[1],String.class).invoke(knowledge,playerName));
            else if((boolean)nbtType.getMethod("func_74764_b",String.class).invoke(nbt,warp[0]))knowledge.getClass().getMethod("set"+warp[1],String.class,int.class).invoke(knowledge,playerName,nbtType.getMethod("func_74762_e",String.class).invoke(nbt,warp[0]));
        }
        // saveResearchNBT omits auto-unlocked entries; TC re-adds them on every world join, so a load must restore them
        // too, or the book reports the first real entries (FOCUSFIRE, NITOR, RESEARCHER1...) as missing prerequisites.
        if(!save) {
            var complete=manager.getMethod("completeResearchUnsaved",String.class,String.class);String name=LegacyObjects.playerName(player);
            for(Object key:autoUnlocked())complete.invoke(null,name,key);
        }
    }
    @Override public void initialize(Object player) throws Exception {
        Class<?> playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object proxy=ThaumcraftReflection.proxy(loader);
        var known=(Map<?,?>)proxy.getClass().getMethod("getKnownAspects").invoke(proxy);
        var managerType=loader.loadClass("thaumcraft.common.lib.research.ResearchManager");
        if(!known.containsKey(LegacyObjects.playerName(player)))managerType.getMethod("loadPlayerData",playerType,java.io.File.class,java.io.File.class,boolean.class).invoke(null,player,null,null,false);
        Object manager=proxy.getClass().getMethod("getResearchManager").invoke(proxy);
        var complete=managerType.getMethod("completeResearch",playerType,String.class);
        for(Object key:autoUnlocked())complete.invoke(manager,player,key);
    }
    private java.util.List<Object> autoUnlocked() throws Exception {
        var keys=new java.util.ArrayList<Object>();
        var categories=(Map<?,?>)loader.loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
        for(Object category:categories.values())for(Object research:((Map<?,?>)category.getClass().getField("research").get(category)).values())
            if((boolean)research.getClass().getMethod("isAutoUnlock").invoke(research))keys.add(research.getClass().getField("key").get(research));
        return keys;
    }
}
