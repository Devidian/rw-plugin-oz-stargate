package de.omegazirkel.risingworld.stargate.runtime;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.Objects;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.PluginGUI;
import de.omegazirkel.risingworld.stargate.arrival.FirstArrivalService;
import de.omegazirkel.risingworld.stargate.arrival.FirstArrivalStore;
import de.omegazirkel.risingworld.stargate.addressbook.AddressBookService;
import de.omegazirkel.risingworld.stargate.addressbook.AddressBookStore;
import de.omegazirkel.risingworld.stargate.discovery.DiscoveryCooldownStore;
import de.omegazirkel.risingworld.stargate.discovery.DiscoveryCandidateStore;
import de.omegazirkel.risingworld.stargate.discovery.DiscoveryPoolService;
import de.omegazirkel.risingworld.stargate.discovery.DiscoveryService;
import de.omegazirkel.risingworld.stargate.discovery.InitialGateStore;
import de.omegazirkel.risingworld.stargate.discovery.InitialGateService;
import de.omegazirkel.risingworld.stargate.visual.GatePreviewService;
import de.omegazirkel.risingworld.stargate.visual.GateModelAssets;
import de.omegazirkel.risingworld.stargate.visual.GateVisualService;
import de.omegazirkel.risingworld.stargate.visual.GateVisualStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdService;
import de.omegazirkel.risingworld.stargate.dhd.DhdStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelStore;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelAssets;
import de.omegazirkel.risingworld.stargate.dhd.DhdModelService;
import de.omegazirkel.risingworld.stargate.horizon.HorizonStore;
import de.omegazirkel.risingworld.stargate.horizon.HorizonService;
import de.omegazirkel.risingworld.stargate.PluginSettings;
import de.omegazirkel.risingworld.stargate.audio.GateAudioService;
import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotService;
import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotStore;
import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.network.LocalGateStore;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.LocalDialService;
import de.omegazirkel.risingworld.stargate.sector.GatePlacementService;
import de.omegazirkel.risingworld.stargate.transfer.TransferService;
import de.omegazirkel.risingworld.stargate.transfer.TransferStore;
import de.omegazirkel.risingworld.stargate.ui.StargatePlayerPluginData;
import de.omegazirkel.risingworld.stargate.ui.StargatePlayerPluginSettings;
import de.omegazirkel.risingworld.stargate.ui.TravelScreenService;
import de.omegazirkel.risingworld.stargate.ui.StargatePluginInfoStatusProvider;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.db.SQLiteConnectionFactory;
import de.omegazirkel.risingworld.tools.settings.PlayerPluginAdminSettings;
import de.omegazirkel.risingworld.tools.ui.InventoryOverlayButtons;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettingsOverlay;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import de.omegazirkel.risingworld.tools.ui.SharedIndicatorProvider;
import de.omegazirkel.risingworld.tools.ui.SharedIndicators;
import net.risingworld.api.objects.Player;

public final class StargatePluginRuntime {
    public static final String COMMAND = "sg";

    private final OZStargate plugin;
    private final String pluginName;
    private final PluginSettings settings;
    private final I18n i18n;
    private final PluginGUI gui;
    private final StargatePlayerEventHandler events;
    private final GateNetworkClient network;
    private final LocalDialService localDial;
    private final DhdService dhd;
    private final DhdModelService dhdModels;
    private final HorizonService horizons;
    private final GatePreviewService previews;
    private final GateModelAssets modelAssets;
    private final GateVisualService visuals;
    private final GateAudioService audio;
    private final TravelScreenService travelScreen;
    private final FirstArrivalService firstArrival;
    private final AddressBookService addressBook;
    private final DiscoveryService discovery;
    private final DiscoveryPoolService discoveryPool;
    private final InitialGateService initialGate;

