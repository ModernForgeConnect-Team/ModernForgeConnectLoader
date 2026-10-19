package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics;
import github.com.gengyoubo.mfcl.bridge.LegacyWorldGraphics;
import github.com.gengyoubo.mfcl.bridge.LegacyRenderBridge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyGuiGraphicsTest {
    @Test void notificationProjectionUsesAbsolutePixelsAndRestoresForegroundTranslation() {
        try(var frame=LegacyGuiGraphics.begin(320,240,0,0,Set.of())) {
            LegacyRenderBridge.translate(70,35,0);LegacyRenderBridge.push();
            LegacyRenderBridge.clear(256);LegacyWorldGraphics.glMatrixMode(5889);LegacyRenderBridge.loadIdentity();
            LegacyRenderBridge.ortho(0,320,240,0,1000,3000);
            LegacyWorldGraphics.glMatrixMode(5888);LegacyRenderBridge.loadIdentity();LegacyRenderBridge.translate(0,0,-2000);
            LegacyGuiGraphics.text("Notice",5,6,0xffffff,false);LegacyRenderBridge.pop();
            LegacyGuiGraphics.text("Slot",5,6,0xffffff,false);
            assertEquals(List.of(new LegacyGuiGraphics.Text("Notice",5,6,0xffffff,false,1,1),new LegacyGuiGraphics.Text("Slot",75,41,0xffffff,false,1,1)),frame.commands());
            assertThrows(UnsupportedOperationException.class,()->LegacyRenderBridge.clear(16384));
        }
        assertThrows(UnsupportedOperationException.class,()->LegacyRenderBridge.clear(256));
    }
    @Test void tooltipDepthChangesStayBetweenItemsAndOverlayDrawing() {
        try (var frame = LegacyGuiGraphics.begin(320,240,0,0,Set.of())) {
            Object item = new Object();
            LegacyGuiGraphics.item(item,10,20,false,null);
            LegacyWorldGraphics.glDisable(2929);
            LegacyWorldGraphics.glDisable(2929);
            LegacyGuiGraphics.rect(8,8,80,25,0xf0100010);
            LegacyGuiGraphics.text("Tooltip",10,10,0xffffff,true);
            LegacyWorldGraphics.glEnable(2929);
            assertEquals(List.of(
                    new LegacyGuiGraphics.Item(item,10,20,false,null,1,1),
                    new LegacyGuiGraphics.DepthTest(false),
                    new LegacyGuiGraphics.Rect(8,8,80,25,0xf0100010,0xf0100010),
                    new LegacyGuiGraphics.Text("Tooltip",10,10,0xffffff,true,1,1),
                    new LegacyGuiGraphics.DepthTest(true)),frame.commands());
        }
    }

    @Test void attributeRestoreAndNextFrameDoNotLeakTooltipDepthState() {
        try (var frame = LegacyGuiGraphics.begin(320,240,0,0,Set.of())) {
            LegacyWorldGraphics.glPushAttrib(0x2000);
            LegacyWorldGraphics.glDisable(2929);
            LegacyWorldGraphics.glPopAttrib();
            assertEquals(List.of(new LegacyGuiGraphics.DepthTest(false),new LegacyGuiGraphics.DepthTest(true)),frame.commands());
            LegacyWorldGraphics.glDisable(2929);
        }
        try (var frame = LegacyGuiGraphics.begin(320,240,0,0,Set.of())) {
            LegacyWorldGraphics.glDisable(2929);
            assertEquals(List.of(new LegacyGuiGraphics.DepthTest(false)),frame.commands());
        }
    }
}
