package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.LegacyObjects;
import java.util.Map;

/** Original TC player-load defaults, normally applied by its old player-data loading event. */
public final class ThaumcraftKnowledge {
    private static final Map<Object,Object> SERVER=new java.util.WeakHashMap<>();
    /** NBT section -> CommonProxy map that the original load*NBT methods add to. */
    private static final String[][] SECTIONS={{"THAUMCRAFT.RESEARCH","getCompletedResearch"},{"THAUMCRAFT.ASPECTS","getKnownAspects"},
            {"THAUMCRAFT.SCAN.OBJECTS","getScannedObjects"},{"THAUMCRAFT.SCAN.ENTITIES","getScannedEntities"},{"THAUMCRAFT.SCAN.PHENOMENA","getScannedPhenomena"}};
    private ThaumcraftKnowledge() {}
    /** Called inside the runtime lock: an integrated server must not share its research maps with the client. */
    public static Scope server(ClassLoader loader,Object world) throws Exception {
        if(!ThaumcraftReflection.present(loader)||loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world))return new Scope(null,null,null);
        Object proxy=ThaumcraftReflection.proxy(loader);
        var field=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge");Object previous=field.get(proxy);
        Object knowledge=SERVER.get(world);
        if(knowledge==null){knowledge=loader.loadClass("thaumcraft.common.lib.research.PlayerKnowledge").getConstructor().newInstance();SERVER.put(world,knowledge);}
        field.set(proxy,knowledge);return new Scope(proxy,field,previous);
    }
    public record Scope(Object proxy,java.lang.reflect.Field field,Object previous) implements AutoCloseable {
        @Override public void close() throws IllegalAccessException {if(field!=null)field.set(proxy,previous);}
    }
    public static void nbt(ClassLoader loader,Object player,Object nbt,boolean save) throws Exception {
        if(!ThaumcraftReflection.present(loader))return;
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
        // saveResearchNBT omits auto-unlocked entries; TC re-adds them on every world join, so a load must restore them
        // too, or the book reports the first real entries (FOCUSFIRE, NITOR, RESEARCHER1...) as missing prerequisites.
        if(!save)autoUnlock(loader,manager,LegacyObjects.playerName(player));
    }
    private static void autoUnlock(ClassLoader loader,Class<?> manager,String name) throws Exception {
        var complete=manager.getMethod("completeResearchUnsaved",String.class,String.class);
        var categories=(Map<?,?>)loader.loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
        for(Object category:categories.values())for(Object research:((Map<?,?>)category.getClass().getField("research").get(category)).values())
            if((boolean)research.getClass().getMethod("isAutoUnlock").invoke(research))complete.invoke(null,name,research.getClass().getField("key").get(research));
    }
    public static void initialize(ClassLoader loader,Object player) throws Exception {
        if(!ThaumcraftReflection.present(loader))return;
        Class<?> playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object proxy=ThaumcraftReflection.proxy(loader);
        var known=(Map<?,?>)proxy.getClass().getMethod("getKnownAspects").invoke(proxy);
        var managerType=loader.loadClass("thaumcraft.common.lib.research.ResearchManager");
        if(!known.containsKey(LegacyObjects.playerName(player)))managerType.getMethod("loadPlayerData",playerType,java.io.File.class,java.io.File.class,boolean.class).invoke(null,player,null,null,false);
        Object manager=proxy.getClass().getMethod("getResearchManager").invoke(proxy);
        var complete=managerType.getMethod("completeResearch",playerType,String.class);
        var categories=(Map<?,?>)loader.loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
        for(Object category:categories.values())for(Object research:((Map<?,?>)category.getClass().getField("research").get(category)).values())
            if((boolean)research.getClass().getMethod("isAutoUnlock").invoke(research))complete.invoke(manager,player,research.getClass().getField("key").get(research));
    }
}
