package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.worldelements.GameObject;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.worldelements.Model;
import net.risingworld.api.worldelements.Light;

/** Shared immutable frames/materials, per-gate visibility/dissolve. No travel access. */
final class GateWormholeVisual extends GameObject {
    private final WormholeTransition transition = new WormholeTransition();
    private final WormholeAssets assets;
    private final Model[] water = new Model[2];
    private final Model[] reverseWater = new Model[2];
    private final Model dissolve, surge, rear;
    private final Light[] horizonLights = new Light[2];
    private boolean visible, surging, swirling;
    private int lastFrame = -1, lastDissolve = -1;
    private float lastScale = -1;

    GateWormholeVisual(GateModelAssets gateAssets) {
        assets = gateAssets.wormhole();
        setLocalPosition(0f, 3.075f, 0f);
        setActive(false);
        // Reference both atlases from the initial model tree, before either half-loop is shown.
        for (int i = 0; i < water.length; i++) {
            int first = i * WormholeLoop.FRAMES_PER_ATLAS;
            water[i] = new Model(assets.frame(first), assets.water(first));
            water[i].setActive(i == 0);
            addChild(water[i]);
            reverseWater[i] = new Model(assets.frame(first), assets.water(first));
            reverseWater[i].setLocalRotation(new Quaternion(0f, 1f, 0f, 0f));
            reverseWater[i].setActive(i == 0);
            addChild(reverseWater[i]);
        }
        dissolve = new Model(assets.closingFrame(0), assets.closingMaterial());
        dissolve.setActive(false);
        surge = new Model(gateAssets.part("wormhole-surge"), assets.surge());
        surge.setLocalPosition(0f, 0f, -.03f);
        surge.setActive(false);
        rear = new Model(gateAssets.part("wormhole-rear-vortex"), assets.surge());
        rear.setLocalPosition(0f, 0f, .03f);
        rear.setActive(false);
        addChild(dissolve); addChild(surge); addChild(rear);
        for (int i = 0; i < horizonLights.length; i++) {
            Light glow = new Light(i == 0 ? Light.Type.Point : Light.Type.Spot);
            if (i == 0) {
                glow.setLocalPosition(0f, 0f, .35f);
                glow.setIntensity(40f);
            } else {
                // A broad beam from in front of the gate covers the aperture
                // like the player's helmet light, without four hot spots.
                glow.setLocalPosition(0f, 0f, -2.3f);
                glow.setLocalRotation(new Quaternion().lookAt(0f, 0f, 1f));
                glow.setSpotAngle(135f);
                glow.setSpotInnerAngle(120f);
                glow.setIntensity(1000f);
            }
            glow.setColor(.72f, .85f, 1f, 1f);
            glow.setRange(12f);
            glow.setShadowsEnabled(false);
            addChild(glow);
            horizonLights[i] = glow;
        }
    }

    void frame(GateView view, long now) {
        WormholePose pose = transition.sample(view, now);
        if (!pose.visible()) {
            if (visible) setActive(false);
            if (surging) surge.setActive(false);
            if (swirling) rear.setActive(false);
            visible = false; surging = false; swirling = false;
            return;
        }
        if (lastScale != pose.radiusScale()) {
            setLocalScale(pose.radiusScale(), pose.radiusScale(), 1f);
            lastScale = pose.radiusScale();
        }
        if (pose.dissolveFrame() >= 0) {
            if (lastDissolve != pose.dissolveFrame()) {
                dissolve.setMesh(assets.closingFrame(pose.dissolveFrame()));
                lastDissolve = pose.dissolveFrame();
            }
            water[0].setActive(false); water[1].setActive(false);
            reverseWater[0].setActive(false); reverseWater[1].setActive(false);
            dissolve.setActive(true);
            lastFrame = -1;
        } else {
            if (lastDissolve >= 0) { dissolve.setActive(false); lastDissolve = -1; }
            int frame = WormholeLoop.frame(now);
            if (frame != lastFrame) {
                int atlas = frame / WormholeLoop.FRAMES_PER_ATLAS;
                water[atlas].setMesh(assets.frame(frame));
                reverseWater[atlas].setMesh(assets.frame(frame));
                if (lastFrame < 0 || atlas != lastFrame / WormholeLoop.FRAMES_PER_ATLAS) {
                    water[atlas].setActive(true);
                    water[1 - atlas].setActive(false);
                    reverseWater[atlas].setActive(true);
                    reverseWater[1 - atlas].setActive(false);
                }
                lastFrame = frame;
            }
        }
        boolean nextSurging = pose.surgeDepth() > .01f;
        if (nextSurging) surge.setLocalScale(1f, 1f, pose.surgeDepth());
        if (nextSurging != surging) surge.setActive(nextSurging);
        surging = nextSurging;
        boolean nextSwirling = pose.rearDepth() > .01f;
        if (nextSwirling) {
            float radius = .65f + .35f * pose.rearDepth() / .9f;
            rear.setLocalScale(radius, radius, pose.rearDepth());
            double half = Math.toRadians(pose.rearAngle()) / 2;
            rear.setLocalRotation(new Quaternion(0, 0, (float) Math.sin(half), (float) Math.cos(half)));
        }
        if (nextSwirling != swirling) rear.setActive(nextSwirling);
        swirling = nextSwirling;
        if (!visible) setActive(true);
        visible = true;
    }
}
