package de.omegazirkel.risingworld.stargate.dhd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import net.risingworld.api.assets.AssetBundle;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.ModelAsset;
import net.risingworld.api.assets.ModelImportSettings;
import net.risingworld.api.assets.ModelImportSettings.MeshImportFlags;
import net.risingworld.api.assets.PrefabAsset;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.worldelements.Model;

/** User-supplied DHD visual; independent material/asset lifetime and interaction collider. */
public final class DhdModelAssets implements AutoCloseable {
    private final Path root;
    private final PluginSettings pluginSettings;
    private final Map<String, ModelAsset> meshes = new HashMap<>();
    private final Map<String, ModelImportSettings> settings = new HashMap<>();
    private MaterialAsset stone, accents, keys, litKeys, markings, litMarkings, activation, activeActivation;
    private TextureAsset surfaceTexture;
    private AssetBundle bundle;
    private PrefabAsset bundlePrefab;
    private boolean bundleFailed;

    public DhdModelAssets(OZStargate plugin, PluginSettings pluginSettings) {
        root = Path.of(plugin.getPath(), "assets", "models", "dhd");
        this.pluginSettings = pluginSettings;
    }

    public DhdRenderModel create() {
        load();
        if (!bundleFailed && "bundle".equals(pluginSettings.gateAssetMode)) {
            try {
                return new BundledDhdModel(this, bundledPrefab());
            } catch (RuntimeException ex) {
                OZStargate.logger().error("Stargate DHD AssetBundle failed; using OBJ DHD: " + ex.getMessage());
                bundleFailed = true;
            }
        }
        return new AnimatedDhdModel(this);
    }

    private PrefabAsset bundledPrefab() {
        if (bundlePrefab != null) return bundlePrefab;
        Path file = root.resolve("stargatedhd.bundle");
        if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing DHD bundle: " + file);
        bundle = AssetBundle.loadFromFile(file.toString());
        if (bundle == null) throw new IllegalStateException("DHD AssetBundle loader returned no asset");
        String name = null;
        for (String candidate : bundle.getAllAssetNames()) {
            if (candidate.toLowerCase(java.util.Locale.ROOT).endsWith("/stargatedhd.prefab")) {
                name = candidate;
                break;
            }
        }
        if (name == null) throw new IllegalStateException("DHD prefab absent from AssetBundle");
        bundlePrefab = PrefabAsset.loadFromAssetBundle(bundle, name);
        if (bundlePrefab == null) throw new IllegalStateException("DHD prefab loader returned no asset");
        OZStargate.logger().info("Using Stargate DHD AssetBundle prefab " + name);
        return bundlePrefab;
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
        if (bundlePrefab != null) { bundlePrefab.dispose(); bundlePrefab = null; }
        if (bundle != null) { bundle.dispose(); bundle = null; }
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
