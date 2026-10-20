package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/**
 * Adapter-owned held scanners: items whose original held use completes by sending a request to the server, which the
 * host cannot route through the old network. The core captures that request on the client, carries it over its own
 * channel and lets the owning adapter re-evaluate it against the authoritative server player. Nothing here knows a
 * particular mod; the TC4 thaumometer is one implementation in {@code compat.thaumcraft}.
 */
public final class LegacyHeldScanners {
    public interface Scanner {
        String id();
        /** Original registry key of the held item, e.g. {@code Thaumcraft:ItemThaumometer}. */
        boolean supports(String item);
        /** True for the completion request the original item sends from its use tick; any other packet is unexpected. */
        boolean completion(Object packet);
        /** Decodes a captured completion request. */
        LegacyItemUse.Scan read(Object packet) throws Exception;
        /** Original target currently being scanned, shown to the player, or null. */
        default String prompt(Object item) throws Exception { return null; }
        /**
         * Re-derives the target with the server's own old player/world and grants it only if it matches the reported
         * scan. Runs inside {@code inLegacy} and the player-state scope; returns whether the scan was accepted.
         */
        boolean complete(Object world, Object player, Object heldStack, LegacyItemUse.Scan scan, String prefix) throws Exception;
    }

    private final Map<String, Scanner> scanners = new LinkedHashMap<>();

    public synchronized AutoCloseable register(Scanner scanner) {
        if (scanners.putIfAbsent(scanner.id(), scanner) != null) throw new IllegalArgumentException("Held scanner already registered: " + scanner.id());
        return () -> { synchronized (this) { scanners.remove(scanner.id(), scanner); } };
    }

    /** The single scanner owning this item. Two adapters claiming one item is a configuration error. */
    public synchronized Optional<Scanner> forItem(String item) {
        Scanner found = null;
        for (var scanner : scanners.values()) if (scanner.supports(item)) {
            if (found != null) throw new IllegalStateException("Held scanners " + found.id() + " and " + scanner.id() + " both claim " + item);
            found = scanner;
        }
        return Optional.ofNullable(found);
    }
}
