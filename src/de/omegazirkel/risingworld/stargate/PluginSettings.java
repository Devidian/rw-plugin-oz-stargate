package de.omegazirkel.risingworld.stargate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import de.omegazirkel.risingworld.stargate.audio.AudioThemeCatalog;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.tools.OZLogger;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsEntry;
import de.omegazirkel.risingworld.tools.settings.AdminSettingsType;
import de.omegazirkel.risingworld.tools.settings.JsonSettingsFile;
import de.omegazirkel.risingworld.tools.settings.SettingsFileEditor;
import net.risingworld.api.World;

public class PluginSettings {
	private static PluginSettings instance = null;

	private static OZStargate plugin;

	private static OZLogger logger() {
		return OZStargate.logger();
	}

	// Settings
	public boolean enableWelcomeMessage = false;
	public String relayUrl = "wss://sgn.omega-zirkel.de/ws";
	public String relayAdvertisedHost = "";
	public String networkCodeOverride = "";
	public String networkCodeTrusted = "";
	public boolean networkEnabled = false;
	public boolean forbidChangeGameMode = true;
	public String audioTheme = "silent";
	public int audioVolume = 70;
	private Path audioDirectory;
	private Path settingsFile;
	private java.util.Map<String, String> currentSettings = new LinkedHashMap<>();
	private java.util.Map<String, String> defaultSettings = new LinkedHashMap<>();

	// END Settings

	public static PluginSettings getInstance(OZStargate p) {
		plugin = p;
		return getInstance();
	}

	public static PluginSettings getInstance() {

		if (instance == null) {
			instance = new PluginSettings();
		}
		return instance;
	}

	private PluginSettings() {
	}

	public void initSettings() {
		Path pluginPath = Path.of(plugin.getPath() != null ? plugin.getPath() : ".");
		audioDirectory = pluginPath.resolve("audio");
		initSettings(pluginPath.resolve("settings." + safeWorldName() + ".json").toString());
	}

	public Path worldSettingsPath() {
		return settingsFile;
	}

