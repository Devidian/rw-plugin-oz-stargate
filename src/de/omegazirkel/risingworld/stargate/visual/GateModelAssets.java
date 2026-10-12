package de.omegazirkel.risingworld.stargate.visual;

import java.nio.file.Files;
import java.nio.file.Path;

import de.omegazirkel.risingworld.OZStargate;
import net.risingworld.api.assets.AssetBundle;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.assets.PrefabAsset;

/** Shared native resources for temporary previews and persisted gate models. */
public final class GateModelAssets implements AutoCloseable {
    private final Path path;
    private final Path bundlePath;
    private final Path wormholeBundlePath;
    private AssetBundle bundle;
    private PrefabAsset bundlePrefab;
    private AssetBundle wormholeBundle;
    private PrefabAsset wormholePrefab;
    private MaterialAsset material, ringMaterial, housingMaterial, glyphMaterial, grooveMaterial;
    private TextureAsset surfaceTexture;
    private MaterialAsset amber, open, dark, amberStrip, openStrip;
    private WormholeAssets wormhole;
    private final PlatformAssets platforms;

    public GateModelAssets(OZStargate plugin) {
        this(Path.of(plugin.getPath()));
    }

    GateModelAssets(Path pluginPath) {
        path = pluginPath.resolve("assets/models/milkyway");
        bundlePath = path.resolve("stargatemilkyway.bundle");
        wormholeBundlePath = path.resolve("stargatewormhole.bundle");
        platforms = new PlatformAssets(path.resolve("stargateplatforms.bundle"));
    }

    public BundledGateModel createPreview() {
        PrefabAsset prefab = bundledPrefab();
        load();
        BundledGateModel model = new BundledGateModel(this, prefab);
        model.setActive("Collision", false);
        model.setActive("Lights", false);
        return model;
    }

    public GateRenderModel createAnimated() {
        PrefabAsset prefab = bundledPrefab();
        load();
        return new BundledGateModel(this, prefab);
    }

    private PrefabAsset bundledPrefab() {
        if (bundlePrefab != null) return bundlePrefab;
        if (!Files.isRegularFile(bundlePath)) throw new IllegalStateException("Missing bundle: " + bundlePath);
        bundle = AssetBundle.loadFromFile(bundlePath.toString());
        if (bundle == null) throw new IllegalStateException("AssetBundle loader returned no asset");
        String name = null;
        for (String candidate : bundle.getAllAssetNames()) {
            if (candidate.toLowerCase(java.util.Locale.ROOT).endsWith("/stargatemilkyway.prefab")
                    || candidate.equalsIgnoreCase("StargateMilkyway.prefab")) {
                name = candidate;
                break;
            }
        }
        if (name == null) throw new IllegalStateException("Stargate prefab absent from bundle");
        bundlePrefab = PrefabAsset.loadFromAssetBundle(bundle, name);
        if (bundlePrefab == null) throw new IllegalStateException("Prefab loader returned no asset");
        OZStargate.logger().info("Using Stargate AssetBundle prefab " + name);
        return bundlePrefab;
    }

    MaterialAsset dark() { return dark; }
    MaterialAsset stone() { return material; }
    MaterialAsset ring() { return ringMaterial; }
    MaterialAsset glyphs() { return glyphMaterial; }
    MaterialAsset groove() { return grooveMaterial; }
    MaterialAsset housing() { return housingMaterial; }
    MaterialAsset amber() { return amber; }
    MaterialAsset open() { return open; }
    MaterialAsset amberStrip() { return amberStrip; }
    MaterialAsset openStrip() { return openStrip; }

    WormholeAssets wormhole() {
        if (wormhole == null) wormhole = new WormholeAssets(path);
        return wormhole;
    }

    WormholeVisual createWormholeVisual() {
        return new BundledWormholeVisual(wormholePrefab(), wormhole());
    }

    boolean supportsPlatform(PlatformType type) { return platforms.available(type); }

    net.risingworld.api.worldelements.Prefab createPlatform(PlatformType type) {
        return platforms.create(type);
    }

