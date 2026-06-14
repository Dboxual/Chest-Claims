package com.chestclaims.gui;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimSession;
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

public final class ClaimConfirmGUI {

    public static final int CONFIRM_SLOT = 10;
    public static final int INFO_SLOT    = 13;
    public static final int CANCEL_SLOT  = 16;

    private ClaimConfirmGUI() {}

    public static void open(Player player, ClaimSession session, ChestClaimsPlugin plugin) {
        ClaimGuiHolder holder = new ClaimGuiHolder(player.getUniqueId(), session);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Confirm Claim", NamedTextColor.DARK_GREEN));
        holder.setInventory(inv);

        boolean upkeepEnabled = plugin.getConfig().getBoolean("upkeep.enabled", true);
        String cycleName      = plugin.getConfig().getString("upkeep.cycle-name", "Day");
        boolean isChunk       = session.getClaimType() == ClaimSession.ClaimType.CHUNK;

        double cost, upkeepCost;
        if (isChunk) {
            cost       = plugin.getConfig().getDouble("chunk-claims.claim-cost-bops", 15.0);
            upkeepCost = plugin.getConfig().getDouble("chunk-claims.upkeep-bops-per-day", 2.0);
        } else {
            double pricePerBlock = plugin.getConfig().getDouble("claims.price-per-block", 0.02);
            double dailyCostPct  = plugin.getConfig().getDouble("upkeep.daily-cost-percent-of-purchase", 2.0);
            cost       = session.getVolume() * pricePerBlock;
            upkeepCost = cost * (dailyCostPct / 100.0);
        }

        NumberFormat fmt  = NumberFormat.getNumberInstance(Locale.US);
        String costFmt    = fmt.format(cost) + " Bops";
        String upkeepFmt  = fmt.format(upkeepCost) + " Bops / " + cycleName;

        inv.setItem(CONFIRM_SLOT, confirmItem(costFmt, upkeepFmt, upkeepEnabled));
        inv.setItem(INFO_SLOT,    infoItem(session, costFmt, upkeepFmt, upkeepEnabled));
        inv.setItem(CANCEL_SLOT,  cancelItem());

        player.openInventory(inv);
    }

    private static ItemStack confirmItem(String cost, String upkeepPerCycle, boolean upkeepEnabled) {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Confirm Purchase", NamedTextColor.GREEN));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(row("Claim Price:", cost,           NamedTextColor.YELLOW));
        if (upkeepEnabled) {
            lore.add(row("Upkeep:",     upkeepPerCycle, NamedTextColor.GOLD));
        }
        lore.add(Component.empty());
        lore.add(txt("Click to purchase this claim.", NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack infoItem(ClaimSession session, String cost, String upkeepPerCycle, boolean upkeepEnabled) {
        boolean isChunk = session.getClaimType() == ClaimSession.ClaimType.CHUNK;
        ItemStack item = new ItemStack(isChunk ? Material.BLUE_CONCRETE : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(isChunk
                ? txt("Chunk Claim Summary", NamedTextColor.AQUA)
                : txt("Claim Summary",       NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (isChunk) {
            lore.add(row("Type:", "Chunk Claim (16 × 16)", NamedTextColor.AQUA));
        }
        lore.add(row("Claim Price:", cost,           NamedTextColor.YELLOW));
        if (upkeepEnabled) {
            lore.add(row("Upkeep:",     upkeepPerCycle, NamedTextColor.GOLD));
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
                txt("Click to cancel claim setup.", NamedTextColor.GRAY)
        ));
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
