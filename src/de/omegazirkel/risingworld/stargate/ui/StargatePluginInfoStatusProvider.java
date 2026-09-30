package de.omegazirkel.risingworld.stargate.ui;

import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProvider;
import net.risingworld.api.objects.Player;

public class StargatePluginInfoStatusProvider implements PluginInfoStatusProvider {
    private final String pluginName;
    private final String version;
    private final String command;
    private final GateNetworkClient network;

    public StargatePluginInfoStatusProvider(String pluginName, String version, String command, GateNetworkClient network) {
        this.pluginName = pluginName;
        this.version = version;
        this.command = command;
        this.network = network;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public String getInfo(Player player) {
        return t().get("tc.stargate.info.panel.info", player)
                .replace("PH_PLUGIN_NAME", pluginName)
                .replace("PH_PLUGIN_VERSION", version)
                .replace("PH_PLUGIN_CMD", command);
    }

    @Override
    public String getStatus(Player player) {
        PluginSettings settings = PluginSettings.getInstance();
        return t().get("tc.stargate.info.panel.status", player)
                .replace("PH_PLUGIN_NAME", pluginName)
                .replace("PH_WELCOME_MESSAGE", String.valueOf(settings.enableWelcomeMessage))
                .replace("PH_TRAVEL_STATUS", t().get(settings.networkEnabled
                        ? "tc.stargate.network.status_enabled" : "tc.stargate.network.status_disabled", player))
                .replace("PH_RELAY_STATUS", t().get(network.isReady()
                        ? "tc.stargate.network.status_online" : "tc.stargate.network.status_offline", player));
    }

    private I18n t() {
        return I18n.getInstance(pluginName);
    }
}
