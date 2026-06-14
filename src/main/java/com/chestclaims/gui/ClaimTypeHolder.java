package com.chestclaims.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public class ClaimTypeHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final Location anchor;
    private Inventory inventory;

    public ClaimTypeHolder(UUID playerUuid, Location anchor) {
        this.playerUuid = playerUuid;
        this.anchor     = anchor.clone();
    }

    public UUID     getPlayerUuid() { return playerUuid; }
    public Location getAnchor()     { return anchor.clone(); }

    @Override
    public Inventory getInventory() { return inventory; }
    void setInventory(Inventory inv) { this.inventory = inv; }
}
