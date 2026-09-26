package de.omegazirkel.risingworld;

import java.nio.file.Path;

import de.omegazirkel.risingworld.stargate.runtime.StargatePluginRuntime;
import de.omegazirkel.risingworld.tools.FileChangeListener;
import de.omegazirkel.risingworld.tools.OZLogger;
import net.risingworld.api.Plugin;
import net.risingworld.api.events.EventMethod;
import net.risingworld.api.events.Listener;
import net.risingworld.api.events.player.PlayerCommandEvent;
import net.risingworld.api.events.player.PlayerConnectEvent;
import net.risingworld.api.events.player.PlayerPermissionGroupChangeEvent;
import net.risingworld.api.events.player.PlayerChangeGameModeEvent;
import net.risingworld.api.events.player.PlayerSpawnEvent;

/**
 * Rising World entry point. This is intentionally the plugin's only event
 * listener; all runtime and feature logic is delegated below {@code stargate}.
 */
public class OZStargate extends Plugin implements Listener, FileChangeListener {
    private static final String LOGGER_NAME = "OZStargate";

    private StargatePluginRuntime runtime;

    public static OZLogger logger() {
        return OZLogger.getInstance(LOGGER_NAME);
    }

    @Override
    public void onEnable() {
        runtime = new StargatePluginRuntime(this);
        runtime.enable();
        registerEventListener(this);
    }

    @Override
    public void onDisable() {
        unregisterEventListener(this);
        if (runtime != null) {
            runtime.disable();
        }
    }

    @Override
    public void onSettingsChanged(Path settingsPath) {
        runtime.reloadSettings(settingsPath);
    }

    @EventMethod
    public void onPlayerCommand(PlayerCommandEvent event) {
        runtime.events().onPlayerCommand(event);
    }

    @EventMethod
    public void onPlayerSpawnEvent(PlayerSpawnEvent event) {
        runtime.events().onPlayerSpawn(event);
    }

    @EventMethod
    public void onPlayerConnect(PlayerConnectEvent event) {
        runtime.events().onPlayerConnect(event);
    }

    @EventMethod
    public void onPlayerPermissionGroupChange(PlayerPermissionGroupChangeEvent event) {
        runtime.events().onPlayerPermissionGroupChange(event);
    }

    @EventMethod
    public void onPlayerChangeGameMode(PlayerChangeGameModeEvent event) {
        runtime.events().onPlayerChangeGameMode(event);
    }
}
