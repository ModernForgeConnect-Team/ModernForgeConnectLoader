package github.com.gengyoubo.mfcl.client;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Drives actual old GuiContainer code alongside its host screen. Contains no mod-specific layout. */
public final class ClientContainerBridge {
    private final LegacyServices services;
    private final LegacyGuiRenderer renderer;
    private final Map<Screen,Bound> bindings=new IdentityHashMap<>();
    private Map<String,LegacyClientData.Stack> reverse;
    private static ClientContainerBridge active;
    public static ClientContainerBridge active(){return active;}
    public static void install(LegacyServices services) {
        if(!Boolean.getBoolean("mfcl.legacyGui"))return;
        var bridge=new ClientContainerBridge(services);active=bridge;
        MinecraftForge.EVENT_BUS.addListener(bridge::init);
        MinecraftForge.EVENT_BUS.addListener(bridge::render);
        MinecraftForge.EVENT_BUS.addListener(bridge::mouse);
        MinecraftForge.EVENT_BUS.addListener(bridge::release);
        MinecraftForge.EVENT_BUS.addListener(bridge::character);
        MinecraftForge.EVENT_BUS.addListener(bridge::key);
        MinecraftForge.EVENT_BUS.addListener(bridge::close);
        MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    private ClientContainerBridge(LegacyServices services){this.services=services;renderer=new LegacyGuiRenderer(services);}
    private static final class Bound {
        final Object gui;final LegacyScreens.Session session;
        List<LegacyGuiGraphics.Command> commands=List.of();Throwable failure;
        Bound(Object gui,LegacyScreens.Session session){this.gui=gui;this.session=session;}
    }
    private void init(ScreenEvent.Init.Post event) {
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
        try(var frame=LegacyGuiGraphics.begin(screen.width,screen.height,0,0,keys())) {
            Bound previous=bindings.remove(screen);if(previous!=null)previous.session.close();
            List<LegacyContainers.Slot> slots=new ArrayList<>();
            for(var slot:screen.getMenu().slots)slots.add(new LegacyContainers.Slot(slot.x,slot.y,snapshot(slot.getItem())));
            Object gui=services.containers().create(screen.width,screen.height,screen.getGuiLeft(),screen.getGuiTop(),screen.getXSize(),screen.getYSize(),slots);
            var session=services.screens().open(gui,screen.width,screen.height);
            services.containers().position(gui,screen.getGuiLeft(),screen.getGuiTop());
            bindings.put(screen,new Bound(gui,session));
        } catch(Exception failure){LogUtils.getLogger().error("MFCL legacy container initialization failed",failure);}
    }
    private LegacyClientData.Stack snapshot(net.minecraft.world.item.ItemStack value) throws Exception {
        if(value.isEmpty())return null;
        if(value.hasTag())throw new UnsupportedOperationException("Host container NBT mapping is not available: "+value);
        if(reverse==null){reverse=new HashMap<>();for(var stack:services.items().catalog()){var modern=ModernItemMapping.renderStack(stack);if(!modern.isEmpty()&&!modern.hasTag())reverse.putIfAbsent(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(modern.getItem()).toString(),stack);}}
        var old=reverse.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.getItem()).toString());
        if(old==null)throw new UnsupportedOperationException("Host item has no old representation: "+value);
        return new LegacyClientData.Stack(old.key(),value.isDamageableItem()?value.getDamageValue():old.metadata(),value.getCount(),old.name(),old.tag());
    }
    private void render(ScreenEvent.Render.Post event) {
        Bound bound=bindings.get(event.getScreen());if(bound==null||bound.failure!=null)return;
        try(var frame=LegacyGuiGraphics.begin(event.getScreen().width,event.getScreen().height,event.getMouseX(),event.getMouseY(),keys())) {
            bound.session.draw(event.getMouseX(),event.getMouseY(),event.getPartialTick());bound.commands=frame.commands();
            renderer.draw(event.getGuiGraphics(),bound.commands);
        } catch(Throwable failure){bound.failure=failure;LogUtils.getLogger().error("MFCL original GUI drawing failed",failure);}
    }
    private void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var screen=Minecraft.getInstance().screen;var bound=bindings.get(screen);if(bound==null||bound.failure!=null)return;
        dispatch(screen,0,0,b->{
            if(screen instanceof AbstractContainerScreen<?> container){var slots=new ArrayList<LegacyContainers.Slot>();for(var slot:container.getMenu().slots)slots.add(new LegacyContainers.Slot(slot.x,slot.y,snapshot(slot.getItem())));services.containers().update(b.gui,slots);}
            b.session.tick();return false;
        });
    }
    private void mouse(ScreenEvent.MouseButtonPressed.Pre event){if(dispatch(event.getScreen(),(int)event.getMouseX(),(int)event.getMouseY(),b->services.containers().mouse(b.gui,(int)event.getMouseX(),(int)event.getMouseY(),event.getButton())))event.setCanceled(true);}
    private void release(ScreenEvent.MouseButtonReleased.Pre event){dispatch(event.getScreen(),(int)event.getMouseX(),(int)event.getMouseY(),b->{services.containers().release(b.gui,(int)event.getMouseX(),(int)event.getMouseY(),event.getButton());return false;});}
    private void character(ScreenEvent.CharacterTyped.Pre event){if(dispatch(event.getScreen(),0,0,b->services.containers().key(b.gui,event.getCodePoint(),0)))event.setCanceled(true);}
    private void key(ScreenEvent.KeyPressed.Pre event){int legacy=switch(event.getKeyCode()){case GLFW.GLFW_KEY_BACKSPACE->14;case GLFW.GLFW_KEY_ENTER->28;case GLFW.GLFW_KEY_R->19;case GLFW.GLFW_KEY_U->22;case GLFW.GLFW_KEY_O->24;default->0;};if(legacy!=0&&dispatch(event.getScreen(),0,0,b->services.containers().key(b.gui,'\0',legacy)))event.setCanceled(true);}
    private interface Action{boolean run(Bound bound)throws Exception;}
    private boolean dispatch(Screen screen,int x,int y,Action action){var bound=bindings.get(screen);if(bound==null||bound.failure!=null)return false;try(var frame=LegacyGuiGraphics.begin(screen.width,screen.height,x,y,keys())){boolean handled=action.run(bound);Object requested=frame.requestedScreen();if(requested!=null)Minecraft.getInstance().tell(()->transition(screen,requested));return handled;}catch(Throwable failure){bound.failure=failure;LogUtils.getLogger().error("MFCL original GUI input failed",failure);return false;}}
    private void transition(Screen parent,Object requested) {
        for(var entry:bindings.entrySet())if(entry.getValue().gui==requested){Minecraft.getInstance().setScreen(entry.getKey());return;}
        Minecraft.getInstance().setScreen(new HostedScreen(parent,requested));
    }
    private final class HostedScreen extends Screen {
        private final Screen parent;private final Object old;
        HostedScreen(Screen parent,Object old){super(net.minecraft.network.chat.Component.empty());this.parent=parent;this.old=old;}
        @Override protected void init(){try(var frame=LegacyGuiGraphics.begin(width,height,0,0,keys())){bindings.put(this,new Bound(old,services.screens().open(old,width,height)));}catch(Exception failure){throw new IllegalStateException("Cannot open original legacy screen",failure);}}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float partialTick){renderBackground(graphics);}
        @Override public boolean mouseClicked(double x,double y,int button){return dispatch(this,(int)x,(int)y,b->{b.session.mousePressed((int)x,(int)y,button);return true;});}
        @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}return super.keyPressed(key,scan,modifiers);}
        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    }
    private void close(ScreenEvent.Closing event){Bound bound=bindings.remove(event.getScreen());if(bound!=null)try{bound.session.close();}catch(Exception failure){LogUtils.getLogger().error("MFCL legacy GUI close failed",failure);}}
    private static Set<Integer> keys(){var result=new HashSet<Integer>();if(Screen.hasControlDown())result.add(29);if(Screen.hasShiftDown())result.add(42);if(Screen.hasAltDown())result.add(56);return result;}
    public List<LegacyGuiGraphics.Command> commands(Screen screen){var bound=bindings.get(screen);if(bound==null)throw new IllegalStateException("No old GUI binding");if(bound.failure!=null)throw new IllegalStateException("Old GUI failed",bound.failure);return bound.commands;}
}
