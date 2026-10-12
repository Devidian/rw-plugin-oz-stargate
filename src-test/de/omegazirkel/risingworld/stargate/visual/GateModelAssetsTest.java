package de.omegazirkel.risingworld.stargate.visual;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import org.junit.Test;

public class GateModelAssetsTest {
    @Test public void gateAndPreviewReportMissingBundle() {
        try (GateModelAssets assets = new GateModelAssets(Path.of("absent-stargate-test-world"))) {
            assertMissing(() -> assets.createAnimated());
            assertMissing(() -> assets.createPreview());
        }
    }

    private static void assertMissing(Runnable create) {
        try { create.run(); fail("Expected missing bundle error"); }
        catch (IllegalStateException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("stargatemilkyway.bundle"));
        }
    }
}
