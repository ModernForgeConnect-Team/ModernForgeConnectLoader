package github.com.gengyoubo.mfcl.bridge;

/** Explicit old vanilla texture moves used by original renderers; mod resource names remain intact. */
public final class LegacyTexturePaths {
    private LegacyTexturePaths() {}
    /** Old resource locations allow uppercase paths which modern ResourceLocation rejects. */
    public static String legacyResource(String original) {
        if(original==null||original.isBlank())throw new IllegalArgumentException("Empty original texture resource");
        int colon=original.indexOf(':');
        return "assets/"+(colon<0?"minecraft":original.substring(0,colon))+"/"+(colon<0?original:original.substring(colon+1));
    }
    public static String modernResource(String original) {
        return switch(original) {
            case "minecraft:textures/entity/arrow.png" -> "minecraft:textures/entity/projectiles/arrow.png";
            default -> original;
        };
    }
}
