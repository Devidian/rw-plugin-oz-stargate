package de.omegazirkel.risingworld.stargate.audio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.visual.GateVisualPlacement;
import net.risingworld.api.assets.SoundAsset;
import net.risingworld.api.objects.Player;
import net.risingworld.api.sounds.Sound;
import net.risingworld.api.utils.Vector3f;

/** Server-thread, per-viewer playback for placed gates. */
public final class GateAudioService {
    public static final String PLAYER_ENABLED_KEY = "stargate.audio.enabled";
    private static final String[] FILES = {"dhd_1.ogg", "dhd_2.ogg", "gate_roll_b.ogg", "gate_roll_c.ogg",
            "chevron_out_1.ogg", "gate_open.ogg", "open_loop.ogg", "shutdown_b.ogg", "go_trough.ogg",
            "dial_fail.ogg", "chevron_1.ogg", "chevron_2.ogg", "chevron_3.ogg", "chevron_4.ogg",
            "chevron_5.ogg", "chevron_6.ogg", "chevron_7.ogg"};
    private record Snapshot(String state, String direction, int chevrons, long stepNanos, long openedNanos) {
        static Snapshot from(GateNetworkClient.GateView view) {
            return new Snapshot(view.state(), view.direction(), view.chevrons(), view.stepStartedNanos(), view.openedNanos());
        }
    }
    private record Playing(Player player, Sound sound, String file, long endsAt) { }
    private record Pending(long stepNanos, long dueNanos) { }
    private final PluginSettings settings;
    private final Map<String, SoundAsset> assets = new HashMap<>();
    private final Map<String, Snapshot> prior = new HashMap<>();
    private final Map<String, Map<String, Sound>> loops = new HashMap<>();
    private final Map<String, List<Playing>> oneShots = new HashMap<>();
    private final Map<String, Pending> pendingRing = new HashMap<>();
    private final Map<String, Pending> pendingChevron = new HashMap<>();
    private final Map<String, Long> closingPlayed = new HashMap<>();
    private String theme;
    private int appliedVolume = -1;
    private boolean closed;
    private Function<String, Vector3f> dhdPosition = gateId -> null;

    public GateAudioService(PluginSettings settings) { this.settings = settings; }
    public void setDhdPosition(Function<String, Vector3f> lookup) { dhdPosition = lookup; }

    public void reload() {
        stopAll();
        assets.clear();
        theme = settings.audioTheme;
        appliedVolume = settings.audioVolume;
        Path root = settings.audioDirectory();
        if (root == null) return;
        for (String file : FILES) {
            Path selected = root.resolve(theme).resolve(file);
            Path fallback = root.resolve("silent").resolve(file);
            if (!"silent".equals(theme) && !Files.isRegularFile(selected))
                OZStargate.logger().warn("Stargate theme " + theme + " is missing " + file + "; using silent");
            Path path = Files.isRegularFile(selected) && !Files.isSymbolicLink(selected) ? selected : fallback;
            if (!Files.isRegularFile(path)) { OZStargate.logger().warn("Missing Stargate sound: " + file); continue; }
            try { assets.put(file, SoundAsset.loadFromFile(path.toAbsolutePath().toString())); }
            catch (RuntimeException ex) { OZStargate.logger().warn("Cannot load Stargate sound " + path + ": " + ex.getMessage()); }
        }
        prior.clear();
        pendingRing.clear(); pendingChevron.clear(); closingPlayed.clear();
    }

