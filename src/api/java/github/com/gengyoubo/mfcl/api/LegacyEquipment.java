package github.com.gengyoubo.mfcl.api;

/**
 * Host-side sources for what old player actors wear. Whenever MFCL builds or refreshes an old player (server actors,
 * the old client player, {@link LegacyRuntime#withPlayer}, {@link LegacyRuntime#withClientPlayer}) it asks the
 * registered sources slot by slot, highest priority first. Armor goes to {@code inventory.armorInventory}; bauble slots
 * go into the original Baubles storage ({@code PlayerHandler.getPlayerBaubles}), so every original
 * {@code BaublesApi.getBaubles} caller sees them. Bauble slots are ignored without original Baubles
 * ({@link LegacyFeatures#EQUIPMENT_BAUBLES}).
 *
 * <p>MFCL's own source has priority 0 and supplies the host's four vanilla armor slots; it defers on an empty slot,
 * so a lower-priority source can fill it (for example a Curios head slot under an empty helmet slot).
 *
 * <p>Threads: sources and {@link Source#changed} run on the thread that owns the player: the logical server thread
 * for server players, the client thread for the local player. They run inside MFCL's runtime lock; calling the API
 * again is allowed, blocking on another thread that uses the API is not. A source that throws is skipped for that
 * slot and recorded in {@link LegacyRuntime#diagnostics()}.
 */
public interface LegacyEquipment {
    int DEFAULT_PRIORITY = 0;

    enum Slot {
        ARMOR_FEET(0), ARMOR_LEGS(1), ARMOR_CHEST(2), ARMOR_HEAD(3),
        BAUBLE_AMULET(0), BAUBLE_RING_0(1), BAUBLE_RING_1(2), BAUBLE_BELT(3);
        private final int legacyIndex;
        Slot(int legacyIndex) { this.legacyIndex = legacyIndex; }
        /** Index in the old armorInventory (boots first) or in the Baubles inventory. */
        public int legacyIndex() { return legacyIndex; }
        public boolean bauble() { return ordinal() >= 4; }
    }

    interface Source {
        /**
         * The host stack (a host {@code ItemStack}) worn in {@code slot} by {@code hostPlayer}, a host {@code Player}.
         * Return null to defer to the next source; return an empty host stack to declare the slot empty.
         * {@code client} is true for the local player's old client player. Called often (old renderers ask
         * every frame), so this must be a cheap lookup.
         */
        Object worn(Object hostPlayer, Slot slot, boolean client);

        /**
         * Original code changed or replaced the stack this source supplied for {@code slot}. {@code newHostStack} is
         * an empty host stack when the original removed it; otherwise its host-owned tags (enchantments, repair cost,
         * display) are those of the stack this source supplied. The source stores it (for example back into Curios).
         * Logical server only.
         */
        default void changed(Object hostPlayer, Slot slot, Object newHostStack) { }
    }

    /** Higher priority is consulted first. Closing unregisters. */
    AutoCloseable register(int priority, Source source);

    /** Host equipment changed outside a host stack's own lifecycle; refreshes cached old client stacks. Any thread. */
    void invalidate(Object hostPlayer);

    /**
     * Whether MFCL ticks a supplied item each player tick (armor: {@code onArmorTick}, baubles: {@code IBauble.onWornTick}),
     * on both sides with a persistent old player. On by default. Vanilla armor slots are ticked by the host item and are
     * never ticked twice. While {@link LegacyEvents.Kind#LIVING_UPDATE} is forwarded, original Baubles ticks the bauble
     * slots from its own LivingUpdateEvent handler instead, regardless of this setting.
     */
    void setTicking(Slot slot, boolean enabled);

    boolean ticking(Slot slot);
}
