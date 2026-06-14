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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TrustedPlayerSelectGUI {

    public static final int PLAYER_SLOT_COUNT = 45;
    public static final int PREVIOUS_PAGE_SLOT = 45;
    public static final int BACK_SLOT = 49;
    public static final int NEXT_PAGE_SLOT = 53;

    private TrustedPlayerSelectGUI() {}

    public static void open(Player player, ClaimData claim, ChestClaimsPlugin plugin) {
        open(player, claim, plugin, 0);
    }

    public static void open(Player player, ClaimData claim, ChestClaimsPlugin plugin, int requestedPage) {
        List<Player> eligiblePlayers = eligiblePlayers(claim);
        int maxPage = Math.max(0, (eligiblePlayers.size() - 1) / PLAYER_SLOT_COUNT);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int start = page * PLAYER_SLOT_COUNT;
        int end = Math.min(start + PLAYER_SLOT_COUNT, eligiblePlayers.size());

        List<UUID> visiblePlayers = new ArrayList<>();
        for (int i = start; i < end; i++) {
            visiblePlayers.add(eligiblePlayers.get(i).getUniqueId());
        }

        ClaimManageHolder holder = new ClaimManageHolder(
                claim,
                ClaimManageHolder.Screen.SELECT_PLAYER,
                visiblePlayers,
                page
        );
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Select Player", NamedTextColor.AQUA));
        holder.setInventory(inv);

        for (int i = 0; i < visiblePlayers.size(); i++) {
            Player target = Bukkit.getPlayer(visiblePlayers.get(i));
            if (target != null) {
                inv.setItem(i, playerHead(target));
            }
        }

        if (eligiblePlayers.isEmpty()) {
            inv.setItem(22, noPlayersItem());
        }
        if (page > 0) {
            inv.setItem(PREVIOUS_PAGE_SLOT, pageButton("Previous Page"));
        }
        inv.setItem(BACK_SLOT, backButton());
        if (page < maxPage) {
            inv.setItem(NEXT_PAGE_SLOT, pageButton("Next Page"));
        }

        player.openInventory(inv);
    }

    private static List<Player> eligiblePlayers(ClaimData claim) {
        List<Player> players = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID uuid = onlinePlayer.getUniqueId();
            if (uuid.equals(claim.getOwnerUuid())) continue;
            if (claim.getTrustedPlayers().contains(uuid)) continue;
            players.add(onlinePlayer);
        }
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return players;
    }

    private static ItemStack playerHead(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.displayName(txt(target.getName(), NamedTextColor.WHITE));
        meta.setOwningPlayer(target);
        meta.lore(List.of(
                Component.empty(),
                txt("Click to trust this player.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack noPlayersItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("No Players Available", NamedTextColor.GRAY));
        meta.lore(List.of(
                Component.empty(),
                txt("No online players can be added", NamedTextColor.RED),
                txt("to this claim right now.", NamedTextColor.RED)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pageButton(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt(name, NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Back", NamedTextColor.GRAY));
        meta.lore(List.of(
                Component.empty(),
                txt("Return to Claim Access.", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
}
