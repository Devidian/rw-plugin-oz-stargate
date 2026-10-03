package de.omegazirkel.risingworld.stargate.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import net.risingworld.api.assets.TextureAsset;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UITarget;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.ScaleMode;

/** A HUD image with one deadline per local or destination-server display. */
public final class TravelScreenService {
    public static final long DURATION_MS = 10_000L;
    private final OZStargate plugin;
    private final TextureAsset image;
    private record Display(long startedAt, OZUIElement screen) { }
    private final Map<String, Display> active = new HashMap<>();
    private record Pending(Player player, Runnable completion) { }
    private final Map<String, Pending> arrivals = new HashMap<>();

    public TravelScreenService(OZStargate plugin) {
        this.plugin = plugin;
        Path path = Path.of(plugin.getPath(), "assets", "ui", "travel-tunnel.png");
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Missing travel image: " + path);
        image = TextureAsset.loadFromFile(path.toString());
        if (image == null) throw new IllegalStateException("Cannot load travel image: " + path);
    }

    public void show(Player player, long startedAt, boolean enabled) {
        if (player == null || !player.isConnected()) return;
        String uid = player.getUID();
        Display existing = active.get(uid);
        if (enabled && existing != null && existing.startedAt() == startedAt) return;
        remove(player);
        long remaining = remainingMs(startedAt, System.currentTimeMillis());
        if (!enabled || remaining == 0 || image == null) return;
        OZUIElement screen = new OZUIElement();
        screen.setPivot(Pivot.UpperLeft);
        screen.setPosition(0, 0, true);
        screen.setSize(100, 100, true);
        screen.setClickable(false);
        screen.setPickable(false);
        screen.style.backgroundImage.set(image);
        screen.style.backgroundImageScaleMode.set(ScaleMode.StretchToFill);
        Display display = new Display(startedAt, screen);
        active.put(uid, display);
        player.addUIElement(screen, UITarget.HUD);
        plugin.executeDelayed(remaining / 1000f, () -> {
            if (active.get(uid) == display) {
                active.remove(uid);
                if (player.isConnected()) player.removeUIElement(display.screen());
            }
        });
    }

    public static long remainingMs(long startedAt, long now) {
        if (startedAt <= 0 || startedAt > now + 1_000L) return 0;
        return Math.max(0, DURATION_MS - Math.max(0, now - startedAt));
    }

    /** Complete a positioned arrival only after the source-server travel deadline. */
    public void finishArrival(Player player, long startedAt, boolean enabled, Runnable completion) {
        Objects.requireNonNull(completion);
        String uid = player.getUID();
        Pending pending = new Pending(player, completion);
        arrivals.put(uid, pending);
        long remaining = enabled ? remainingMs(startedAt, System.currentTimeMillis()) : 0;
        if (remaining == 0) { complete(player); return; }
        plugin.executeDelayed(remaining / 1000f, () -> {
            if (arrivals.get(uid) == pending) {
                arrivals.remove(uid);
                remove(uid, player);
                if (player.isConnected()) pending.completion().run();
            }
        });
    }

    /** Disconnect cancels the live callback; persisted arrivals resume after reconnect. */
    public void disconnect(Player player) {
        if (player == null) return;
        arrivals.remove(player.getUID());
        remove(player);
    }

    public void complete(Player player) {
        if (player == null) return;
        Pending pending = arrivals.remove(player.getUID());
        remove(player);
        if (pending != null) pending.completion().run();
    }

    public void remove(Player player) {
        if (player == null) return;
        remove(player.getUID(), player);
    }

    private void remove(String uid, Player player) {
        Display display = active.remove(uid);
        if (display != null && player.isConnected()) player.removeUIElement(display.screen());
    }

    public void close() {
        for (Pending pending : java.util.List.copyOf(arrivals.values())) complete(pending.player());
        for (Player player : net.risingworld.api.Server.getAllPlayers()) remove(player);
        active.clear();
        image.dispose();
    }
}
