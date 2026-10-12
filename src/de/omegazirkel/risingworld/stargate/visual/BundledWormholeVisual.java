package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.assets.PrefabAsset;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.worldelements.GameObject;
import net.risingworld.api.worldelements.Prefab;

/** Prefab meshes and lights; the existing WormholePose controls their playback. */
final class BundledWormholeVisual extends Prefab implements WormholeVisual {
    private final WormholeTransition transition = new WormholeTransition();
    private boolean visible, surging, swirling;
    private int lastFrame = -1, lastDissolve = -1;
    private float lastScale = -1;

    BundledWormholeVisual(PrefabAsset prefab, WormholeAssets assets) {
        super(prefab);
        addComponent(null, "DisallowGPUDrivenRendering");
        setLayer(Layer.DEFAULT);
        setLocalPosition(0f, 3.075f, 0f);
        setActive(false);
        for (int frame = 0; frame < WormholeLoop.FRAMES; frame++) {
            String path = framePath(frame);
            setMaterial("Loop/Front/" + path, assets.water(frame));
            setMaterial("Loop/Rear/" + path, assets.water(frame));
        }
        for (int frame = 0; frame < 12; frame++)
            setMaterial(dissolvePath(frame), assets.closingMaterial());
        setMaterial("Surge", assets.surge());
        setMaterial("RearVortex", assets.surge());
    }

    @Override public GameObject object() { return this; }

    @Override public void frame(GateView view, long now) {
        WormholePose pose = transition.sample(view, now);
        if (!pose.visible()) {
            if (visible) setActive(false);
            visible = false;
            return;
        }
        if (lastScale != pose.radiusScale()) {
            setLocalScale(pose.radiusScale(), pose.radiusScale(), 1f);
            lastScale = pose.radiusScale();
        }
        if (pose.dissolveFrame() >= 0) {
            if (lastFrame >= 0) {
                activeFrame(lastFrame, false);
                lastFrame = -1;
            }
            if (lastDissolve != pose.dissolveFrame()) {
                if (lastDissolve >= 0) setActive(dissolvePath(lastDissolve), false);
                setActive(dissolvePath(pose.dissolveFrame()), true);
                lastDissolve = pose.dissolveFrame();
            }
        } else {
            if (lastDissolve >= 0) {
                setActive(dissolvePath(lastDissolve), false);
                lastDissolve = -1;
            }
            int frame = WormholeLoop.frame(now);
            if (frame != lastFrame) {
                if (lastFrame >= 0) activeFrame(lastFrame, false);
                activeFrame(frame, true);
                lastFrame = frame;
            }
        }
        boolean nextSurging = pose.surgeDepth() > .01f;
        if (nextSurging) setLocalScale("Surge",
                new Vector3f(1f, 1f, pose.surgeDepth()));
        if (nextSurging != surging) setActive("Surge", nextSurging);
        surging = nextSurging;
        boolean nextSwirling = pose.rearDepth() > .01f;
        if (nextSwirling) {
            float radius = .65f + .35f * pose.rearDepth() / .9f;
            setLocalScale("RearVortex", new Vector3f(radius, radius, pose.rearDepth()));
            double half = Math.toRadians(pose.rearAngle()) / 2;
            setLocalRotation("RearVortex", new Quaternion(0f, 0f,
                    (float) Math.sin(half), (float) Math.cos(half)));
        }
        if (nextSwirling != swirling) setActive("RearVortex", nextSwirling);
        swirling = nextSwirling;
        if (!visible) setActive(true);
        visible = true;
    }

    private void activeFrame(int frame, boolean active) {
        String path = framePath(frame);
        setActive("Loop/Front/" + path, active);
        setActive("Loop/Rear/" + path, active);
    }

    private static String framePath(int frame) {
        return String.format(java.util.Locale.ROOT, "Frame_%03d", frame);
    }

    private static String dissolvePath(int frame) {
        return String.format(java.util.Locale.ROOT, "Dissolve/Frame_%02d", frame);
    }
}
