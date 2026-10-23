package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.PortableContainerHooks;
import java.lang.reflect.*;

/** TC4 field layout and storage format; the common portable-container bridge owns its lifecycle. */
final class ThaumcraftPortableContainerHooks implements PortableContainerHooks.Policy {
    private final boolean harness;
    ThaumcraftPortableContainerHooks(boolean harness){this.harness=harness;}
    private static Object field(Object target,String name)throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    public Object player(Object container)throws Exception{return field(container,"player");}
    public Object world(Object container)throws Exception{return field(container,"worldObj");}
    public Object openingStack(Object container)throws Exception{return field(container,harness?"armor":"pouch");}
    public int openingContainerSlot(Object container)throws Exception{return ((Number)field(container,"blockSlot")).intValue();}
    public Object[] contents(Object container)throws Exception{return (Object[])field(container.getClass().getField("input").get(container),"stackList");}
    public void writeContents(Object container,Object opening,Object[] stacks)throws Exception {
        ClassLoader loader=container.getClass().getClassLoader();Class<?> stack=loader.loadClass("net.minecraft.item.ItemStack");
        if(harness){
            Class<?> compound=loader.loadClass("net.minecraft.nbt.NBTTagCompound");Object tag=compound.getConstructor().newInstance();
            if(stacks[0]!=null)stack.getMethod("func_77955_b",compound).invoke(stacks[0],tag);
            stack.getMethod("func_77983_a",String.class,loader.loadClass("net.minecraft.nbt.NBTBase")).invoke(opening,"jar",tag);
        }else{
            Object item=stack.getMethod("func_77973_b").invoke(opening);
            item.getClass().getMethod("setInventory",stack,stacks.getClass()).invoke(item,opening,stacks);
        }
    }
}
