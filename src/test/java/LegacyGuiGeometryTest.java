package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyGuiGeometryTest {
    @Test void conversionMatchesExistingTopologyIncludingShortAndIncompleteStreams() {
        for(int mode=4;mode<=9;mode++)for(int count=0;count<65;count++) {
            var actual=new ArrayList<int[]>();
            LegacyGuiGeometry.quads(mode,count,(a,b,c,d)->actual.add(new int[]{a,b,c,d}));
            var expected=LegacyWorldRenderer.quads(mode,count);
            assertEquals(expected.length,actual.size(),"mode="+mode+", count="+count);
            for(int i=0;i<expected.length;i++)assertArrayEquals(expected[i],actual.get(i));
        }
    }
    @Test void batchesOnlyCompatibleTextureFormatAndState() {
        var quad=primitive(7,"aspect",LegacyVertexStream.RenderState.OPAQUE);
        assertTrue(LegacyGuiGeometry.compatible(quad,primitive(6,"aspect",quad.state())));
        assertFalse(LegacyGuiGeometry.compatible(quad,primitive(7,"other",quad.state())));
        assertFalse(LegacyGuiGeometry.compatible(quad,primitive(7,"",quad.state())));
        assertFalse(LegacyGuiGeometry.compatible(quad,primitive(1,"aspect",quad.state())));
        assertFalse(LegacyGuiGeometry.compatible(quad,primitive(7,"aspect",new LegacyVertexStream.RenderState(true,770,1,true,false))));
        assertFalse(LegacyGuiGeometry.compatible(quad,primitive(7,"aspect",new LegacyVertexStream.RenderState(false,770,771,true,false,false))));
        assertTrue(LegacyGuiGeometry.compatible(primitive(1,"unused",quad.state()),primitive(7,"",quad.state())));
    }
    @Test void conversionRejectsUnsupportedModes() {
        assertThrows(UnsupportedOperationException.class,()->LegacyGuiGeometry.quads(99,4,(a,b,c,d)->fail()));
    }
    private static LegacyVertexStream.Primitive primitive(int mode,String texture,LegacyVertexStream.RenderState state) {
        return new LegacyVertexStream.Primitive(mode,texture,List.of(),state);
    }
}
