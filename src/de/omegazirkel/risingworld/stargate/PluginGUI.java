package de.omegazirkel.risingworld.stargate;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.stargate.sector.GatePlacementService;
import net.risingworld.api.Plugin;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.MessageBoxButtons;

public class PluginGUI {
    private static PluginGUI instance = null;
    private String pluginName;
    private GatePlacementService placement;

    private PluginGUI() {

    }

    public static PluginGUI getInstance(Plugin p) {

        AssetManager.loadIconFromPlugin(p, "oz-stargate");
        AssetManager.loadIconFromPlugin(p, "stargate-gate-dhd");
        AssetManager.loadIconFromPlugin(p, "stargate-gate");
        AssetManager.loadIconFromPlugin(p, "stargate-dhd");
        AssetManager.loadIconFromPlugin(p, "stargate-delete");
        PluginGUI gui = getInstance();
        gui.pluginName = p.getDescription("name");
        return gui;
    }

    public static PluginGUI getInstance() {
        if (instance == null) {
            instance = new PluginGUI();
        }
        return instance;
    }

    public void openMainMenu(Player uiPlayer) {
        List<MenuItem> menuItems = new ArrayList<>();
        if (uiPlayer.isAdmin() && placement != null) {
            I18n i18n = I18n.getInstance(pluginName);
            menuItems.add(new MenuItem(pluginName, "stargate-gate-dhd", i18n.get("tc.stargate.menu.place_gate_dhd", uiPlayer), player -> {
                player.hideRadialMenu(true);
                placement.placeGate(player, true);
            }));
            menuItems.add(new MenuItem(pluginName, "stargate-gate", i18n.get("tc.stargate.menu.place_gate", uiPlayer), player -> {
                player.hideRadialMenu(true);
                placement.placeGate(player, false);
            }));
            if (placement.hasGateHere(uiPlayer)) {
                menuItems.add(new MenuItem(pluginName, "stargate-dhd",
                        i18n.get("tc.stargate.menu.place_dhd", uiPlayer), player -> {
                        player.hideRadialMenu(true);
                        placement.placeDhd(player);
                        }));
                menuItems.add(new MenuItem(pluginName, "stargate-delete",
                        i18n.get("tc.stargate.menu.delete_gate_dhd", uiPlayer), player -> {
                    player.hideRadialMenu(true);
                    String id = placement.gateHere(player);
                    if (id == null) return;
                    player.showMessageBox(MessageBoxButtons.Yes_No,
                            i18n.get("tc.stargate.menu.delete_confirm_title", player),
                            i18n.get("tc.stargate.menu.delete_confirm_body", player).replace("PH_GATE", id),
                            0, answer -> { if (answer == 0) placement.removeGate(player, id); });
                }));
            }
        }
        menuItems.add(new MenuItem(pluginName, "info-status", I18n.getInstance(pluginName).get("tc.stargate.menu.info", uiPlayer), player -> {
            player.hideRadialMenu(true);
            PluginInfoStatusProviders.show(player, pluginName);
        }));
        menuItems.add(MenuItem.closeMenu(uiPlayer));
        PluginMenuManager.showMenu(uiPlayer, menuItems);
    }

    public void setPlacement(GatePlacementService placement) { this.placement = placement; }

}
