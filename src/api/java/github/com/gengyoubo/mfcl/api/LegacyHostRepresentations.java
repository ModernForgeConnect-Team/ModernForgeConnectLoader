package github.com.gengyoubo.mfcl.api;

/**
 * Read-side identities for host content unknown to the old registry. Register during setup,
 * in a stable order on both sides. Existing original identities are preserved. These objects
 * let original mod code query host targets; they do not implement the host content's gameplay.
 */
public interface LegacyHostRepresentations {
    /** Returns the original stack identity, or a new registered host item identity. */
    LegacyStack item(String hostRegistryId);
    /** Returns null for original blocks without an item representation. */
    LegacyStack block(String hostRegistryId);
    /** Returns the original EntityList name, or a distinct registered host entity name. */
    String entity(String hostRegistryId);
}
