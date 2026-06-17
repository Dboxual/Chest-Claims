package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
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

class ClaimBorderTask extends BukkitRunnable {

    private final Player player;
    private final ClaimStorage claimStorage;
    private final ClaimSetupListener setupListener;
    private final ChestClaimsPlugin plugin;

    ClaimBorderTask(Player player, ClaimStorage claimStorage,
                    ClaimSetupListener setupListener, ChestClaimsPlugin plugin) {
        this.player        = player;
        this.claimStorage  = claimStorage;
        this.setupListener = setupListener;
        this.plugin        = plugin;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }
        if (player.getInventory().getItemInMainHand().getType() != Material.SHEARS) {
            cancel();
            return;
        }
        if (setupListener.hasSession(player.getUniqueId())) return;

        int    radius     = plugin.getConfig().getInt("claim-border-preview.radius-blocks", 64);
        int    maxClaims  = plugin.getConfig().getInt("claim-border-preview.max-claims-shown", 8);
        int    maxPerEdge = plugin.getConfig().getInt("selection-outline.max-particles-per-edge", 15);
        float  size       = (float) plugin.getConfig().getDouble("selection-outline.particle-size", 1.0);
        Color  color      = parseColor(plugin.getConfig().getString(
                                "selection-outline.particle-color", "255,165,0"));

        String   worldName = player.getWorld().getName();
        Location pLoc      = player.getLocation();
        double   radiusSq  = (double) radius * radius;

        List<ClaimData> nearby = new ArrayList<>();
        for (ClaimData claim : claimStorage.getClaims()) {
            if (nearby.size() >= maxClaims) break;
            if (!claim.getWorld().equals(worldName)) continue;
            if (claim.getState() == ClaimState.INACTIVE) continue;
            Location anchor = claim.getAnchor();
            double dx = anchor.getX() - pLoc.getX();
            double dz = anchor.getZ() - pLoc.getZ();
            if (dx * dx + dz * dz > radiusSq) continue;
            nearby.add(claim);
        }

        for (ClaimData claim : nearby) {
            drawClaimOutline(player, claim, color, size, maxPerEdge);
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
     * Internal edges (shared between two claimed chunks) are skipped.
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

            if (!chunks.contains(cx + "," + (cz - 1)))
                OutlineTask.spawnLine(player, world, xMin, y, zMin, xMax, y, zMin, dust, maxPerEdge);
            if (!chunks.contains(cx + "," + (cz + 1)))
                OutlineTask.spawnLine(player, world, xMin, y, zMax, xMax, y, zMax, dust, maxPerEdge);
            if (!chunks.contains((cx - 1) + "," + cz))
                OutlineTask.spawnLine(player, world, xMin, y, zMin, xMin, y, zMax, dust, maxPerEdge);
            if (!chunks.contains((cx + 1) + "," + cz))
                OutlineTask.spawnLine(player, world, xMax, y, zMin, xMax, y, zMax, dust, maxPerEdge);
        }
    }

    private Color parseColor(String rgb) {
        try {
            String[] p = rgb.split(",");
            return Color.fromRGB(Integer.parseInt(p[0].trim()),
                    Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return Color.ORANGE;
        }
    }
}
