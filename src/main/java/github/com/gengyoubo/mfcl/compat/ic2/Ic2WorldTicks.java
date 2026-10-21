package github.com.gengyoubo.mfcl.compat.ic2;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.List;

/** Drives IC2's original load callbacks, tick subscriptions and EU graph at the host world boundaries. */
final class Ic2WorldTicks {
    static void install(LegacyServices services) throws Exception {
        var loader=services.loader();var world=loader.loadClass("net.minecraft.world.World");
        var tile=loader.loadClass("net.minecraft.tileentity.TileEntity");var machine=loader.loadClass("ic2.core.block.TileEntityBlock");
        var active=world.getField("field_147482_g");var owner=tile.getMethod("func_145831_w");var invalid=tile.getMethod("func_145837_r");
        Object handler=loader.loadClass("ic2.core.IC2").getField("tickHandler").get(null);
        var event=loader.loadClass("cpw.mods.fml.common.gameevent.TickEvent$WorldTickEvent");
        var side=loader.loadClass("cpw.mods.fml.relauncher.Side");var phase=loader.loadClass("cpw.mods.fml.common.gameevent.TickEvent$Phase");
        var constructor=event.getConstructor(side,phase,world);var dispatch=handler.getClass().getMethod("onWorldTick",event);
        var callbacks=handler.getClass().getDeclaredMethod("processTickCallbacks",world);callbacks.setAccessible(true);
        var remote=world.getField("field_72995_K");var unload=loader.loadClass("ic2.core.WorldData").getMethod("onWorldUnload",world);
        services.logic().tickPolicy(value->machine.isInstance(value)&&((List<?>)active.get(owner.invoke(value))).contains(value));
        services.worlds().lifecycle(new LegacyWorlds.Lifecycle() {
            public void tick(Object value,boolean start) throws Exception {
                if(start) {
                    var entries=(List<?>)active.get(value);
                    for(Object entry:List.copyOf(entries))if((boolean)invalid.invoke(entry)||owner.invoke(entry)!=value)entries.remove(entry);
                }
                // Old FML WorldTickEvent is server-only. ClientTick drives callbacks, without a server wind/EU graph.
                if(remote.getBoolean(value)) {if(start)callbacks.invoke(null,value);return;}
                dispatch.invoke(handler,constructor.newInstance(side.getField("SERVER").get(null),phase.getField(start?"START":"END").get(null),value));
            }
            public void unloaded(Object value) throws Exception {unload.invoke(null,value);((List<?>)active.get(value)).clear();}
        });
    }
}
