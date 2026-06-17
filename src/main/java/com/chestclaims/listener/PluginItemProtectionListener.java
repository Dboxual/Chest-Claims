package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class PluginItemProtectionListener implements Listener {

    private final NamespacedKey pluginItemKey;

    public PluginItemProtectionListener(ChestClaimsPlugin plugin) {
        this.pluginItemKey = new NamespacedKey(plugin, "plugin-item");
    }

    // ── inventory click ────────────────────────────────────────────────────
    // Blocks plugin items from being placed in any container, machine, or crafting grid.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isPluginItem(event.getCursor()) && !isPluginItem(event.getCurrentItem())) return;

        InventoryType topType = event.getView().getTopInventory().getType();

        // Pure player inventory screen — allow free rearranging
        if (topType == InventoryType.PLAYER) return;

        // Survival 2×2 crafting screen — only block clicks into the crafting grid or shift-clicks
        if (topType == InventoryType.CRAFTING) {
            boolean clickedTop = event.getClickedInventory() != null
                    && event.getClickedInventory() == event.getView().getTopInventory();
            if (clickedTop || event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        // All other inventory types (containers, machines, crafting tables, etc.) — block
        event.setCancelled(true);
    }

    // ── inventory drag ─────────────────────────────────────────────────────
    // Prevents dragging a plugin item into any inventory that isn't the player's own.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isPluginItem(event.getOldCursor())) return;
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.PLAYER) {
            event.setCancelled(true);
        }
    }

    // ── crafting recipe result ─────────────────────────────────────────────
    // Safety net: clears the crafting result if any ingredient is a plugin item.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isPluginItem(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    // ── automated item transport ───────────────────────────────────────────
    // Blocks hoppers, dispensers, and droppers from moving plugin items.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isPluginItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ── helper ─────────────────────────────────────────────────────────────

    private boolean isPluginItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(pluginItemKey, PersistentDataType.BYTE);
    }
}
