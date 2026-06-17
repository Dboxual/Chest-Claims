package com.chestclaims.gui;

import com.chestclaims.claim.ClaimData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClaimManageHolder implements InventoryHolder {

    public enum Screen { MANAGE, DELETE_CONFIRM, ACCESS, SELECT_PLAYER, OUTLINE_SETTINGS }

    private final ClaimData claim;
    private final Screen screen;
    private final List<UUID> selectablePlayers;
    private final int page;
    private Inventory inventory;

    public ClaimManageHolder(ClaimData claim, Screen screen) {
        this(claim, screen, List.of(), 0);
    }

    public ClaimManageHolder(ClaimData claim, Screen screen, List<UUID> selectablePlayers, int page) {
        this.claim  = claim;
        this.screen = screen;
        this.selectablePlayers = new ArrayList<>(selectablePlayers);
        this.page = page;
    }

    public ClaimData getClaim()  { return claim; }
    public Screen    getScreen() { return screen; }
    public List<UUID> getSelectablePlayers() { return selectablePlayers; }
    public int getPage() { return page; }

    @Override
    public Inventory getInventory() { return inventory; }
    void setInventory(Inventory inv) { this.inventory = inv; }
}
