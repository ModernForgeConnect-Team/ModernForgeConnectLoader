package github.com.gengyoubo.mfcl.api;

/**
 * Read-side identities for host content unknown to the old registry. Declare in a stable
 * order on both sides. Existing original identities are preserved. These objects
 * let original mod code query host targets; they do not implement the host content's gameplay.
 *
 * <p>Once {@link #item} has returned an identity, every stack of that host item reaching old code (inventories,
 * crafting grids, {@link LegacyMappings#ITEM_STACK}) maps to exactly that key and metadata, so original recipe
 * matching against the identity succeeds. Its count and complete host NBT are kept
 * ({@link LegacyFeatures#HOST_ITEM_NBT}): the NBT travels in the reserved old tag {@code mfcl_host}, and an old stack
 * of the identity (a recipe output, say) converts back to the identical host stack. Original matching that ignores
 * NBT or only requires the recipe's own tags is unaffected; matching that demands equal NBT sees {@code mfcl_host}.
 * Stacks of a host item whose identity was never requested have no old form.
 */
public interface LegacyHostRepresentations {
    /** Returns the original stack identity, or a new read-only host item identity. */
    LegacyStack item(String hostRegistryId);
    /** Returns null for original blocks without an item representation. */
    LegacyStack block(String hostRegistryId);
    /** Returns the original EntityList name, or a distinct registered host entity name. */
    String entity(String hostRegistryId);
}
