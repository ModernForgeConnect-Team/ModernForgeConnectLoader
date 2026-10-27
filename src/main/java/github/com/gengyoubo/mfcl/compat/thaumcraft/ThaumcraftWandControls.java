package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.client.LegacyKeyMappings;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** TC4's own "Change Wand Focus"/"Misc Wand Toggle" bindings (bridged host mappings); original server handlers own focus transfers and wand mode changes. */
final class ThaumcraftWandControls {
    private static final String FOCUS="Change Wand Focus",MODE="Misc Wand Toggle";
    private ThaumcraftWandControls() {}
    static void install(LegacyServices services){
        MinecraftForge.EVENT_BUS.addListener((InputEvent.Key event)->{
            var mc=Minecraft.getInstance();if(event.getAction()!=GLFW.GLFW_PRESS||mc.player==null||mc.screen!=null)return;
            // TC4 polls these from its client PlayerTickEvent, which the host does not run; the press itself is handled here.
            boolean focus=pressed(FOCUS,event),mode=!focus&&pressed(MODE,event);
            if(!focus&&!mode)return;
            try{
                var content=ModernContentBridge.active();var held=content.renderSnapshot(mc.player.getMainHandItem());
                if(held==null||!held.key().equals("Thaumcraft:WandCasting"))return;
                if(focus){
                    // F is also the modern offhand shortcut. Consume that queued click while a wand owns this key.
                    if(mc.options.keySwapOffhand.matches(event.getKey(),event.getScanCode()))while(mc.options.keySwapOffhand.consumeClick()){}
                    open(services);
                }else send(services,null);
            }catch(Exception failure){LogUtils.getLogger().error("MFCL wand control failed",failure);}
        });
    }
    private static boolean pressed(String binding,InputEvent.Key event){
        var key=LegacyKeyMappings.find(binding);return key!=null&&key.matches(event.getKey(),event.getScanCode());
    }
    static void open(LegacyServices services) throws Exception {
        var mc=Minecraft.getInstance();var content=ModernContentBridge.active();
        if(!services.items().inLegacy(()->ThaumcraftWandFocus.supportsFocus(services.loader(),services.items().materialize(content.gameplayStack(mc.player.getMainHandItem()))))){
            mc.player.displayClientMessage(Component.translatable("mfcl.wand.focus.sceptre"),true);return;
        }
        var choices=services.items().inLegacy(()->{
            var result=new TreeMap<String,Object>();
            for(int i=0;i<36;i++){
                var host=mc.player.getInventory().getItem(i);if(host.isEmpty())continue;
                try{ThaumcraftWandFocus.add(services.loader(),services.items().materialize(content.gameplayStack(host)),result);}catch(UnsupportedOperationException unmapped){}
            }
            var names=new LinkedHashMap<String,String>();for(var choice:result.entrySet())names.put(choice.getKey(),services.items().displayName(services.items().snapshot(choice.getValue())));return names;
        });
        mc.setScreen(new Selector(services,choices));
    }
    static void send(LegacyServices services,String focus) throws Exception {
        var mc=Minecraft.getInstance();var content=ModernContentBridge.active();
        var packet=services.items().inLegacy(()->{
            Object world=services.worlds().bound(mc.level);Object actor=content.itemUse().clientActor(world,new LegacyTiles.Placer(mc.player.getUUID(),mc.player.getName().getString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot()),null);
            var loader=services.loader();var player=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Object request=focus==null?loader.loadClass("thaumcraft.common.lib.network.misc.PacketItemKeyToServer").getConstructor(player,int.class).newInstance(actor,1)
                    :loader.loadClass("thaumcraft.common.lib.network.misc.PacketFocusChangeToServer").getConstructor(player,String.class).newInstance(actor,focus);
            return LegacyPackets.encode(ThaumcraftReflection.packetHandler(loader),request,"SERVER");
        });ScanSyncChannel.sendLegacyPacket(packet);
    }
    private static final class Selector extends Screen {
        private final LegacyServices services;private final List<Map.Entry<String,String>> choices;private int page;
        Selector(LegacyServices services,Map<String,String> choices){super(Component.translatable("mfcl.wand.focus.title"));this.services=services;this.choices=new ArrayList<>(choices.entrySet());}
        @Override protected void init(){
            int rows=Math.max(1,(height-100)/22),start=page*rows,end=Math.min(choices.size(),start+rows);
            for(int i=start;i<end;i++){var choice=choices.get(i);addRenderableWidget(Button.builder(Component.literal(choice.getValue()),button->select(choice.getKey())).bounds(width/2-110,36+(i-start)*22,220,20).build());}
            addRenderableWidget(Button.builder(Component.translatable("mfcl.wand.focus.remove"),button->select("REMOVE")).bounds(width/2-110,height-54,220,20).build());
            if(page>0)addRenderableWidget(Button.builder(Component.literal("←"),button->{page--;rebuildWidgets();}).bounds(width/2-110,height-28,60,20).build());
            if(end<choices.size())addRenderableWidget(Button.builder(Component.literal("→"),button->{page++;rebuildWidgets();}).bounds(width/2+50,height-28,60,20).build());
        }
        private void select(String key){try{send(services,key);onClose();}catch(Exception failure){LogUtils.getLogger().error("MFCL focus selection failed",failure);}}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){renderBackground(graphics);super.render(graphics,x,y,partial);graphics.drawCenteredString(font,title,width/2,14,0xffffff);}
    }
}
