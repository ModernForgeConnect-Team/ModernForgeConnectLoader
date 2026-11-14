package github.com.gengyoubo.mfcl.directsmoke;

import github.com.gengyoubo.mfcl.client.DirectSmokeApi;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import thaumcraft.api.aspects.Aspect;

/**
 * Direct class for the in-game API smoke test, written with MCP names exactly like a 1.7.10 mod. The directSmoke
 * source set compiles it against a deobfuscated copy of the prepared runtime and reobfuscates it to SRG names.
 */
public final class DirectSmokeLogic implements DirectSmokeApi {
    @Override public String helmetName(Object player) {
        ItemStack helmet = ((EntityPlayer) player).getCurrentArmor(3);
        return helmet == null ? "" : helmet.getDisplayName();
    }
    @Override public Object helmet(Object player) { return ((EntityPlayer) player).getCurrentArmor(3); }
    @Override public String loaders() {
        return getClass().getClassLoader().getClass().getName() + "|" + MinecraftServer.class.getClassLoader().getClass().getName();
    }
    @Override public int aspectCount() { return Aspect.aspects.size(); }
    /** CommandBase.compareTo is original bytecode calling getCommandName() by its SRG name: only a renamed override answers. */
    @Override public int originalCallsOverride() {
        return new DirectCommand("alpha").compareTo(new DirectCommand("beta"));
    }
}
