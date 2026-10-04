package de.omegazirkel.risingworld.stargate.discovery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.addressbook.AddressBookService;
import de.omegazirkel.risingworld.stargate.audio.GateAudioTiming;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.sector.GatePlacementService;
import de.omegazirkel.risingworld.stargate.sector.LocalDialService;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.objects.Player;
import net.risingworld.api.Server;

/** Player-scoped discovery attempt, cooldown and feedback. */
public final class DiscoveryService implements AutoCloseable {
    private final OZStargate plugin;
    private final PluginSettings settings;
    private final DiscoveryCooldownStore store;
    private final GatePlacementService placement;
    private final DiscoveryPoolService pool;
    private final GateNetworkClient network;
    private final LocalDialService localDial;
    private final AddressBookService addressBook;
    private final I18n i18n;
    private final Map<String, Attempt> pending = new HashMap<>();
    private final Map<String, GateNetworkClient.GateView> views = new HashMap<>();
    private boolean closed;
    private de.omegazirkel.risingworld.stargate.DiscordEvents discordEvents;

    public void setDiscordEvents(de.omegazirkel.risingworld.stargate.DiscordEvents events) { discordEvents = events; }

    private record Attempt(String uid, String source) { }

    public DiscoveryService(OZStargate plugin, PluginSettings settings, DiscoveryCooldownStore store,
            GatePlacementService placement, DiscoveryPoolService pool, GateNetworkClient network,
            LocalDialService localDial,
            AddressBookService addressBook, I18n i18n) {
        this.plugin = plugin; this.settings = settings; this.store = store;
        this.placement = placement; this.pool = pool; this.network = network; this.localDial = localDial;
        this.addressBook = addressBook; this.i18n = i18n;
        network.setArtificialDiscovery(views::get, views::containsKey);
    }

    public void attempt(Player player, String sourceGateId) {
        if (closed || player == null || !player.isConnected() || !player.isSpawned()) return;
        String uid = player.getUID();
        if (pending.containsKey(uid)) { tell(player, "pending", null); return; }
        if (views.containsKey(sourceGateId) || !network.isIdleForArtificialArrival(sourceGateId)) {
            tell(player, "pending", null); return;
        }
        long now = System.currentTimeMillis();
        try {
            if (!(player.isAdmin() && settings.discoveryNoAdminCooldown)) {
                long next = store.nextAt(uid);
                if (next > now) {
                    tell(player, "cooldown", Long.toString((next - now + 999) / 1000));
                    return;
                }
                store.started(uid, now + settings.discoveryCooldownMinutes * 60_000L);
            }
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot save Stargate discovery cooldown: " + ex.getMessage());
            tell(player, "error", null); return;
        }
        Attempt attempt = new Attempt(uid, sourceGateId);
        pending.put(uid, attempt);
        tell(player, "dialing", null);
        step(attempt, 0);
    }

    private void step(Attempt attempt, int chevrons) {
        if (closed || pending.get(attempt.uid()) != attempt) return;
        views.put(attempt.source(), new GateNetworkClient.GateView("OUTGOING", "OUTGOING", "",
                chevrons, true, GateAudioTiming.DIAL_STEP_MS, System.nanoTime()));
        network.refreshViews();
        if (chevrons < 7) plugin.executeDelayed(GateAudioTiming.DIAL_STEP_MS / 1000f,
                () -> step(attempt, chevrons + 1));
        else finish(attempt);
    }

    private void finish(Attempt attempt) {
        if (closed || pending.get(attempt.uid()) != attempt) return;
        Player player = Server.getPlayerByUID(attempt.uid());
        if (player == null || !player.isConnected()
                || !"IDLE".equals(network.remoteGateView(attempt.source()).state())) {
            stop(attempt); return;
        }
        try {
            if (!localDial.isLocal(attempt.source()) || network.hasPendingUnregister(attempt.source())) {
                stop(attempt); tell(player, "error", null); return;
            }
        } catch (SQLException ex) {
            stop(attempt);
            OZStargate.logger().error("Cannot verify discovery source gate: " + ex.getMessage());
            tell(player, "error", null); return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= settings.discoverySuccessPercent) {
            stop(attempt); tell(player, "failed", null); return;
        }
        try {
            List<SectorAddress> targets = new ArrayList<>(pool.sectors(attempt.source()));
            Collections.shuffle(targets);
            for (SectorAddress target : targets) {
                if (network.hasPendingRegistration(target)) continue;
                List<DiscoveryCandidateStore.Chunk> chunks = new ArrayList<>(pool.chunks(attempt.source(), target));
                Collections.shuffle(chunks);
                for (DiscoveryCandidateStore.Chunk chunk : chunks) {
                    if (placement.createDiscovered(player, chunk.x(), chunk.z(), id -> {
                        pool.gateCreated(target);
                        if (closed || pending.get(attempt.uid()) != attempt) return;
                        // The source remains reserved until the local connection takes ownership.
                        views.remove(attempt.source());
                        if (localDial.connectDiscovered(attempt.uid(), attempt.source(), id)) {
                            addressBook.learn(attempt.uid(), id);
                            if (discordEvents != null) discordEvents.discovery(Server.getPlayerByUID(attempt.uid()), id);
                            tell(Server.getPlayerByUID(attempt.uid()), "found", id);
                        } else tell(Server.getPlayerByUID(attempt.uid()), "error", null);
                        pending.remove(attempt.uid());
                        network.refreshViews();
                    })) {
                        plugin.executeDelayed(25f, () -> {
                            if (pending.get(attempt.uid()) == attempt) {
                                stop(attempt);
                                tell(Server.getPlayerByUID(attempt.uid()), "error", null);
                            }
                        });
                        return;
                    }
                    pool.removeChunk(attempt.source(), chunk);
                }
            }
            stop(attempt);
            tell(player, "no_site", null);
        } catch (SQLException ex) {
            stop(attempt);
            OZStargate.logger().error("Cannot choose Stargate discovery site: " + ex.getMessage());
            tell(player, "error", null);
        }
    }

    private void stop(Attempt attempt) {
        if (pending.remove(attempt.uid(), attempt)) {
            views.remove(attempt.source());
            network.refreshViews();
        }
    }

    public void disconnect(String uid) {
        Attempt attempt = pending.get(uid);
        if (attempt != null) stop(attempt);
    }

    private void tell(Player player, String key, String value) {
        if (player == null || !player.isConnected()) return;
        String message = i18n.get("tc.stargate.discovery." + key, player);
        if (value != null) message = message.replace(key.equals("found") ? "PH_GATE" : "PH_SECONDS", value);
        player.sendTextMessage(message);
    }

    @Override public void close() { closed = true; pending.clear(); views.clear(); network.refreshViews(); }
}
