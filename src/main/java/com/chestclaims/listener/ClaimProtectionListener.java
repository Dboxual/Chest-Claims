package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.gui.ClaimManageGUI;
import com.chestclaims.teams.TeamsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.UUID;

public class ClaimProtectionListener implements Listener {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;

    public ClaimProtectionListener(ChestClaimsPlugin plugin, ClaimStorage claimStorage) {
        this.plugin = plugin;
        this.claimStorage = claimStorage;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();

        ClaimData anchorClaim = claimStorage.getClaimByAnchor(event.getBlock().getLocation());
        if (anchorClaim != null) {
            boolean isOwner = anchorClaim.getOwnerUuid().equals(playerUuid);
            if (isOwner) {
                // Owner always gets the delete GUI so they can manage/remove the claim
                event.setCancelled(true);
                ClaimManageGUI.openDeleteConfirm(event.getPlayer(), anchorClaim);
            } else if (anchorClaim.getState() != ClaimState.INACTIVE) {
                // ACTIVE: nobody but the owner may break the anchor
                event.setCancelled(true);
                if (hasAccess(anchorClaim, playerUuid)) {
                    event.getPlayer().sendActionBar(
                            Component.text("You cannot break this claim's anchor chest.", NamedTextColor.RED));
                } else {
                    event.getPlayer().sendActionBar(
                            Component.text("You do not own this claim.", NamedTextColor.RED));
                }
            }
            // INACTIVE + non-owner: fall through — anchor breaks normally
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

    private boolean hasAccess(ClaimData claim, UUID playerUuid) {
        if (claim.getOwnerUuid().equals(playerUuid)) return true;
        if (claim.getTrustedPlayers().contains(playerUuid)) return true;
        if (claim.isTeamAccessEnabled() && TeamsHook.areTeammates(claim.getOwnerUuid(), playerUuid)) return true;
        return false;
    }
}
