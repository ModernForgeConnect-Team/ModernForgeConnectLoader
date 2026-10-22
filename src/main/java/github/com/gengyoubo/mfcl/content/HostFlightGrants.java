package github.com.gengyoubo.mfcl.content;

import net.minecraft.server.level.ServerPlayer;

/**
 * Host flight granted by an original mod's item or effect. The grant is marked in the player's persistent data under
 * the source's own key, so only that source revokes it (also after a relog), and creative/spectator flight is never touched.
 */
public final class HostFlightGrants {
    private final String marker;
    public HostFlightGrants(String marker) { this.marker = marker; }
    public boolean owned(ServerPlayer player) { return player.getPersistentData().getBoolean(marker); }
    /** The original logic decided the player may fly: claim the resulting host permission for this source. */
    public void claim(ServerPlayer player) { if (player.getAbilities().mayfly) player.getPersistentData().putBoolean(marker, true); }
    /** Drops this source's claim and the flight it granted; flight from game mode stays. */
    public void revoke(ServerPlayer player) {
        player.getPersistentData().remove(marker);
        if (player.getAbilities().instabuild || player.isSpectator()) return;
        if (player.getAbilities().mayfly || player.getAbilities().flying) {
            player.getAbilities().mayfly = false; player.getAbilities().flying = false; player.onUpdateAbilities();
        }
    }
}
