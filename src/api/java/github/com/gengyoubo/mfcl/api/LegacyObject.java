package github.com.gengyoubo.mfcl.api;

/** Opaque reference owned by one live legacy runtime. Never cast to a modern Minecraft class. */
public interface LegacyObject {
    String typeName();
}