    private PrefabAsset wormholePrefab() {
        if (wormholePrefab != null) return wormholePrefab;
        if (!Files.isRegularFile(wormholeBundlePath))
            throw new IllegalStateException("Missing bundle: " + wormholeBundlePath);
        wormholeBundle = AssetBundle.loadFromFile(wormholeBundlePath.toString());
        if (wormholeBundle == null) throw new IllegalStateException("Wormhole bundle loader returned no asset");
        String name = null;
        for (String candidate : wormholeBundle.getAllAssetNames()) {
            if (candidate.toLowerCase(java.util.Locale.ROOT).endsWith("/stargatewormhole.prefab")) {
                name = candidate;
                break;
            }
        }
        if (name == null) throw new IllegalStateException("Wormhole prefab absent from bundle");
        wormholePrefab = PrefabAsset.loadFromAssetBundle(wormholeBundle, name);
        if (wormholePrefab == null) throw new IllegalStateException("Wormhole prefab loader returned no asset");
        OZStargate.logger().info("Using wormhole AssetBundle prefab " + name);
        return wormholePrefab;
    }

    private void load() {
        if (material != null) return;
        try {
            Path texture = path.resolve("gate-subtle-color.png");
            if (!Files.isRegularFile(texture)) throw new IllegalStateException("Missing gate surface texture");
            surfaceTexture = TextureAsset.loadFromFile(texture.toString());
            material = surface("oz-stargate-authored-body", .08f, .18f);
            ringMaterial = surface("oz-stargate-authored-ring", .16f, .26f);
            housingMaterial = surface("oz-stargate-authored-housing", .12f, .22f);
            glyphMaterial = MaterialAsset.create("oz-stargate-authored-glyph-faces");
            glyphMaterial.setColor(.56f, .57f, .58f, 1f);
            glyphMaterial.setMetallic(.08f);
            glyphMaterial.setSmoothness(.12f);
            grooveMaterial = surface("oz-stargate-authored-grooves", .08f, .12f);
            grooveMaterial.setColor(.54f, .54f, .54f, 1f);
            dark = MaterialAsset.create("oz-stargate-chevron-unlit-strips");
            dark.setColor(0.12f, 0.07f, 0.035f, 1f);
            amber = MaterialAsset.create("oz-stargate-chevron-locked");
            amber.setColor(1f, 0.42f, 0.035f, 1f);
            amber.setSmoothness(0.35f);
            open = MaterialAsset.create("oz-stargate-chevron-open");
            open.setColor(1f, 0.7f, 0.18f, 1f);
            open.setSmoothness(0.35f);
            amberStrip = MaterialAsset.create("oz-stargate-chevron-amber-v-strip");
            amberStrip.setColor(1f, .64f, .12f, 1f);
            amberStrip.setSmoothness(.35f);
            openStrip = MaterialAsset.create("oz-stargate-chevron-open-v-strip");
            openStrip.setColor(1f, .86f, .35f, 1f);
            openStrip.setSmoothness(.35f);
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

    @Override public void close() {
        platforms.close();
        if (wormholePrefab != null) { wormholePrefab.dispose(); wormholePrefab = null; }
        if (wormholeBundle != null) { wormholeBundle.dispose(); wormholeBundle = null; }
        if (bundlePrefab != null) { bundlePrefab.dispose(); bundlePrefab = null; }
        if (bundle != null) { bundle.dispose(); bundle = null; }
        if (wormhole != null) { wormhole.close(); wormhole = null; }
        if (dark != null) { dark.dispose(); dark = null; }
        if (amber != null) { amber.dispose(); amber = null; }
        if (open != null) { open.dispose(); open = null; }
        if (amberStrip != null) { amberStrip.dispose(); amberStrip = null; }
        if (openStrip != null) { openStrip.dispose(); openStrip = null; }
        if (material != null) { material.dispose(); material = null; }
        if (ringMaterial != null) { ringMaterial.dispose(); ringMaterial = null; }
        if (glyphMaterial != null) { glyphMaterial.dispose(); glyphMaterial = null; }
        if (grooveMaterial != null) { grooveMaterial.dispose(); grooveMaterial = null; }
        if (housingMaterial != null) { housingMaterial.dispose(); housingMaterial = null; }
        if (surfaceTexture != null) { surfaceTexture.dispose(); surfaceTexture = null; }
    }
}