    public void update(Map<String, GateVisualPlacement> placements, Map<String, Set<Player>> listeners,
            GateNetworkClient network) {
        if (closed || settings.audioDirectory() == null) return;
        if (!settings.audioTheme.equals(theme)) reload();
        long now = System.nanoTime();
        for (String gateId : Set.copyOf(prior.keySet())) if (!placements.containsKey(gateId)) {
            stopGate(gateId); prior.remove(gateId); pendingRing.remove(gateId); pendingChevron.remove(gateId);
            closingPlayed.remove(gateId);
        }
        for (Map.Entry<String, GateVisualPlacement> entry : placements.entrySet()) {
            String gateId = entry.getKey();
            GateNetworkClient.GateView view = network.gateView(gateId);
            Snapshot current = Snapshot.from(view);
            Snapshot old = prior.put(gateId, current);
            Set<Player> players = listeners.getOrDefault(gateId, Set.of());
            if (old != null && !current.equals(old)) transition(gateId, entry.getValue(), players, old, current, now);
            Pending ring = pendingRing.get(gateId);
            if (ring != null && now >= ring.dueNanos()) {
                pendingRing.remove(gateId);
                if ("OUTGOING".equals(current.state()) && current.stepNanos() == ring.stepNanos())
                    play(gateId, entry.getValue(), players,
                            current.chevrons() % 2 == 0 ? "gate_roll_b.ogg" : "gate_roll_c.ogg",
                            false, duration(current.chevrons() % 2 == 0 ? "gate_roll_b.ogg" : "gate_roll_c.ogg"));
            }
            Pending chevron = pendingChevron.get(gateId);
            if (chevron != null && now >= chevron.dueNanos()) {
                pendingChevron.remove(gateId);
                if ("OUTGOING".equals(current.state()) && current.stepNanos() == chevron.stepNanos())
                    play(gateId, entry.getValue(), players, "chevron_out_1.ogg", false, 2.286f);
            }
            if ("OPEN".equals(current.state())
                    && GateAudioTiming.closingCueDueAt(network.closingAtNanos(gateId), now)
                    && !Long.valueOf(current.openedNanos()).equals(closingPlayed.get(gateId))) {
                closingPlayed.put(gateId, current.openedNanos());
                play(gateId, entry.getValue(), players, "shutdown_b.ogg", false,
                        GateAudioTiming.SHUTDOWN_MS / 1000f);
            }
            syncLoop(gateId, entry.getValue(), players, current);
        }
        oneShots.values().forEach(list -> list.removeIf(playing -> {
            if (playing.endsAt() <= now) return true;
            if (enabled(playing.player())) return false;
            playing.sound().stop(true);
            return true;
        }));
        if (appliedVolume != settings.audioVolume) {
            float volume = settings.audioVolume / 100f;
            for (Map<String, Sound> gate : loops.values())
                for (Sound sound : gate.values()) sound.setVolume(volume);
            for (List<Playing> sounds : oneShots.values())
                for (Playing playing : sounds) playing.sound().setVolume(volume);
            appliedVolume = settings.audioVolume;
        }
    }

    private void transition(String id, GateVisualPlacement placement, Set<Player> players,
            Snapshot old, Snapshot next, long now) {
        GateAudioTransition cues = GateAudioTransition.between(old.state(), old.direction(), old.stepNanos(),
                next.state(), next.direction(), next.stepNanos(), next.chevrons());
        if (cues.changedMode()) {
            boolean closingAlreadyPlayed = "OPEN".equals(old.state()) && "IDLE".equals(next.state())
                    && Long.valueOf(old.openedNanos()).equals(closingPlayed.get(id));
            stopShots(id, closingAlreadyPlayed);
            pendingRing.remove(id);
            pendingChevron.remove(id);
            if (cues.stateCue() != null && !(closingAlreadyPlayed && "shutdown_b.ogg".equals(cues.stateCue())))
                play(id, placement, players, cues.stateCue(), false, duration(cues.stateCue()));
            if (!"OPEN".equals(next.state())) closingPlayed.remove(id);
        }
        int previousIncomingLocks = "INCOMING".equals(old.state()) ? old.chevrons() : 0;
        if ("INCOMING".equals(next.state()) && next.chevrons() > previousIncomingLocks) {
            for (int lock = Math.max(1, previousIncomingLocks + 1); lock <= Math.min(7, next.chevrons()); lock++)
                play(id, placement, players, "chevron_" + lock + ".ogg", false,
                        1.321f);
        }
        if (cues.startOutgoingStep()) {
            Vector3f button = dhdPosition.apply(id);
            if (button != null) playAt(id, button, players,
                    next.chevrons() % 2 == 0 ? "dhd_1.ogg" : "dhd_2.ogg", 1.131f);
            pendingRing.put(id, new Pending(next.stepNanos(),
                    next.stepNanos() + GateAudioTiming.DHD_MS * 1_000_000L));
            pendingChevron.put(id, new Pending(next.stepNanos(),
                    next.stepNanos() + GateAudioTiming.chevronStartMillis(next.chevrons()) * 1_000_000L));
        }
    }

