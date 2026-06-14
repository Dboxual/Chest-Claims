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

public final class ClaimManageGUI {

    // Main management screen (27 slots)
    //   [ outline(11) ]   [ chest/info(13) ]   [ access(15) ]
    public static final int OUTLINE_TOGGLE_SLOT = 11;
    public static final int MANAGE_INFO_SLOT    = 13;
    public static final int MANAGE_ACCESS_SLOT  = 15;

    // Delete-confirmation screen (27 slots)
    //   [ yes(10) ]   [ warn(13) ]   [ no(16) ]
    public static final int DELETE_YES_SLOT  = 10;
    public static final int DELETE_WARN_SLOT = 13;
    public static final int DELETE_NO_SLOT   = 16;

    private ClaimManageGUI() {}

    // ── main management screen ─────────────────────────────────────────────

    public static void openManage(Player player, ClaimData claim, ChestClaimsPlugin plugin) {
        ClaimManageHolder holder = new ClaimManageHolder(claim, ClaimManageHolder.Screen.MANAGE);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Claim Management", NamedTextColor.DARK_AQUA));
        holder.setInventory(inv);

        inv.setItem(OUTLINE_TOGGLE_SLOT, outlineToggleItem(claim));
        inv.setItem(MANAGE_INFO_SLOT,    infoItem(claim, plugin));
        inv.setItem(MANAGE_ACCESS_SLOT,  accessItem());

        player.openInventory(inv);
    }

    private static ItemStack outlineToggleItem(ClaimData claim) {
        boolean enabled = claim.isOutlineEnabled();
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(enabled
                ? txt("Claim Outline: ON",  NamedTextColor.GREEN)
                : txt("Claim Outline: OFF", NamedTextColor.GRAY));
        meta.lore(List.of(
                Component.empty(),
                txt("Show claim boundaries to nearby", NamedTextColor.GRAY),
                txt("players with particles.",          NamedTextColor.GRAY),
                Component.empty(),
                txt("Click to " + (enabled ? "disable" : "enable") + ".", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack infoItem(ClaimData claim, ChestClaimsPlugin plugin) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Your Claim", NamedTextColor.GOLD));

        boolean upkeepEnabled = plugin.getConfig().getBoolean("upkeep.enabled", true);
        String pricePaid  = NumberFormat.getNumberInstance(Locale.US).format(claim.getCost()) + " Bops";
        String upkeepText = upkeepCostText(claim, upkeepEnabled, plugin);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (claim.isChunkClaim()) {
            lore.add(row("Type:", "Chunk Claim", NamedTextColor.AQUA));
        }
        lore.add(row("Owner:",      claim.getOwnerName(), NamedTextColor.WHITE));
        lore.add(row("Price Paid:", pricePaid,            NamedTextColor.YELLOW));
        lore.add(row("Upkeep:",     upkeepText,           upkeepEnabled ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String upkeepCostText(ClaimData claim, boolean upkeepEnabled, ChestClaimsPlugin plugin) {
        if (!upkeepEnabled) return "Disabled";
        String cycle = plugin.getConfig().getString("upkeep.cycle-name", "Day");
        String cost  = NumberFormat.getNumberInstance(Locale.US).format(claim.getDailyUpkeepCost());
        return cost + " Bops / " + cycle;
    }

    private static ItemStack accessItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Claim Access", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                Component.empty(),
                txt("Manage trusted players and", NamedTextColor.GRAY),
                txt("team access for this claim.", NamedTextColor.GRAY),
                Component.empty(),
                txt("Click to manage.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    // ── delete-confirm screen ──────────────────────────────────────────────

    public static void openDeleteConfirm(Player player, ClaimData claim) {
        ClaimManageHolder holder = new ClaimManageHolder(claim, ClaimManageHolder.Screen.DELETE_CONFIRM);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Delete Claim?", NamedTextColor.RED));
        holder.setInventory(inv);

        inv.setItem(DELETE_YES_SLOT,  deleteYesButton());
        inv.setItem(DELETE_WARN_SLOT, warnItem());
        inv.setItem(DELETE_NO_SLOT,   deleteNoButton());

        player.openInventory(inv);
    }

    private static ItemStack deleteYesButton() {
        ItemStack item = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Yes, Delete Claim", NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack warnItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Are you sure?", NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                txt("This cannot be undone.", NamedTextColor.GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack deleteNoButton() {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("No, Keep Claim", NamedTextColor.GREEN));
        item.setItemMeta(meta);
        return item;
    }

    // ── shared ─────────────────────────────────────────────────────────────

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component row(String label, String value, NamedTextColor valueColor) {
        return txt(label + " ", NamedTextColor.GRAY).append(txt(value, valueColor));
    }
}