	public void initSettings(String filePath) {
		settingsFile = Path.of(filePath);
		Path defaultSettingsFile = settingsFile.resolveSibling("settings.default.json");
		try {
			JsonSettingsFile.prepareWorldSettings(settingsFile);
			java.util.Map<String, String> settings = JsonSettingsFile.loadFlat(settingsFile);
			java.util.Map<String, String> defaults = JsonSettingsFile.loadFlat(defaultSettingsFile);

			// motd settings
			enableWelcomeMessage = settings.getOrDefault("enableWelcomeMessage",
					defaults.getOrDefault("enableWelcomeMessage", "false")).contentEquals("true");

			relayUrl = settings.getOrDefault("relay.url", defaults.getOrDefault("relay.url", relayUrl));
			relayAdvertisedHost = settings.getOrDefault("relay.advertisedHost", "").trim();
			networkCodeOverride = settings.getOrDefault("networkCode.override", "").trim();
			networkCodeTrusted = settings.getOrDefault("networkCode.trusted", "").trim();
			networkEnabled = Boolean.parseBoolean(settings.getOrDefault("network.enabled",
					defaults.getOrDefault("network.enabled", "false")));
			forbidChangeGameMode = Boolean.parseBoolean(settings.getOrDefault("forbiddenActions.ChangeGameMode",
					defaults.getOrDefault("forbiddenActions.ChangeGameMode", "true")));
			String selectedTheme = settings.getOrDefault("audio.theme", defaults.getOrDefault("audio.theme", "silent"));
			audioTheme = availableAudioThemes().contains(selectedTheme) ? selectedTheme : "silent";
			try { audioVolume = Math.max(0, Math.min(100, Integer.parseInt(settings.getOrDefault("audio.volume",
					defaults.getOrDefault("audio.volume", "70"))))); }
			catch (NumberFormatException invalid) { audioVolume = 70; }
			logger().info(plugin.getName() + " Plugin settings loaded");
			logger().info("Sending welcome message on login is: " + String.valueOf(enableWelcomeMessage));
			currentSettings = settings;
			defaultSettings = defaults;

		} catch (IOException ex) {
			logger().error("IOException on initSettings: " + ex.getMessage());
			ex.printStackTrace();
		} catch (NumberFormatException ex) {
			logger().error("NumberFormatException on initSettings: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	public List<AdminSettingsEntry> adminSettingsEntries() {
		I18n i18n = I18n.getInstance(plugin.getDescription("name"));
		List<AdminSettingsEntry> entries = new ArrayList<>(Arrays.asList(
				AdminSettingsEntry.group("playerMessages", i18n.get("tc.setting.playermessages.label"),
						i18n.get("tc.setting.playermessages.desc")),
				entry("enableWelcomeMessage", i18n.get("tc.setting.enablewelcomemessage.label"),
						i18n.get("tc.setting.enablewelcomemessage.desc"),
						AdminSettingsType.BOOLEAN),
				AdminSettingsEntry.group("network", i18n.get("tc.stargate.network.settings.title"),
						i18n.get("tc.stargate.network.settings.desc")),
				entry("network.enabled", i18n.get("tc.stargate.network.settings.enabled"),
						i18n.get("tc.stargate.network.settings.enabled_desc"), AdminSettingsType.BOOLEAN),
				entry("relay.url", i18n.get("tc.stargate.network.settings.url"),
						i18n.get("tc.stargate.network.settings.url_desc"), AdminSettingsType.STRING),
				entry("relay.advertisedHost", i18n.get("tc.stargate.network.settings.host"),
						i18n.get("tc.stargate.network.settings.host_desc"), AdminSettingsType.STRING),
				entry("networkCode.override", i18n.get("tc.stargate.network.settings.override"),
						i18n.get("tc.stargate.network.settings.override_desc"), AdminSettingsType.STRING),
				AdminSettingsEntry.group("forbiddenActions", i18n.get("tc.stargate.trust.settings.title"),
						i18n.get("tc.stargate.trust.settings.desc")),
				entry("forbiddenActions.ChangeGameMode", i18n.get("tc.stargate.trust.settings.change_mode"),
						i18n.get("tc.stargate.trust.settings.change_mode_desc"), AdminSettingsType.BOOLEAN)));
		entries.add(AdminSettingsEntry.group("audio", i18n.get("tc.stargate.audio.settings.title"),
				i18n.get("tc.stargate.audio.settings.desc")));
		entries.add(new AdminSettingsEntry("audio.theme", i18n.get("tc.stargate.audio.settings.theme"),
				i18n.get("tc.stargate.audio.settings.theme_desc"), audioTheme, "silent", AdminSettingsType.SELECT,
				false, value -> availableAudioThemes().contains(value)
					&& SettingsFileEditor.writeValue(settingsFile, "audio.theme", value),
				availableAudioThemes()));
		entries.add(new AdminSettingsEntry("audio.volume", i18n.get("tc.stargate.audio.settings.volume"),
				i18n.get("tc.stargate.audio.settings.volume_desc"), Integer.toString(audioVolume), "70",
				AdminSettingsType.INTEGER, false, value -> {
					try {
						int parsed = Integer.parseInt(value);
						return parsed >= 0 && parsed <= 100
								&& SettingsFileEditor.writeValue(settingsFile, "audio.volume", Integer.toString(parsed));
					} catch (NumberFormatException invalid) { return false; }
				}));
		return entries;
	}

	public Path audioDirectory() { return audioDirectory; }

	public List<String> availableAudioThemes() {
		return AudioThemeCatalog.list(audioDirectory);
	}

	public synchronized void trustNetworkCode(String code) {
		if (code == null || code.isBlank() || settingsFile == null || code.equals(networkCodeTrusted)) return;
		if (SettingsFileEditor.writeValue(settingsFile, "networkCode.trusted", code)) {
			networkCodeTrusted = code;
			currentSettings.put("networkCode.trusted", code);
		}
	}

	private AdminSettingsEntry entry(String key, String label, String description, AdminSettingsType type) {
		return new AdminSettingsEntry(
				key,
				label,
				description,
				currentSettings.getOrDefault(key, defaultSettings.getOrDefault(key, "")),
				defaultSettings.getOrDefault(key, ""),
				type,
				false,
				value -> SettingsFileEditor.writeValue(settingsFile, key, value));
	}

	private static String safeWorldName() {
		String world;
		try {
			world = World.getName();
		} catch (LinkageError ex) {
			world = "default";
		}
		return (world == null || world.isBlank() ? "default" : world).replaceAll("[^A-Za-z0-9._-]", "_");
	}
}
