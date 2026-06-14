package com.chestclaims.gui;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.teams.TeamsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public final class ClaimAccessGUI {

    // Layout (27 slots, 3 rows):
    //   Row 1 (slots 0–8): trusted player heads — click to remove (up to 9)
    //   Slot 11: Team Access toggle
    //   Slot 13: Add Trusted Player
    //   Slot 22: Back button
    public static final int TRUSTED_ROW_START = 0;   // slots 0..8
    public static final int TEAM_TOGGLE_SLOT  = 11;
    public static final int ADD_TRUSTED_SLOT  = 13;
    public static final int BACK_SLOT         = 22;

    private ClaimAccessGUI() {}

    public static void open(Player player, ClaimData claim, ChestClaimsPlugin plugin) {
        ClaimManageHolder holder = new ClaimManageHolder(claim, ClaimManageHolder.Screen.ACCESS);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Claim Access", NamedTextColor.LIGHT_PURPLE));
        holder.setInventory(inv);

        // Trusted players in row 1 (slots 0-8)
        List<UUID> trusted = claim.getTrustedPlayers();
        for (int i = 0; i < Math.min(trusted.size(), 9); i++) {
            inv.setItem(TRUSTED_ROW_START + i, trustedPlayerItem(trusted.get(i)));
        }

        inv.setItem(TEAM_TOGGLE_SLOT, teamToggleItem(claim));
        inv.setItem(ADD_TRUSTED_SLOT, addTrustedItem());
        inv.setItem(BACK_SLOT,        backButton());

        player.openInventory(inv);
    }

    private static ItemStack teamToggleItem(ClaimData claim) {
        if (!TeamsHook.isAvailable()) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(txt("Team Access", NamedTextColor.GRAY));
            meta.lore(List.of(
                    Component.empty(),
                    txt("BetterTeams is not installed.", NamedTextColor.RED),
                    txt("Install BetterTeams to enable", NamedTextColor.GRAY),
                    txt("team access for this claim.", NamedTextColor.GRAY)
            ));
            item.setItemMeta(meta);
            return item;
        }

        boolean on = claim.isTeamAccessEnabled();
        ItemStack item = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(on
                ? txt("Team Access: ON",  NamedTextColor.GREEN)
                : txt("Team Access: OFF", NamedTextColor.GRAY));
        meta.lore(List.of(
                Component.empty(),
                txt("Allow your BetterTeams teammates", NamedTextColor.GRAY),
                txt("to build and interact in this claim.", NamedTextColor.GRAY),
                Component.empty(),
                txt("Click to " + (on ? "disable" : "enable") + ".", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack addTrustedItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Add Trusted Player", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.empty(),
                txt("Trusted players can build, break,", NamedTextColor.GRAY),
                txt("and interact inside this claim.", NamedTextColor.GRAY),
                Component.empty(),
                txt("Click to choose an online player.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Back", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    private static ItemStack trustedPlayerItem(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName() != null ? op.getName() : uuid.toString().substring(0, 8) + "...";

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(txt(name, NamedTextColor.WHITE));
        meta.setOwningPlayer(op);
        meta.lore(List.of(
                Component.empty(),
                txt("Trusted player", NamedTextColor.GREEN),
                Component.empty(),
                txt("Click to remove trust.", NamedTextColor.RED)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
}
