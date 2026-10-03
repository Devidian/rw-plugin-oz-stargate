package de.omegazirkel.risingworld.stargate.dhd;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.discovery.DiscoveryService;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.sector.LocalDialService;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.World;
import net.risingworld.api.events.player.PlayerObjectInteractionEvent;
import net.risingworld.api.objects.Player;
import net.risingworld.api.objects.world.ObjectElement;
import net.risingworld.api.ui.UITarget;
import net.risingworld.api.utils.Vector3f;

/** Console linking and UI workflow. All methods run on the game thread. */
public final class DhdService {
    private record Selection(String gateId, long expiresAt) { }
    static final class Session {
        final Player player;
        final DhdStore.Binding binding;
        final String gateId;
        DhdOverlay overlay;
        List<String> addresses = List.of();
        Set<String> localAddresses = Set.of();
        String selected;
        int page;
        boolean loading;
        boolean failed;
        Session(Player player, DhdStore.Binding binding, String gateId) {
            this.player = player; this.binding = binding; this.gateId = gateId;
        }
    }
    private final DhdStore store;
    private final OZStargate plugin;
    private final LocalGateStore gates;
    private final GateNetworkClient network;
    private final LocalDialService localDial;
    private final I18n i18n;
    private final Map<String, Selection> selections = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();
    private BiPredicate<Player, String> modelValidator = (player, gateId) -> false;
    private DiscoveryService discovery;

    public DhdService(OZStargate plugin, DhdStore store, LocalGateStore gates, GateNetworkClient network,
            LocalDialService localDial, I18n i18n) {
        this.plugin = plugin; this.store = store; this.gates = gates; this.network = network; this.localDial = localDial; this.i18n = i18n;
        network.setStateObserver(this::updateViews);
    }

    public void setModelValidator(BiPredicate<Player, String> validator) { modelValidator = validator; }
    public void setDiscovery(DiscoveryService discovery) { this.discovery = discovery; }

