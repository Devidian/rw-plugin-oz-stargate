package de.omegazirkel.risingworld.stargate.dhd;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import org.junit.Test;

public class DhdModelAssetsTest {
    @Test public void dhdReportsMissingBundle() {
        try (DhdModelAssets assets = new DhdModelAssets(Path.of("absent-stargate-test-world"))) {
            try { assets.create(); fail("Expected missing bundle error"); }
            catch (IllegalStateException ex) {
                assertTrue(ex.getMessage(), ex.getMessage().contains("stargatedhd.bundle"));
            }
        }
    }
}
