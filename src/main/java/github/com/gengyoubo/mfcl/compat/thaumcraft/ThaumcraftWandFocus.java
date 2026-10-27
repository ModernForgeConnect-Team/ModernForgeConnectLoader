package github.com.gengyoubo.mfcl.compat.thaumcraft;

import java.util.*;

/** Original sorting keys, including foci inside inventory pouches; the server never accepts client item data. */
public final class ThaumcraftWandFocus {
    private ThaumcraftWandFocus() {}
    /** Use TC4's NBT-aware predicate: sceptres share the wand item ID with wands and staves. */
    public static boolean supportsFocus(ClassLoader loader,Object stack) throws Exception {
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        Object item=present(stackType,stack);
        return loader.loadClass("thaumcraft.common.items.wands.ItemWandCasting").isInstance(item)
                && !(boolean)item.getClass().getMethod("isSceptre",stackType).invoke(item,stack);
    }
    public static Map<String,Object> available(ClassLoader loader,Object player) throws Exception {
        Object inventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        var result=new TreeMap<String,Object>();
        for(Object stack:(Object[])inventory.getClass().getField("field_70462_a").get(inventory))add(loader,stack,result);
        return result;
    }
    public static void add(ClassLoader loader,Object stack,Map<String,Object> result) throws Exception {
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        var focusType=loader.loadClass("thaumcraft.api.wands.ItemFocusBasic");
        Object item=present(stackType,stack);
        if(focusType.isInstance(item))put(stackType,item,stack,result);
        else if(loader.loadClass("thaumcraft.common.items.wands.ItemFocusPouch").isInstance(item))
            for(Object focus:(Object[])item.getClass().getMethod("getInventory",stackType).invoke(item,stack)) {
                Object inner=present(stackType,focus);
                if(focusType.isInstance(inner))put(stackType,inner,focus,result);
            }
    }
    /** The item of a non-empty stack, otherwise null. */
    private static Object present(Class<?> stackType,Object stack) throws Exception {
        if(stack==null||stackType.getField("field_77994_a").getInt(stack)<=0)return null;
        return stackType.getMethod("func_77973_b").invoke(stack);
    }
    private static void put(Class<?> stackType,Object focus,Object stack,Map<String,Object> result) throws Exception {
        result.put((String)focus.getClass().getMethod("getSortingHelper",stackType).invoke(focus,stack),stack);
    }
}
