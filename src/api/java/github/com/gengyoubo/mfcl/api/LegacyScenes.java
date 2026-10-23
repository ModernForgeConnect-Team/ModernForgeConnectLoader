package github.com.gengyoubo.mfcl.api;

/**
 * {@link LegacyFeatures#SCENES}: private, off-world original block scenes for previews such as recipe viewers,
 * tooltips and manuals. A scene is not part of any host or old dimension; nothing in it ticks, saves or reaches
 * the server. Client thread only.
 */
public interface LegacyScenes {
    /** A new empty scene. Close it when it is no longer shown. */
    LegacyScene create();
}
