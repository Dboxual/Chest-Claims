package com.chestclaims.claim;

import org.bukkit.Location;
import java.util.UUID;

public class ClaimSession {

    public enum ClaimType { CUSTOM, CHUNK }

    private final UUID playerUuid;
    private final Location anchor;
    private Location pos1;
    private Location pos2;
    private ClaimType claimType = ClaimType.CUSTOM;

    public ClaimSession(UUID playerUuid, Location anchor) {
        this.playerUuid = playerUuid;
        this.anchor = anchor.clone();
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public Location getAnchor() { return anchor.clone(); }

    public ClaimType getClaimType() { return claimType; }
    public void setClaimType(ClaimType type) { this.claimType = type; }

    public Location getPos1() { return pos1 == null ? null : pos1.clone(); }
    public void setPos1(Location pos1) { this.pos1 = pos1.clone(); }

    public Location getPos2() { return pos2 == null ? null : pos2.clone(); }
    public void setPos2(Location pos2) { this.pos2 = pos2.clone(); }

    public boolean isComplete() { return pos1 != null && pos2 != null; }

    public int getWidth() {
        if (!isComplete()) return 0;
        return Math.abs(pos2.getBlockX() - pos1.getBlockX()) + 1;
    }

    public int getHeight() {
        if (!isComplete()) return 0;
        return Math.abs(pos2.getBlockY() - pos1.getBlockY()) + 1;
    }

    public int getDepth() {
        if (!isComplete()) return 0;
        return Math.abs(pos2.getBlockZ() - pos1.getBlockZ()) + 1;
    }

    public long getVolume() {
        if (!isComplete()) return 0;
        return (long) getWidth() * getHeight() * getDepth();
    }

    /** Returns true if the anchor chest block falls within the selected cuboid. */
    public boolean containsAnchor() {
        if (!isComplete()) return false;
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int bx = anchor.getBlockX(), by = anchor.getBlockY(), bz = anchor.getBlockZ();
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }
}
