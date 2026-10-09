package de.omegazirkel.risingworld.stargate.ui;

import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.InventoryOverlayPanel;
import de.omegazirkel.risingworld.tools.ui.OZUIElement;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import de.omegazirkel.risingworld.tools.ui.PluginShortcutVisibility;
import net.risingworld.api.objects.Player;
import de.omegazirkel.risingworld.stargate.audio.GateAudioService;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.Unit;

public class StargatePlayerPluginSettings extends PlayerPluginSettings {
    public static final String TRAVEL_SCREEN_DISABLED_KEY = "oz.stargate.travelScreenDisabled";
    public static final String ADDRESS_MODE_KEY = "oz.stargate.dhdAddressMode";
    public static final String ADDRESS_ALIASES = "ALIASES";
    public static final String ADDRESS_OWN = "OWN";
    public static final String ADDRESS_LOCAL = "LOCAL";
    public static final String ADDRESS_NETWORK = "NETWORK";

    public static String addressMode(Player player) {
        if (player == null || OZTools.playerSettings() == null) return ADDRESS_ALIASES;
        String value = OZTools.playerSettings().getString(player.getDbID(), ADDRESS_MODE_KEY).orElse(ADDRESS_ALIASES);
        return switch (value) {
            case ADDRESS_LOCAL, ADDRESS_NETWORK, ADDRESS_OWN -> value;
            default -> ADDRESS_ALIASES;
        };
    }

    public static boolean travelScreenEnabled(Player player) {
        return player == null || OZTools.playerSettings() == null
                || !OZTools.playerSettings().getBoolean(player.getDbID(), TRAVEL_SCREEN_DISABLED_KEY).orElse(false);
    }

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
                OZUIElement audioSetting = defaultSettingsContainer();
                audioSetting.addChild(defaultSettingsLabel(I18n.getInstance(pluginLabel)
                        .get("tc.settings.audio_enabled", uiPlayer)));
                audioSetting.addChild(switchButtons(uiPlayer, GateAudioService.enabled(uiPlayer), event -> {
                    if (OZTools.playerSettings() != null) OZTools.playerSettings().setBoolean(uiPlayer.getDbID(),
                            GateAudioService.PLAYER_ENABLED_KEY, !GateAudioService.enabled(uiPlayer));
                    redrawContent();
                }));
                flexWrapper.addChild(audioSetting);
                OZUIElement travelSetting = defaultSettingsContainer();
                travelSetting.addChild(defaultSettingsLabel(I18n.getInstance(pluginLabel)
                        .get("tc.settings.travel_screen_disabled", uiPlayer)));
                travelSetting.addChild(switchButtons(uiPlayer, !travelScreenEnabled(uiPlayer), event -> {
                    if (OZTools.playerSettings() != null) OZTools.playerSettings().setBoolean(uiPlayer.getDbID(),
                            TRAVEL_SCREEN_DISABLED_KEY, travelScreenEnabled(uiPlayer));
                    redrawContent();
                }));
                flexWrapper.addChild(travelSetting);
                OZUIElement addressSetting = defaultSettingsContainer();
                I18n language = I18n.getInstance(pluginLabel);
                addressSetting.addChild(defaultSettingsLabel(language.get("tc.stargate.player.address_mode", uiPlayer)));
                UIElement choices = new UIElement();
                choices.setPivot(Pivot.LowerLeft);
                choices.setPosition(0, 100, true);
                choices.style.width.set(100, Unit.Percent);
                choices.style.height.set(38, Unit.Pixel);
                String[] modes = { ADDRESS_OWN, ADDRESS_ALIASES, ADDRESS_LOCAL, ADDRESS_NETWORK };
                String[] keys = { "own", "aliases", "local", "network" };
                for (int i = 0; i < modes.length; i++) {
                    final String mode = modes[i];
                    AdvancedButton choice = AdvancedButtonFactory.defaultButton(
                            language.get("tc.stargate.player.address_" + keys[i], uiPlayer), event -> {
                                if (OZTools.playerSettings() != null)
                                    OZTools.playerSettings().setString(uiPlayer.getDbID(), ADDRESS_MODE_KEY, mode);
                                redrawContent();
                            });
                    choice.setPivot(Pivot.UpperLeft);
                    choice.style.position.set(Position.Absolute);
                    choice.style.left.set(i * 25, Unit.Percent);
                    choice.style.width.set(24, Unit.Percent);
                    choice.style.height.set(30, Unit.Pixel);
                    choice.setBorderColor(mode.equals(addressMode(uiPlayer)) ? 0x286B43FF : 0x7A5D2AFF);
                    choices.addChild(choice);
                }
                addressSetting.addChild(choices);
                flexWrapper.addChild(addressSetting);
            }

        };
    }

}
