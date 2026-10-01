package de.omegazirkel.risingworld.stargate.audio;

import static org.junit.Assert.assertEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class AudioThemeCatalogTest {
    @Test public void listsOnlySafeRealThemeDirectories() throws Exception {
        Path root = Files.createTempDirectory("stargate-themes");
        try {
            Files.createDirectory(root.resolve("silent"));
            Files.createDirectory(root.resolve("reference"));
            Files.createDirectory(root.resolve("bad name"));
            Files.writeString(root.resolve("not-a-theme"), "x");
            Files.createSymbolicLink(root.resolve("linked"), root.resolve("reference"));
            assertEquals(List.of("reference", "silent"), AudioThemeCatalog.list(root));
            assertEquals(List.of("silent"), AudioThemeCatalog.list(root.resolve("missing")));
        } finally {
            try (var paths = Files.list(root)) {
                for (Path path : paths.toList()) Files.delete(path);
            }
            Files.delete(root);
        }
    }
}
