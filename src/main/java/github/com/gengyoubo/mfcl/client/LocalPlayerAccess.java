package github.com.gengyoubo.mfcl.client;

/** Client-only access to the local player, kept out of common classes that also load on a dedicated server. */
public final class LocalPlayerAccess {
    private LocalPlayerAccess() {}
    public static net.minecraft.world.entity.player.Player player() { return net.minecraft.client.Minecraft.getInstance().player; }
    public static boolean onClientThread() { return net.minecraft.client.Minecraft.getInstance().isSameThread(); }
}
