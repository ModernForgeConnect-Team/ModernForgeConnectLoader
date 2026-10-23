package github.com.gengyoubo.mfcl.api;

/**
 * Names reported by {@link LegacyRuntime#features()}. Probe a feature instead of catching
 * {@link UnsupportedOperationException}: a member whose feature is reported does not throw it.
 * A feature can be missing because this MFCL build lacks it or because its original prerequisite
 * (for example Baubles) is not loaded.
 */
public final class LegacyFeatures {
    private LegacyFeatures() { }
    /** {@link LegacyRuntime#hostRepresentations()}. */
    public static final String HOST_REPRESENTATIONS = "host-representations";
    /** {@link LegacyRuntime#hostBlock}. */
    public static final String HOST_BLOCKS = "host-blocks";
    /** {@link LegacyRuntime#languages()}. */
    public static final String LANGUAGES = "languages";
    /** {@link LegacyRuntime#withPlayer}. */
    public static final String PLAYER = "player";
    /** {@link LegacyRuntime#withClientPlayer}; only on a physical client. */
    public static final String CLIENT_PLAYER = "client-player";
    /** Old player actors carry the host entity id and stay the same object per player and world. */
    public static final String PLAYER_IDENTITY = "player.identity";
    /** Flight, speeds, fall distance, step height, potion effects and entity data follow the host player both ways. */
    public static final String PLAYER_SYNC = "player.sync";
    /** {@link LegacyRuntime#equipment()}: the four armor slots. */
    public static final String EQUIPMENT = "equipment";
    /** The four bauble slots of {@link LegacyEquipment}; requires original Baubles. */
    public static final String EQUIPMENT_BAUBLES = "equipment.baubles";
    /** {@link LegacyEquipment#setTicking}: onArmorTick / onWornTick for supplied items. */
    public static final String EQUIPMENT_TICK = "equipment.tick";
    /** {@link LegacyEquipment.Source#changed} and main inventory write-back. */
    public static final String EQUIPMENT_WRITE_BACK = "equipment.write-back";
    /** {@link LegacyRuntime#events()}. */
    public static final String EVENTS = "events";
    /** {@link LegacyMappings#applyTo}. */
    public static final String MAPPINGS_APPLY = "mappings.apply";
    /** {@link LegacyValues} array members. */
    public static final String MLA_ARRAYS = "mla.arrays";
    /** {@link LegacyValues} collection snapshots. */
    public static final String MLA_COLLECTIONS = "mla.collections";
    /** {@link LegacyValues#newList}, {@link LegacyValues#add}, {@link LegacyValues#removeIf} and the other in-place collection edits. */
    public static final String MLA_COLLECTIONS_MUTABLE = "mla.collections.mutable";
    /**
     * MLA parameters accept {@code String} and boxed values wherever the old parameter type accepts them, including
     * {@code Object} and {@code Object[]} elements (old varargs such as recipe inputs), for methods and constructors;
     * contracts may declare {@code Object} parameters and returns.
     */
    public static final String MLA_OBJECT_ARGUMENTS = "mla.object-arguments";
    /** Host-only items registered through {@link LegacyHostRepresentations#item} keep their count and complete NBT through old code. */
    public static final String HOST_ITEM_NBT = "host-representations.nbt";
    /** {@link LegacyValues#isInstance}. */
    public static final String MLA_INSTANCE = "mla.instance";
    /** {@link LegacyRedirects}, registered through {@link github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider}. */
    public static final String REDIRECTS = "redirects";
    /** {@link LegacyRuntime#scenes()}; only on a physical client. */
    public static final String SCENES = "scenes";
}
