package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import java.lang.reflect.*;

/** TC4's portable containers commit changed contents to their opening stack, without a held-item swap. */
public final class ThaumcraftPortableContainerHooks {
    private ThaumcraftPortableContainerHooks() {}
    private static Field field(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static boolean harness(Object container){return container.getClass().getName().equals("thaumcraft.common.container.ContainerHoverHarness");}
    private static Object held(Object container)throws Exception{return field(container,harness(container)?"armor":"pouch").get(container);}
    private static int hotbarSlot(Object container)throws Exception{return field(container,"blockSlot").getInt(container)-(harness(container)?28:45);}
    public static boolean clickAllowed(Object container,int slot,int button,int mode,Object player)throws Exception {
        if(slot==field(container,"blockSlot").getInt(container)||mode==2&&button==hotbarSlot(container))return false;
        if(harness(container)&&slot==0&&mode==2){
            Object inventory=player.getClass().getField("field_71071_by").get(player);
            Object[] stacks=(Object[])inventory.getClass().getField("field_70462_a").get(inventory);
            if(button<0||button>=9)return false;
            Object input=container.getClass().getField("input").get(container);
            return stacks[button]==null||(boolean)input.getClass().getMethod("func_94041_b",int.class,container.getClass().getClassLoader().loadClass("net.minecraft.item.ItemStack")).invoke(input,0,stacks[button]);
        }
        return true;
    }
    public static boolean valid(Object container,Object player) throws Exception {
        Object inventory=player.getClass().getField("field_71071_by").get(player);
        int slot=hotbarSlot(container);
        Object[] stacks=(Object[])inventory.getClass().getField("field_70462_a").get(inventory);
        Object pouch=held(container);
        return pouch!=null&&pouch.getClass().getField("field_77994_a").getInt(pouch)>0&&slot>=0&&slot<9&&stacks[slot]==pouch;
    }
    /** Called at construction to remember the original contents; no NBT write or packet for an unchanged pouch. */
    public static Object remember(Object container) throws Exception {
        Object input=container.getClass().getField("input").get(container);
        Object[] stacks=(Object[])field(input,"stackList").get(input);
        Object[] copies=stacks.clone();
        for(int index=0;index<copies.length;index++)if(copies[index]!=null)copies[index]=copies[index].getClass().getMethod("func_77946_l").invoke(copies[index]);
        return copies;
    }
    public static void save(Object container) throws Exception {
        Object world=field(container,"worldObj").get(container);
        if(world.getClass().getField("field_72995_K").getBoolean(world))return;
        Object player=field(container,"player").get(container);
        // Removed/replaced bags must never be recreated from a stale GUI, including delayed clicks/close packets.
        if(!valid(container,player))return;
        Object input=container.getClass().getField("input").get(container);
        Object[] stacks=(Object[])field(input,"stackList").get(input);
        Field saved=container.getClass().getField("mfcl$portableContents");Object[] prior=(Object[])saved.get(container);
        Class<?> stack=container.getClass().getClassLoader().loadClass("net.minecraft.item.ItemStack");
        Method equal=stack.getMethod("func_77989_b",stack,stack);
        boolean changed=prior==null;
        for(int index=0;!changed&&index<stacks.length;index++)changed=!(boolean)equal.invoke(null,stacks[index],prior[index]);
        if(!changed)return;
        Object pouch=held(container);
        if(harness(container)){
            Class<?> compound=container.getClass().getClassLoader().loadClass("net.minecraft.nbt.NBTTagCompound");
            Object tag=compound.getConstructor().newInstance();
            if(stacks[0]!=null)stack.getMethod("func_77955_b",compound).invoke(stacks[0],tag);
            stack.getMethod("func_77983_a",String.class,container.getClass().getClassLoader().loadClass("net.minecraft.nbt.NBTBase")).invoke(pouch,"jar",tag);
        }else{
            Object item=stack.getMethod("func_77973_b").invoke(pouch);
            item.getClass().getMethod("setInventory",stack,stacks.getClass()).invoke(item,pouch,stacks);
        }
        saved.set(container,remember(container));
    }
}
