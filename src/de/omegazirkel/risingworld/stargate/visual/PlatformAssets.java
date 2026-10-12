package de.omegazirkel.risingworld.stargate.visual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import net.risingworld.api.assets.AssetBundle;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.assets.PrefabAsset;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.worldelements.Prefab;

/** Shared prefab resources for the four optional Accessory Pack platforms. */
final class PlatformAssets implements AutoCloseable {
    private final Path bundlePath;
    private final Map<PlatformType, PrefabAsset> prefabs = new EnumMap<>(PlatformType.class);
    private AssetBundle bundle;
    private MaterialAsset sandstone, darkStone, insetStone, lamp;
    private TextureAsset stoneTexture;

    PlatformAssets(Path bundlePath) { this.bundlePath = bundlePath; }

    boolean available(PlatformType type) {
        try { prefab(type); return true; }
        catch (RuntimeException ex) {
            OZStargate.logger().error("Stargate platform unavailable: " + ex.getMessage());
            return false;
        }
    }

    Prefab create(PlatformType type) {
        Prefab object = new Prefab(prefab(type));
        object.addComponent(null, "DisallowGPUDrivenRendering");
        object.setLayer(Layer.DEFAULT);
        object.setLayer("Collision", Layer.OBJECT, true);
        // The parent gate prefab is scaled 2x; counter its visual lift so the
        // platform stays anchored at the aimed floor point, including old saves.
        object.setLocalPosition(0f,
                1.2f - type.deckHeightWorld() / 2f - type.gateLiftWorld() / 2f, 0f);
        for (int lod = 0; lod < 2; lod++) {
            int surfaces = lod == 0 && type != PlatformType.CIRCULAR ? 3 : 1;
            for (int surface = 0; surface < surfaces; surface++)
                object.setMaterial("LOD" + lod + "/Body/Surface_" + surface,
                        material(type, lod, surface));
        }
        return object;
    }

    private PrefabAsset prefab(PlatformType type) {
        PrefabAsset existing = prefabs.get(type);
        if (existing != null) return existing;
        if (bundle == null) {
            if (!Files.isRegularFile(bundlePath)) throw new IllegalStateException("Missing bundle: " + bundlePath);
            bundle = AssetBundle.loadFromFile(bundlePath.toString());
            if (bundle == null) throw new IllegalStateException("Platform bundle loader returned no asset");
        }
        String filename = "stargateplatform" + type.key().replace("_", "") + ".prefab";
        String name = null;
        for (String candidate : bundle.getAllAssetNames()) {
            if (candidate.toLowerCase(Locale.ROOT).endsWith("/" + filename)
                    || candidate.equalsIgnoreCase(filename)) { name = candidate; break; }
        }
        if (name == null) throw new IllegalStateException("Missing platform prefab " + filename);
        PrefabAsset asset = PrefabAsset.loadFromAssetBundle(bundle, name);
        if (asset == null) throw new IllegalStateException("Platform prefab loader returned no asset: " + name);
        prefabs.put(type, asset);
        OZStargate.logger().info("Using Stargate platform AssetBundle prefab " + name);
        return asset;
    }

    private MaterialAsset material(PlatformType type, int lod, int surface) {
        if (type == PlatformType.CIRCULAR) return sandstone();
        if (lod == 0 && type == PlatformType.GRACE_OF_GOD && surface == 1) return lamp();
        if (lod == 0 && type != PlatformType.GRACE_OF_GOD && surface == 1) return insetStone();
        return darkStone();
    }

    private TextureAsset stoneTexture() {
        if (stoneTexture == null) {
            if (bundle == null) throw new IllegalStateException("Platform bundle not loaded");
            String textureName = null;
            for (String candidate : bundle.getAllAssetNames()) {
                if (candidate.toLowerCase(Locale.ROOT).endsWith("/platformstone.png")) {
                    textureName = candidate; break;
                }
            }
            if (textureName == null) throw new IllegalStateException("Platform stone texture absent from bundle");
            stoneTexture = TextureAsset.loadFromAssetBundle(bundle, textureName);
            if (stoneTexture == null) throw new IllegalStateException("Platform stone texture missing from bundle");
        }
        return stoneTexture;
    }

    private MaterialAsset sandstone() {
        if (sandstone == null) sandstone = textured("oz-stargate-platform-sandstone", .96f, .77f, .55f);
        return sandstone;
    }

    private MaterialAsset darkStone() {
        if (darkStone == null) darkStone = textured("oz-stargate-platform-dark-stone", .55f, .57f, .62f);
        return darkStone;
    }

    private MaterialAsset insetStone() {
        if (insetStone == null) insetStone = textured("oz-stargate-platform-inset", .38f, .40f, .44f);
        return insetStone;
    }

    private MaterialAsset lamp() {
        if (lamp == null) {
            lamp = MaterialAsset.create("oz-stargate-platform-lamp");
            lamp.setColor(.90f, .86f, .64f, 1f);
            lamp.setMetallic(.15f);
            lamp.setSmoothness(.55f);
        }
        return lamp;
    }

    private MaterialAsset textured(String name, float red, float green, float blue) {
        MaterialAsset result = MaterialAsset.create(name);
        result.setTexture(stoneTexture());
        result.setColor(red, green, blue, 1f);
        result.setMetallic(.12f);
        result.setSmoothness(.22f);
        return result;
    }

    @Override public void close() {
        for (PrefabAsset asset : prefabs.values()) asset.dispose();
        prefabs.clear();
        if (bundle != null) { bundle.dispose(); bundle = null; }
        if (sandstone != null) { sandstone.dispose(); sandstone = null; }
        if (darkStone != null) { darkStone.dispose(); darkStone = null; }
        if (insetStone != null) { insetStone.dispose(); insetStone = null; }
        if (lamp != null) { lamp.dispose(); lamp = null; }
        if (stoneTexture != null) { stoneTexture.dispose(); stoneTexture = null; }
    }
}
