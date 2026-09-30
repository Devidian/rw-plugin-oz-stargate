package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.*;
import org.junit.Test;

public class GateCollisionGeometryTest {
    private boolean contains(double x, double y) {
        for (int i = 0; i < GateCollisionGeometry.SEGMENTS; i++) {
            var s = GateCollisionGeometry.segment(i);
            double a = Math.toRadians(s.angle()), dx = x-s.x(), dy = y-s.y();
            double u = Math.cos(a)*dx + Math.sin(a)*dy, v = -Math.sin(a)*dx + Math.cos(a)*dy;
            if (Math.abs(u) <= s.width()/2 && Math.abs(v) <= s.height()/2) return true;
        }
        return false;
    }

    @Test public void solidRingHasNoGapsAndLeavesClearAperture() {
        for (int i = 0; i < 720; i++) {
            double angle = i*Math.PI/360;
            for (double radius : new double[]{2.31,2.6,3.065})
                assertTrue(contains(Math.cos(angle)*radius,3.075+Math.sin(angle)*radius));
            assertFalse(contains(Math.cos(angle)*2.29,3.075+Math.sin(angle)*2.29));
            assertFalse(contains(Math.cos(angle)*3.12,3.075+Math.sin(angle)*3.12));
        }
        assertFalse(contains(0,3.075));
        var bottom = GateCollisionGeometry.segment(36);
        assertEquals(0f,bottom.y()-bottom.height()/2,.00001f);
        assertEquals(.48f,bottom.depth(),0f);
    }
}
