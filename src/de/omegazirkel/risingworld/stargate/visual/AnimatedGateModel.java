package de.omegazirkel.risingworld.stargate.visual;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.assets.MaterialAsset;
import net.risingworld.api.collider.BoxCollider;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.worldelements.GameObject;
import net.risingworld.api.worldelements.Light;
import net.risingworld.api.worldelements.Model;

/** One native hierarchy per visible gate, shared by nearby viewers. */
final class AnimatedGateModel extends Model {
    private final Model ring;
    private final GateWormholeVisual wormhole;
    private final Model[] chevrons = new Model[9], strips = new Model[9], fixedLights = new Model[9];
    private final Light[] chevronLights = new Light[9], rearChevronLights = new Light[9];
    private final float[] depths = new float[9];
    private final MaterialAsset dark, amber, open;
    private GateView view;
    private final MaterialAsset[] appliedLights = new MaterialAsset[9];
    private float angle;

    AnimatedGateModel(GateModelAssets assets) {
        super(assets.part("body"), assets.stone());
        dark = assets.dark(); amber = assets.amber(); open = assets.open();
        setLocalScale(2f, 2f, 2f);
        // Primitive collision lives on independent children, not the asynchronously imported model.
        for (int i = 0; i < GateCollisionGeometry.SEGMENTS; i++) {
            GateCollisionGeometry.Segment shape = GateCollisionGeometry.segment(i);
            GameObject blocker = new GameObject(Layer.OBJECT);
            blocker.setLocalPosition(shape.x(), shape.y(), 0);
            blocker.setLocalRotation(rotation(shape.angle()));
            blocker.setCollider(new BoxCollider(shape.width(), shape.height(), shape.depth(), false));
            addChild(blocker);
        }
        ring = new Model(assets.part("ring"), assets.ring());
        ring.setLocalPosition(0f, 3.075f, 0f);
        // Preserve authored symbol faces and rotate them with the ring, without changing collision.
        ring.addChild(new Model(assets.part("ring-glyphs"), assets.glyphs()));
        addChild(ring);
        wormhole = new GateWormholeVisual(assets);
        addChild(wormhole);
        for (int i = 0; i < chevrons.length; i++) {
            GameObject assembly = new GameObject();
            assembly.setLocalPosition(0f, 3.073437f, 0f);
            assembly.setLocalRotation(rotation(i * 40f));
            addChild(assembly);
            assembly.addChild(new Model(assets.part("chevron-fixed-frame"), assets.housing()));
            fixedLights[i] = new Model(assets.part("chevron-fixed-light"), dark);
            assembly.addChild(fixedLights[i]);
            chevrons[i] = new Model(assets.part("chevron-v-frame"), assets.housing());
            assembly.addChild(chevrons[i]);
            strips[i] = new Model(assets.part("chevron-v-strips"), dark);
            chevrons[i].addChild(strips[i]);
            Light glow = new Light(Light.Type.Point);
            // Authored chevrons sit 2.7-3.1 m above the assembly pivot. A
            // light near local Y=0 illuminates the wormhole, not the strips.
            glow.setLocalPosition(0f, 2.88f, -.3f);
            glow.setColor(1f, .48f, .1f, 1f);
            glow.setRange(1.8f);
            glow.setIntensity(4.5f);
            glow.setShadowsEnabled(false);
            glow.setActive(false);
            assembly.addChild(glow);
            chevronLights[i] = glow;
            Light rearGlow = new Light(Light.Type.Point);
            rearGlow.setLocalPosition(0f, 2.88f, .3f);
            rearGlow.setColor(1f, .48f, .1f, 1f);
            rearGlow.setRange(1.8f);
            rearGlow.setIntensity(4.5f);
            rearGlow.setShadowsEnabled(false);
            rearGlow.setActive(false);
            assembly.addChild(rearGlow);
            rearChevronLights[i] = rearGlow;
        }
    }

    void update(GateView nextView) {
        view = nextView;
        frame(System.nanoTime());
    }

    void frame(long now) {
        if (view == null) return;
        wormhole.frame(view, now);
        GateDialMotion pose = GateDialMotion.sample(view, now);
        GateAnimationState state = GateAnimationState.from(view);
        if (pose.drivingRing() && Math.abs(angle - pose.angle()) > 0.00001f) {
            angle = pose.angle();
            // API tween arguments are SPEED, not duration. Explicit poses make timing deterministic.
            ring.setLocalRotation(rotation(angle));
        }
        // Idle/preemption freezes the last ring pose, avoiding a fast reset sweep.
        for (int i = 0; i < chevrons.length; i++) {
            boolean lit = state.lit(i) || (pose.movingChevron() == i && pose.strokeLit());
            MaterialAsset light = !lit ? dark : state.mode() == GateAnimationState.Mode.OPEN ? open : amber;
            if (appliedLights[i] != light) {
                strips[i].setMaterial(light);
                fixedLights[i].setMaterial(light);
                chevronLights[i].setActive(lit);
                rearChevronLights[i].setActive(lit);
                if (lit) {
                    if (state.mode() == GateAnimationState.Mode.OPEN) {
                        chevronLights[i].setColor(1f, .78f, .39f, 1f);
                        rearChevronLights[i].setColor(1f, .78f, .39f, 1f);
                    } else {
                        chevronLights[i].setColor(1f, .48f, .12f, 1f);
                        rearChevronLights[i].setColor(1f, .48f, .12f, 1f);
                    }
                }
                appliedLights[i] = light;
            }
            float depth = pose.movingChevron() == i ? pose.depth() : 0f;
            if (Math.abs(depths[i] - depth) > 0.000001f) {
                chevrons[i].setLocalPosition(0f, -depth, 0f);
                depths[i] = depth;
            }
        }
    }

    private static Quaternion rotation(float degrees) {
        double half = Math.toRadians(degrees) / 2;
        return new Quaternion(0f, 0f, (float) Math.sin(half), (float) Math.cos(half));
    }
}
