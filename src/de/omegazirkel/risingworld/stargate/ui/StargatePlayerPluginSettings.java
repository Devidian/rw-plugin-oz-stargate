package de.omegazirkel.risingworld.stargate.ui;

import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.InventoryOverlayPanel;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import net.risingworld.api.objects.Player;
import de.omegazirkel.risingworld.tools.I18n;

public class StargatePlayerPluginSettings extends PlayerPluginSettings {

    public StargatePlayerPluginSettings(String pluginLabel, String pluginVersion) {
        this.pluginLabel = pluginLabel;
        this.pluginVersion = pluginVersion;
    }

    public boolean shortcutVisible(Player player) {
        return player == null || OZTools.playerSettings() == null
                || OZTools.playerSettings().getBoolean(player.getDbID(),
                        PluginShortcutVisibility.playerSettingKey(pluginLabel)).orElse(true);
    }

    public boolean debugMessagesVisible(Player player) {
        return StargateChat.debugEnabled(player);
    }

    @Override
    public BasePlayerPluginSettingsPanel createPlayerPluginSettingsUIElement(Player uiPlayer) {
        return new BasePlayerPluginSettingsPanel(uiPlayer, pluginLabel) {

            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                OZUIElement setting = defaultSettingsContainer();
                setting.addChild(defaultSettingsLabel(I18n.getInstance(pluginLabel)
                        .get("tc.shortcut.visible", uiPlayer)));
                setting.addChild(switchButtons(uiPlayer, shortcutVisible(uiPlayer), event -> {
                    if (OZTools.playerSettings() != null) {
                        OZTools.playerSettings().setBoolean(uiPlayer.getDbID(),
                                PluginShortcutVisibility.playerSettingKey(pluginLabel), !shortcutVisible(uiPlayer));
                    }
                    InventoryOverlayPanel.refreshAllVisible();
                    redrawContent();
                }));
                flexWrapper.addChild(setting);
                OZUIElement debugSetting = defaultSettingsContainer();
                debugSetting.addChild(defaultSettingsLabel(I18n.getInstance(pluginLabel)
                        .get("tc.settings.debug_messages", uiPlayer)));
                debugSetting.addChild(switchButtons(uiPlayer, debugMessagesVisible(uiPlayer), event -> {
                    if (OZTools.playerSettings() != null) {
                        OZTools.playerSettings().setBoolean(uiPlayer.getDbID(),
                                StargateChat.DEBUG_SETTING, !debugMessagesVisible(uiPlayer));
                    }
                    redrawContent();
                }));
                flexWrapper.addChild(debugSetting);
            }

        };
    }

}
