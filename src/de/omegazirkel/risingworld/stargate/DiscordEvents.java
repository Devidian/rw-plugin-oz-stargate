package de.omegazirkel.risingworld.stargate;

import java.util.HashMap;
import java.util.Map;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.bridge.AuditEvent;
import de.omegazirkel.risingworld.tools.bridge.EventMessage;
import net.risingworld.api.objects.Player;

/** Reports completed Stargate state transitions through the optional event provider. */
public final class DiscordEvents {
    private final I18n i18n;
    private final PluginSettings settings;

    public DiscordEvents(OZStargate plugin, PluginSettings settings, LocalGateStore gates, I18n i18n) {
        this.i18n = i18n;
        this.settings = settings;
    }

    public void internal(Player player, String source, String target) {
        send(player, "internal", "travel", "PH_SOURCE", gate(source), "PH_TARGET", gate(target));
    }

    public void externalDeparture(Player player, String source, String target) {
        send(player, "external_departure", "travel", "PH_SOURCE", gate(source), "PH_TARGET", target);
    }

    public void externalArrival(Player player, String source, String target) {
        send(player, "external_arrival", "travel", "PH_SOURCE", source, "PH_TARGET", gate(target));
    }

    public void discovery(Player player, String gateId) {
        send(player, "discovery", "discovery", "PH_GATE", gate(gateId));
    }

    public void networkConnected() { send(null, "network_connected", "network"); }
    public void networkDisconnected() { send(null, "network_disconnected", "network"); }
    public void networkCodeChanged(String reason) {
        settings.eventLogs.publish("network_code_changed", () -> AuditEvent.create(null,
                EventMessage.localizedWithMessages(i18n, "tc.stargate.discord.events.network_code_changed",
                        Map.of("PH_REASON", EventMessage.localized(i18n, reason, Map.of()))),
                "network_code_changed", "oz-stargate", "network"));
    }

    private String gate(String gateId) {
        return gateId;
    }

    private void send(Player player, String event, String channel, String... replacements) {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("PH_PLAYER", player == null ? "?" : player.getName());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            arguments.put(replacements[i], String.valueOf(replacements[i + 1]));
        }
        settings.eventLogs.publish(event, () -> AuditEvent.create(player,
                EventMessage.localized(i18n, "tc.stargate.discord.events." + event, arguments),
                event, "oz-stargate", channel));
    }
}
