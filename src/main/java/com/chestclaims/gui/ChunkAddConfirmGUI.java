package com.chestclaims.gui;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChunkAddConfirmGUI {

    // Layout (27 slots):
    //   [ confirm(11) ]   [ info(13) ]   [ cancel(15) ]
    public static final int CONFIRM_SLOT = 11;
    public static final int INFO_SLOT    = 13;
    public static final int CANCEL_SLOT  = 15;

    private ChunkAddConfirmGUI() {}

    public static void open(Player player, ClaimData claim, int targetChunkX, int targetChunkZ,
                            ChestClaimsPlugin plugin) {
        ChunkAddConfirmHolder holder = new ChunkAddConfirmHolder(claim, targetChunkX, targetChunkZ);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Add Chunk?", NamedTextColor.DARK_AQUA));
        holder.setInventory(inv);

        int currentChunks = claim.getClaimedChunks().size();
        int newChunks     = currentChunks + 1;
        double cost        = plugin.getConfig().getDouble("chunk-claims.claim-cost-bops", 15.0);
        double upkeepEach  = plugin.getConfig().getDouble("chunk-claims.upkeep-bops-per-day", 2.0);
        double newUpkeep   = newChunks * upkeepEach;
        boolean upkeepOn   = plugin.getConfig().getBoolean("upkeep.enabled", true);
        String cycle       = plugin.getConfig().getString("upkeep.cycle-name", "Day");

        inv.setItem(CONFIRM_SLOT, confirmItem(cost));
        inv.setItem(INFO_SLOT,    infoItem(targetChunkX, targetChunkZ, currentChunks, newChunks,
                                           cost, newUpkeep, upkeepOn, cycle));
        inv.setItem(CANCEL_SLOT,  cancelItem());

        player.openInventory(inv);
    }

    private static ItemStack confirmItem(double cost) {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Confirm - Add Chunk", NamedTextColor.GREEN));
        meta.lore(List.of(
                Component.empty(),
                txt("Cost: " + fmt(cost) + " Bops", NamedTextColor.YELLOW),
                Component.empty(),
                txt("Click to confirm.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack infoItem(int cx, int cz,
                                      int currentChunks, int newChunks,
                                      double cost, double newUpkeep,
                                      boolean upkeepOn, String cycle) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Chunk Details", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(row("Chunk X:", String.valueOf(cx)));
        lore.add(row("Chunk Z:", String.valueOf(cz)));
        lore.add(Component.empty());
        lore.add(row("Current size:", currentChunks + " chunk(s)"));
        lore.add(row("New size:",     newChunks     + " chunk(s)"));
        lore.add(Component.empty());
        lore.add(row("Cost:", fmt(cost) + " Bops"));
        if (upkeepOn) {
            lore.add(row("New upkeep:", fmt(newUpkeep) + " Bops / " + cycle));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack cancelItem() {
        ItemStack item = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Cancel", NamedTextColor.RED));
        meta.lore(List.of(
                Component.empty(),
                txt("Click to cancel. No charge.", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component row(String label, String value) {
        return txt(label + " ", NamedTextColor.GRAY)
                .append(txt(value, NamedTextColor.WHITE));
    }

    private static String fmt(double value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }
}
