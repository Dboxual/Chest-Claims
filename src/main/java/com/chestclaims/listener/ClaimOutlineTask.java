package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimStorage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Server-wide task that draws particle outlines around claims that have
 * outlineEnabled = true, visible to all nearby players.
 * Runs on a fixed tick interval; send particles directly per-player.
 */
public class ClaimOutlineTask extends BukkitRunnable {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;

    public ClaimOutlineTask(ChestClaimsPlugin plugin, ClaimStorage claimStorage) {
        this.plugin       = plugin;
        this.claimStorage = claimStorage;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("claim-outline.enabled", true)) return;

        double viewDist   = plugin.getConfig().getDouble("claim-outline.view-distance-blocks", 32.0);
        double viewDistSq = viewDist * viewDist;
        int    maxPerEdge = plugin.getConfig().getInt("selection-outline.max-particles-per-edge", 15);
        float  size       = (float) plugin.getConfig().getDouble("selection-outline.particle-size", 1.0);
        Color  color      = parseColor(plugin.getConfig().getString("claim-outline.particle-color", "0,200,255"));

        for (ClaimData claim : claimStorage.getClaims()) {
            if (!claim.isOutlineEnabled()) continue;

            String   worldName = claim.getWorld();
            Location anchor    = claim.getAnchor();
            double   anchorX   = anchor.getBlockX() + 0.5;
            double   anchorZ   = anchor.getBlockZ() + 0.5;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().getName().equals(worldName)) continue;
                double dx = player.getLocation().getX() - anchorX;
                double dz = player.getLocation().getZ() - anchorZ;
                if (dx * dx + dz * dz > viewDistSq) continue;

                new OutlineTask(player, claim::getPos1, claim::getPos2, color, size, maxPerEdge)
                        .drawOutline();
            }
        }
    }

    private Color parseColor(String rgb) {
        try {
            String[] p = rgb.split(",");
            return Color.fromRGB(
                    Integer.parseInt(p[0].trim()),
                    Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return Color.AQUA;
        }
    }
}
