package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimStorage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClaimBorderListener implements Listener {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;
    private final ClaimSetupListener setupListener;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public ClaimBorderListener(ChestClaimsPlugin plugin, ClaimStorage claimStorage,
                               ClaimSetupListener setupListener) {
        this.plugin        = plugin;
        this.claimStorage  = claimStorage;
        this.setupListener = setupListener;
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player    = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        boolean newIsShears = newItem != null && newItem.getType() == Material.SHEARS;
        boolean oldIsShears = player.getInventory().getItemInMainHand().getType() == Material.SHEARS;

        if (newIsShears && !oldIsShears) {
            startPreview(player);
        } else if (!newIsShears) {
            stopPreview(player.getUniqueId());
        }
    }

    // ignoreCancelled = true so Sneak+F cancel (event cancelled by ClaimSetupListener)
    // does not stop the preview — shears stay in main hand after a cancelled swap.
    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        ItemStack main = event.getMainHandItem();
        if (main != null && main.getType() == Material.SHEARS) {
            stopPreview(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() == Material.SHEARS) {
            startPreview(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopPreview(event.getPlayer().getUniqueId());
    }

    private void startPreview(Player player) {
        if (!plugin.getConfig().getBoolean("claim-border-preview.enabled", true)) return;
        UUID uuid = player.getUniqueId();
        stopPreview(uuid);
        int updateTicks = plugin.getConfig().getInt("claim-border-preview.update-ticks", 20);
        BukkitTask task = new ClaimBorderTask(player, claimStorage, setupListener, plugin)
                .runTaskTimer(plugin, 2L, updateTicks);
        tasks.put(uuid, task);
    }

    private void stopPreview(UUID uuid) {
        BukkitTask task = tasks.remove(uuid);
        if (task != null) task.cancel();
    }
}
