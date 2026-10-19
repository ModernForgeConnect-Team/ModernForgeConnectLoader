package github.com.gengyoubo.mfcl.api;

/** Detached stack value. NBT uses original 1.7.10 SNBT syntax, including indexed lists. */
public record LegacyStack(String key, int metadata, int count, String snbt) {
    public LegacyStack {
        java.util.Objects.requireNonNull(key);
        if (!key.contains(":")) throw new IllegalArgumentException("Expected namespace:item key");
        if (metadata < 0 || count < 1 || count > 127) throw new IllegalArgumentException("Invalid legacy stack size/metadata");
        snbt = snbt == null ? "" : snbt;
    }
}
