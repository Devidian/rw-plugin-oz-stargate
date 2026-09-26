package de.omegazirkel.risingworld.stargate.transfer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Durable, world-local transfer journal. Every mutation is conditional on its prior state. */
public final class TransferStore {
    public record Transfer(String id, String uid, String direction, String state, String gateId,
            String peerGateId, String data, long expiresAt) { }
    public record Base(String uid, byte[] inventory, byte[] clothes, String state) { }

    private final Connection db;

    public TransferStore(Connection db) { this.db = db; }

    public synchronized void initialize() throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_transfers (transfer_id TEXT PRIMARY KEY, player_uid TEXT NOT NULL, direction TEXT NOT NULL, state TEXT NOT NULL, gate_id TEXT NOT NULL, peer_gate_id TEXT NOT NULL, data TEXT NOT NULL, expires_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS stargate_transfers_uid ON stargate_transfers(player_uid,direction,state)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS stargate_visitor_base (player_uid TEXT PRIMARY KEY, inventory BLOB NOT NULL, clothes BLOB NOT NULL, state TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        }
    }

    public synchronized boolean insert(Transfer transfer) throws SQLException {
        if (byId(transfer.id()) != null) return false;
        try (PreparedStatement statement = db.prepareStatement("INSERT INTO stargate_transfers VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, transfer.id());
            statement.setString(2, transfer.uid());
            statement.setString(3, transfer.direction());
            statement.setString(4, transfer.state());
            statement.setString(5, transfer.gateId());
            statement.setString(6, transfer.peerGateId());
            statement.setString(7, transfer.data());
            statement.setLong(8, transfer.expiresAt());
            statement.setLong(9, System.currentTimeMillis());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized Transfer byId(String id) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("SELECT transfer_id,player_uid,direction,state,gate_id,peer_gate_id,data,expires_at FROM stargate_transfers WHERE transfer_id=?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? row(rows) : null; }
        }
    }

    public synchronized List<Transfer> forPlayer(String uid) throws SQLException {
        List<Transfer> transfers = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement("SELECT transfer_id,player_uid,direction,state,gate_id,peer_gate_id,data,expires_at FROM stargate_transfers WHERE player_uid=? ORDER BY updated_at DESC")) {
            statement.setString(1, uid);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) transfers.add(row(rows)); }
        }
        return transfers;
    }

    public synchronized List<Transfer> active() throws SQLException {
        List<Transfer> transfers = new ArrayList<>();
        try (Statement statement = db.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT transfer_id,player_uid,direction,state,gate_id,peer_gate_id,data,expires_at FROM stargate_transfers WHERE state NOT IN ('DONE','CANCELLED')")) {
            while (rows.next()) transfers.add(row(rows));
        }
        return transfers;
    }

    public synchronized boolean transition(String id, String from, String to) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("UPDATE stargate_transfers SET state=?,updated_at=? WHERE transfer_id=? AND state=?")) {
            statement.setString(1, to);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, id);
            statement.setString(4, from);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean completeOutgoing(Transfer transfer) throws SQLException {
        if (!db.getAutoCommit()) throw new SQLException("Unexpected nested transfer transaction");
        db.setAutoCommit(false);
        try {
            boolean changed = transition(transfer.id(), transfer.state(), "DONE");
            if (changed) baseState(transfer.uid(), "HELD", "RESTORE_PENDING");
            db.commit();
            return changed;
        } catch (SQLException ex) {
            db.rollback();
            throw ex;
        } finally { db.setAutoCommit(true); }
    }

    public synchronized Base base(String uid) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("SELECT player_uid,inventory,clothes,state FROM stargate_visitor_base WHERE player_uid=?")) {
            statement.setString(1, uid);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new Base(rows.getString(1), rows.getBytes(2), rows.getBytes(3), rows.getString(4)) : null;
            }
        }
    }

    public synchronized boolean saveBase(String uid, byte[] inventory, byte[] clothes) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("INSERT OR IGNORE INTO stargate_visitor_base VALUES (?,?,?,?,?)")) {
            statement.setString(1, uid);
            statement.setBytes(2, inventory);
            statement.setBytes(3, clothes);
            statement.setString(4, "HELD");
            statement.setLong(5, System.currentTimeMillis());
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean baseState(String uid, String from, String to) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("UPDATE stargate_visitor_base SET state=?,updated_at=? WHERE player_uid=? AND state=?")) {
            statement.setString(1, to);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uid);
            statement.setString(4, from);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean consumeBase(String uid, String state) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("DELETE FROM stargate_visitor_base WHERE player_uid=? AND state=?")) {
            statement.setString(1, uid);
            statement.setString(2, state);
            return statement.executeUpdate() == 1;
        }
    }

    private static Transfer row(ResultSet rows) throws SQLException {
        return new Transfer(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4),
                rows.getString(5), rows.getString(6), rows.getString(7), rows.getLong(8));
    }
}
