package github.com.gengyoubo.mfcl.bridge;

/** Explicit old vanilla texture moves used by original renderers; mod resource names remain intact. */
public final class LegacyTexturePaths {
    private LegacyTexturePaths() {}
    public static String modernResource(String original) {
        return switch(original) {
            case "minecraft:textures/entity/arrow.png" -> "minecraft:textures/entity/projectiles/arrow.png";
            default -> original;
        };
    }
}
