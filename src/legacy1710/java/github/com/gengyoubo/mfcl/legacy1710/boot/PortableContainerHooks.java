package github.com.gengyoubo.mfcl.legacy1710.boot;

import java.lang.reflect.*;
import java.util.*;

/** Shared identity, slot validation and change tracking for original item-backed containers. */
public final class PortableContainerHooks {
    public interface Policy {
        Object player(Object container) throws Exception;
        Object world(Object container) throws Exception;
        Object openingStack(Object container) throws Exception;
        int openingContainerSlot(Object container) throws Exception;
        Object[] contents(Object container) throws Exception;
        void writeContents(Object container,Object stack,Object[] contents) throws Exception;
    }
    private static final Map<String,Policy> POLICIES=new HashMap<String,Policy>();
    private PortableContainerHooks() {}
    public static synchronized void register(String type,Policy policy) {
        if(type==null||policy==null)throw new NullPointerException("Portable container policy");
        if(POLICIES.containsKey(type))throw new IllegalArgumentException("Portable container policy already registered: "+type);
        POLICIES.put(type,policy);
    }
    private static synchronized Policy policy(Object container) {
        Policy policy=POLICIES.get(container.getClass().getName());
        if(policy==null)throw new IllegalArgumentException("No portable container policy for "+container.getClass().getName());
        return policy;
    }
    private static Object inventory(Object player)throws Exception{return player.getClass().getField("field_71071_by").get(player);}
    private static List<?> slots(Object container)throws Exception{return (List<?>)container.getClass().getField("field_75151_b").get(container);}
    private static int openingInventorySlot(Object container,Object player,Policy policy)throws Exception {
        int index=policy.openingContainerSlot(container);List<?> slots=slots(container);
        if(index<0||index>=slots.size())return -1;
        Object slot=slots.get(index);
        Class<?> slotType=container.getClass().getClassLoader().loadClass("net.minecraft.inventory.Slot");
        return slotType.getField("field_75224_c").get(slot)==inventory(player)?(int)slotType.getMethod("getSlotIndex").invoke(slot):-1;
    }
    public static boolean clickAllowed(Object container,int slot,int button,int mode,Object player)throws Exception {
        Policy policy=policy(container);
        if(slot==policy.openingContainerSlot(container))return false;
        if(mode!=2)return true;
        if(button<0||button>=9||button==openingInventorySlot(container,player,policy))return false;
        List<?> slots=slots(container);if(slot<0||slot>=slots.size())return false;
        Object target=slots.get(slot),sourceInventory=inventory(player);
        Object source=((Object[])sourceInventory.getClass().getField("field_70462_a").get(sourceInventory))[button];
        if(source==null)return true;
        ClassLoader loader=container.getClass().getClassLoader();Class<?> stack=loader.loadClass("net.minecraft.item.ItemStack"),slotType=loader.loadClass("net.minecraft.inventory.Slot");
        // Some original containers bypass these checks in their number-key branch.
        if(!(boolean)slotType.getMethod("func_75214_a",stack).invoke(target,source))return false;
        Object targetInventory=slotType.getField("field_75224_c").get(target);
        int index=(int)slotType.getMethod("getSlotIndex").invoke(target);
        return (boolean)loader.loadClass("net.minecraft.inventory.IInventory").getMethod("func_94041_b",int.class,stack).invoke(targetInventory,index,source);
    }
    public static boolean valid(Object container,Object player)throws Exception {
        Policy policy=policy(container);int index=openingInventorySlot(container,player,policy);
        Object[] stacks=(Object[])inventory(player).getClass().getField("field_70462_a").get(inventory(player));
        Object opening=policy.openingStack(container);
        return opening!=null&&opening.getClass().getField("field_77994_a").getInt(opening)>0&&index>=0&&index<stacks.length&&stacks[index]==opening;
    }
    public static Object remember(Object container)throws Exception {
        Object[] copies=policy(container).contents(container).clone();
        for(int index=0;index<copies.length;index++)if(copies[index]!=null)copies[index]=copies[index].getClass().getMethod("func_77946_l").invoke(copies[index]);
        return copies;
    }
    public static void save(Object container)throws Exception {
        Policy policy=policy(container);Object world=policy.world(container);
        if(world.getClass().getField("field_72995_K").getBoolean(world)||!valid(container,policy.player(container)))return;
        Object[] stacks=policy.contents(container);Field saved=container.getClass().getField("mfcl$portableContents");Object[] prior=(Object[])saved.get(container);
        Class<?> stack=container.getClass().getClassLoader().loadClass("net.minecraft.item.ItemStack");Method equal=stack.getMethod("func_77989_b",stack,stack);
        boolean changed=prior==null||prior.length!=stacks.length;
        for(int index=0;!changed&&index<stacks.length;index++)changed=!(boolean)equal.invoke(null,stacks[index],prior[index]);
        if(!changed)return;
        policy.writeContents(container,policy.openingStack(container),stacks);saved.set(container,remember(container));
    }
}
