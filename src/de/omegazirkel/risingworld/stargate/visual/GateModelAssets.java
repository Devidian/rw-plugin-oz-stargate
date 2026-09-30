package de.omegazirkel.risingworld.stargate.visual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.assets.ModelAsset;
import net.risingworld.api.assets.ModelImportSettings;
import net.risingworld.api.assets.ModelImportSettings.MeshImportFlags;
import net.risingworld.api.worldelements.Model;

/** Shared native resources for temporary previews and persisted gate models. */
public final class GateModelAssets implements AutoCloseable {
    // Source OBJ uses metres; Rising World uses two block units per metre.
    private static final float WORLD_UNITS_PER_METRE = 2f;
    private final Path path;
    private ModelAsset mesh;
    private MaterialAsset material, ringMaterial, housingMaterial, glyphMaterial;
    private TextureAsset surfaceTexture;
    private ModelImportSettings imports;
    private final Map<String, ModelAsset> parts = new HashMap<>();
    private final Map<String, ModelImportSettings> partImports = new HashMap<>();
    private MaterialAsset amber, open, dark;
    private WormholeAssets wormhole;

    public GateModelAssets(OZStargate plugin) {
        path = Path.of(plugin.getPath(), "assets", "models", "milkyway", "textured-milkyway-preview.obj");
    }

    public Model create() {
        Model model = new Model(previewMesh(), material);
        model.setLocalScale(WORLD_UNITS_PER_METRE, WORLD_UNITS_PER_METRE, WORLD_UNITS_PER_METRE);
        return model;
    }

    private ModelAsset previewMesh() {
        load();
        if (mesh == null) {
            mesh = ModelAsset.loadFromFile(path.toString(), imports);
            if (mesh == null) throw new IllegalStateException("Model loader returned no asset");
        }
        return mesh;
    }

    public Model createAnimated() {
        load();
        return new AnimatedGateModel(this);
    }

    MaterialAsset dark() { return dark; }
    MaterialAsset stone() { return material; }
    MaterialAsset ring() { return ringMaterial; }
    MaterialAsset glyphs() { return glyphMaterial; }
    MaterialAsset housing() { return housingMaterial; }
    MaterialAsset amber() { return amber; }
    MaterialAsset open() { return open; }

    WormholeAssets wormhole() {
        if (wormhole == null) wormhole = new WormholeAssets(path.getParent());
        return wormhole;
    }

    ModelAsset part(String name) {
        return parts.computeIfAbsent(name, key -> {
            boolean textured = switch (key) {
                case "body", "ring", "chevron-fixed-frame", "chevron-v-frame" -> true;
                default -> false;
            };
            Path file = path.resolveSibling((textured ? "textured-" : "") + "articulated-" + key + ".obj");
            if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing gate part: " + file);
            // Each asynchronous native import owns its settings context.
            ModelImportSettings settings = partImports.computeIfAbsent(key,
                    ignored -> importSettings("oz-stargate-" + key));
            ModelAsset asset = ModelAsset.loadFromFile(file.toString(), settings);
            if (asset == null) throw new IllegalStateException("Model loader returned no part: " + key);
            return asset;
        });
    }

    private void load() {
        if (imports != null) return;
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing gate model: " + path);
        try {
            imports = importSettings("oz-stargate-static-preview");
            Path texture = path.resolveSibling("gate-subtle-color.png");
            if (!Files.isRegularFile(texture)) throw new IllegalStateException("Missing gate surface texture");
            surfaceTexture = TextureAsset.loadFromFile(texture.toString());
            material = surface("oz-stargate-authored-body", .08f, .18f);
            ringMaterial = surface("oz-stargate-authored-ring", .16f, .26f);
            housingMaterial = surface("oz-stargate-authored-housing", .12f, .22f);
            glyphMaterial = MaterialAsset.create("oz-stargate-authored-glyph-faces");
            glyphMaterial.setColor(.56f, .57f, .58f, 1f);
            glyphMaterial.setMetallic(.08f);
            glyphMaterial.setSmoothness(.12f);
            dark = MaterialAsset.create("oz-stargate-chevron-unlit-strips");
            dark.setColor(0.12f, 0.07f, 0.035f, 1f);
            amber = MaterialAsset.create("oz-stargate-chevron-locked");
            amber.setColor(1f, 0.42f, 0.035f, 1f);
            amber.setSmoothness(0.35f);
            open = MaterialAsset.create("oz-stargate-chevron-open");
            open.setColor(1f, 0.7f, 0.18f, 1f);
            open.setSmoothness(0.35f);
        } catch (RuntimeException ex) { close(); throw ex; }
    }

    private MaterialAsset surface(String name, float metallic, float smoothness) {
        MaterialAsset result = MaterialAsset.create(name);
        result.setTexture(surfaceTexture);
        result.setColor(.85f, .85f, .85f, 1f);
        result.setMetallic(metallic);
        result.setSmoothness(smoothness);
        return result;
    }

    private static ModelImportSettings importSettings(String name) {
        ModelImportSettings settings = ModelImportSettings.create(name);
        settings.meshScaleFactor = 1f;
        settings.meshPivot = ModelImportSettings.ModelPivot.Default;
        settings.meshNormalSmoothingAngle = 40f;
        // Native combination requires CPU-readable source meshes.
        settings.setModelSettings(MeshImportFlags.MergeToSingleObject, MeshImportFlags.KeepMeshesReadable,
                MeshImportFlags.GenerateNormals, MeshImportFlags.OptimizeMesh);
        settings.setMaterialSettings();
        return settings;
    }

    @Override public void close() {
        for (ModelAsset part : parts.values()) part.dispose();
        parts.clear();
        for (ModelImportSettings settings : partImports.values()) settings.dispose();
        partImports.clear();
        if (wormhole != null) { wormhole.close(); wormhole = null; }
        if (dark != null) { dark.dispose(); dark = null; }
        if (amber != null) { amber.dispose(); amber = null; }
        if (open != null) { open.dispose(); open = null; }
        if (mesh != null) { mesh.dispose(); mesh = null; }
        if (material != null) { material.dispose(); material = null; }
        if (ringMaterial != null) { ringMaterial.dispose(); ringMaterial = null; }
        if (glyphMaterial != null) { glyphMaterial.dispose(); glyphMaterial = null; }
        if (housingMaterial != null) { housingMaterial.dispose(); housingMaterial = null; }
        if (surfaceTexture != null) { surfaceTexture.dispose(); surfaceTexture = null; }
        if (imports != null) { imports.dispose(); imports = null; }
    }
}
