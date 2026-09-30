package de.omegazirkel.risingworld.stargate.ui;

import java.sql.SQLException;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.OZTools;
import de.omegazirkel.risingworld.stargate.sector.LocalSectorStore;
import de.omegazirkel.risingworld.stargate.sector.SectorAddress;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.Server;
import net.risingworld.api.objects.Player;
import net.risingworld.api.utils.Vector2i;

/** Player chat policy. Safety-critical kick and dialog text is handled by its caller. */
public final class StargateChat {
    public static final String DEBUG_SETTING = "stargate.debugMessages";

    private StargateChat() { }

    public static boolean debugEnabled(Player player) {
        return player != null && OZTools.playerSettings() != null
                && OZTools.playerSettings().getBoolean(player.getDbID(), DEBUG_SETTING).orElse(false);
    }

    public static void debug(Player player, String message) {
        if (debugEnabled(player) && player.isConnected()) player.sendTextMessage(message);
    }

    public static void incoming(String gateId, LocalSectorStore sectors, I18n i18n) {
        try {
            SectorAddress gateSector = sectors.addressOf(gateId);
            if (gateSector == null) return;
            for (Player player : Server.getAllPlayers()) {
                Vector2i playerSector = player.getSectorPosition();
                if (player.isConnected() && playerSector != null
                        && gateSector.x() == playerSector.x && gateSector.z() == playerSector.y) {
                    player.sendTextMessage(i18n.get("tc.stargate.network.incoming", player)
                            .replace("PH_GATE", gateId));
                }
            }
        } catch (SQLException ex) {
            OZStargate.logger().error("Cannot notify Stargate sector: " + ex.getMessage());
        }
    }
}
