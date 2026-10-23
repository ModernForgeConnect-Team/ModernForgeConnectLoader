package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import github.com.gengyoubo.mfcl.bridge.LegacyModAdapter;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.TickEvent;

/** TC4's Utils.resetFloatCounter must affect the real connection during the sword's held levitation. */
final class ThaumcraftElementalTools {
    private static final java.lang.reflect.Field FLOAT_TICKS=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(ServerGamePacketListenerImpl.class,"f_9737_");
    private ThaumcraftElementalTools(){}
    static void install(LegacyServices services)throws Exception {
        LegacyModAdapter.withHostContext(()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent event)->{
                if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player)||!player.isUsingItem())return;
                var content=ModernContentBridge.active();if(content==null||content.services()!=services)return;
                if(player.getUseItem().getItem()!=content.items().get("Thaumcraft:ItemSwordElemental"))return;
                try{FLOAT_TICKS.setInt(player.connection,0);}catch(IllegalAccessException failure){throw new IllegalStateException("Cannot apply TC4 sword floating counter reset",failure);}
            });
        });
    }
}
