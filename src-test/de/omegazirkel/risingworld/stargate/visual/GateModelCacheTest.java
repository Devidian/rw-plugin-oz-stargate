package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.assertSame;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.worldelements.GameObject;

public class GateModelCacheTest {
    @Test public void creationMayReenterAndModifyTheModelCache() {
        ConcurrentMap<String, GateRenderModel> models = new ConcurrentHashMap<>();
        GateRenderModel created = model();
        GateRenderModel other = model();
        AtomicInteger builds = new AtomicInteger();

        assertSame(created, GateVisualService.cachedModel(models, "A", () -> {
            builds.incrementAndGet();
            models.put("OTHER", other);
            return created;
        }));
        assertSame(created, models.get("A"));
        assertSame(other, models.get("OTHER"));
        assertSame(created, GateVisualService.cachedModel(models, "A", () -> {
            builds.incrementAndGet();
            return model();
        }));
        org.junit.Assert.assertEquals(1, builds.get());
    }

    @Test public void creationKeepsAnExistingModelInsertedDuringTheCallback() {
        ConcurrentMap<String, GateRenderModel> models = new ConcurrentHashMap<>();
        GateRenderModel first = model();
        GateRenderModel second = model();

        assertSame(first, GateVisualService.cachedModel(models, "A", () -> {
            models.put("A", first);
            return second;
        }));
        assertSame(first, models.get("A"));
    }

    private static GateRenderModel model() {
        return new GateRenderModel() {
            @Override public GameObject object() { return null; }
            @Override public void update(GateView view) { }
            @Override public void frame(long now) { }
        };
    }
}
