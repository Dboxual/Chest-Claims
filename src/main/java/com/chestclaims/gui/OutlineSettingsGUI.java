package com.chestclaims.gui;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.OutlineColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class OutlineSettingsGUI {

    // Layout (27 slots):
    //   Row 0: toggle at slot 4
    //   Row 1: 7 color choices at slots 10-16
    //   Row 2: back button at slot 22
    public static final int TOGGLE_SLOT      = 4;
    public static final int FIRST_COLOR_SLOT = 10; // slots 10–16 for GREEN..WHITE
    public static final int BACK_SLOT        = 22;

    // Colors in display order (must match slot offset 0–6 from FIRST_COLOR_SLOT)
    public static final OutlineColor[] COLOR_ORDER = OutlineColor.values();

    private OutlineSettingsGUI() {}

    public static void open(Player player, ClaimData claim, ChestClaimsPlugin plugin) {
        ClaimManageHolder holder = new ClaimManageHolder(claim, ClaimManageHolder.Screen.OUTLINE_SETTINGS);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Outline Settings", NamedTextColor.DARK_AQUA));
        holder.setInventory(inv);

        boolean on = claim.isOutlineEnabled();
        inv.setItem(TOGGLE_SLOT, toggleItem(on));

        String currentRgb = claim.getOutlineColor();
        for (int i = 0; i < COLOR_ORDER.length; i++) {
            OutlineColor color = COLOR_ORDER[i];
            boolean selected = color.rgb.equals(currentRgb);
            inv.setItem(FIRST_COLOR_SLOT + i, colorItem(color, selected));
        }

        inv.setItem(BACK_SLOT, backItem());

        player.openInventory(inv);
    }

    private static ItemStack toggleItem(boolean on) {
        ItemStack item = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(on
                ? txt("Outline: ON",  NamedTextColor.GREEN)
                : txt("Outline: OFF", NamedTextColor.GRAY));
        meta.lore(List.of(
                Component.empty(),
                txt("Show claim boundaries to nearby", NamedTextColor.GRAY),
                txt("players with particles.",         NamedTextColor.GRAY),
                Component.empty(),
                txt("Click to " + (on ? "disable" : "enable") + ".", NamedTextColor.YELLOW)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack colorItem(OutlineColor color, boolean selected) {
        ItemStack item = new ItemStack(color.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt(color.displayName, NamedTextColor.WHITE));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (selected) {
            lore.add(txt("(Selected)", NamedTextColor.GOLD));
        } else {
            lore.add(txt("Click to select.", NamedTextColor.YELLOW));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack backItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(txt("Back", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    private static Component txt(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }
}
