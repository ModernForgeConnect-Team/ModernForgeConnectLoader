package github.com.gengyoubo.mfcl.api;

import java.util.Objects;

/** Immutable metadata for a successfully loaded original mod. IDs retain their original case. */
public record LegacyModInfo(String id, String name, String version, String sourceMinecraft) {
    public LegacyModInfo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(version);
        Objects.requireNonNull(sourceMinecraft);
    }
}
