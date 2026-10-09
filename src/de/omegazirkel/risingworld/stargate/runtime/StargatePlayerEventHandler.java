package de.omegazirkel.risingworld.stargate.runtime;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.dhd.DhdService;
import de.omegazirkel.risingworld.stargate.arrival.FirstArrivalService;
import de.omegazirkel.risingworld.stargate.addressbook.AddressBookService;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelService;
import de.omegazirkel.risingworld.stargate.horizon.HorizonService;
import de.omegazirkel.risingworld.stargate.visual.GatePreviewService;
import de.omegazirkel.risingworld.stargate.visual.GateVisualService;
import de.omegazirkel.risingworld.stargate.sector.GatePlacementService;
import net.risingworld.api.events.player.PlayerChangePositionEvent;
import net.risingworld.api.events.player.PlayerObjectInteractionEvent;
import net.risingworld.api.events.player.PlayerGameObjectInteractionEvent;
import net.risingworld.api.events.player.PlayerDisconnectEvent;
import de.omegazirkel.risingworld.stargate.PluginGUI;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotService;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.stargate.ui.TravelScreenService;
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
    private final DhdService dhd;
    private final DhdModelService dhdModels;
    private final HorizonService horizons;
    private final GatePreviewService previews;
    private final GateVisualService visuals;
    private final GatePlacementService placement;
    private final TravelScreenService travelScreen;
    private final FirstArrivalService firstArrival;
    private final AddressBookService addressBook;
    private final Colors colors = Colors.getInstance();

    StargatePlayerEventHandler(OZStargate plugin, String pluginName, PluginSettings settings, I18n i18n,
            PluginGUI gui, InventorySnapshotService inventories, GateNetworkClient network, TransferService transfers, DhdService dhd, DhdModelService dhdModels, HorizonService horizons, GatePreviewService previews, GateVisualService visuals, GatePlacementService placement, TravelScreenService travelScreen, FirstArrivalService firstArrival, AddressBookService addressBook) {
        this.plugin = plugin;
        this.pluginName = pluginName;
        this.settings = settings;
        this.i18n = i18n;
        this.gui = gui;
        this.inventories = inventories;
        this.network = network;
        this.transfers = transfers;
        this.dhd = dhd;
        this.dhdModels = dhdModels;
        this.horizons = horizons;
        this.previews = previews;
        this.visuals = visuals;
        this.placement = placement;
        this.travelScreen = travelScreen;
        this.firstArrival = firstArrival;
        this.addressBook = addressBook;
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
        if (subcommand.equals("placegate") && args.length == 1) {
            placement.create(player);
            return;
        }
        if (subcommand.equals("placegate") || subcommand.equals("removegatemodel")) {
            visuals.command(player, subcommand, args);
            return;
        }
        if (subcommand.equals("placedhd") || subcommand.equals("removedhd")) {
            dhdModels.command(player, subcommand, args);
            return;
        }
        if (subcommand.equals("previewgate") || subcommand.equals("clearpreview")) {
            previews.command(player, subcommand.equals("clearpreview"));
            return;
        }
        if (subcommand.equals("aligngate") || subcommand.equals("sethorizon") || subcommand.equals("showhorizon") || subcommand.equals("removehorizon")) {
            horizons.command(player, subcommand, args);
            return;
        }
        if (subcommand.equals("binddhd") || subcommand.equals("unbinddhd") || subcommand.equals("canceldhd")) {
            dhd.command(player, subcommand, args.length >= 2 ? args[1] : null);
            return;
        }
        if (subcommand.equals("registergate") || subcommand.equals("unregistergate")
                || subcommand.equals("dial") || subcommand.equals("trust") || subcommand.equals("warp")) {
            if (!player.isAdmin()) {
                StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player));
                return;
            }
            switch (subcommand) {
                case "registergate" -> network.register(player);
                case "unregistergate" -> {
                    if (args.length < 2) StargateChat.debug(player, i18n.get("tc.stargate.network.usage_unregister", player));
                    else network.unregister(player, args[1]);
                }
                case "trust" -> network.trust(player, args.length >= 2 ? args[1] : player.getUID());
                case "warp" -> {
                    if (args.length < 2) StargateChat.debug(player, i18n.get("tc.stargate.network.usage_warp", player));
                    else transfers.warp(player, args[1]);
                }
                case "dial" -> {
                    if (args.length < 2) StargateChat.debug(player, i18n.get("tc.stargate.network.usage_dial", player));
                    else network.dial(player, args[1], args.length >= 3 ? args[2] : null);
                }
            }
            return;
        }
        if (subcommand.equals("gatelist")) { network.list(player); return; }
        if (subcommand.equals("pack") || subcommand.equals("unpack") || subcommand.equals("recover")) {
            if (!player.isAdmin()) {
                StargateChat.debug(player, i18n.get("tc.stargate.inventory.admin_only", player));
                return;
            }
            if (transfers.hasActive(player.getUID())) {
                if (subcommand.equals("recover")) {
                    transfers.resume();
                    transfers.onSpawn(player);
                    return;
                }
                StargateChat.debug(player, i18n.get("tc.stargate.network.warp_pending", player));
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
            case "help" -> StargateChat.debug(player, colors.okay + plugin.getName() + ":> " + colors.endTag
                    + i18n.get(player.isAdmin() ? "tc.cmd.help_admin" : "tc.cmd.help", player).replace("PH_PLUGIN_CMD", StargatePluginRuntime.COMMAND));
            case "open" -> gui.openMainMenu(player);
            default -> StargateChat.debug(player, i18n.get("tc.err.cmd.unknown", player)
                    .replace("PH_PLUGIN_CMD", StargatePluginRuntime.COMMAND));
        }
    }

    public void onPlayerObjectInteraction(PlayerObjectInteractionEvent event) { dhd.interact(event); }
    public void onPlayerGameObjectInteraction(PlayerGameObjectInteractionEvent event) { dhdModels.interact(event); }
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        firstArrival.disconnect(event.getPlayer());
        addressBook.disconnect(event.getPlayer());
        travelScreen.disconnect(event.getPlayer());
        previews.disconnect(event.getPlayer());
        visuals.disconnect(event.getPlayer());
        dhdModels.disconnect(event.getPlayer());
        horizons.disconnect(event.getPlayer());
        dhd.disconnect(event.getPlayer());
    }
    public void onPlayerChangePosition(PlayerChangePositionEvent event) { horizons.move(event); }

    public void onPlayerSpawn(PlayerSpawnEvent event) {
        horizons.reset(event.getPlayer());
        horizons.onSpawn(event.getPlayer());
        transfers.onSpawn(event.getPlayer());
        firstArrival.spawn(event.getPlayer());
        visuals.onSpawn(event.getPlayer());
        network.updatePlayer(event.getPlayer());
        addressBook.check(event.getPlayer());
        if (!settings.enableWelcomeMessage) {
            return;
        }
        Player player = event.getPlayer();
        StargateChat.debug(player, i18n.get("tc.msg.plugin.welcome", player.getSystemLanguage())
                .replace("PH_PLUGIN_NAME", plugin.getDescription("name"))
                .replace("PH_PLUGIN_CMD", StargatePluginRuntime.COMMAND)
                .replace("PH_PLUGIN_VERSION", plugin.getDescription("version")));
    }

    public void onPlayerConnect(PlayerConnectEvent event) {
        transfers.onConnect(event.getPlayer());
        firstArrival.connect(event);
        network.updatePlayer(event.getPlayer());
        addressBook.sync(event.getPlayer());
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
