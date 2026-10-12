package de.omegazirkel.risingworld.stargate.visual;

import java.util.Arrays;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.assets.PrefabAsset;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.worldelements.GameObject;
import net.risingworld.api.worldelements.Prefab;

/** Development candidate using LODs, authored collision and Chevron lights. */
final class BundledGateModel extends Prefab implements GateRenderModel {
    private static final int LODS = 4;
    private static final String[] STATES = { "Dark", "Amber", "Open" };
    private final WormholeVisual wormhole;
    private final int[] appliedStates = new int[9];
    private final float[] depths = new float[9];
    private GateView view;
    private float angle;

    BundledGateModel(GateModelAssets assets, PrefabAsset prefab) {
        super(prefab);
        // Rising World's GPU Resident Drawer can render bundled HDRP shaders
        // magenta when their DOTS instancing variants are absent.
        addComponent(null, "DisallowGPUDrivenRendering");
        setLayer(Layer.DEFAULT);
        setLocalScale(2f, 2f, 2f);
        applyNativeMaterials(assets);
        Arrays.fill(appliedStates, -1);
        // The prefab contains the stable 48-segment collision ring.
        wormhole = assets.createWormholeVisual();
        addChild(wormhole.object());
    }

    @Override public GameObject object() { return this; }

    @Override public void update(GateView nextView) {
        view = nextView;
        frame(System.nanoTime());
    }

    @Override public void frame(long now) {
        if (view == null) return;
        wormhole.frame(view, now);
        GateDialMotion pose = GateDialMotion.sample(view, now);
        GateAnimationState state = GateAnimationState.from(view);
        if (pose.drivingRing() && Math.abs(angle - pose.angle()) > .00001f) {
            angle = pose.angle();
            Quaternion turn = rotation(angle);
            for (int lod = 0; lod < LODS; lod++) setLocalRotation("LOD" + lod + "/RingPivot", turn);
        }
        for (int i = 0; i < appliedStates.length; i++) {
            boolean lit = state.lit(i) || pose.movingChevron() == i && pose.strokeLit();
            int desired = !lit ? 0 : state.mode() == GateAnimationState.Mode.OPEN ? 2 : 1;
            if (appliedStates[i] != desired) {
                for (int lod = 0; lod < LODS; lod++) {
                    String prefix = "LOD" + lod + "/Chevron_" + i + "/";
                    for (int variant = 0; variant < STATES.length; variant++) {
                        setActive(prefix + "V/chevron-v-00/VStrip" + STATES[variant], desired == variant);
                        setActive(prefix + "RearV/chevron-v-00/VStrip" + STATES[variant], desired == variant);
                        setActive(prefix + "chevron-block-00/FixedLight" + STATES[variant], desired == variant);
                    }
                }
                String light = "Lights/Chevron_" + i + "/";
                setActive(light + "AmberFront", desired == 1);
                setActive(light + "AmberRear", desired == 1);
                setActive(light + "OpenFront", desired == 2);
                setActive(light + "OpenRear", desired == 2);
                setActive(light + "AmberStripFront", desired == 1);
                setActive(light + "AmberStripRear", desired == 1);
                setActive(light + "OpenStripFront", desired == 2);
                setActive(light + "OpenStripRear", desired == 2);
                appliedStates[i] = desired;
            }
            float depth = pose.movingChevron() == i ? pose.depth() : 0f;
            if (Math.abs(depths[i] - depth) > .000001f) {
                Vector3f offset = new Vector3f(0f, -depth, 0f);
                for (int lod = 0; lod < LODS; lod++)
                    setLocalPosition("LOD" + lod + "/Chevron_" + i + "/V", offset);
                for (int lod = 0; lod < LODS; lod++)
                    setLocalPosition("LOD" + lod + "/Chevron_" + i + "/RearV", offset);
                depths[i] = depth;
            }
        }
    }

    private static Quaternion rotation(float degrees) {
        double half = Math.toRadians(degrees) / 2;
        return new Quaternion(0f, 0f, (float) Math.sin(half), (float) Math.cos(half));
    }

    private void applyNativeMaterials(GateModelAssets assets) {
        // Materials baked into HDRP bundles can lose required shader variants in
        // Rising World. Native API materials use the game's own HDRP shader.
        MaterialAsset[] lightStates = { assets.dark(), assets.amber(), assets.open() };
        MaterialAsset[] stripStates = { assets.dark(), assets.amberStrip(), assets.openStrip() };
        for (int lod = 0; lod < LODS; lod++) {
            String level = "LOD" + lod + "/";
            setMaterial(level + "Body/body-00", assets.stone());
            setMaterial(level + "RingPivot/ring-00", assets.glyphs());
            setMaterial(level + "RingPivot/ring-01", assets.ring());
            setMaterial(level + "RingPivot/ring-02", assets.groove());
            for (int chevron = 0; chevron < 9; chevron++) {
                String prefix = level + "Chevron_" + chevron + "/";
                setMaterial(prefix + "V/chevron-v-01", assets.housing());
                setMaterial(prefix + "RearV/chevron-v-01", assets.housing());
                setMaterial(prefix + "chevron-block-01", assets.housing());
                for (int state = 0; state < STATES.length; state++) {
                    setMaterial(prefix + "V/chevron-v-00/VStrip" + STATES[state], stripStates[state]);
                    setMaterial(prefix + "RearV/chevron-v-00/VStrip" + STATES[state], stripStates[state]);
                    setMaterial(prefix + "chevron-block-00/FixedLight" + STATES[state], lightStates[state]);
                }
            }
        }
    }

}
