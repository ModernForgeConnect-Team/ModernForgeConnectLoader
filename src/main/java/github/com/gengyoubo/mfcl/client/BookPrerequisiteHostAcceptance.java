package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Verifies the original completion packet unlocks a dependent entry while the book stays open. */
final class BookPrerequisiteHostAcceptance {
    private final ModernContentBridge content;
    private final Map<String,Object> proof;
    private CompletableFuture<?> pending;
    private String parent,child;
    private int phase,frames;
    BookPrerequisiteHostAcceptance(ModernContentBridge content,Map<String,Object> proof){this.content=content;this.proof=proof;}
    boolean tick(Minecraft mc) throws Exception {
        if(pending!=null&&pending.isCompletedExceptionally())pending.join();
        if(phase==0){pending=mc.getSingleplayerServer().submit(()->{try{seed(mc);}catch(Exception failure){throw new IllegalStateException(failure);}});phase=1;frames=0;}
        else if(phase==1&&pending.isDone()&&++frames>20){pending.join();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=2;frames=0;}
        else if(phase==2&&mc.screen!=null&&++frames>20){
            verify(mc,false);proof.put("unmetOriginalPrerequisiteRemainsLocked",child);
            pending=mc.getSingleplayerServer().submit(()->{try{complete(mc);}catch(Exception failure){throw new IllegalStateException(failure);}});phase=3;frames=0;
        }else if(phase==3&&pending.isDone()&&++frames>20){
            pending.join();verify(mc,true);proof.put("originalCompletionPacketUnlocksDependentWithoutReopening",parent+" -> "+child);
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);phase=4;frames=0;
        }else if(phase==4&&mc.screen==null&&++frames>10){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=5;frames=0;}
        else if(phase==5&&mc.screen!=null&&++frames>20){verify(mc,true);proof.put("completedPrerequisiteSurvivesBookReopen",true);
            autoUnlocked(mc);proof.put("autoUnlockedResearchSurvivesSavedReload",true);mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0);phase=6;frames=0;}
        else if(phase==6&&mc.screen==null&&++frames>10){
            var server=mc.getSingleplayerServer();
            pending=server.submit(()->{try{return server.getCommands().getDispatcher().execute("debug thaumcraft resetresearch",server.getPlayerList().getPlayers().get(0).createCommandSourceStack().withPermission(4));}
                catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure){throw new IllegalStateException(failure);}});phase=7;frames=0;
        }else if(phase==7&&pending.isDone()&&++frames>20){
            if(!Integer.valueOf(1).equals(pending.join()))throw new IllegalStateException("resetresearch did not succeed");
            reset(mc);proof.put("debugResetResearchRestoresInitialState",true);return true;
        }
        return false;
    }
    /** After the reset, client and server hold exactly the auto-unlocked entries. */
    private void reset(Minecraft mc) throws Exception {
        content.services().items().inLegacy(()->{
            var categories=(Map<?,?>)loader().loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
            var initial=new TreeSet<String>();
            for(Object category:categories.values())for(Object item:((Map<?,?>)category.getClass().getField("research").get(category)).values())
                if((boolean)item.getClass().getMethod("isAutoUnlock").invoke(item))initial.add((String)item.getClass().getField("key").get(item));
            String name=mc.player.getName().getString();
            var book=new TreeSet<Object>((List<?>)((Map<?,?>)loader().loadClass("thaumcraft.client.gui.GuiResearchBrowser").getField("completedResearch").get(null)).get(name));
            Object proxy=loader().loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
            var client=new TreeSet<Object>((List<?>)((Map<?,?>)proxy.getClass().getMethod("getCompletedResearch").invoke(proxy)).get(name));
            if(!book.equals(initial)||!client.equals(initial))throw new IllegalStateException("Reset left book "+book+", client "+client+", expected "+initial);
            return null;
        });
        var server=mc.getSingleplayerServer();
        server.submit(()->{
            var player=server.getPlayerList().getPlayers().get(0);
            var saved=new ArrayList<String>();
            content.withApiPlayer(player,actor->{try{saved.addAll((List<String>)loader().loadClass("thaumcraft.common.lib.research.ResearchManager").getMethod("getResearchForPlayer",String.class).invoke(null,player.getName().getString()));return null;}catch(Exception failure){throw new IllegalStateException(failure);}});
            if(saved.contains(parent))throw new IllegalStateException("Server still knows "+parent+" after reset: "+saved);
            return null;
        }).join();
    }
    private ClassLoader loader(){return content.services().loader();}
    private Object research(String key) throws Exception{return loader().loadClass("thaumcraft.api.research.ResearchCategories").getMethod("getResearch",String.class).invoke(null,key);}
    private LegacyTiles.Placer source(net.minecraft.server.level.ServerPlayer player){return new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0);}
    @SuppressWarnings("unchecked") private void seed(Minecraft mc) throws Exception {
        var services=content.services();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);Object world=services.worlds().bound(player.serverLevel());
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){
            var categories=(Map<?,?>)loader().loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
            var completed=new ArrayList<String>();
            for(Object category:categories.values())for(Object item:((Map<?,?>)category.getClass().getField("research").get(category)).values()){
                String key=(String)item.getClass().getField("key").get(item);completed.add(key);
                String[] parents=(String[])item.getClass().getField("parents").get(item);
                if(child==null&&parents!=null&&!(boolean)item.getClass().getMethod("isAutoUnlock").invoke(item)&&!(boolean)item.getClass().getMethod("isVirtual").invoke(item))
                    for(String candidate:parents){Object prerequisite=research(candidate);if(prerequisite!=null&&!(boolean)prerequisite.getClass().getMethod("isAutoUnlock").invoke(prerequisite)&&!(boolean)prerequisite.getClass().getMethod("isVirtual").invoke(prerequisite)
                            &&(int)loader().loadClass("thaumcraft.api.ThaumcraftApi").getMethod("getWarp",Object.class).invoke(null,candidate)==0){parent=candidate;child=key;break;}}
            }
            if(child==null)throw new IllegalStateException("No original prerequisite fixture");completed.remove(parent);completed.remove(child);
            Object proxy=loader().loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
            Object knowledge=loader().loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
            ((Map<String,ArrayList<String>>)knowledge.getClass().getField("researchCompleted").get(knowledge)).put(player.getName().getString(),completed);
            Object actor=content.itemUse().clientActor(world,source(player),null);
            player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));return null;
        }});
        content.itemUse().sync(world,source(player)).forEach(message->ScanSyncChannel.sendToPlayer(player,message));
    }
    private void complete(Minecraft mc) throws Exception {
        var services=content.services();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);Object world=services.worlds().bound(player.serverLevel());
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world);var packets=LegacyPackets.capture(player.getUUID())){
            Object actor=content.itemUse().clientActor(world,source(player),null);
            services.playerData().write(actor,player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA));
            Object proxy=loader().loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
            Object manager=proxy.getClass().getMethod("getResearchManager").invoke(proxy);
            manager.getClass().getMethod("completeResearch",loader().loadClass("net.minecraft.entity.player.EntityPlayer"),String.class).invoke(manager,actor,parent);
            Object packet=loader().loadClass("thaumcraft.common.lib.network.playerdata.PacketResearchComplete").getConstructor(String.class).newInstance(parent);
            Object wrapper=loader().loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null);
            wrapper.getClass().getMethod("sendTo",loader().loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage"),loader().loadClass("net.minecraft.entity.player.EntityPlayerMP")).invoke(wrapper,packet,actor);
            player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));
            // Deliberately send only the incremental packet: a full sync would hide a broken completion handler.
            if(packets.messages().size()!=1)throw new IllegalStateException("Unexpected completion packets for "+parent+": "+packets.messages());
            packets.messages().forEach(message->ScanSyncChannel.sendToPlayer(player,message));return null;
        }});
    }
    /** The reopen loads saved data, which original TC writes without auto-unlocked entries. */
    private void autoUnlocked(Minecraft mc) throws Exception {
        content.services().items().inLegacy(()->{
            Object browser=ClientContainerBridge.active().legacyScreen(mc.screen);
            var entries=(List<?>)((Map<?,?>)browser.getClass().getField("completedResearch").get(null)).get(mc.player.getName().getString());
            var categories=(Map<?,?>)loader().loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
            var missing=new ArrayList<String>();
            for(Object category:categories.values())for(Object item:((Map<?,?>)category.getClass().getField("research").get(category)).values()){
                String key=(String)item.getClass().getField("key").get(item);
                if((boolean)item.getClass().getMethod("isAutoUnlock").invoke(item)&&!entries.contains(key))missing.add(key);
            }
            if(!missing.isEmpty())throw new IllegalStateException("Saved reload dropped auto-unlocked research "+missing);
            return null;
        });
    }
    private void verify(Minecraft mc,boolean unlocked) throws Exception {
        content.services().items().inLegacy(()->{
            Object browser=ClientContainerBridge.active().legacyScreen(mc.screen);
            if(!browser.getClass().getName().equals("thaumcraft.client.gui.GuiResearchBrowser"))throw new IllegalStateException("Book did not remain open");
            var completed=(Map<?,?>)browser.getClass().getField("completedResearch").get(null);
            var entries=(List<?>)completed.get(mc.player.getName().getString());
            if(entries==null||entries.contains(parent)!=unlocked||entries.contains(child))throw new IllegalStateException("Book prerequisite completion is stale: "+parent+" -> "+child);
            var canUnlock=browser.getClass().getDeclaredMethod("canUnlockResearch",loader().loadClass("thaumcraft.api.research.ResearchItem"));canUnlock.setAccessible(true);
            if((boolean)canUnlock.invoke(browser,research(child))!=unlocked)throw new IllegalStateException("Original prerequisite gate did not update");
            return null;
        });
    }
}
