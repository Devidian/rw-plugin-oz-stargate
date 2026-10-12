package de.omegazirkel.risingworld.stargate.visual;

import java.nio.file.Files;
import java.nio.file.Path;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.TextureAsset;

/** Shared runtime textures and materials for the bundled wormhole. */
final class WormholeAssets implements AutoCloseable {
    private final TextureAsset[] textures = new TextureAsset[3];
    private final MaterialAsset[] water = new MaterialAsset[2];
    private TextureAsset closingTexture;
    private MaterialAsset closingMaterial;
    private MaterialAsset surge;

    WormholeAssets(Path folder) {
        try {
            for (int i = 0; i < textures.length; i++) {
                Path file = folder.resolve(i < 2 ? "puddle-loop-" + i + ".png" : "puddle-surge.png");
                if (!Files.isRegularFile(file)) throw new IllegalStateException("Missing puddle texture: " + file);
                textures[i] = TextureAsset.loadFromFile(file.toString());
                MaterialAsset material = MaterialAsset.create("oz-stargate-puddle-" + i);
                if (i < 2) water[i] = material; else surge = material;
                material.setTexture(textures[i]);
                material.setColor(1f, 1f, 1f, 1f);
                material.setSmoothness(.15f);
                // The loop uses a separately oriented mesh for each face;
                // double-sided rendering does not flip its lighting normal.
                if (i == 2) material.setFlags(MaterialAsset.Flags.DoubleSided);
            }
            Path closingFile = folder.resolve("puddle-dissolve.png");
            if (!Files.isRegularFile(closingFile)) throw new IllegalStateException("Missing dissolve texture");
            closingTexture = TextureAsset.loadFromFile(closingFile.toString());
            closingMaterial = MaterialAsset.create("oz-stargate-puddle-dissolve");
            closingMaterial.setTexture(closingTexture);
            closingMaterial.setColor(1f, 1f, 1f, 1f);
            closingMaterial.setSmoothness(.15f);
            closingMaterial.setFlags(MaterialAsset.Flags.DoubleSided, MaterialAsset.Flags.Transparent);
        } catch (RuntimeException ex) { close(); throw ex; }
    }

    MaterialAsset water(int index) { return water[index / WormholeLoop.FRAMES_PER_ATLAS]; }
    MaterialAsset closingMaterial() { return closingMaterial; }

    MaterialAsset surge() { return surge; }

    @Override public void close() {
        for (MaterialAsset material : water) if (material != null) material.dispose();
        if (closingMaterial != null) closingMaterial.dispose();
        if (closingTexture != null) closingTexture.dispose();
        if (surge != null) surge.dispose();
        for (TextureAsset texture : textures) if (texture != null) texture.dispose();
    }
}
