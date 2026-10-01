package de.omegazirkel.risingworld.stargate.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Folder names are setting values and must remain a single safe path segment. */
public final class AudioThemeCatalog {
    private AudioThemeCatalog() { }

    public static List<String> list(Path root) {
        if (root == null) return List.of("silent");
        try (Stream<Path> paths = Files.list(root)) {
            List<String> names = paths.filter(Files::isDirectory).filter(path -> !Files.isSymbolicLink(path))
                    .map(path -> path.getFileName().toString()).filter(name -> name.matches("[A-Za-z0-9_-]+"))
                    .sorted(Comparator.naturalOrder()).toList();
            return names.contains("silent") ? names : Stream.concat(Stream.of("silent"), names.stream()).toList();
        } catch (IOException ex) { return List.of("silent"); }
    }
}
