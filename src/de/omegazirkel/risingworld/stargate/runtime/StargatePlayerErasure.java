package de.omegazirkel.risingworld.stargate.runtime;

import net.risingworld.api.Server;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

final class StargatePlayerErasure {
    private final Connection database;

    StargatePlayerErasure(Connection database) {
        this.database = database;
    }

    boolean ready(int playerDbId) {
        String uid = Server.getPlayerUID(playerDbId);
        try {
            return uid != null && !uid.isBlank() && !hasUnsettledData(uid);
        } catch (SQLException ex) {
            return false;
        }
    }

    private boolean hasUnsettledData(String uid) throws SQLException {
        try (var statement = database.prepareStatement("""
                SELECT
                    EXISTS (SELECT 1 FROM stargate_transfers WHERE player_uid = ?1 AND state NOT IN ('DONE', 'CANCELLED'))
                    OR EXISTS (SELECT 1 FROM stargate_first_visits WHERE player_uid = ?1 AND state <> 'DONE')
                    OR EXISTS (SELECT 1 FROM stargate_pending_arrivals WHERE player_uid = ?1)
                    OR EXISTS (SELECT 1 FROM stargate_arrival_screens WHERE player_uid = ?1)
                    OR EXISTS (SELECT 1 FROM stargate_local_travel_visibility WHERE player_uid = ?1)
                    OR EXISTS (SELECT 1 FROM stargate_visitor_base WHERE player_uid = ?1)
                    OR EXISTS (SELECT 1 FROM inventory_escrow WHERE player_uid = ?1)
                    OR EXISTS (SELECT 1 FROM inventory_snapshots WHERE player_uid = ?1)
                    OR EXISTS (SELECT 1 FROM inventory_recovery WHERE player_uid = ?1)
                    OR EXISTS (SELECT 1 FROM stargate_faction_shares WHERE owner_uid = ?1)
                    OR EXISTS (SELECT 1 FROM stargate_faction_share_all WHERE owner_uid = ?1)
                """)) {
            statement.setString(1, uid);
            try (var row = statement.executeQuery()) { return row.next() && row.getBoolean(1); }
        }
    }

    synchronized boolean erase(int playerDbId) throws SQLException {
        String uid = Server.getPlayerUID(playerDbId);
        if (uid == null || uid.isBlank()) return false;
        boolean autoCommit = database.getAutoCommit();
        database.setAutoCommit(false);
        try {
            if (hasUnsettledData(uid)) {
                database.rollback();
                return false;
            }
            for (String table : List.of("stargate_transfers", "stargate_first_visits", "stargate_address_book",
                    "stargate_personal_names", "stargate_discovery_cooldowns")) {
                try (var statement = database.prepareStatement("DELETE FROM " + table + " WHERE player_uid = ?")) {
                    statement.setString(1, uid);
                    statement.executeUpdate();
                }
            }
            database.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            database.rollback();
            throw ex;
        } finally {
            database.setAutoCommit(autoCommit);
        }
    }
}
