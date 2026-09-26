package de.omegazirkel.risingworld.stargate.ui;

import de.omegazirkel.risingworld.tools.ui.BasePlayerPluginSettingsPanel;
import de.omegazirkel.risingworld.tools.ui.PlayerPluginSettings;
import net.risingworld.api.objects.Player;
import net.risingworld.api.ui.UILabel;
import de.omegazirkel.risingworld.tools.I18n;

public class StargatePlayerPluginSettings extends PlayerPluginSettings {

    public StargatePlayerPluginSettings(String pluginLabel, String pluginVersion) {
        this.pluginLabel = pluginLabel;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public BasePlayerPluginSettingsPanel createPlayerPluginSettingsUIElement(Player uiPlayer) {
        return new BasePlayerPluginSettingsPanel(uiPlayer, pluginLabel) {

            @Override
            protected void redrawContent() {
                flexWrapper.removeAllChilds();
                UILabel placeholderLabel = new UILabel(I18n.getInstance(pluginLabel)
                        .get("tc.stargate.settings.empty", uiPlayer));
                flexWrapper.addChild(placeholderLabel);
            }

        };
    }

}
