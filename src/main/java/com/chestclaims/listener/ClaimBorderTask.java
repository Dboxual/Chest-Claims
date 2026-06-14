package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

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
            new OutlineTask(player, claim::getPos1, claim::getPos2, color, size, maxPerEdge)
                    .drawOutline();
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
