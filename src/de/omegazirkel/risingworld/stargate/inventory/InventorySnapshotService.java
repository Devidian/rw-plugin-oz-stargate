package de.omegazirkel.risingworld.stargate.inventory;

import java.sql.SQLException;
import java.util.Arrays;

import de.omegazirkel.risingworld.OZStargate;
import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotStore.Escrow;
import de.omegazirkel.risingworld.stargate.inventory.InventorySnapshotStore.State;
import de.omegazirkel.risingworld.stargate.ui.StargateChat;
import de.omegazirkel.risingworld.tools.I18n;
import net.risingworld.api.objects.Clothes;
import net.risingworld.api.objects.Inventory;
import net.risingworld.api.objects.Item;
import net.risingworld.api.objects.Player;

/** Single-use inventory and worn-clothing escrow for the Development experiment. */
public final class InventorySnapshotService {
    private final InventorySnapshotStore store;
    private final I18n i18n;

    public InventorySnapshotService(InventorySnapshotStore store, I18n i18n) {
        this.store = store;
        this.i18n = i18n;
    }

    public void pack(Player player) {
        Inventory inventory = player.getInventory();
        Clothes clothes = player.getClothes();
        if (inventory == null || clothes == null) {
            message(player, "tc.stargate.inventory.unavailable");
            return;
        }
        String uid = uid(player);
        try {
            if (store.legacyRecovery(uid) != null) {
                message(player, "tc.stargate.inventory.recovery_pending");
                return;
            }
            Escrow current = store.escrow(uid);
            if (current != null) {
                message(player, current.state() == State.PACKED
                        ? "tc.stargate.inventory.already_packed" : "tc.stargate.inventory.recovery_pending");
                return;
            }
            byte[] inventoryData = inventory.serialize();
            byte[] clothesData = hasClothes(clothes) ? clothes.serialize() : new byte[0];
            if (inventoryData == null) inventoryData = empty(inventory) ? new byte[0] : null;
            if (inventoryData == null || clothesData == null || !store.prepare(uid, inventoryData, clothesData)) {
                message(player, "tc.stargate.inventory.unavailable");
                return;
            }
            // From this point onward the durable PREPARED row is authoritative.
            // An interruption stays blocked until /sg recover reconciles it.
            inventory.clear();
            clothes.removeAll();
            inventory.syncWithClient();
            if (!empty(inventory) || hasClothes(clothes)
                    || !store.transition(uid, State.PREPARED, State.PACKED)) {
                throw new IllegalStateException("Cannot finish inventory packing");
            }
            store.clearLegacySnapshot(uid);
            message(player, "tc.stargate.inventory.packed");
        } catch (SQLException | RuntimeException ex) {
            OZStargate.logger().error("Cannot pack inventory and clothes for UID " + uid + ": " + ex.getMessage());
            message(player, "tc.stargate.inventory.recovery_pending");
        }
    }

    public void unpack(Player player) {
        Inventory inventory = player.getInventory();
        Clothes clothes = player.getClothes();
        if (inventory == null || clothes == null) {
            message(player, "tc.stargate.inventory.unavailable");
            return;
        }
        String uid = uid(player);
        try {
            Escrow escrow = store.escrow(uid);
            if (escrow == null) {
                message(player, store.hasLegacySnapshot(uid)
                        ? "tc.stargate.inventory.legacy_snapshot" : "tc.stargate.inventory.missing");
                return;
            }
            if (escrow.state() != State.PACKED) {
                message(player, "tc.stargate.inventory.recovery_pending");
                return;
            }
            if (!empty(inventory) || hasClothes(clothes)) {
                message(player, "tc.stargate.inventory.not_empty");
                return;
            }
            if (!store.transition(uid, State.PACKED, State.UNPACKING)) {
                throw new IllegalStateException("Cannot start inventory unpacking");
            }
            if (!restore(inventory, clothes, escrow) || !store.consume(uid, State.UNPACKING)) {
                throw new IllegalStateException("Cannot finish inventory unpacking");
            }
            message(player, "tc.stargate.inventory.unpacked");
        } catch (SQLException | RuntimeException ex) {
            OZStargate.logger().error("Cannot unpack inventory and clothes for UID " + uid + ": " + ex.getMessage());
            message(player, "tc.stargate.inventory.recovery_pending");
        }
    }

