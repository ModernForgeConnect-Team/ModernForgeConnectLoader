package github.com.gengyoubo.mfcl.legacy1710.probe;

import github.com.gengyoubo.mfcl.legacy1710.boot.PortableContainerHooks;
import java.lang.reflect.*;
import java.util.*;

/** Server acceptance fixture with its own field layout and storage callback, independent of any reference mod. */
public final class PortableContainerProbe {
    public static final class Fixture {
        public final List<Object> field_75151_b=new ArrayList<Object>();
        public Object mfcl$portableContents;
        Object actor,world,opening;
        Object[] contents;
        int writes;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static Map<String,Object> run(final Object world,final Object player,Object opening,Object payload)throws Exception {
        final ClassLoader loader=PortableContainerProbe.class.getClassLoader();Class<?> stack=loader.loadClass("net.minecraft.item.ItemStack");
        Object inventory=player.getClass().getField("field_71071_by").get(player);Object[] main=(Object[])inventory.getClass().getField("field_70462_a").get(inventory);
        Object[] prior=main.clone();Map<String,Object> proof=new LinkedHashMap<String,Object>();
        try {
            main[3]=opening;main[4]=payload;main[5]=null;
            PortableContainerHooks.register(Fixture.class.getName(),new PortableContainerHooks.Policy(){
                public Object player(Object value){return ((Fixture)value).actor;}
                public Object world(Object value){return ((Fixture)value).world;}
                public Object openingStack(Object value){return ((Fixture)value).opening;}
                public int openingContainerSlot(Object value){return 0;}
                public Object[] contents(Object value){return ((Fixture)value).contents;}
                public void writeContents(Object value,Object target,Object[] contents){check(target==((Fixture)value).opening,"Wrong opening item");((Fixture)value).writes++;}
            });
            Fixture fixture=new Fixture();fixture.actor=player;fixture.world=world;fixture.opening=opening;
            fixture.contents=(Object[])Array.newInstance(stack,2);fixture.contents[0]=stack.getMethod("func_77946_l").invoke(payload);
            Class<?> inv=loader.loadClass("net.minecraft.inventory.IInventory"),slot=loader.loadClass("net.minecraft.inventory.Slot");
            fixture.field_75151_b.add(slot.getConstructor(inv,int.class,int.class,int.class).newInstance(inventory,3,8,20));
            Object output=loader.loadClass("net.minecraft.inventory.InventoryBasic").getConstructor(String.class,boolean.class,int.class).newInstance("Fixture",true,1);
            fixture.field_75151_b.add(loader.loadClass("net.minecraft.inventory.SlotFurnace").getConstructor(loader.loadClass("net.minecraft.entity.player.EntityPlayer"),inv,int.class,int.class,int.class).newInstance(player,output,0,8,40));
            fixture.mfcl$portableContents=PortableContainerHooks.remember(fixture);
            check(PortableContainerHooks.valid(fixture,player),"Fixture opening slot did not resolve");
            check(!PortableContainerHooks.clickAllowed(fixture,0,0,0,player)&&!PortableContainerHooks.clickAllowed(fixture,1,3,2,player),"Opening item was movable");
            check(!PortableContainerHooks.clickAllowed(fixture,1,4,2,player),"Number-key swap bypassed output-slot restriction");
            check(PortableContainerHooks.clickAllowed(fixture,1,5,2,player),"Empty number-key source was rejected");proof.put("derivedSlotAndOriginalRestrictions",true);
            PortableContainerHooks.save(fixture);check(fixture.writes==0,"Unchanged contents were written");
            stack.getField("field_77994_a").setInt(fixture.contents[0],2);PortableContainerHooks.save(fixture);
            check(fixture.writes==1,"Changed contents did not commit once");PortableContainerHooks.save(fixture);check(fixture.writes==1,"Unchanged contents committed twice");
            proof.put("changeTrackingAndPolicyCommit",true);
            main[3]=payload;check(!PortableContainerHooks.valid(fixture,player),"Replaced opening item remained valid");
            fixture.contents[1]=payload;PortableContainerHooks.save(fixture);check(fixture.writes==1&&main[3]==payload,"Stale container recreated opening item");
            proof.put("replacementCannotResurrectOpeningItem",true);
            return proof;
        } finally {System.arraycopy(prior,0,main,0,main.length);}
    }
}
