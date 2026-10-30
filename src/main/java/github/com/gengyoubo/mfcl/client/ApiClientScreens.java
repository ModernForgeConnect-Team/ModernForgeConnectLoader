package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.mfcl.internal.api.LegacyApiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Generic native GUI extension point. No original mod's layout or packet names belong here. */
public final class ApiClientScreens implements LegacyClientScreens {
    private static ApiClientScreens active;
    private final LegacyApiRuntime runtime;
    private final ModernContentBridge content;
    private final Map<String,Function<Context,Controller>> factories = new HashMap<>();
    public ApiClientScreens(LegacyApiRuntime runtime, ModernContentBridge content) {
        this.runtime=runtime;this.content=content;active=this;
    }
    @Override public synchronized AutoCloseable register(String name,Function<Context,Controller> factory) {
        Objects.requireNonNull(name);Objects.requireNonNull(factory);
        if(!runtime.isOpen())throw new IllegalStateException("Legacy runtime is closed");
        if(factories.putIfAbsent(name,factory)!=null)throw new IllegalArgumentException("Native view already registered: "+name);
        return ()->{synchronized(this){factories.remove(name,factory);}};
    }
    static Controller create(AbstractContainerScreen<?> host,Object gui,BooleanSupplier bound) {
        var owner=active;if(owner==null||!owner.runtime.isOpen())return null;
        Function<Context,Controller> factory;
        synchronized(owner){factory=owner.factories.get(gui.getClass().getName());}
        if(factory==null)return null;
        return factory.apply(owner.new ScreenContext(host,gui,bound));
    }
    private final class ScreenContext implements Context {
        private final AbstractContainerScreen<?> host;
        private final Object gui;
        private final BooleanSupplier bound;
        ScreenContext(AbstractContainerScreen<?> host,Object gui,BooleanSupplier bound){this.host=host;this.gui=gui;this.bound=bound;}
        private void check() {
            if(!runtime.isOpen()||!LocalPlayerAccess.onClientThread()||!bound.getAsBoolean())throw new IllegalStateException("Expired or foreign native screen context");
        }
        public LegacyRuntime runtime(){return runtime;}
        public <H> H hostScreen(Class<H> type){check();return type.cast(host);}
        public <T> T withScreen(Function<LegacyObject,T> operation) {
            check();Objects.requireNonNull(operation);
            return runtime.call("CLIENT_SCREEN",gui.getClass().getName(),()->{
                check();var alive=new AtomicBoolean(true);
                try {
                    return content.withApiClientPlayer(player->ClientContainerBridge.active().nativeAction(host,
                            ()->operation.apply(runtime.borrowed(gui,()->alive.get()&&bound.getAsBoolean()))));
                } finally {alive.set(false);}
            });
        }
        public void clickMenuButton(int button) {
            check();var mc=Minecraft.getInstance();
            if(mc.player==null||mc.player.containerMenu!=host.getMenu()||mc.getConnection()==null)throw new IllegalStateException("Device menu is not active");
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(host.getMenu().containerId,button));
        }
        public void sendToServer(LegacyObject channel,LegacyObject packet) {
            check();var mc=Minecraft.getInstance();
            if(mc.player==null||mc.player.containerMenu!=host.getMenu()||mc.getConnection()==null)throw new IllegalStateException("Device menu is not active");
            runtime.call("CLIENT_SCREEN_PACKET",gui.getClass().getName(),()->{
                var encoded=github.com.gengyoubo.mfcl.bridge.LegacyPackets.encode(runtime.raw(channel),runtime.raw(packet),"SERVER");
                github.com.gengyoubo.mfcl.content.ScanSyncChannel.sendDevicePacket(host.getMenu().containerId,encoded);return null;
            });
        }
    }
}
