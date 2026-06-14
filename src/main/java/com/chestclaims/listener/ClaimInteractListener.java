package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.gui.ClaimManageGUI;
import com.chestclaims.teams.TeamsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * Handles right-click on confirmed claim anchor chests (outside of setup mode).
 * Owner → opens Claim Management GUI.
 * Trusted / team-member → allowed to open the chest normally.
 * Inactive claim → anyone may interact (no protection).
 * Other non-owner → shows action-bar message.
 */
public class ClaimInteractListener implements Listener {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;
    private final ClaimSetupListener setupListener;

    public ClaimInteractListener(ChestClaimsPlugin plugin,
                                 ClaimStorage claimStorage,
                                 ClaimSetupListener setupListener) {
        this.plugin        = plugin;
        this.claimStorage  = claimStorage;
        this.setupListener = setupListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!isAnchorBlock(event.getClickedBlock().getType())) return;

        Player player = event.getPlayer();

        // Setup listener owns all interactions while a session is active
        if (setupListener.hasSession(player.getUniqueId())) return;

        ClaimData claim = claimStorage.getClaimByAnchor(event.getClickedBlock().getLocation());
        if (claim == null) return;

        UUID playerUuid = player.getUniqueId();

        if (claim.getOwnerUuid().equals(playerUuid)) {
            event.setCancelled(true);
            ClaimManageGUI.openManage(player, claim, plugin);
            return;
        }

        // INACTIVE: no protection — let anyone interact
        if (claim.getState() == ClaimState.INACTIVE) return;

        // ACTIVE + trusted / team: let them open the chest normally
        if (hasNonOwnerAccess(claim, playerUuid)) return;

        // ACTIVE + untrusted non-owner: block and show message
        event.setCancelled(true);
        String msg = plugin.getConfig().getString("messages.not-owner",
                "&7This claim belongs to &e{owner}&7.");
        player.sendActionBar(parse(msg.replace("{owner}", claim.getOwnerName())));
    }

    private boolean hasNonOwnerAccess(ClaimData claim, UUID playerUuid) {
        if (claim.getTrustedPlayers().contains(playerUuid)) return true;
        if (claim.isTeamAccessEnabled() && TeamsHook.areTeammates(claim.getOwnerUuid(), playerUuid)) return true;
        return false;
    }

    private boolean isAnchorBlock(Material mat) {
        return mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL;
    }

    private Component parse(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
