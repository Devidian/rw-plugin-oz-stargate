package de.omegazirkel.risingworld.stargate.dhd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.ModelAsset;
import net.risingworld.api.assets.ModelImportSettings;
import net.risingworld.api.assets.ModelImportSettings.MeshImportFlags;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.worldelements.Model;

/** User-supplied DHD visual; independent material/asset lifetime and interaction collider. */
public final class DhdModelAssets implements AutoCloseable {
    private final Path root;
    private final Map<String, ModelAsset> meshes = new HashMap<>();
    private final Map<String, ModelImportSettings> settings = new HashMap<>();
    private MaterialAsset stone, accents, keys, litKeys, markings, litMarkings, activation, activeActivation;
    private TextureAsset surfaceTexture;

    public DhdModelAssets(OZStargate plugin) {
        root = Path.of(plugin.getPath(), "assets", "models", "dhd");
    }

    public Model create() {
        load();
        return new AnimatedDhdModel(this);
    }

    ModelAsset mesh(String name) {
        return meshes.computeIfAbsent(name, key -> {
            Path path = root.resolve(key + ".obj");
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing DHD mesh " + path);
            ModelImportSettings imports = settings.computeIfAbsent(key, ignored -> {
                ModelImportSettings value = ModelImportSettings.create("oz-stargate-" + key);
                value.meshScaleFactor = 1f;
                value.meshPivot = ModelImportSettings.ModelPivot.Default;
                value.meshNormalSmoothingAngle = 40f;
                value.setModelSettings(MeshImportFlags.MergeToSingleObject, MeshImportFlags.KeepMeshesReadable,
                        MeshImportFlags.GenerateNormals, MeshImportFlags.OptimizeMesh);
                value.setMaterialSettings();
                return value;
            });
            ModelAsset asset = ModelAsset.loadFromFile(path.toString(), imports);
            if (asset == null) throw new IllegalStateException("DHD loader returned no asset: " + key);
            return asset;
        });
    }

    private void load() {
        if (stone != null) return;
        Path texture = root.resolveSibling("milkyway").resolve("gate-subtle-color.png");
        if (!Files.isRegularFile(texture)) throw new IllegalStateException("Missing DHD stone texture " + texture);
        surfaceTexture = TextureAsset.loadFromFile(texture.toString());
        stone = material("oz-stargate-supplied-dhd-stone", .74f, .76f, .78f, .06f, .19f);
        stone.setTexture(surfaceTexture);
        accents = material("oz-stargate-supplied-dhd-accents", .58f, .59f, .6f, .07f, .22f);
        accents.setTexture(surfaceTexture);
        keys = material("oz-stargate-supplied-dhd-keys", .51f, .49f, .44f, .08f, .21f);
        litKeys = material("oz-stargate-supplied-dhd-lit-keys", 1f, .65f, .19f, .08f, .3f);
        markings = material("oz-stargate-supplied-dhd-symbols", .66f, .64f, .59f, .06f, .16f);
        litMarkings = material("oz-stargate-supplied-dhd-lit-symbols", 1f, .82f, .35f, .06f, .28f);
        activation = material("oz-stargate-supplied-dhd-activate", .68f, .13f, .06f, .16f, .36f);
        activeActivation = material("oz-stargate-supplied-dhd-active", 1f, .28f, .1f, .16f, .4f);
    }

    MaterialAsset stone() { return stone; }
    MaterialAsset accents() { return accents; }
    MaterialAsset keys() { return keys; }
    MaterialAsset litKeys() { return litKeys; }
    MaterialAsset markings() { return markings; }
    MaterialAsset litMarkings() { return litMarkings; }
    MaterialAsset activation() { return activation; }
    MaterialAsset activeActivation() { return activeActivation; }

    private static MaterialAsset material(String name, float r, float g, float b, float metal, float smooth) {
        MaterialAsset result = MaterialAsset.create(name);
        result.setColor(r, g, b, 1f);
        result.setMetallic(metal);
        result.setSmoothness(smooth);
        return result;
    }

    @Override public void close() {
        for (ModelAsset mesh : meshes.values()) mesh.dispose();
        meshes.clear();
        for (ModelImportSettings importSettings : settings.values()) importSettings.dispose();
        settings.clear();
        if (stone != null) { stone.dispose(); stone = null; }
        if (accents != null) { accents.dispose(); accents = null; }
        if (keys != null) { keys.dispose(); keys = null; }
        if (litKeys != null) { litKeys.dispose(); litKeys = null; }
        if (markings != null) { markings.dispose(); markings = null; }
        if (litMarkings != null) { litMarkings.dispose(); litMarkings = null; }
        if (activation != null) { activation.dispose(); activation = null; }
        if (activeActivation != null) { activeActivation.dispose(); activeActivation = null; }
        if (surfaceTexture != null) { surfaceTexture.dispose(); surfaceTexture = null; }
    }
}
