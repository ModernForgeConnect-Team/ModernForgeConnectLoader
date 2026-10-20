package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyTexturePaths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyTexturePathsTest {
    @Test void vanillaArrowMovesWhileModResourcesKeepTheirIdentity() {
        assertEquals("minecraft:textures/entity/projectiles/arrow.png",LegacyTexturePaths.modernResource("minecraft:textures/entity/arrow.png"));
        assertEquals("thaumcraft:textures/models/arrow.png",LegacyTexturePaths.modernResource("thaumcraft:textures/models/arrow.png"));
        assertEquals("minecraft:textures/entity/bat.png",LegacyTexturePaths.modernResource("minecraft:textures/entity/bat.png"));
    }
}
