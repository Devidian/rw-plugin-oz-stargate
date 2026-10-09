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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_known_gate_addresses (network_code TEXT NOT NULL, gate_id TEXT NOT NULL, address TEXT NOT NULL, alias TEXT NOT NULL DEFAULT '', PRIMARY KEY(network_code,gate_id))");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_known_gate_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_known_gate_addresses WHERE gate_id=OLD.gate_id; END");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_personal_names (player_uid TEXT NOT NULL, gate_id TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(player_uid,gate_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_faction_shares (owner_db_id INTEGER NOT NULL, owner_uid TEXT NOT NULL, faction_id INTEGER NOT NULL, gate_id TEXT NOT NULL, PRIMARY KEY(owner_db_id,faction_id,gate_id))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_faction_share_all (owner_db_id INTEGER NOT NULL, owner_uid TEXT NOT NULL, faction_id INTEGER NOT NULL, PRIMARY KEY(owner_db_id,faction_id))");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_personal_name_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_personal_names WHERE gate_id=OLD.gate_id; END");
            statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS stargate_faction_share_deleted AFTER DELETE ON stargates BEGIN DELETE FROM stargate_faction_shares WHERE gate_id=OLD.gate_id; END");
        }
        if (!hasLocalAddressColumn()) try (Statement statement = database.createStatement()) {
            statement.executeUpdate("ALTER TABLE stargate_known_gate_addresses ADD COLUMN local_address TEXT");
        }
    }

    private boolean hasLocalAddressColumn() throws SQLException {
        try (Statement statement = database.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA table_info(stargate_known_gate_addresses)")) {
            while (rows.next()) if ("local_address".equals(rows.getString("name"))) return true;
        }
        return false;
    }

    public synchronized boolean learn(String code, String uid, String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement("INSERT OR IGNORE INTO stargate_address_book(network_code,player_uid,gate_id,pending) VALUES(?,?,?,1)")) {
            statement.setString(1, code); statement.setString(2, uid); statement.setString(3, gateId);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized List<String> known(String code, String uid) throws SQLException { return list(code, uid, false); }
    public synchronized List<String> pending(String code, String uid) throws SQLException { return list(code, uid, true); }

    public synchronized List<String> knownLocal(String uid) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement s = database.prepareStatement("SELECT DISTINCT b.gate_id FROM stargate_address_book b JOIN stargates g ON g.gate_id=b.gate_id WHERE b.player_uid=? ORDER BY b.gate_id")) {
            s.setString(1, uid);
            try (ResultSet r = s.executeQuery()) { while (r.next()) ids.add(r.getString(1)); }
        }
        return List.copyOf(ids);
    }

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
        try (PreparedStatement delete = database.prepareStatement("DELETE FROM stargate_address_book WHERE network_code=? AND player_uid=? AND gate_id NOT IN (SELECT gate_id FROM stargates)");
             PreparedStatement insert = database.prepareStatement("INSERT INTO stargate_address_book(network_code,player_uid,gate_id,pending) VALUES(?,?,?,0) ON CONFLICT(network_code,player_uid,gate_id) DO UPDATE SET pending=0")) {
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
        try (PreparedStatement statement = database.prepareStatement("DELETE FROM stargate_known_gate_addresses WHERE network_code=? AND gate_id=?")) {
            statement.setString(1, code); statement.setString(2, gateId); statement.executeUpdate();
        }
    }

    public synchronized void setGateDetails(String code, String gateId, String address, String localAddress, String alias) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "INSERT INTO stargate_known_gate_addresses(network_code,gate_id,address,local_address,alias) VALUES(?,?,?,?,?) ON CONFLICT(network_code,gate_id) DO UPDATE SET address=excluded.address,local_address=excluded.local_address,alias=excluded.alias")) {
            statement.setString(1, code); statement.setString(2, gateId);
            statement.setString(3, address); statement.setString(4, localAddress); statement.setString(5, alias);
            statement.executeUpdate();
        }
    }

    public synchronized String localByAddress(String code, String address) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT local_address FROM stargate_known_gate_addresses WHERE network_code=? AND address=?")) {
            statement.setString(1, code); statement.setString(2, address);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized String address(String code, String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT address FROM stargate_known_gate_addresses WHERE network_code=? AND gate_id=?")) {
            statement.setString(1, code); statement.setString(2, gateId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized String alias(String code, String gateId) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT alias FROM stargate_known_gate_addresses WHERE network_code=? AND gate_id=?")) {
            statement.setString(1, code); statement.setString(2, gateId);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized String aliasByAddress(String code, String address) throws SQLException {
        try (PreparedStatement statement = database.prepareStatement(
                "SELECT alias FROM stargate_known_gate_addresses WHERE network_code=? AND address=?")) {
            statement.setString(1, code); statement.setString(2, address);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    public synchronized String gateIdByAddress(String code, String address) throws SQLException {
        try (PreparedStatement s = database.prepareStatement("SELECT gate_id FROM stargate_known_gate_addresses WHERE network_code=? AND (address=? OR local_address=?) LIMIT 1")) {
            s.setString(1, code); s.setString(2, address); s.setString(3, address);
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    public synchronized void movePending(String fromCode, String toCode, String uid) throws SQLException {
        try (PreparedStatement copy = database.prepareStatement("INSERT OR IGNORE INTO stargate_address_book(network_code,player_uid,gate_id,pending) SELECT ?,player_uid,gate_id,1 FROM stargate_address_book WHERE network_code=? AND player_uid=? AND pending=1");
             PreparedStatement delete = database.prepareStatement("DELETE FROM stargate_address_book WHERE network_code=? AND player_uid=? AND pending=1")) {
            copy.setString(1, toCode); copy.setString(2, fromCode); copy.setString(3, uid); copy.executeUpdate();
            delete.setString(1, fromCode); delete.setString(2, uid); delete.executeUpdate();
        }
    }

    public synchronized void migrateLocalGates(String fromCode, String toCode, List<String> ids) throws SQLException {
        if (fromCode == null || fromCode.isBlank() || fromCode.equals(toCode) || ids.isEmpty()) return;
        boolean autoCommit = database.getAutoCommit();
        database.setAutoCommit(false);
        try (PreparedStatement copyBook = database.prepareStatement("INSERT INTO stargate_address_book(network_code,player_uid,gate_id,pending) SELECT ?,player_uid,gate_id,1 FROM stargate_address_book WHERE network_code=? AND gate_id=? ON CONFLICT(network_code,player_uid,gate_id) DO UPDATE SET pending=1");
             PreparedStatement deleteBook = database.prepareStatement("DELETE FROM stargate_address_book WHERE network_code=? AND gate_id=?");
             PreparedStatement copyDetails = database.prepareStatement("INSERT OR IGNORE INTO stargate_known_gate_addresses(network_code,gate_id,address,alias,local_address) SELECT ?,gate_id,address,alias,local_address FROM stargate_known_gate_addresses WHERE network_code=? AND gate_id=?");
             PreparedStatement deleteDetails = database.prepareStatement("DELETE FROM stargate_known_gate_addresses WHERE network_code=? AND gate_id=?")) {
            for (String id : ids) {
                for (PreparedStatement copy : List.of(copyBook, copyDetails)) {
                    copy.setString(1, toCode); copy.setString(2, fromCode); copy.setString(3, id); copy.executeUpdate();
                }
                for (PreparedStatement delete : List.of(deleteBook, deleteDetails)) {
                    delete.setString(1, fromCode); delete.setString(2, id); delete.executeUpdate();
                }
            }
            database.commit();
        } catch (SQLException ex) { database.rollback(); throw ex; }
        finally { database.setAutoCommit(autoCommit); }
    }

    public synchronized String personalName(String uid, String gateId) throws SQLException {
        try (PreparedStatement s = database.prepareStatement("SELECT name FROM stargate_personal_names WHERE player_uid=? AND gate_id=?")) {
            s.setString(1, uid); s.setString(2, gateId);
            try (ResultSet r = s.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    public synchronized void setPersonalName(String uid, String gateId, String name) throws SQLException {
        if (name.isBlank()) {
            try (PreparedStatement s = database.prepareStatement("DELETE FROM stargate_personal_names WHERE player_uid=? AND gate_id=?")) {
                s.setString(1, uid); s.setString(2, gateId); s.executeUpdate();
            }
        } else try (PreparedStatement s = database.prepareStatement("INSERT INTO stargate_personal_names(player_uid,gate_id,name) VALUES(?,?,?) ON CONFLICT(player_uid,gate_id) DO UPDATE SET name=excluded.name")) {
            s.setString(1, uid); s.setString(2, gateId); s.setString(3, name); s.executeUpdate();
        }
    }

    public record Share(int ownerDbId, String ownerUid, int factionId, String gateId, boolean all) { }

    public synchronized List<Share> shares(int factionId) throws SQLException {
        List<Share> result = new ArrayList<>();
        try (PreparedStatement s = database.prepareStatement("SELECT owner_db_id,owner_uid,faction_id,gate_id,0 FROM stargate_faction_shares WHERE faction_id=? UNION ALL SELECT owner_db_id,owner_uid,faction_id,'',1 FROM stargate_faction_share_all WHERE faction_id=?")) {
            s.setInt(1, factionId); s.setInt(2, factionId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) result.add(new Share(r.getInt(1), r.getString(2), r.getInt(3), r.getString(4), r.getInt(5) == 1)); }
        }
        return result;
    }

    public synchronized boolean shared(int ownerDbId, int factionId, String gateId) throws SQLException {
        try (PreparedStatement s = database.prepareStatement("SELECT 1 FROM stargate_faction_shares WHERE owner_db_id=? AND faction_id=? AND gate_id=? UNION SELECT 1 FROM stargate_faction_share_all WHERE owner_db_id=? AND faction_id=?")) {
            s.setInt(1, ownerDbId); s.setInt(2, factionId); s.setString(3, gateId); s.setInt(4, ownerDbId); s.setInt(5, factionId);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    public synchronized boolean allShared(int ownerDbId, int factionId) throws SQLException {
        try (PreparedStatement s = database.prepareStatement("SELECT 1 FROM stargate_faction_share_all WHERE owner_db_id=? AND faction_id=?")) {
            s.setInt(1, ownerDbId); s.setInt(2, factionId);
            try (ResultSet r = s.executeQuery()) { return r.next(); }
        }
    }

    public synchronized void setShare(int ownerDbId, String ownerUid, int factionId, String gateId, boolean enabled) throws SQLException {
        String sql = enabled ? "INSERT OR IGNORE INTO stargate_faction_shares(owner_db_id,owner_uid,faction_id,gate_id) VALUES(?,?,?,?)"
                : "DELETE FROM stargate_faction_shares WHERE owner_db_id=? AND owner_uid=? AND faction_id=? AND gate_id=?";
        try (PreparedStatement s = database.prepareStatement(sql)) {
            s.setInt(1, ownerDbId); s.setString(2, ownerUid); s.setInt(3, factionId); s.setString(4, gateId); s.executeUpdate();
        }
    }

    public synchronized void setAllShared(int ownerDbId, String ownerUid, int factionId, boolean enabled) throws SQLException {
        String sql = enabled ? "INSERT OR IGNORE INTO stargate_faction_share_all(owner_db_id,owner_uid,faction_id) VALUES(?,?,?)"
                : "DELETE FROM stargate_faction_share_all WHERE owner_db_id=? AND owner_uid=? AND faction_id=?";
        try (PreparedStatement s = database.prepareStatement(sql)) {
            s.setInt(1, ownerDbId); s.setString(2, ownerUid); s.setInt(3, factionId); s.executeUpdate();
        }
    }
}
