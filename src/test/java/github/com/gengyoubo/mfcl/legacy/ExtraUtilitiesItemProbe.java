package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Real item GUI request and bag storage, including nonzero hotbar slots and inventory stack replacement. */
final class ExtraUtilitiesItemProbe {
    static void verify(LegacyServices services,Object world) throws Exception {
        services.items().inLegacy(()->{
            var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");
            Object bag=loader.loadClass("com.rwtema.extrautils.ExtraUtils").getField("goldenBag").get(null);
            var held=services.items().snapshot(stackType.getConstructor(loader.loadClass("net.minecraft.item.Item"),int.class,int.class).newInstance(bag,1,0));
            var source=new LegacyTiles.Placer(new UUID(66,77),"Bag",123,64,-89,0,0);
            var use=new LegacyItemUse(services);Object player=use.clientActor(world,source,null);
            var inventory=new ArrayList<LegacyClientData.Stack>(Collections.nCopies(36,null));inventory.set(5,held);
            services.playerInventory().write(player,inventory);services.playerInventory().select(player,5);
            var result=use.rightClick(world,source,held);
            if(!result.used()||result.device()==null||!result.device().descriptor().item()||result.device().descriptor().x()!=5)
                throw new AssertionError("Item GUI request was lost: "+result);
            var opened=result.device();
            if(LegacyDeviceContainers.slots(opened.container()).size()!=90)throw new AssertionError("Golden bag slot layout was lost");
            var container=loader.loadClass("net.minecraft.inventory.Container");var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            if(playerType.getField("field_71069_bz").get(player)==null)throw new AssertionError("Actor inventory container is missing");
            Object oldInventory=playerType.getField("field_71071_by").get(player);
            Object stone=services.items().materialize(new LegacyClientData.Stack("minecraft:stone",0,3,"",""));
            oldInventory.getClass().getMethod("func_70437_b",stackType).invoke(oldInventory,stone);
            container.getMethod("func_75144_a",int.class,int.class,int.class,playerType).invoke(opened.container(),0,0,0,player);
            var saved=services.playerInventory().read(player);
            if(saved.get(5).tag().isBlank())throw new AssertionError("Bag click did not persist the stored stack: "+saved.get(5));
            services.playerInventory().write(player,saved);
            Object stored=LegacyDeviceContainers.slots(opened.container()).get(0).getClass().getMethod("func_75211_c").invoke(LegacyDeviceContainers.slots(opened.container()).get(0));
            var storedStack=services.items().snapshot(stored);
            if(!storedStack.key().equals("minecraft:stone")||storedStack.count()!=3)throw new AssertionError("Bag storage did not survive inventory projection: "+storedStack);
            if(!(boolean)container.getMethod("func_75145_c",playerType).invoke(opened.container(),player))throw new AssertionError("Inventory refresh invalidated the held bag");
            System.out.println("MFCL_XU_ITEM_GUI_PASS: original bag GUI capture, hotbar slot, 90 slots, stored item NBT and actor inventory container");
            return null;
        });
    }
}
