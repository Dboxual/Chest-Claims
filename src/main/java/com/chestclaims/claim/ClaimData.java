package com.chestclaims.claim;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ClaimData {

    private final UUID id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String world;
    private final Location anchor;
    private Location pos1;
    private Location pos2;
    private final long createdAt;
    private final long volume;
    private final double cost;

    // Upkeep fields — mutable so the upkeep manager can update them in-place
    private ClaimState state;
    private long upkeepPaidUntil;   // epoch ms; 0 = not yet set
    private double dailyUpkeepCost; // precomputed at claim creation / migration

    // Outline settings — per-claim color and enabled toggle
    private boolean outlineEnabled = false;
    private String outlineColor = OutlineColor.AQUA.rgb; // default cyan

    private boolean chunkClaim = false;

    // Chunk claims: set of "chunkX,chunkZ" keys for each claimed chunk
    private LinkedHashSet<String> claimedChunks = new LinkedHashSet<>();

    // Access management — persisted per-claim
    private List<UUID> trustedPlayers = new ArrayList<>();
    private boolean teamAccessEnabled = false;

    public ClaimData(UUID id, UUID ownerUuid, String ownerName, String world,
                     Location anchor, Location pos1, Location pos2,
                     long createdAt, long volume, double cost) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.world = world;
        this.anchor = anchor.clone();
        this.pos1 = pos1.clone();
        this.pos2 = pos2.clone();
        this.createdAt = createdAt;
        this.volume = volume;
        this.cost = cost;
        this.state = ClaimState.ACTIVE;
        this.upkeepPaidUntil = 0L;
        this.dailyUpkeepCost = 0.0;
    }

    public UUID getId() { return id; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public String getWorld() { return world; }
    public Location getAnchor() { return anchor.clone(); }
    public Location getPos1() { return pos1.clone(); }
    public Location getPos2() { return pos2.clone(); }
    public void setPos1(Location pos1) { this.pos1 = pos1.clone(); }
    public void setPos2(Location pos2) { this.pos2 = pos2.clone(); }
    public long getCreatedAt() { return createdAt; }
    public long getVolume() { return volume; }
    public double getCost() { return cost; }

    public ClaimState getState() { return state; }
    public void setState(ClaimState state) { this.state = state; }

    public long getUpkeepPaidUntil() { return upkeepPaidUntil; }
    public void setUpkeepPaidUntil(long upkeepPaidUntil) { this.upkeepPaidUntil = upkeepPaidUntil; }

    public double getDailyUpkeepCost() { return dailyUpkeepCost; }
    public void setDailyUpkeepCost(double dailyUpkeepCost) { this.dailyUpkeepCost = dailyUpkeepCost; }

    public boolean isOutlineEnabled() { return outlineEnabled; }
    public void setOutlineEnabled(boolean outlineEnabled) { this.outlineEnabled = outlineEnabled; }

    public String getOutlineColor() { return outlineColor; }
    public void setOutlineColor(String outlineColor) { this.outlineColor = outlineColor; }

    public boolean isChunkClaim() { return chunkClaim; }
    public void setChunkClaim(boolean chunkClaim) { this.chunkClaim = chunkClaim; }

    public Set<String> getClaimedChunks() { return claimedChunks; }
    public void setClaimedChunks(LinkedHashSet<String> claimedChunks) {
        this.claimedChunks = claimedChunks;
    }
    public void addClaimedChunk(String key) { claimedChunks.add(key); }

    public List<UUID> getTrustedPlayers() { return trustedPlayers; }
    public void setTrustedPlayers(List<UUID> trustedPlayers) { this.trustedPlayers = trustedPlayers; }

    public boolean isTeamAccessEnabled() { return teamAccessEnabled; }
    public void setTeamAccessEnabled(boolean teamAccessEnabled) { this.teamAccessEnabled = teamAccessEnabled; }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;

        if (chunkClaim && !claimedChunks.isEmpty()) {
            String key = (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
            if (!claimedChunks.contains(key)) return false;
            int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
            int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
            int by = loc.getBlockY();
            return by >= minY && by <= maxY;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }
}
