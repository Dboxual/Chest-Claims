package com.chestclaims.gui;

import com.chestclaims.ChestClaimsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class ClaimTypeSelectGUI {

    public static final int CUSTOM_CLAIM_SLOT = 11;
    public static final int CHUNK_CLAIM_SLOT  = 15;

    private ClaimTypeSelectGUI() {}

    public static void open(Player player, Location anchor, ChestClaimsPlugin plugin) {
        ClaimTypeHolder holder = new ClaimTypeHolder(player.getUniqueId(), anchor);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Choose Claim Type", NamedTextColor.DARK_AQUA));
        holder.setInventory(inv);

        boolean chunkEnabled = plugin.getConfig().getBoolean("chunk-claims.enabled", true);
        double  chunkCost    = plugin.getConfig().getDouble("chunk-claims.claim-cost-bops", 15.0);
        double  chunkUpkeep  = plugin.getConfig().getDouble("chunk-claims.upkeep-bops-per-day", 2.0);

        inv.setItem(CUSTOM_CLAIM_SLOT, customClaimItem());
        inv.setItem(CHUNK_CLAIM_SLOT,  chunkClaimItem(chunkCost, chunkUpkeep, chunkEnabled));

        player.openInventory(inv);
    }

    private static ItemStack customClaimItem() {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Custom Claim", NamedTextColor.GREEN));
        meta.lore(List.of(
                Component.empty(),
                txt("Create a custom-sized protected area.", NamedTextColor.GRAY),
                Component.empty(),
                txt("Click to select.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack chunkClaimItem(double cost, double upkeep, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.BLUE_CONCRETE : Material.GRAY_CONCRETE);
        ItemMeta meta  = item.getItemMeta();
        meta.displayName(txt("Chunk Claim", enabled ? NamedTextColor.AQUA : NamedTextColor.GRAY));

        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        List<Component> lore;
        if (enabled) {
            lore = List.of(
                    Component.empty(),
                    txt("Protect the chunk you are standing in.", NamedTextColor.GRAY),
                    Component.empty(),
                    row("Cost:",   fmt.format(cost)   + " Bops",     NamedTextColor.YELLOW),
                    row("Upkeep:", fmt.format(upkeep) + " Bops/day", NamedTextColor.GOLD),
                    Component.empty(),
                    txt("Click to select.", NamedTextColor.YELLOW)
            );
        } else {
            lore = List.of(
                    Component.empty(),
                    txt("Chunk claims are not enabled.", NamedTextColor.RED)
            );
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component row(String label, String value, NamedTextColor valueColor) {
        return txt(label + " ", NamedTextColor.GRAY).append(txt(value, valueColor));
    }
}
