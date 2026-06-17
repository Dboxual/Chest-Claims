package com.chestclaims.gui;

import com.chestclaims.claim.ClaimData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ChunkAddConfirmHolder implements InventoryHolder {

    private final ClaimData claim;
    private final int targetChunkX;
    private final int targetChunkZ;
    private Inventory inventory;

    public ChunkAddConfirmHolder(ClaimData claim, int targetChunkX, int targetChunkZ) {
        this.claim        = claim;
        this.targetChunkX = targetChunkX;
        this.targetChunkZ = targetChunkZ;
    }

    public ClaimData getClaim()       { return claim; }
    public int getTargetChunkX()      { return targetChunkX; }
    public int getTargetChunkZ()      { return targetChunkZ; }

    @Override
    public Inventory getInventory()   { return inventory; }
    void setInventory(Inventory inv)  { this.inventory = inv; }
}