    public void command(Player player, String command, String gateId) {
        if (!player.isAdmin()) { StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player)); return; }
        try {
            if (command.equals("canceldhd")) { selections.remove(player.getUID()); tell(player, "cancelled"); return; }
            if (command.equals("binddhd") && (gateId == null || !gates.exists(gateId))) { tell(player, "usage_bind"); return; }
            selections.put(player.getUID(), new Selection(command.equals("binddhd") ? gateId : null, System.currentTimeMillis() + 60000));
            tell(player, command.equals("binddhd") ? "select_bind" : "select_unbind");
        } catch (SQLException ex) { databaseError(player, ex); }
    }

    public void interact(PlayerObjectInteractionEvent event) {
        // Respect protection plugins and other handlers which already refused this interaction.
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        ObjectElement object = event.getObject();
        if (object == null || !object.isValid()) return;
        DhdStore.ObjectKey key = new DhdStore.ObjectKey(event.getGlobalID(), event.getChunkPositionX(), event.getChunkPositionY(), event.getChunkPositionZ());
        try {
            Selection selection = selections.remove(player.getUID());
            if (selection != null) {
                event.setCancelled(true);
                if (!player.isAdmin()) { StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player)); return; }
                if (selection.expiresAt() < System.currentTimeMillis()) { tell(player, "expired"); return; }
                if (!near(player.getPosition(), object.getWorldPosition(), 4)) { tell(player, "unavailable"); return; }
                if (selection.gateId() == null) {
                    tell(player, store.unbind(key) ? "unbound" : "not_bound");
                } else {
                    LocalGateStore.Gate gate = gates.gate(selection.gateId());
                    if (gate == null || !near(gate.position(), object.getWorldPosition(), 16)) { tell(player, "near_gate"); return; }
                    boolean bound = store.bind(new DhdStore.Binding(key, object.getCreationDate(), object.getTypeID(), selection.gateId()));
                    tell(player, bound ? "bound" : "already_bound");
                }
                updateViews();
                return;
            }
            DhdStore.Binding binding = store.find(key);
            if (binding == null) return;
            event.setCancelled(true);
            if (!matches(binding, object) || !near(player.getPosition(), object.getWorldPosition(), 4) || !gates.exists(binding.gateId())) {
                tell(player, "unavailable"); return;
            }
            open(player, binding.gateId(), binding);
        } catch (SQLException ex) { event.setCancelled(true); databaseError(player, ex); }
    }

    /** Called only after the model service verifies an active placed model and proximity. */
    public void openModel(Player player, String gateId) {
        if (!modelValidator.test(player, gateId)) return;
        try {
            if (gates.exists(gateId)) open(player, gateId, null);
        } catch (SQLException ex) { databaseError(player, ex); }
    }

    private void open(Player player, String gateId, DhdStore.Binding binding) {
        Session previous = sessions.get(player.getUID());
        if (previous != null) close(previous);
        Session session = new Session(player, binding, gateId);
        session.overlay = new DhdOverlay(session, this, network, i18n);
        sessions.put(player.getUID(), session);
        player.addUIElement(session.overlay, UITarget.Modal);
        refresh(session);
    }

    void refresh(Session session) {
        if (!valid(session) || session.loading) return;
        session.loading = true; session.failed = false; session.overlay.update();
        try { session.localAddresses = Set.copyOf(gates.ids()); }
        catch (SQLException ex) { session.loading = false; databaseError(session.player, ex); return; }
        network.requestAddresses(session.player, addresses -> {
            if (sessions.get(session.player.getUID()) != session) return;
            session.loading = false;
            session.failed = addresses == null;
            session.addresses = addresses == null ? List.of() : addresses.stream()
                    .filter(id -> !id.equals(session.gateId)).sorted().toList();
            if (session.selected != null && !session.addresses.contains(session.selected)) session.selected = null;
            session.page = Math.min(session.page, Math.max(0, (session.addresses.size() - 1) / 8));
            session.overlay.update();
        });
    }

    void select(Session session, int slot) {
        if (!valid(session) || session.loading) return;
        int index = session.page * 8 + slot;
        if (index < session.addresses.size()) session.selected = session.addresses.get(index);
        session.overlay.update();
    }

    void page(Session session, int delta) {
        if (!valid(session) || session.loading) return;
        session.page = Math.max(0, Math.min(Math.max(0, (session.addresses.size() - 1) / 8), session.page + delta));
        session.overlay.update();
    }

    void dial(Session session) {
        if (!valid(session) || session.loading || session.selected == null || !session.addresses.contains(session.selected)) return;
        if (!network.gateView(session.gateId).state().equals("IDLE")) return;
        dialAddress(session.player, session.gateId, session.selected);
        session.overlay.update();
    }

    void discover(Session session) {
        if (discovery == null || !valid(session) || session.loading) return;
        if (!network.gateView(session.gateId).ready()
                || !network.gateView(session.gateId).state().equals("IDLE")) return;
        discovery.attempt(session.player, session.gateId);
    }

    void manual(Session session) {
        if (!valid(session)) return;
        Player player = session.player;
        String source = session.gateId;
        close(session);
        plugin.executeDelayed(.1f, () -> {
            if (!player.isConnected()) return;
            player.showInputMessageBox(i18n.get("tc.stargate.dhd.manual_title", player),
                    i18n.get("tc.stargate.dhd.manual_prompt", player), "", answer -> plugin.enqueue(() -> {
                        if (!player.isConnected() || answer == null || answer.isBlank()) return;
                        String target = answer.trim().toUpperCase(Locale.ROOT);
                        if (!target.matches("[A-Z0-9]{16}") || target.equals(source)) { player.sendTextMessage(i18n.get("tc.stargate.dhd.manual_invalid", player)); return; }
                        try {
                            if (!gates.exists(source) || !nearSource(player, session)) { player.sendTextMessage(i18n.get("tc.stargate.dhd.unavailable", player)); return; }
                            dialAddress(player, source, target);
                        } catch (SQLException ex) { databaseError(player, ex); }
                    }));
        });
    }

    private boolean nearSource(Player player, Session session) throws SQLException {
        if (session.binding == null) return modelValidator.test(player, session.gateId);
        DhdStore.ObjectKey key = session.binding.object();
        ObjectElement object = World.getObject(key.id(), key.x(), key.y(), key.z());
        return matches(session.binding, object) && near(player.getPosition(), object.getWorldPosition(), 4)
                && session.binding.equals(store.find(key));
    }

    private void dialAddress(Player player, String source, String target) {
        try {
            if (localDial.isLocal(target)) localDial.dial(player, source, target);
            else network.dial(player, target, source);
        } catch (SQLException ex) { databaseError(player, ex); }
    }

    public void addressesChanged() {
        for (Session session : new java.util.ArrayList<>(sessions.values())) refresh(session);
    }

    private boolean valid(Session session) {
        if (sessions.get(session.player.getUID()) != session) return false;
        try {
            if (session.binding == null) {
                if (session.player.isConnected() && modelValidator.test(session.player, session.gateId)
                        && gates.exists(session.gateId)) return true;
                close(session);
                if (session.player.isConnected()) tell(session.player, "unavailable");
                return false;
            }
            DhdStore.ObjectKey key = session.binding.object();
            ObjectElement object = World.getObject(key.id(), key.x(), key.y(), key.z());
            if (session.player.isConnected() && matches(session.binding, object)
                    && near(session.player.getPosition(), object.getWorldPosition(), 4)
                    && session.binding.equals(store.find(key)) && gates.exists(session.binding.gateId())) return true;
            close(session);
            if (session.player.isConnected()) tell(session.player, "unavailable");
        } catch (SQLException ex) { close(session); databaseError(session.player, ex); }
        return false;
    }

    private void updateViews() {
        for (Session session : new ArrayList<>(sessions.values())) if (valid(session)) session.overlay.update();
    }

    void close(Session session) {
        if (!sessions.remove(session.player.getUID(), session)) return;
        if (session.player.isConnected()) session.overlay.dismiss();
    }

    public void disconnect(Player player) {
        sessions.remove(player.getUID()); selections.remove(player.getUID());
        if (discovery != null) discovery.disconnect(player.getUID());
    }

    public void close() {
        for (Session session : new ArrayList<>(sessions.values())) close(session);
        selections.clear(); network.setStateObserver(() -> { });
    }

    private static boolean matches(DhdStore.Binding binding, ObjectElement object) {
        return object != null && object.isValid() && object.getCreationDate() == binding.createdAt() && object.getTypeID() == binding.type();
    }

    private static boolean near(Vector3f a, Vector3f b, float distance) {
        float x = a.x - b.x, y = a.y - b.y, z = a.z - b.z;
        return x * x + y * y + z * z <= distance * distance;
    }

    private void tell(Player player, String key) { StargateChat.debug(player, i18n.get("tc.stargate.dhd." + key, player)); }
    private void databaseError(Player player, SQLException error) {
        OZStargate.logger().error("DHD database failure: " + error.getMessage());
        if (player.isConnected()) tell(player, "database_error");
    }
}
