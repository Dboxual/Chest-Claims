package com.chestclaims.listener;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Supplier;

/**
 * Draws a particle outline of a cuboid region for a single player.
 *
 * Accepts pos1/pos2 as Suppliers so the same task can serve:
 *   - active selection (supplies current session pos1/pos2, which may change)
 *   - confirmed claim borders (supplies fixed ClaimData pos1/pos2)
 * Future border-visibility system should instantiate this class with fixed
 * ClaimData position suppliers rather than session suppliers.
 */
class OutlineTask extends BukkitRunnable {

    private final Player player;
    private final Supplier<Location> pos1Supplier;
    private final Supplier<Location> pos2Supplier;
    private final Color color;
    private final float edgeSize;
    private final float cornerSize;
    private final int maxPerEdge;

    OutlineTask(Player player,
                Supplier<Location> pos1Supplier,
                Supplier<Location> pos2Supplier,
                Color color,
                float edgeSize,
                int maxPerEdge) {
        this.player       = player;
        this.pos1Supplier = pos1Supplier;
        this.pos2Supplier = pos2Supplier;
        this.color        = color;
        this.edgeSize     = edgeSize;
        this.cornerSize   = Math.min(4.0f, edgeSize * 2.0f);
        this.maxPerEdge   = maxPerEdge;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }
        drawOutline();
    }

    void drawOutline() {
        Location pos1 = pos1Supplier.get();
        Location pos2 = pos2Supplier.get();

        if (pos1 == null) return;

        if (pos2 == null) {
            // Only pos1 set: single marker above the block
            spawnDust(pos1.clone().add(0.5, 1.1, 0.5), edgeSize);
            return;
        }

        World w = pos1.getWorld();
        double xMin = Math.min(pos1.getBlockX(), pos2.getBlockX());
        double xMax = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1.0;
        double yMin = Math.min(pos1.getBlockY(), pos2.getBlockY());
        double yMax = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1.0;
        double zMin = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        double zMax = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1.0;

        // 8 corner markers — larger particles make corners immediately identifiable
        Particle.DustOptions cornerDust = new Particle.DustOptions(color, cornerSize);
        spawnAt(w, xMin, yMin, zMin, cornerDust);
        spawnAt(w, xMax, yMin, zMin, cornerDust);
        spawnAt(w, xMax, yMin, zMax, cornerDust);
        spawnAt(w, xMin, yMin, zMax, cornerDust);
        spawnAt(w, xMin, yMax, zMin, cornerDust);
        spawnAt(w, xMax, yMax, zMin, cornerDust);
        spawnAt(w, xMax, yMax, zMax, cornerDust);
        spawnAt(w, xMin, yMax, zMax, cornerDust);

        // 12 edges
        Particle.DustOptions edgeDust = new Particle.DustOptions(color, edgeSize);
        drawEdge(w, xMin, yMin, zMin, xMax, yMin, zMin, edgeDust);
        drawEdge(w, xMax, yMin, zMin, xMax, yMin, zMax, edgeDust);
        drawEdge(w, xMax, yMin, zMax, xMin, yMin, zMax, edgeDust);
        drawEdge(w, xMin, yMin, zMax, xMin, yMin, zMin, edgeDust);
        drawEdge(w, xMin, yMax, zMin, xMax, yMax, zMin, edgeDust);
        drawEdge(w, xMax, yMax, zMin, xMax, yMax, zMax, edgeDust);
        drawEdge(w, xMax, yMax, zMax, xMin, yMax, zMax, edgeDust);
        drawEdge(w, xMin, yMax, zMax, xMin, yMax, zMin, edgeDust);
        drawEdge(w, xMin, yMin, zMin, xMin, yMax, zMin, edgeDust);
        drawEdge(w, xMax, yMin, zMin, xMax, yMax, zMin, edgeDust);
        drawEdge(w, xMax, yMin, zMax, xMax, yMax, zMax, edgeDust);
        drawEdge(w, xMin, yMin, zMax, xMin, yMax, zMax, edgeDust);
    }

    private void drawEdge(World w, double x1, double y1, double z1,
                          double x2, double y2, double z2, Particle.DustOptions dust) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01) return;
        int steps = Math.min(maxPerEdge, Math.max(1, (int) Math.ceil(len * 2)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            player.spawnParticle(Particle.DUST,
                    new Location(w, x1 + dx * t, y1 + dy * t, z1 + dz * t),
                    1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private void spawnAt(World w, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, new Location(w, x, y, z),
                1, 0.0, 0.0, 0.0, 0.0, dust);
    }

    private void spawnDust(Location loc, float size) {
        player.spawnParticle(Particle.DUST, loc, 1, 0.0, 0.0, 0.0, 0.0,
                new Particle.DustOptions(color, size));
    }

    /**
     * Draws a single particle line segment for the given player.
     * Package-private — used by chunk-claim horizontal perimeter drawing.
     */
    static void spawnLine(Player player, World world,
                          double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          Particle.DustOptions dust, int maxPerEdge) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01) return;
        int steps = Math.min(maxPerEdge, Math.max(1, (int) Math.ceil(len * 2)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            player.spawnParticle(Particle.DUST,
                    new Location(world, x1 + dx * t, y1 + dy * t, z1 + dz * t),
                    1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }
}
