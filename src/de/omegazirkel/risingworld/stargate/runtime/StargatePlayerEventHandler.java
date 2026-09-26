package de.omegazirkel.risingworld.stargate.runtime;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginGUI;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotService;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
import de.omegazirkel.risingworld.tools.Colors;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerConnectEvent;
import net.risingworld.api.events.player.PlayerPermissionGroupChangeEvent;
import net.risingworld.api.events.player.PlayerChangeGameModeEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;
import net.risingworld.api.objects.Player;

/** Event workflow delegate. It is deliberately not a Rising World Listener. */
public final class StargatePlayerEventHandler {
    private final OZStargate plugin;
    private final String pluginName;
    private final PluginSettings settings;
    private final I18n i18n;
    private final PluginGUI gui;
    private final InventorySnapshotService inventories;
    private final GateNetworkClient network;
    private final TransferService transfers;
    private final Colors colors = Colors.getInstance();

    StargatePlayerEventHandler(OZStargate plugin, String pluginName, PluginSettings settings, I18n i18n,
            PluginGUI gui, InventorySnapshotService inventories, GateNetworkClient network, TransferService transfers) {
        this.plugin = plugin;
        this.pluginName = pluginName;
        this.settings = settings;
        this.i18n = i18n;
        this.gui = gui;
        this.inventories = inventories;
        this.network = network;
        this.transfers = transfers;
    }

    public void onPlayerCommand(PlayerCommandEvent event) {
        Player player = event.getPlayer();
        String[] commandParts = event.getCommand().split(" ", 2);
        if (!commandParts[0].equals("/" + StargatePluginRuntime.COMMAND)) {
            return;
        }
        if (commandParts.length < 2) {
            gui.openMainMenu(player);
            return;
        }
        String[] args = commandParts[1].trim().split("\\s+");
        String subcommand = args[0].toLowerCase(java.util.Locale.ROOT);
        if (subcommand.equals("registergate") || subcommand.equals("unregistergate")
                || subcommand.equals("gatelist") || subcommand.equals("dial") || subcommand.equals("trust") || subcommand.equals("warp")) {
            if ((subcommand.equals("registergate") || subcommand.equals("unregistergate")
                    || subcommand.equals("trust")) && !player.isAdmin()) {
                player.sendTextMessage(i18n.get("tc.stargate.inventory.admin_only", player));
                return;
            }
            switch (subcommand) {
                case "registergate" -> network.register(player);
                case "unregistergate" -> {
                    if (args.length < 2) player.sendTextMessage(i18n.get("tc.stargate.network.usage_unregister", player));
                    else network.unregister(player, args[1]);
                }
                case "gatelist" -> network.list(player);
                case "trust" -> network.trust(player, args.length >= 2 ? args[1] : player.getUID());
                case "warp" -> {
                    if (args.length < 2) player.sendTextMessage(i18n.get("tc.stargate.network.usage_warp", player));
                    else transfers.warp(player, args[1]);
                }
                case "dial" -> {
                    if (args.length < 2) player.sendTextMessage(i18n.get("tc.stargate.network.usage_dial", player));
                    else network.dial(player, args[1], args.length >= 3 ? args[2] : null);
                }
            }
            return;
        }
        if (subcommand.equals("pack") || subcommand.equals("unpack") || subcommand.equals("recover")) {
            if (!player.isAdmin()) {
                player.sendTextMessage(i18n.get("tc.stargate.inventory.admin_only", player));
                return;
            }
            if (transfers.hasActive(player.getUID())) {
                if (subcommand.equals("recover")) {
                    transfers.resume();
                    transfers.onSpawn(player);
                    return;
                }
                player.sendTextMessage(i18n.get("tc.stargate.network.warp_pending", player));
                return;
            }
            switch (subcommand) {
                case "pack" -> inventories.pack(player);
                case "unpack" -> inventories.unpack(player);
                case "recover" -> inventories.recover(player);
            }
            return;
        }
        switch (subcommand) {
            case "info", "status" -> PluginInfoStatusProviders.show(player, pluginName);
            case "help" -> player.sendTextMessage(colors.okay + plugin.getName() + ":> " + colors.endTag
                    + i18n.get("tc.cmd.help", player).replace("PH_PLUGIN_CMD", StargatePluginRuntime.COMMAND));
            case "open" -> gui.openMainMenu(player);
            default -> player.sendTextMessage(i18n.get("tc.err.cmd.unknown", player)
                    .replace("PH_PLUGIN_CMD", StargatePluginRuntime.COMMAND));
        }
    }

    public void onPlayerSpawn(PlayerSpawnEvent event) {
        transfers.onSpawn(event.getPlayer());
        network.updatePlayer(event.getPlayer());
        if (!settings.enableWelcomeMessage) {
            return;
        }
        Player player = event.getPlayer();
        player.sendTextMessage(i18n.get("tc.msg.plugin.welcome", player.getSystemLanguage())
                .replace("PH_PLUGIN_NAME", plugin.getDescription("name"))
                .replace("PH_PLUGIN_CMD", StargatePluginRuntime.COMMAND)
                .replace("PH_PLUGIN_VERSION", plugin.getDescription("version")));
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        network.updatePlayer(event.getPlayer());
    }

    public void onPlayerPermissionGroupChange(PlayerPermissionGroupChangeEvent event) {
        Player player = event.getPlayer();
        plugin.executeDelayed(0f, () -> {
            if (player.isConnected()) network.updatePlayer(player);
        });
    }

    public void onPlayerChangeGameMode(PlayerChangeGameModeEvent event) {
        if (!settings.forbidChangeGameMode) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.showErrorMessageBox(i18n.get("tc.stargate.trust.change_mode_title", player),
                i18n.get("tc.stargate.trust.change_mode_forbidden", player));
    }
}
