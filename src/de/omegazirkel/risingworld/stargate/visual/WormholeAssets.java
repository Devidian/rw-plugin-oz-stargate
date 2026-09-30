package de.omegazirkel.risingworld.stargate.visual;

import java.nio.file.Files;
import java.nio.file.Path;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.MeshAsset;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.utils.Vector3f;

/** Immutable, shared animation assets: no texture uploads or mesh rebuilds in the frame loop. */
final class WormholeAssets implements AutoCloseable {
    private final MeshAsset[] frames = new MeshAsset[WormholeLoop.FRAMES];
    private final TextureAsset[] textures = new TextureAsset[3];
    private final MaterialAsset[] water = new MaterialAsset[2];
    private final MeshAsset[] closingFrames = new MeshAsset[12];
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
            for (int frame = 0; frame < closingFrames.length; frame++) {
                MeshAsset mesh = MeshAsset.create("oz-stargate-dissolve-frame-" + frame, 97, 288);
                closingFrames[frame] = mesh;
                closingVertex(mesh, frame, 0, 0);
                for (int j = 0; j < 96; j++) {
                    double angle = j * Math.PI * 2 / 96;
                    closingVertex(mesh, frame, (float) Math.cos(angle), (float) Math.sin(angle));
                }
                for (int j = 0; j < 96; j++) mesh.addIndices(0, 1 + (j + 1) % 96, 1 + j);
                mesh.uploadMeshData(false);
            }
            for (int frame = 0; frame < frames.length; frame++) {
                MeshAsset mesh = MeshAsset.create("oz-stargate-puddle-frame-" + frame, 97, 288);
                frames[frame] = mesh;
                vertex(mesh, frame, 0, 0);
                for (int j = 0; j < 96; j++) {
                    double angle = j * Math.PI * 2 / 96;
                    vertex(mesh, frame, (float) Math.cos(angle), (float) Math.sin(angle));
                }
                for (int j = 0; j < 96; j++) mesh.addIndices(0, 1 + (j + 1) % 96, 1 + j);
                mesh.uploadMeshData(false);
            }
        } catch (RuntimeException ex) { close(); throw ex; }
    }

    private static void vertex(MeshAsset mesh, int frame, float x, float y) {
        mesh.addVertex(x * 2.31f, y * 2.31f, 0);
        mesh.addNormal(new Vector3f(0, 0, -1));
        mesh.addUV(WormholeLoop.u(frame, x), WormholeLoop.v(frame, y));
    }

    MeshAsset frame(int index) { return frames[index]; }
    MaterialAsset water(int index) { return water[index / WormholeLoop.FRAMES_PER_ATLAS]; }
    MeshAsset closingFrame(int index) { return closingFrames[index]; }
    MaterialAsset closingMaterial() { return closingMaterial; }

    private static void closingVertex(MeshAsset mesh, int frame, float x, float y) {
        mesh.addVertex(x * 2.31f, y * 2.31f, 0);
        mesh.addNormal(new Vector3f(0, 0, -1));
        mesh.addUV(((frame % 4) * 384 + .5f + (x + 1) * .5f * 383) / 1536f,
                1 - ((frame / 4) * 384 + .5f + (1 - y) * .5f * 383) / 1152f);
    }
    MaterialAsset surge() { return surge; }

    @Override public void close() {
        for (MeshAsset mesh : frames) if (mesh != null) mesh.dispose();
        for (MaterialAsset material : water) if (material != null) material.dispose();
        for (MeshAsset mesh : closingFrames) if (mesh != null) mesh.dispose();
        if (closingMaterial != null) closingMaterial.dispose();
        if (closingTexture != null) closingTexture.dispose();
        if (surge != null) surge.dispose();
        for (TextureAsset texture : textures) if (texture != null) texture.dispose();
    }
}
