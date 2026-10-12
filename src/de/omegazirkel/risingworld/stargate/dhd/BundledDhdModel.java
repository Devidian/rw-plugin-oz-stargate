package de.omegazirkel.risingworld.stargate.dhd;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient.GateView;
import net.risingworld.api.assets.PrefabAsset;
import net.risingworld.api.utils.Layer;
import net.risingworld.api.worldelements.GameObject;
import net.risingworld.api.worldelements.Prefab;

/** Two-LOD DHD prefab with authored light and collision components. */
final class BundledDhdModel extends Prefab implements DhdRenderModel {
    private final DhdModelAssets assets;
    private int activeCircuits = -1;
    private boolean opened;

    BundledDhdModel(DhdModelAssets assets, PrefabAsset prefab) {
        super(prefab);
        this.assets = assets;
        addComponent(null, "DisallowGPUDrivenRendering");
        setLayer(Layer.OBJECT);
        setLayer("Collision", Layer.OBJECT, true);
        setLocalScale(2f, 2f, 2f);
        // Point lights start inactive in the prefab and follow selected circuits.
        for (int lod = 0; lod < 2; lod++) {
            String prefix = "LOD" + lod + "/";
            setMaterial(prefix + "body", assets.stone());
            setMaterial(prefix + "accents", assets.accents());
            setMaterial(prefix + "keys_unlit", assets.keys());
            setMaterial(prefix + "symbols_unlit", assets.markings());
            setMaterial(prefix + "activate", assets.activation());
            for (int i = 0; i < 7; i++) {
                setMaterial(prefix + "keys_" + i, assets.keys());
                setMaterial(prefix + "symbols_" + i, assets.markings());
            }
        }
    }

    @Override public GameObject object() { return this; }

    @Override public void update(GateView view) {
        int circuits = AnimatedDhdModel.litCircuits(view);
        if (circuits != activeCircuits) {
            for (int lod = 0; lod < 2; lod++) {
                String prefix = "LOD" + lod + "/";
                for (int i = 0; i < 7; i++) {
                    setMaterial(prefix + "keys_" + i, i < circuits ? assets.litKeys() : assets.keys());
                    setMaterial(prefix + "symbols_" + i, i < circuits ? assets.litMarkings() : assets.markings());
                }
            }
            for (int i = 0; i < 7; i++)
                setActive("Lights/Symbol_" + i, i < circuits);
            activeCircuits = circuits;
        }
        boolean active = view.ready() && "OPEN".equals(view.state());
        if (opened != active) {
            for (int lod = 0; lod < 2; lod++)
                setMaterial("LOD" + lod + "/activate",
                        active ? assets.activeActivation() : assets.activation());
            setActive("Lights/Activation", active);
            opened = active;
        }
    }
}
