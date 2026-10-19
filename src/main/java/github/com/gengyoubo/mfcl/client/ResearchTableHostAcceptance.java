package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.concurrent.*;

/** Native slot packets and the original GUI's drag-release/erase paths in the disposable device world. */
final class ResearchTableHostAcceptance {
    private final ModernContentBridge content;
    private final Map<String,Object> proof;
    private CompletableFuture<?> pending;
    private int phase,frames,q,r;
    private String hexKey;
    ResearchTableHostAcceptance(ModernContentBridge content,Map<String,Object> proof){this.content=content;this.proof=proof;}
    boolean tick(Minecraft mc) throws Exception {
        if(pending!=null&&pending.isCompletedExceptionally())pending.join();
        if(phase==0){pending=server(mc,()->{seed(mc);return true;});phase=1;frames=0;}
        else if(phase==1&&pending.isDone()&&++frames>12) {
            pending.join();click(mc,29);click(mc,0);click(mc,30);click(mc,1);phase=2;frames=0;
        }else if(phase==2&&++frames>12) {
            if(!mc.player.containerMenu.getCarried().isEmpty()||mc.player.containerMenu.getSlot(1).getItem().isEmpty())throw new IllegalStateException("Native research note insertion failed");
            proof.put("researchNoteNativeSlotInsertion",true);drawRelease(mc);phase=3;frames=0;
        }else if(phase==3&&++frames>12) {
            verifyClientCell(mc,2);pending=server(mc,()->{verifyServerCell(mc,2,19,1);return true;});phase=4;frames=0;
        }else if(phase==4&&pending.isDone()&&++frames>8) {
            pending.join();erase(mc);phase=5;frames=0;
        }else if(phase==5&&++frames>12) {
            verifyClientCell(mc,0);pending=server(mc,()->{verifyServerCell(mc,0,19,2);return true;});phase=6;frames=0;
        }else if(phase==6&&pending.isDone()) {
            pending.join();click(mc,1);click(mc,30);phase=7;frames=0;
        }else if(phase==7&&++frames>12) {
            if(!mc.player.containerMenu.getSlot(1).getItem().isEmpty()||mc.player.containerMenu.getSlot(30).getItem().isEmpty()||!mc.player.containerMenu.getCarried().isEmpty())throw new IllegalStateException("Edited research note could not be returned to inventory");
            proof.put("researchNoteOriginalGuiPlaceEraseAndSynchronization",true);return true;
        }
        return false;
    }
    private ClassLoader loader(){return content.services().loader();}
    private Object aspect() throws Exception {return loader().loadClass("thaumcraft.api.aspects.Aspect").getMethod("getAspect",String.class).invoke(null,"aer");}
    private Object knowledge() throws Exception {return loader().loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(loader().loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null));}
    private Object data(Object note) throws Exception {return loader().loadClass("thaumcraft.common.lib.research.ResearchManager").getMethod("getData",loader().loadClass("net.minecraft.item.ItemStack")).invoke(null,note);}
    private void seed(Minecraft mc) throws Exception {
        var services=content.services();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);Object world=services.worlds().bound(player.serverLevel());
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world)) {
            Object note=loader().loadClass("thaumcraft.common.lib.research.ResearchManager").getMethod("createNote",loader().loadClass("net.minecraft.item.ItemStack"),String.class,loader().loadClass("net.minecraft.world.World"))
                    .invoke(null,services.items().materialize(new LegacyClientData.Stack("Thaumcraft:ItemResearchNotes",0,1,"","")),"NITOR",world);
            Object data=data(note);var entries=(Map<?,?>)data.getClass().getField("hexEntries").get(data);
            hexKey=null;
            for(var entry:entries.entrySet())if(entry.getValue().getClass().getField("type").getInt(entry.getValue())==0){hexKey=(String)entry.getKey();break;}
            if(hexKey==null)throw new IllegalStateException("Generated note has no editable hex");
            Object hex=((Map<?,?>)data.getClass().getField("hexes").get(data)).get(hexKey);q=hex.getClass().getField("q").getInt(hex);r=hex.getClass().getField("r").getInt(hex);
            player.getInventory().setItem(0,content.stack("Thaumcraft:ItemInkwell",0,1,""));player.getInventory().setItem(1,content.gameplayStack(services.items().snapshot(note)));
            knowledge().getClass().getMethod("setAspectPool",String.class,loader().loadClass("thaumcraft.api.aspects.Aspect"),short.class).invoke(knowledge(),player.getName().getString(),aspect(),(short)20);
            Object actor=((LegacyDeviceMenu)player.containerMenu).originalPlayer();
            player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));
            return null;
        }});
        content.itemUse().sync(world,new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0)).forEach(message->ScanSyncChannel.sendToPlayer(player,message));
        player.containerMenu.broadcastChanges();
    }
    private void click(Minecraft mc,int slot) {
        var screen=(LegacyDeviceScreen)mc.screen;var value=screen.getMenu().getSlot(slot);
        int x=screen.getGuiLeft()+value.x+8,y=screen.getGuiTop()+value.y+8;
        screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
    }
    private int[] point(Minecraft mc) throws Exception {
        Object hex=loader().loadClass("thaumcraft.common.lib.utils.HexUtils$Hex").getConstructor(int.class,int.class).newInstance(q,r);
        Object pixel=hex.getClass().getMethod("toPixel",int.class).invoke(hex,9);
        var screen=(LegacyDeviceScreen)mc.screen;
        return new int[]{screen.getGuiLeft()+169+(int)Math.round(pixel.getClass().getField("x").getDouble(pixel)),screen.getGuiTop()+83+(int)Math.round(pixel.getClass().getField("y").getDouble(pixel))};
    }
    private void drawRelease(Minecraft mc) throws Exception {
        var screen=(LegacyDeviceScreen)mc.screen;Object gui=screen.originalGui();int[] point=point(mc);
        content.services().items().inLegacy(()->{
            var dragged=gui.getClass().getDeclaredField("draggedAspect");dragged.setAccessible(true);dragged.set(gui,aspect());
            var down=gui.getClass().getDeclaredField("isMouseButtonDown");down.setAccessible(true);down.setInt(gui,1);
            try(var frame=LegacyGuiGraphics.begin(screen.width,screen.height,point[0],point[1],Set.of());var input=new LegacyDeviceInput()) {
                gui.getClass().getMethod("func_73863_a",int.class,int.class,float.class).invoke(gui,point[0],point[1],0f);
                if(input.packets().size()!=1)throw new IllegalStateException("Original research drag release did not emit one packet");
                input.packets().forEach(packet->ScanSyncChannel.sendDevicePacket(screen.getMenu().containerId,packet));
            }
            return null;
        });
    }
    private void erase(Minecraft mc) throws Exception {int[] point=point(mc);var screen=(LegacyDeviceScreen)mc.screen;screen.mouseClicked(point[0],point[1],1);screen.mouseReleased(point[0],point[1],1);}
    private void verifyClientCell(Minecraft mc,int type) throws Exception {
        var screen=(LegacyDeviceScreen)mc.screen;
        Object note=content.services().items().materialize(content.gameplayStack(screen.getMenu().getSlot(1).getItem()));
        verifyCell(data(note),type);
        verifyCell(screen.originalGui().getClass().getField("note").get(screen.originalGui()),type);
    }
    private void verifyCell(Object data,int type) throws Exception {
        Object entry=data==null?null:((Map<?,?>)data.getClass().getField("hexEntries").get(data)).get(hexKey);
        if(entry==null||entry.getClass().getField("type").getInt(entry)!=type)throw new IllegalStateException("Research cell did not synchronize to type "+type);
    }
    private void verifyServerCell(Minecraft mc,int type,int pool,int ink) throws Exception {
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var menu=(LegacyDeviceMenu)player.containerMenu;var services=content.services();
        services.items().inLegacy(()->{try(var state=services.playerState().enter(services.worlds().bound(player.serverLevel()))) {
            verifyCell(data(services.items().materialize(content.gameplayStack(menu.getSlot(1).getItem()))),type);
            int actual=((Number)knowledge().getClass().getMethod("getAspectPoolFor",String.class,loader().loadClass("thaumcraft.api.aspects.Aspect")).invoke(knowledge(),player.getName().getString(),aspect())).intValue();
            if(actual!=pool||content.metadata(menu.getSlot(0).getItem())!=ink)throw new IllegalStateException("Research ink/aspect consumption mismatch: "+actual+","+content.metadata(menu.getSlot(0).getItem()));
            return null;
        }});
    }
    private <T> CompletableFuture<T> server(Minecraft mc,Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
}
