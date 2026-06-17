package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.gui.ClaimManageGUI;
import com.chestclaims.teams.TeamsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

public class ClaimProtectionListener implements Listener {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;

    public ClaimProtectionListener(ChestClaimsPlugin plugin, ClaimStorage claimStorage) {
        this.plugin = plugin;
        this.claimStorage = claimStorage;
    }

    // ── Player block break / place ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        ClaimData anchorClaim = claimStorage.getClaimByAnchor(event.getBlock().getLocation());
        if (anchorClaim != null) {
            boolean isOwner = anchorClaim.getOwnerUuid().equals(playerUuid);
            boolean isAdmin = event.getPlayer().hasPermission("chestclaims.admin");
            if (isOwner || isAdmin) {
                event.setCancelled(true);
                ClaimManageGUI.openDeleteConfirm(event.getPlayer(), anchorClaim);
            } else {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(
                        Component.text("You can't break this claim chest.", NamedTextColor.RED));
            }
            return;
        }

        ClaimData claim = claimStorage.getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (claim.getState() == ClaimState.INACTIVE) return;
        if (hasAccess(claim, playerUuid)) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(
                Component.text("You cannot break blocks inside this claim.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ClaimData claim = claimStorage.getClaimAt(event.getBlock().getLocation());
        if (claim == null) return;
        if (claim.getState() == ClaimState.INACTIVE) return;
        if (hasAccess(claim, event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(
                Component.text("You cannot place blocks inside this claim.", NamedTextColor.RED));
    }

    // ── Container access ──────────────────────────────────────────────────
    // Protects chests, barrels, furnaces, hoppers, droppers, dispensers,
    // shulker boxes, brewing stands, crafters, and any other InventoryHolder block
    // that is NOT the claim anchor (anchor is handled by ClaimInteractListener).

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        // Only inventory-holding blocks
        if (!(event.getClickedBlock().getState() instanceof org.bukkit.block.Container)) return;

        // Anchor blocks are handled exclusively by ClaimInteractListener
        if (claimStorage.getClaimByAnchor(event.getClickedBlock().getLocation()) != null) return;

        ClaimData claim = claimStorage.getClaimAt(event.getClickedBlock().getLocation());
        if (claim == null || claim.getState() != ClaimState.ACTIVE) return;

        Player player = event.getPlayer();
        if (hasAccess(claim, player.getUniqueId())) return;
        if (player.hasPermission("chestclaims.admin")) return;

        event.setCancelled(true);
        player.sendActionBar(
                Component.text("You can't open containers in this claim.", NamedTextColor.RED));
    }

    // ── Bucket: prevent filling a bucket from liquid inside a claim ────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event.getBlock() == null) return;
        ClaimData claim = claimStorage.getClaimAt(event.getBlock().getLocation());
        if (claim == null || claim.getState() != ClaimState.ACTIVE) return;
        if (hasAccess(claim, event.getPlayer().getUniqueId())) return;
        if (event.getPlayer().hasPermission("chestclaims.admin")) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(
                Component.text("You can't take from blocks inside this claim.", NamedTextColor.RED));
    }

    // ── Explosion: protect all blocks inside ACTIVE claims ────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isExplosionProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isExplosionProtected(block.getLocation()));
    }

    private boolean isExplosionProtected(Location loc) {
        // Anchor blocks are always protected regardless of state
        if (claimStorage.getClaimByAnchor(loc) != null) return true;
        ClaimData claim = claimStorage.getClaimAt(loc);
        return claim != null && claim.getState() == ClaimState.ACTIVE;
    }

    // ── Fire: prevent fire from destroying blocks inside ACTIVE claims ─────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        // Anchor always protected
        if (claimStorage.getClaimByAnchor(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
            return;
        }
        ClaimData claim = claimStorage.getClaimAt(event.getBlock().getLocation());
        if (claim != null && claim.getState() == ClaimState.ACTIVE) {
            event.setCancelled(true);
        }
    }

    // Prevent fire from spreading INTO an ACTIVE claim
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Material newType = event.getNewState().getType();
        if (newType != Material.FIRE && newType != Material.SOUL_FIRE) return;
        ClaimData claim = claimStorage.getClaimAt(event.getBlock().getLocation());
        if (claim != null && claim.getState() == ClaimState.ACTIVE) {
            event.setCancelled(true);
        }
    }

    // Block player-caused ignition and lava-ignition inside ACTIVE claims
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        ClaimData claim = claimStorage.getClaimAt(event.getBlock().getLocation());
        if (claim == null || claim.getState() != ClaimState.ACTIVE) return;

        Player player = event.getPlayer();
        if (player != null) {
            if (hasAccess(claim, player.getUniqueId())) return;
            if (player.hasPermission("chestclaims.admin")) return;
            event.setCancelled(true);
            player.sendActionBar(
                    Component.text("You cannot start fires inside this claim.", NamedTextColor.RED));
        } else if (event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            // Lava outside or adjacent to a claim must not ignite blocks inside it
            event.setCancelled(true);
        }
        // SPREAD handled by BlockSpreadEvent; LIGHTNING: intentionally not blocked
    }

    // ── Liquid grief: block water/lava from flowing into ACTIVE claims ──────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!event.getBlock().isLiquid()) return;

        ClaimData destClaim = claimStorage.getClaimAt(event.getToBlock().getLocation());
        if (destClaim == null || destClaim.getState() != ClaimState.ACTIVE) return;

        // Allow liquid that originates inside the same claim (owner-placed liquid flowing freely)
        ClaimData srcClaim = claimStorage.getClaimAt(event.getBlock().getLocation());
        if (srcClaim != null && srcClaim.getId().equals(destClaim.getId())) return;

        event.setCancelled(true);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private boolean hasAccess(ClaimData claim, UUID playerUuid) {
        if (claim.getOwnerUuid().equals(playerUuid)) return true;
        if (claim.getTrustedPlayers().contains(playerUuid)) return true;
        if (claim.isTeamAccessEnabled() && TeamsHook.areTeammates(claim.getOwnerUuid(), playerUuid)) return true;
        return false;
    }
}
