package de.omegazirkel.risingworld.stargate;

import java.sql.SQLException;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.objects.Player;

/** Optional, local-only announcements at completed Stargate state transitions. */
public final class DiscordEvents {
    private final PluginSettings settings;
    private final LocalGateStore gates;
    private final DiscordBridge bridge;
    private final I18n i18n;

    public DiscordEvents(OZStargate plugin, PluginSettings settings, LocalGateStore gates, I18n i18n) {
        this.settings = settings;
        this.gates = gates;
        this.bridge = new DiscordBridge(plugin);
        this.i18n = i18n;
    }

    public void internal(Player player, String source, String target) {
        send(settings.discordInternalTravelChannel, "internal", "PH_PLAYER", name(player),
                "PH_SOURCE", gate(source), "PH_TARGET", gate(target));
    }

    public void externalDeparture(Player player, String source, String target) {
        send(settings.discordExternalTravelChannel, "external_departure", "PH_PLAYER", name(player),
                "PH_SOURCE", gate(source), "PH_TARGET", target);
    }

    public void externalArrival(Player player, String source, String target) {
        send(settings.discordExternalTravelChannel, "external_arrival", "PH_PLAYER", name(player),
                "PH_SOURCE", source, "PH_TARGET", gate(target));
    }

    public void discovery(Player player, String gateId) {
        send(settings.discordDiscoveryChannel, "discovery", "PH_PLAYER", name(player), "PH_GATE", gate(gateId));
    }

    public void networkConnected() { send(settings.discordNetworkStatusChannel, "network_connected"); }
    public void networkDisconnected() { send(settings.discordNetworkStatusChannel, "network_disconnected"); }
    public void networkCodeChanged(String reason) {
        send(settings.discordNetworkStatusChannel, "network_code_changed", "PH_REASON", reason);
    }

    private String gate(String gateId) {
        try {
            String alias = gates.alias(gateId);
            return alias == null || alias.isBlank() ? gateId : gateId + " (" + alias + ")";
        } catch (SQLException ex) {
            OZStargate.logger().warn("Cannot read Stargate alias for Discord event: " + ex.getMessage());
            return gateId;
        }
    }

    private static String name(Player player) { return player == null ? "?" : player.getName(); }

    private void send(long channel, String event, String... replacements) {
        if (channel <= 0 || !bridge.isAvailable()) return;
        String language = bridge.getBotLanguage();
        String message = i18n.get("tc.stargate.discord.events." + event, language);
        for (int i = 0; i + 1 < replacements.length; i += 2)
            message = message.replace(replacements[i], replacements[i + 1]);
        bridge.sendTextMessage(message, channel);
    }
}
