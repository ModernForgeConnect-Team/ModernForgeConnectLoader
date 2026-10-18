package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyItemLayout;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyItemLayoutTest {
    @Test void tallOffsetModelsAreCenteredAndFitWithoutChangingTheirProportions() {
        var geometry = box(-.8, -1.5, -.7, -.2, .5, -.3);
        var fit = LegacyItemLayout.fitGui(geometry);
        var bounds = fit.apply(LegacyItemLayout.guiBounds(geometry));
        assertTrue(fit.scale() < 1);
        assertEquals(0, (bounds.minX()+bounds.maxX())*.5, 1e-10);
        assertEquals(0, (bounds.minY()+bounds.maxY())*.5, 1e-10);
        assertEquals(.9, Math.max(bounds.maxX()-bounds.minX(), bounds.maxY()-bounds.minY()), 1e-10);
        assertTrue(bounds.minX() >= -.5 && bounds.maxX() <= .5 && bounds.minY() >= -.5 && bounds.maxY() <= .5);
    }
    @Test void ordinaryCubesAndAlreadyFittingAsymmetricModelsKeepTheirDisplayPosition() {
        assertEquals(LegacyItemLayout.GuiFit.IDENTITY, LegacyItemLayout.fitGui(box(-.5,-.5,-.5,.5,.5,.5)));
        assertEquals(LegacyItemLayout.GuiFit.IDENTITY, LegacyItemLayout.fitGui(box(-.4,-.4,-.4,.2,-.1,.2)));
        assertEquals(LegacyItemLayout.GuiFit.IDENTITY, LegacyItemLayout.fitGui(List.of()));
    }
    private static List<LegacyVertexStream.Primitive> box(double x0, double y0, double z0, double x1, double y1, double z1) {
        var vertices = new ArrayList<LegacyVertexStream.Vertex>();
        for (double x : new double[]{x0,x1}) for (double y : new double[]{y0,y1}) for (double z : new double[]{z0,z1})
            vertices.add(new LegacyVertexStream.Vertex(x,y,z,0,0,0,0,1,-1));
        return List.of(new LegacyVertexStream.Primitive(7,"test:banner",vertices));
    }
}
