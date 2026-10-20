package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/**
 * Rejects ambiguous message registrations while a channel is being set up, instead of letting a later packet be
 * encoded with one handler and decoded by another. Both old SimpleNetworkWrapper discriminators and host
 * SimpleChannel indices are written as a single byte, so IDs that alias after truncation are also duplicates.
 * <p>Old FML keeps one discriminator-to-type table per channel but lets a type be registered once per side under
 * different discriminators (TC4's {@code PacketNote} uses 18 to the client and 21 to the server). Forge 1.20.1 keys its
 * codec by Java class alone, so a host channel must use exactly one index per type.</p>
 */
public final class NetworkRegistrationGuard {
    private final String channel;
    private final boolean oneIdPerType;
    private final Map<Integer, Class<?>> types = new HashMap<>();
    private final Map<Class<?>, Integer> ids = new HashMap<>();
    private final Set<String> handlers = new HashSet<>();

    private NetworkRegistrationGuard(String channel, boolean oneIdPerType) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.oneIdPerType = oneIdPerType;
    }

    /** Rules of an old SimpleNetworkWrapper: IDs are unique per channel, a type may use one ID per side. */
    public static NetworkRegistrationGuard legacy(String channel) { return new NetworkRegistrationGuard(channel, false); }

    /** Rules of a host SimpleChannel: both IDs and Java types are unique. */
    public static NetworkRegistrationGuard host(String channel) { return new NetworkRegistrationGuard(channel, true); }

    /**
     * Records one handler. Reusing an ID for another type, or registering a type twice for one direction, fails with
     * both owners named.
     */
    public synchronized void register(int id, Class<?> type, String direction) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(direction, "direction");
        if (id < Byte.MIN_VALUE || id > 255)
            throw new IllegalStateException(prefix() + "message ID " + id + " for " + type.getName() + " does not fit in one byte");
        int key = id & 0xFF;
        Class<?> owner = types.get(key);
        if (owner != null && owner != type)
            throw new IllegalStateException(prefix() + "message ID " + key + " is registered for both "
                    + owner.getName() + " and " + type.getName());
        Integer previous = ids.get(type);
        if (oneIdPerType && previous != null && previous != key)
            throw new IllegalStateException(prefix() + "Java type " + type.getName() + " is registered with message IDs "
                    + previous + " and " + key);
        if (!handlers.add(direction + '\0' + type.getName()))
            throw new IllegalStateException(prefix() + type.getName() + " already has a " + direction + " handler");
        types.put(key, type);
        ids.putIfAbsent(type, key);
    }

    public synchronized int size() { return handlers.size(); }

    private String prefix() { return "Network channel " + channel + ": "; }
}