    public StargatePluginRuntime(OZStargate plugin) {
        this.plugin = plugin;
        pluginName = plugin.getDescription("name");
        settings = PluginSettings.getInstance(plugin);
        i18n = I18n.getInstance(plugin);
        gui = PluginGUI.getInstance(plugin);
        Connection database = SQLiteConnectionFactory.open(plugin);
        InventorySnapshotStore snapshots = new InventorySnapshotStore(database);
        LocalGateStore gates = new LocalGateStore(database);
        LocalSectorStore localSectors = new LocalSectorStore(database);
        DhdStore consoles = new DhdStore(database);
        DhdModelStore dhdModelStore = new DhdModelStore(database);
        HorizonStore horizonStore = new HorizonStore(database);
        GateVisualStore visualStore = new GateVisualStore(database);
        TransferStore transferStore = new TransferStore(database);
        FirstArrivalStore firstArrivalStore = new FirstArrivalStore(database);
        AddressBookStore addressBookStore = new AddressBookStore(database);
        DiscoveryCooldownStore discoveryStore = new DiscoveryCooldownStore(database);
        DiscoveryCandidateStore candidateStore = new DiscoveryCandidateStore(database);
        InitialGateStore initialGateStore = new InitialGateStore(database);
        try {
            snapshots.initialize();
            initialGateStore.initialize();
            gates.initialize();
            localSectors.initialize();
            localSectors.migrateExisting(gates);
            consoles.initialize();
            dhdModelStore.initialize();
            horizonStore.initialize();
            visualStore.initialize();
            transferStore.initialize();
            firstArrivalStore.initialize();
            addressBookStore.initialize();
            discoveryStore.initialize();
            candidateStore.initialize();
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot initialize Stargate inventory database", ex);
        }
        network = new GateNetworkClient(plugin, settings, i18n, gates, localSectors);
        addressBook = new AddressBookService(plugin, settings, i18n, addressBookStore,
                consoles, dhdModelStore, gates, network);
        network.setAddressBook(addressBook);
        travelScreen = new TravelScreenService(plugin);
        audio = new GateAudioService(settings);
        localDial = new LocalDialService(plugin, localSectors, gates, network, i18n);
        localDial.setAddressBook(addressBook);
        network.setLocalDial(localDial);
        TransferService transfers = new TransferService(plugin, transferStore, snapshots, gates, network, i18n);
        network.setTransfers(transfers);
        transfers.setTravelScreen(travelScreen);
        dhd = new DhdService(plugin, consoles, gates, network, localDial, i18n);
        addressBook.setChanged(dhd::addressesChanged);
        try {
            dhdModels = new DhdModelService(plugin, dhdModelStore, gates, dhd,
                    new DhdModelAssets(plugin), i18n, network);
        } catch (SQLException ex) { throw new IllegalStateException("Cannot load DHD models", ex); }
        audio.setDhdPosition(dhdModels::audioPosition);
        try {
            horizons = new HorizonService(plugin, horizonStore, gates, localSectors, network, localDial, transfers, i18n, visualStore, transferStore);
        } catch (SQLException ex) { throw new IllegalStateException("Cannot load Stargate horizons", ex); }
        transfers.setArrivalObserver(horizons::reset);
        modelAssets = new GateModelAssets(plugin);
        previews = new GatePreviewService(plugin, i18n, modelAssets);
        try {
            visuals = new GateVisualService(plugin, visualStore, gates, modelAssets, i18n, horizons::isAligned, network, audio);
        } catch (SQLException ex) { throw new IllegalStateException("Cannot load Stargate models", ex); }
        horizons.setTravelObserver(visuals::travelled);
        horizons.setTravelScreen(travelScreen);
        horizons.setArrivalObserver(visuals::arrived);
        horizons.setArrivalPlayerObserver(visuals::arrivedPlayer);
        transfers.setArrivalSoundObserver(visuals::arrived);
        transfers.setArrivalPlayerSoundObserver(visuals::arrivedPlayer);
        firstArrival = new FirstArrivalService(plugin, firstArrivalStore, gates, network, transfers, horizons, visuals, travelScreen);
        addressBook.setDiscoveryAllowed(uid -> !firstArrival.inProgress(uid) && !transfers.hasActive(uid));
        network.setGateDeletedObserver(id -> { addressBook.removed(id); localDial.gateDeleted(id); horizons.gateDeleted(id); visuals.gateDeleted(id); dhdModels.gateDeleted(id); });
        GatePlacementService placement = new GatePlacementService(plugin, gates, localSectors, visualStore, horizonStore,
                dhdModelStore, visuals, horizons, dhdModels, network, i18n);
        discoveryPool = new DiscoveryPoolService(plugin, settings, gates, localSectors, candidateStore, placement);
        initialGate = new InitialGateService(plugin, settings, initialGateStore, gates, network, placement);
        discovery = new DiscoveryService(plugin, settings, discoveryStore, placement, discoveryPool,
                network, localDial, addressBook, i18n);
        dhd.setDiscovery(discovery);
        gui.setPlacement(placement);
        events = new StargatePlayerEventHandler(plugin, pluginName, settings, i18n, gui,
                new InventorySnapshotService(snapshots, i18n), network, transfers, dhd, dhdModels, horizons, previews, visuals, placement, travelScreen, firstArrival, addressBook);
    }