    private static float duration(String file) {
        return switch (file) {
            case "gate_open.ogg" -> 3.373f;
            case "shutdown_b.ogg" -> 3.216f;
            case "dial_fail.ogg" -> 2.146f;
            case "gate_roll_b.ogg" -> 3.182f;
            case "gate_roll_c.ogg" -> 3.090f;
            default -> 1.321f;
        };
    }

    private void syncLoop(String id, GateVisualPlacement placement, Set<Player> players, Snapshot state) {
        Map<String, Sound> gateLoops = loops.computeIfAbsent(id, ignored -> new HashMap<>());
        for (String uid : Set.copyOf(gateLoops.keySet())) {
            boolean allowed = "OPEN".equals(state.state()) && players.stream().anyMatch(p -> p.getUID().equals(uid) && enabled(p));
            if (!allowed) gateLoops.remove(uid).stop(true);
        }
        if (!"OPEN".equals(state.state())) return;
        for (Player player : players) if (enabled(player) && !gateLoops.containsKey(player.getUID())) {
            Sound sound = start(player, gatePosition(placement), "open_loop.ogg", true);
            if (sound != null) gateLoops.put(player.getUID(), sound);
        }
    }

    private void play(String id, GateVisualPlacement placement, Set<Player> players, String file, boolean loop, float seconds) {
        playAt(id, gatePosition(placement), players, file, seconds);
    }

    private void playAt(String id, Vector3f position, Set<Player> players, String file, float seconds) {
        for (Player player : players) if (enabled(player)) {
            Sound sound = start(player, position, file, false);
            if (sound != null) oneShots.computeIfAbsent(id, ignored -> new ArrayList<>())
                    .add(new Playing(player, sound, file, System.nanoTime() + (long) (seconds * 1_000_000_000L)));
        }
    }

    private static Vector3f gatePosition(GateVisualPlacement placement) {
        return new Vector3f(placement.x(), placement.y() + 6f, placement.z());
    }

    private Sound start(Player player, Vector3f position, String file, boolean loop) {
        SoundAsset asset = assets.get(file);
        if (asset == null || settings.audioVolume == 0) return null;
        try {
            Sound sound = player.playSound(asset, loop, settings.audioVolume / 100f, 1f, position);
            if (sound != null) sound.setMinMaxDistance(4f, 64f);
            return sound;
        } catch (RuntimeException ex) {
            OZStargate.logger().warn("Cannot play Stargate sound " + file + ": " + ex.getMessage());
            return null;
        }
    }

    public void travel(String gateId, GateVisualPlacement placement, Set<Player> players) {
        if (placement != null) play(gateId, placement, players, "go_trough.ogg", false, 1.722f);
    }

    public static boolean enabled(Player player) {
        return player != null && player.isConnected() && (OZTools.playerSettings() == null
                || OZTools.playerSettings().getBoolean(player.getDbID(), PLAYER_ENABLED_KEY).orElse(true));
    }

    public void disconnect(Player player) {
        for (Map<String, Sound> gate : loops.values()) {
            Sound loop = gate.remove(player.getUID());
            if (loop != null) loop.stop(true);
        }
        for (List<Playing> sounds : oneShots.values()) sounds.removeIf(playing -> {
            if (playing.player() != player) return false;
            playing.sound().stop(true); return true;
        });
    }

    private void stopShots(String id) {
        stopShots(id, false);
    }
    private void stopShots(String id, boolean preserveShutdown) {
        List<Playing> sounds = oneShots.get(id);
        if (sounds == null) return;
        sounds.removeIf(playing -> {
            if (preserveShutdown && "shutdown_b.ogg".equals(playing.file())) return false;
            playing.sound().stop(true);
            return true;
        });
        if (sounds.isEmpty()) oneShots.remove(id);
    }
    private void stopGate(String id) {
        stopShots(id);
        Map<String, Sound> gate = loops.remove(id);
        if (gate != null) for (Sound sound : gate.values()) sound.stop(true);
    }
    private void stopAll() {
        for (String id : Set.copyOf(oneShots.keySet())) stopShots(id);
        for (String id : Set.copyOf(loops.keySet())) stopGate(id);
    }
    public void close() { closed = true; stopAll(); assets.clear(); }
}
