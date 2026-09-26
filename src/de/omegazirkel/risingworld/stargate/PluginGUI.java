package de.omegazirkel.risingworld.stargate;

import java.util.ArrayList;
import java.util.List;

import de.omegazirkel.risingworld.tools.ui.AssetManager;
import de.omegazirkel.risingworld.tools.ui.MenuItem;
import de.omegazirkel.risingworld.tools.ui.PluginInfoStatusProviders;
import de.omegazirkel.risingworld.tools.ui.PluginMenuManager;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Plugin;
import net.risingworld.api.objects.Player;

public class PluginGUI {
    private static PluginGUI instance = null;
    private String pluginName;

    private PluginGUI() {

    }

    public static PluginGUI getInstance(Plugin p) {

        AssetManager.loadIconFromPlugin(p, "oz-stargate");
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
        menuItems.add(new MenuItem(pluginName, "info-status", I18n.getInstance(pluginName).get("tc.stargate.menu.info", uiPlayer), player -> {
            player.hideRadialMenu(true);
            PluginInfoStatusProviders.show(player, pluginName);
        }));
        menuItems.add(MenuItem.closeMenu(uiPlayer));
        PluginMenuManager.showMenu(uiPlayer, menuItems);
    }

}
