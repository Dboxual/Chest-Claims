package com.chestclaims.gui;

import com.chestclaims.claim.ClaimSession;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class ClaimGuiHolder implements InventoryHolder {

    private final UUID playerUuid;
    private final ClaimSession session;
    private Inventory inventory;

    public ClaimGuiHolder(UUID playerUuid, ClaimSession session) {
        this.playerUuid = playerUuid;
        this.session = session;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public ClaimSession getSession() { return session; }

    @Override
    public Inventory getInventory() { return inventory; }
    void setInventory(Inventory inv) { this.inventory = inv; }
}
