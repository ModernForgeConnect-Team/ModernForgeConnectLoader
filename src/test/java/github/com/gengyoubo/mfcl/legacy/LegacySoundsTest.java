package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacySounds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacySoundsTest {
    @Test void mapsVanillaEventsAndPreservesModNamespaces() {
        assertEquals("minecraft:entity.experience_orb.pickup",LegacySounds.modernId("random.orb"));
        assertEquals("minecraft:ui.button.click",LegacySounds.modernId("minecraft:gui.button.press"));
        assertEquals("thaumcraft:cameraticks",LegacySounds.modernId("thaumcraft:cameraticks"));
        assertEquals("thaumcraft:runicshieldcharge",LegacySounds.modernId("thaumcraft:runicShieldCharge"));
    }
    @Test void convertsOldNamespaceAndCaseWithoutLosingSoundOptions() {
        var converted=LegacySounds.definitions("thaumcraft","""
            {"runicShieldCharge":{"category":"master","sounds":["runicShieldCharge",{"name":"other:MixedCase","weight":3,"volume":0.5,"pitch":0.8,"stream":true}]},"camera":{"replace":true,"sounds":[{"name":"cameraticks","type":"event"}]}}
            """);
        var event=converted.getAsJsonObject("runicshieldcharge");assertFalse(event.has("category"));
        assertEquals("thaumcraft:runicshieldcharge",event.getAsJsonArray("sounds").get(0).getAsString());
        var weighted=event.getAsJsonArray("sounds").get(1).getAsJsonObject();
        assertEquals("other:mixedcase",weighted.get("name").getAsString());assertEquals(3,weighted.get("weight").getAsInt());
        assertEquals(.5,weighted.get("volume").getAsDouble());assertTrue(weighted.get("stream").getAsBoolean());
        assertTrue(converted.getAsJsonObject("camera").get("replace").getAsBoolean());
        assertEquals("thaumcraft:cameraticks",converted.getAsJsonObject("camera").getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
    }
}
