package github.com.gengyoubo.mfcl.api;

/**
 * Forwards host Forge events to the old {@code MinecraftForge.EVENT_BUS}, with the player's persistent old actor as the
 * entity. Forwarding is off until some addon asks for a kind; requests are counted and the kind stays forwarded until
 * every handle is closed. Only player entities are forwarded. Results flow back: a canceled old event cancels the host
 * event, and the documented mutable fields are copied back. Equipment write-back, {@link LegacyFeatures#PLAYER_SYNC}
 * and old packets queued by subscribers are applied after each forwarded event.
 *
 * <p>Other old events can be posted directly: construct them with {@code @LegacyConstructor} and call
 * {@code EventBus.post} through an accessor of the static {@code MinecraftForge.EVENT_BUS} field.
 */
public interface LegacyEvents {
    enum Kind {
        /** Host LivingHurtEvent (server) → old LivingHurtEvent; {@code ammount} is copied back. */
        LIVING_HURT,
        /** Host LivingTickEvent (server player, and the local player on the client) → old LivingEvent.LivingUpdateEvent. */
        LIVING_UPDATE,
        /** Host LivingFallEvent (server) → old LivingFallEvent; {@code distance} is copied back. */
        LIVING_FALL,
        /** Host PlayerEvent.BreakSpeed (both sides) → old PlayerEvent.BreakSpeed; {@code newSpeed} is copied back. */
        BREAK_SPEED,
        /**
         * Host ItemTooltipEvent (client thread) for host items that have an original representation → old
         * ItemTooltipEvent; lines old subscribers add are appended. Original items already post the old event themselves.
         */
        ITEM_TOOLTIP
    }

    /** Starts forwarding {@code kind}. Closing the handle withdraws this request. Any thread. */
    AutoCloseable forward(Kind kind);

    boolean forwarding(Kind kind);
}
