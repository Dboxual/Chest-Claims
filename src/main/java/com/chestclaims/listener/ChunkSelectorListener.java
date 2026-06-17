package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.gui.ChunkAddConfirmGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class ChunkSelectorListener implements Listener {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;
    private final NamespacedKey selectorKey;
    private final NamespacedKey pluginItemKey;

    public ChunkSelectorListener(ChestClaimsPlugin plugin, ClaimStorage claimStorage) {
        this.plugin        = plugin;
        this.claimStorage  = claimStorage;
        this.selectorKey   = new NamespacedKey(plugin, "chunk-selector");
        this.pluginItemKey = new NamespacedKey(plugin, "plugin-item");
    }

    // ── public API ─────────────────────────────────────────────────────────

    /** Gives the player a Chunk Selector item tagged to the specified claim. */
    public void startSelection(Player player, ClaimData claim) {
        removeSelectorItem(player); // clear any existing selector first

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-selector-no-space",
                    "&cYour inventory is full. Free up a slot to receive the Chunk Selector.")));
            return;
        }

        ItemStack selector = buildSelectorItem(claim.getId());
        player.getInventory().addItem(selector);

        player.sendMessage(parse(plugin.getConfig().getString(
                "messages.chunk-selector-given",
                "&aChunk Selector given! Right-click a block inside the chunk you want to add.")));
    }

    /** Removes all Chunk Selector items from the player's inventory. */
    public void removeSelectorItem(Player player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isSelectorItem(item)) {
                inv.setItem(i, null);
            }
        }
    }

    // ── event handlers ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!isSelectorItem(held)) return;

        event.setCancelled(true); // prevent normal block interaction

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        UUID claimId = getClaimIdFromSelector(held);
        if (claimId == null) {
            removeSelectorItem(player);
            return;
        }

        ClaimData claim = claimStorage.getClaims().stream()
                .filter(c -> c.getId().equals(claimId))
                .findFirst().orElse(null);

        if (claim == null || !claim.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(parse("&cClaim not found. Selection cancelled."));
            removeSelectorItem(player);
            return;
        }

        int targetCx = clicked.getX() >> 4;
        int targetCz = clicked.getZ() >> 4;
        String targetKey = targetCx + "," + targetCz;

        // Validate world
        if (!clicked.getWorld().getName().equals(claim.getWorld())) {
            player.sendMessage(parse("&cYou must select a chunk in the same world as your claim."));
            return;
        }

        // Already in claim
        if (claim.getClaimedChunks().contains(targetKey)) {
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-already-in-claim",
                    "&cThat chunk is already part of your claim.")));
            return;
        }

        // Adjacency
        if (!isAdjacentToClaimChunks(claim, targetCx, targetCz)) {
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-not-adjacent",
                    "&cThat chunk is not connected to your existing claim.")));
            return;
        }

        // Not claimed by others
        if (claimStorage.isChunkClaimed(targetCx, targetCz, claim.getWorld(), claimId)) {
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-already-claimed",
                    "&cThat chunk is already claimed by someone else.")));
            return;
        }

        // All checks passed — open confirmation GUI
        final int cx = targetCx;
        final int cz = targetCz;
        Bukkit.getScheduler().runTask(plugin,
                () -> ChunkAddConfirmGUI.open(player, claim, cx, cz, plugin));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeSelectorItem(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isSelectorItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private ItemStack buildSelectorItem(UUID claimId) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Chunk Selector", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.empty(),
                Component.text("Right-click a block inside the chunk", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("you want to add to your claim.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Cannot be dropped.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(selectorKey, PersistentDataType.STRING, claimId.toString());
        meta.getPersistentDataContainer().set(pluginItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSelectorItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(selectorKey, PersistentDataType.STRING);
    }

    private UUID getClaimIdFromSelector(ItemStack item) {
        try {
            String raw = item.getItemMeta().getPersistentDataContainer()
                    .get(selectorKey, PersistentDataType.STRING);
            return raw != null ? UUID.fromString(raw) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAdjacentToClaimChunks(ClaimData claim, int newCx, int newCz) {
        for (String key : claim.getClaimedChunks()) {
            String[] parts = key.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            if (Math.abs(cx - newCx) + Math.abs(cz - newCz) == 1) return true;
        }
        return false;
    }

    private Component parse(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
