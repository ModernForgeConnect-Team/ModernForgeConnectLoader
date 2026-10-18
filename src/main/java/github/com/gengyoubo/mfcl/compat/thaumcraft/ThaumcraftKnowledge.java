package github.com.gengyoubo.mfcl.compat.thaumcraft;

import java.util.Map;

/** Original TC player-load defaults, normally applied by its old player-data loading event. */
public final class ThaumcraftKnowledge {
    private ThaumcraftKnowledge() {}
    public static void initialize(ClassLoader loader,Object player) throws Exception {
        if(loader.getResource("thaumcraft/common/Thaumcraft.class")==null)return;
        Class<?> tc=loader.loadClass("thaumcraft.common.Thaumcraft"),playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object proxy=tc.getField("proxy").get(null);
        String name=(String)playerType.getMethod("func_70005_c_").invoke(player);
        var known=(Map<?,?>)proxy.getClass().getMethod("getKnownAspects").invoke(proxy);
        var managerType=loader.loadClass("thaumcraft.common.lib.research.ResearchManager");
        if(!known.containsKey(name))managerType.getMethod("loadPlayerData",playerType,java.io.File.class,java.io.File.class,boolean.class).invoke(null,player,null,null,false);
        Object manager=proxy.getClass().getMethod("getResearchManager").invoke(proxy);
        var categories=(Map<?,?>)loader.loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
        for(Object category:categories.values())for(Object research:((Map<?,?>)category.getClass().getField("research").get(category)).values())
            if((boolean)research.getClass().getMethod("isAutoUnlock").invoke(research))
                managerType.getMethod("completeResearch",playerType,String.class).invoke(manager,player,research.getClass().getField("key").get(research));
    }
}