    public void recover(Player player) {
        Inventory inventory = player.getInventory();
        Clothes clothes = player.getClothes();
        if (inventory == null || clothes == null) {
            message(player, "tc.stargate.inventory.unavailable");
            return;
        }
        String uid = uid(player);
        try {
            Escrow escrow = store.escrow(uid);
            if (escrow != null) {
                switch (escrow.state()) {
                    case PREPARED -> {
                        if (matches(inventory, clothes, escrow) && store.consume(uid, State.PREPARED)) {
                            message(player, "tc.stargate.inventory.pack_cancelled");
                        } else if (empty(inventory) && !hasClothes(clothes)
                                && store.transition(uid, State.PREPARED, State.PACKED)) {
                            store.clearLegacySnapshot(uid);
                            message(player, "tc.stargate.inventory.packed");
                        } else message(player, "tc.stargate.inventory.manual_recovery");
                    }
                    case PACKED -> message(player, "tc.stargate.inventory.already_packed");
                    case UNPACKING -> {
                        if (empty(inventory) && !hasClothes(clothes)) restore(inventory, clothes, escrow);
                        if (matches(inventory, clothes, escrow) && store.consume(uid, State.UNPACKING)) {
                            message(player, "tc.stargate.inventory.unpacked");
                        } else message(player, "tc.stargate.inventory.manual_recovery");
                    }
                }
                return;
            }
            byte[] legacyRecovery = store.legacyRecovery(uid);
            if (legacyRecovery == null) {
                message(player, "tc.stargate.inventory.no_recovery");
            } else if (!empty(inventory)) {
                message(player, "tc.stargate.inventory.manual_recovery");
            } else if (inventory.deserialize(legacyRecovery)) {
                inventory.syncWithClient();
                store.clearLegacyRecovery(uid);
                message(player, "tc.stargate.inventory.recovered");
            } else {
                message(player, "tc.stargate.inventory.manual_recovery");
            }
        } catch (SQLException | RuntimeException ex) {
            OZStargate.logger().error("Cannot recover inventory for UID " + uid + ": " + ex.getMessage());
            message(player, "tc.stargate.inventory.manual_recovery");
        }
    }

    private static boolean restore(Inventory inventory, Clothes clothes, Escrow escrow) {
        inventory.clear();
        clothes.removeAll();
        boolean inventoryRestored = escrow.inventory().length == 0 || inventory.deserialize(escrow.inventory());
        boolean clothesRestored = escrow.clothes().length == 0 || clothes.deserialize(escrow.clothes());
        inventory.syncWithClient();
        return inventoryRestored && clothesRestored;
    }

    private static boolean matches(Inventory inventory, Clothes clothes, Escrow escrow) {
        byte[] inventoryData = escrow.inventory().length == 0 && empty(inventory)
                ? new byte[0] : inventory.serialize();
        byte[] clothesData = escrow.clothes().length == 0 && !hasClothes(clothes)
                ? new byte[0] : clothes.serialize();
        return Arrays.equals(inventoryData, escrow.inventory()) && Arrays.equals(clothesData, escrow.clothes());
    }

    private static boolean empty(Inventory inventory) {
        Item[] items = inventory.getAllItems();
        if (items == null) return true;
        for (Item item : items) if (item != null) return false;
        return true;
    }

    private static boolean hasClothes(Clothes clothes) {
        Clothes.Garment[] garments = clothes.getAll();
        if (garments == null) return false;
        for (Clothes.Garment garment : garments) if (garment != null) return true;
        return false;
    }

    private static String uid(Player player) {
        return String.valueOf(player.getUID());
    }

    private void message(Player player, String key) {
        StargateChat.debug(player, i18n.get(key, player));
    }
}
