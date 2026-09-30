package de.omegazirkel.risingworld.stargate.dhd;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.collider.BoxCollider;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.worldelements.GameObject;
import net.risingworld.api.worldelements.Light;
import net.risingworld.api.worldelements.Model;

/** Visual state follows the relay's gate view; it does not authorize dialing. */
final class AnimatedDhdModel extends Model {
    // Selected glyph mesh centres, offset 4 cm along each face normal. The
    // native OBJ import mirrors X, so child light coordinates mirror it too.
    private static final float[][] SYMBOL_LIGHT_POSITIONS = {
        { .3296f, .9652f, .0040f}, {-.3416f, .9251f, -.0572f},
        { .0473f, .7598f, -.3429f}, { .0310f, 1.1043f, .2447f},
        {-.0987f, 1.0630f, .0908f}, {-.1585f, .9146f, -.1840f},
        { .1645f, .9376f, -.1506f}
    };
    private final Model[] keyCircuits = new Model[7];
    private final Model[] symbolCircuits = new Model[7];
    private final Light[] symbolLights = new Light[7];
    private final Model activation;
    private final Light activationLight;
    private final DhdModelAssets assets;
    private int activeCircuits = -1;
    private boolean opened;

    AnimatedDhdModel(DhdModelAssets assets) {
        super(assets.mesh("supplied-dhd-body"), assets.stone());
        this.assets = assets;
        setLocalScale(2f, 2f, 2f);
        // The API's SphereCollider serializes as Box; only native boxes provide
        // reliable client physics and interaction with this PluginAPI version.
        GameObject column = new GameObject(Layer.OBJECT);
        column.setLocalPosition(0f, .43f, 0f);
        column.setCollider(new BoxCollider(.62f, .86f, .62f, false));
        addChild(column);
        addRoundDisk(.20f, .47f, .40f);
        addRoundDisk(.52f, .66f, .38f);
        addRoundDisk(.87f, .76f, .55f);
        addChild(new Model(assets.mesh("supplied-dhd-accents"), assets.accents()));
        addChild(new Model(assets.mesh("supplied-dhd-keys-unlit"), assets.keys()));
        addChild(new Model(assets.mesh("supplied-dhd-symbols-unlit"), assets.markings()));
        for (int i = 0; i < keyCircuits.length; i++) {
            keyCircuits[i] = new Model(assets.mesh("supplied-dhd-keys-" + i), assets.keys());
            addChild(keyCircuits[i]);
            symbolCircuits[i] = new Model(assets.mesh("supplied-dhd-symbols-" + i), assets.markings());
            addChild(symbolCircuits[i]);
            Light glow = new Light(Light.Type.Point);
            glow.setLocalPosition(SYMBOL_LIGHT_POSITIONS[i][0], SYMBOL_LIGHT_POSITIONS[i][1],
                    SYMBOL_LIGHT_POSITIONS[i][2]);
            glow.setColor(1f, .63f, .19f, 1f);
            glow.setRange(.48f);
            glow.setIntensity(3.2f);
            glow.setShadowsEnabled(false);
            glow.setActive(false);
            addChild(glow);
            symbolLights[i] = glow;
        }
        activation = new Model(assets.mesh("supplied-dhd-activate"), assets.activation());
        addChild(activation);
        activationLight = new Light(Light.Type.Point);
        activationLight.setLocalPosition(0f, 1.22f, 0f);
        activationLight.setColor(1f, .12f, .04f, 1f);
        activationLight.setRange(3.6f);
        activationLight.setIntensity(2.5f);
        activationLight.setShadowsEnabled(false);
        activationLight.setActive(false);
        addChild(activationLight);
    }

    private void addRoundDisk(float height, float radius, float thickness) {
        // Filled strips approximate a round solid without the unsupported native
        // SphereCollider and without gaps between a hollow ring and the column.
        final int strips = 12;
        float depth = radius * 2f / strips;
        for (int i = 0; i < strips; i++) {
            float low = -radius + i * depth;
            float high = low + depth;
            float nearest = low <= 0f && high >= 0f ? 0f : Math.min(Math.abs(low), Math.abs(high));
            float width = 2f * (float) Math.sqrt(radius * radius - nearest * nearest);
            GameObject piece = new GameObject(Layer.OBJECT);
            piece.setLocalPosition(0f, height, (low + high) / 2f);
            piece.setCollider(new BoxCollider(width, thickness, depth, false));
            addChild(piece);
        }
    }

    void update(GateView view) {
        boolean active = view.ready() && ("OUTGOING".equals(view.state()) || "INCOMING".equals(view.state())
                || "OPEN".equals(view.state()));
        int circuits = !active ? 0 : "OUTGOING".equals(view.state())
                ? Math.min(7, Math.max(1, view.chevrons() + 1)) : 7;
        if (circuits != activeCircuits) {
            for (int i = 0; i < keyCircuits.length; i++) {
                keyCircuits[i].setMaterial(i < circuits ? assets.litKeys() : assets.keys());
                symbolCircuits[i].setMaterial(i < circuits ? assets.litMarkings() : assets.markings());
                symbolLights[i].setActive(i < circuits);
            }
            activeCircuits = circuits;
        }
        boolean nextOpened = active && "OPEN".equals(view.state());
        if (nextOpened != opened) {
            activation.setMaterial(nextOpened ? assets.activeActivation() : assets.activation());
            activationLight.setActive(nextOpened);
            opened = nextOpened;
        }
    }
}
