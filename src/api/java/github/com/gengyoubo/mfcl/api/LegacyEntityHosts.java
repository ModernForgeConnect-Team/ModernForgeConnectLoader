package github.com.gengyoubo.mfcl.api;

import java.util.Optional;

/**
 * Registers modern server-side hosts for exact legacy entity classes. Register on both physical sides from
 * {@link MfclApi#whenReady}; the factory runs on the owning server thread inside the legacy runtime scope.
 * Unregistered entities use MFCL's original-logic host. Registrations do not replace existing entities.
 *
 * <p>The addon registers the modern EntityType, attributes, renderer and networking itself. Its entity is responsible
 * for its gameplay, saving the bytes returned by {@link #save}, and calling {@link #restore} on the server after load.
 * A host that needs an old client-side renderer must provide its own client synchronization and restoration.
 */
public interface LegacyEntityHosts {
    @FunctionalInterface interface Factory {
        /**
         * Create an unspawned modern Entity in {@code serverLevel}; do not add it to the world. The original is valid
         * for this callback only. MFCL copies its position, rotation and velocity, adopts the host identity and adds
         * the entity when the enclosing world operation commits. Return null to use MFCL's ordinary host.
         */
        Object create(Object serverLevel, LegacyObject original);
    }
    /** Register an exact binary class name; duplicate registrations fail. Closing removes only this registration. */
    AutoCloseable register(String legacyClass, Factory factory);
    /** The original bound to this host, valid on the current owning thread while the host remains alive. */
    Optional<LegacyObject> original(Object host);
    /** Save the bound original's complete legacy NBT; the returned bytes belong to the caller. */
    byte[] save(Object host);
    /** Restore and bind an original after host load. Call on the server thread, after the world is available. */
    void restore(Object host, String legacyClass, byte[] nbt);
    /** Update the original's position, velocity, rotation and, for living entities, health from its modern host. */
    void sync(Object host);
}
