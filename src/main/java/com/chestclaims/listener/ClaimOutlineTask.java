package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimStorage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server-wide task that draws particle outlines around claims that have
 * outlineEnabled = true, visible to all nearby players.
 * Runs on a fixed tick interval; sends particles directly per-player.
 * Per-claim outline color is used (set via OutlineSettingsGUI).
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

        List<ClaimData> orphaned = new ArrayList<>();

        for (ClaimData claim : claimStorage.getClaims()) {
            if (!claim.isOutlineEnabled()) continue;

            String   worldName = claim.getWorld();
            World    w         = Bukkit.getWorld(worldName);
            Location anchor    = claim.getAnchor();

            // Safety: if the anchor block no longer exists, queue the claim for removal
            if (w != null && w.isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) {
                Material mat = w.getBlockAt(anchor).getType();
                if (mat != Material.CHEST && mat != Material.TRAPPED_CHEST && mat != Material.BARREL) {
                    orphaned.add(claim);
                    continue;
                }
            }

            Color  color   = parseColor(claim.getOutlineColor());
            double anchorX = anchor.getBlockX() + 0.5;
            double anchorZ = anchor.getBlockZ() + 0.5;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().getName().equals(worldName)) continue;
                double dx = player.getLocation().getX() - anchorX;
                double dz = player.getLocation().getZ() - anchorZ;
                if (dx * dx + dz * dz > viewDistSq) continue;

                drawClaimOutline(player, claim, color, size, maxPerEdge);
            }
        }

        // Remove any claims whose anchor block is gone — prevents ghost outlines
        for (ClaimData claim : orphaned) {
            Location anchor = claim.getAnchor();
            plugin.getLogger().warning("Claim " + claim.getId() + " owned by " + claim.getOwnerName()
                    + " has no anchor block at "
                    + anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ()
                    + " in '" + claim.getWorld() + "' — removing orphaned claim.");
            claimStorage.removeClaim(claim.getId());
            plugin.getHologramManager().onClaimDeleted(claim.getId());
            claimStorage.save();
        }
    }

    /**
     * Draws the outline for a claim.
     * Chunk claims: horizontal perimeter ring at the player's Y level, outer edges only.
     * Custom claims: standard 3D cuboid via OutlineTask.
     */
    private void drawClaimOutline(Player player, ClaimData claim, Color color, float size, int maxPerEdge) {
        if (claim.isChunkClaim() && !claim.getClaimedChunks().isEmpty()) {
            drawChunkPerimeter(player, claim, color, size, maxPerEdge);
        } else {
            new OutlineTask(player, claim::getPos1, claim::getPos2, color, size, maxPerEdge)
                    .drawOutline();
        }
    }

    /**
     * Draws the outer horizontal perimeter of a chunk claim at the player's current Y + 1.
     * Edges shared between two claimed chunks (internal edges) are skipped, so only the
     * visible outer border is drawn regardless of the claim's shape.
     */
    private void drawChunkPerimeter(Player player, ClaimData claim, Color color, float size, int maxPerEdge) {
        Set<String> chunks = claim.getClaimedChunks();
        World world = claim.getAnchor().getWorld();
        if (world == null) return;

        double y = player.getLocation().getBlockY() + 1.0;
        Particle.DustOptions dust = new Particle.DustOptions(color, size);

        for (String key : chunks) {
            String[] parts = key.split(",");
            int cx  = Integer.parseInt(parts[0]);
            int cz  = Integer.parseInt(parts[1]);
            double xMin = cx * 16,      xMax = cx * 16 + 16;
            double zMin = cz * 16,      zMax = cz * 16 + 16;

            // North edge — between this chunk and (cx, cz-1)
            if (!chunks.contains(cx + "," + (cz - 1)))
                OutlineTask.spawnLine(player, world, xMin, y, zMin, xMax, y, zMin, dust, maxPerEdge);
            // South edge — between this chunk and (cx, cz+1)
            if (!chunks.contains(cx + "," + (cz + 1)))
                OutlineTask.spawnLine(player, world, xMin, y, zMax, xMax, y, zMax, dust, maxPerEdge);
            // West edge — between this chunk and (cx-1, cz)
            if (!chunks.contains((cx - 1) + "," + cz))
                OutlineTask.spawnLine(player, world, xMin, y, zMin, xMin, y, zMax, dust, maxPerEdge);
            // East edge — between this chunk and (cx+1, cz)
            if (!chunks.contains((cx + 1) + "," + cz))
                OutlineTask.spawnLine(player, world, xMax, y, zMin, xMax, y, zMax, dust, maxPerEdge);
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
