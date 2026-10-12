package de.omegazirkel.risingworld.stargate.visual;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Quaternion;
import net.risingworld.api.utils.Vector3f;
import net.risingworld.api.worldelements.GameObject;

/** Temporary, admin-only visual proof. Never changes gate/zone/arrival or transfer state. */
public final class GatePreviewService {
    private record Preview(Player player, GameObject model) { }
    private final OZStargate plugin;
    private final I18n i18n;
    private final Map<String, Preview> previews = new HashMap<>();
    private final GateModelAssets assets;
    private boolean closed;

    public GatePreviewService(OZStargate plugin, I18n i18n, GateModelAssets assets) {
        this.plugin = plugin; this.i18n = i18n; this.assets = assets;
    }

    public void command(Player player, boolean remove) {
        if (!player.isAdmin()) { StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player)); return; }
        if (closed) return;
        if (remove) { disconnect(player); tell(player, "removed"); return; }
        Vector3f direction = player.getViewDirection();
        GateVisualPlacement placement;
        try {
            placement = GateVisualPlacement.atFeet("PREVIEW", player.getPosition(), direction.x, direction.z);
        } catch (IllegalArgumentException ex) { tell(player, "horizontal"); return; }
        try {
            GameObject model = assets.createPreview();
            disconnect(player);
            model.setLocalPosition(placement.x(), placement.renderedY(), placement.z());
            // Authored front is -Z: walking backwards from placement reveals the front face.
            model.setLocalRotation(new Quaternion().lookAt(placement.forwardX(), 0f, placement.forwardZ()));
            player.addGameObject(model);
            Preview preview = new Preview(player, model);
            previews.put(player.getUID(), preview);
            tell(player, "shown");
            plugin.executeDelayed(120f, () -> {
                if (!closed && previews.get(player.getUID()) == preview) remove(preview);
            });
        } catch (RuntimeException ex) {
            OZStargate.logger().error("Cannot create Stargate model preview: " + ex.getMessage());
            tell(player, "failed");
        }
    }

    private void remove(Preview preview) {
        previews.remove(preview.player().getUID(), preview);
        if (preview.player().isConnected()) preview.player().removeGameObject(preview.model());
    }
    public void disconnect(Player player) {
        Preview preview = previews.get(player.getUID());
        if (preview != null) remove(preview);
    }
    public void close() {
        closed = true;
        for (Preview preview : List.copyOf(previews.values())) remove(preview);
    }
    private void tell(Player player, String key) { StargateChat.debug(player, i18n.get("tc.stargate.preview." + key, player)); }
}