    public void enable() {
        settings.initSettings();
        audio.reload();
        network.start();
        addressBook.start();
        localDial.start();
        initialGate.start();
        discoveryPool.start();
        visuals.start();
        dhdModels.start();
        PluginMenuManager.registerPluginMenu(new MenuItem(pluginName, "oz-stargate", "OZ Stargate",
                (Player player) -> gui.openMainMenu(player)));
        StargatePlayerPluginSettings playerSettings = new StargatePlayerPluginSettings(pluginName,
                plugin.getDescription("version"));
        PluginShortcutVisibility.register(pluginName, playerSettings::shortcutVisible);
        InventoryOverlayButtons.registerButton(pluginName, i18n.get("tc.stargate.menu.open"), "oz-stargate",
                event -> gui.openMainMenu(event.getPlayer()));
        SharedIndicators.registerProvider(pluginName, new SharedIndicatorProvider() {
            @Override
            public boolean showIndicator(Player player) {
                return false;
            }

            @Override
            public String getIcon(Player player) {
                return "oz-stargate";
            }
        });
        String version = plugin.getDescription("version");
        PluginInfoStatusProviders.registerProvider(new StargatePluginInfoStatusProvider(pluginName, version, COMMAND, network));
        PlayerPluginSettingsOverlay.registerPlayerPluginSettings(playerSettings);
        PlayerPluginSettingsOverlay.registerPlayerPluginData(new StargatePlayerPluginData(pluginName, version));
        PlayerPluginSettingsOverlay.registerPlayerPluginAdminSettings(new PlayerPluginAdminSettings(pluginName, version,
                settings::adminSettingsEntries, () -> reloadSettings(settings.worldSettingsPath())));
        OZStargate.logger().info("✅ " + plugin.getName() + " Plugin is enabled version:" + version);
    }

    public void disable() {
        initialGate.close();
        discovery.close();
        discoveryPool.close();
        previews.close();
        firstArrival.close();
        addressBook.close();
        travelScreen.close();
        audio.close();
        visuals.close();
        dhdModels.close();
        modelAssets.close();
        horizons.close();
        dhd.close();
        localDial.close();
        network.close();
        InventoryOverlayButtons.unregisterButtons(pluginName);
        PluginShortcutVisibility.unregister(pluginName);
        SharedIndicators.unregisterProvider(pluginName);
        PluginInfoStatusProviders.unregisterProvider(pluginName);
    }

    public void reloadSettings(Path settingsPath) {
        // The package updater also replaces settings.default.json. Only the
        // world-specific file may become the active runtime configuration.
        if ("settings.default.json".equals(settingsPath.getFileName().toString())) {
            return;
        }
        String priorUrl = settings.relayUrl;
        String priorHost = settings.relayAdvertisedHost;
        String priorOverride = settings.networkCodeOverride;
        String audioThemeBefore = settings.audioTheme;
        boolean priorNetworkEnabled = settings.networkEnabled;
        boolean priorForbiddenMode = settings.forbidChangeGameMode;
        settings.initSettings(settingsPath.toString());
        if (!Objects.equals(audioThemeBefore, settings.audioTheme)) audio.reload();
        if (!Objects.equals(priorUrl, settings.relayUrl)
                || !Objects.equals(priorHost, settings.relayAdvertisedHost)
                || !Objects.equals(priorOverride, settings.networkCodeOverride)
                || priorNetworkEnabled != settings.networkEnabled
                || priorForbiddenMode != settings.forbidChangeGameMode) {
            network.reload();
        }
    }

    public StargatePlayerEventHandler events() {
        return events;
    }
}
