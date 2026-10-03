package de.omegazirkel.risingworld.stargate.addressbook;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** World-local per-player cache. Relay snapshots replace synced rows on reconnect. */
public final class AddressBookStore {
    private final Connection database;

    public AddressBookStore(Connection database) { this.database = database; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_address_book (network_code TEXT NOT NULL, player_uid TEXT NOT NULL, gate_id TEXT NOT NULL, pending INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(network_code,player_uid,gate_id))");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS stargate_address_book_pending ON stargate_address_book(network_code,player_uid,pending)");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_address_book_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_address_book WHERE gate_id=OLD.gate_id; END");
        }
    }

    public synchronized boolean learn(String code, String uid, String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("INSERT OR IGNORE INTO stargate_address_book(network_code,player_uid,gate_id,pending) VALUES(?,?,?,1)")) {
            statement.setString(1, code); statement.setString(2, uid); statement.setString(3, gateId);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized List<String> known(String code, String uid) throws SQLException { return list(code, uid, false); }
    public synchronized List<String> pending(String code, String uid) throws SQLException { return list(code, uid, true); }

    private List<String> list(String code, String uid, boolean pendingOnly) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = database.prepareStatement("SELECT gate_id FROM stargate_address_book WHERE network_code=? AND player_uid=?"
                + (pendingOnly ? " AND pending=1" : "") + " ORDER BY gate_id")) {
            statement.setString(1, code); statement.setString(2, uid);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) ids.add(rows.getString(1)); }
        }
        return List.copyOf(ids);
    }

    public synchronized void replace(String code, String uid, List<String> ids) throws SQLException {
        boolean autoCommit = database.getAutoCommit();
        database.setAutoCommit(false);
        try (PreparedStatement delete = database.prepareStatement("DELETE FROM stargate_address_book WHERE network_code=? AND player_uid=?");
             PreparedStatement insert = database.prepareStatement("INSERT OR IGNORE INTO stargate_address_book(network_code,player_uid,gate_id,pending) VALUES(?,?,?,0)")) {
            delete.setString(1, code); delete.setString(2, uid); delete.executeUpdate();
            for (String id : ids) {
                insert.setString(1, code); insert.setString(2, uid); insert.setString(3, id); insert.addBatch();
            }
            insert.executeBatch();
            database.commit();
        } catch (SQLException ex) {
            database.rollback();
            throw ex;
        } finally { database.setAutoCommit(autoCommit); }
    }

    public synchronized void remove(String code, String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_address_book WHERE network_code=? AND gate_id=?")) {
            statement.setString(1, code); statement.setString(2, gateId); statement.executeUpdate();
        }
    }

    public synchronized void movePending(String fromCode, String toCode, String uid) throws SQLException {
        try (PreparedStatement copy = database.prepareStatement("INSERT OR IGNORE INTO stargate_address_book(network_code,player_uid,gate_id,pending) SELECT ?,player_uid,gate_id,1 FROM stargate_address_book WHERE network_code=? AND player_uid=? AND pending=1");
             PreparedStatement delete = database.prepareStatement("DELETE FROM stargate_address_book WHERE network_code=? AND player_uid=? AND pending=1")) {
            copy.setString(1, toCode); copy.setString(2, fromCode); copy.setString(3, uid); copy.executeUpdate();
            delete.setString(1, fromCode); delete.setString(2, uid); delete.executeUpdate();
        }
    }
}
