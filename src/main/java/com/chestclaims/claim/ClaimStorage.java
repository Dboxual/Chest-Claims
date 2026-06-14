package com.chestclaims.claim;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ClaimStorage {

    private final Plugin plugin;
    private final File file;
    private final List<ClaimData> claims = new ArrayList<>();

    public ClaimStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) return;

        double dailyCostPercent = plugin.getConfig().getDouble("upkeep.daily-cost-percent-of-purchase", 2.0);

        for (String key : section.getKeys(false)) {
            try {
                ConfigurationSection c = section.getConfigurationSection(key);
                if (c == null) continue;

                UUID id = UUID.fromString(key);
                UUID ownerUuid = UUID.fromString(c.getString("owner-uuid"));
                String ownerName = c.getString("owner-name", "Unknown");
                String world = c.getString("world");

                Location anchor = readLoc(c.getConfigurationSection("anchor"), world);
                Location pos1 = readLoc(c.getConfigurationSection("pos1"), world);
                Location pos2 = readLoc(c.getConfigurationSection("pos2"), world);

                long createdAt = c.getLong("created-at");
                long volume = c.getLong("volume");
                double cost = c.getDouble("cost");

                ClaimData data = new ClaimData(id, ownerUuid, ownerName, world, anchor, pos1, pos2, createdAt, volume, cost);

                // Upkeep fields — default to ACTIVE on first load (migration)
                String stateStr = c.getString("state", "ACTIVE");
                ClaimState state;
                try {
                    state = ClaimState.valueOf(stateStr);
                } catch (IllegalArgumentException e) {
                    // "GRACE" was removed in v1.0.13; those claims had expired upkeep → INACTIVE
                    state = "GRACE".equalsIgnoreCase(stateStr) ? ClaimState.INACTIVE : ClaimState.ACTIVE;
                }
                data.setState(state);
                data.setUpkeepPaidUntil(c.getLong("upkeep-paid-until", 0L));

                // daily-upkeep-cost stored per-claim; compute from config if missing (migration)
                double storedDailyCost = c.getDouble("daily-upkeep-cost", -1.0);
                data.setDailyUpkeepCost(storedDailyCost >= 0
                        ? storedDailyCost
                        : cost * (dailyCostPercent / 100.0));
                data.setOutlineEnabled(c.getBoolean("outline-enabled", false));
                data.setChunkClaim(c.getBoolean("chunk-claim", false));

                // Access management — default: empty trusted list, team access off
                List<String> rawTrusted = c.getStringList("trusted-players");
                List<UUID> trustedPlayers = new ArrayList<>();
                for (String s : rawTrusted) {
                    try { trustedPlayers.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                }
                data.setTrustedPlayers(trustedPlayers);
                data.setTeamAccessEnabled(c.getBoolean("team-access-enabled", false));

                claims.add(data);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load claim " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + claims.size() + " claim(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ClaimData c : claims) {
            String path = "claims." + c.getId().toString();
            yaml.set(path + ".owner-uuid", c.getOwnerUuid().toString());
            yaml.set(path + ".owner-name", c.getOwnerName());
            yaml.set(path + ".world", c.getWorld());
            writeLoc(yaml, path + ".anchor", c.getAnchor());
            writeLoc(yaml, path + ".pos1", c.getPos1());
            writeLoc(yaml, path + ".pos2", c.getPos2());
            yaml.set(path + ".created-at", c.getCreatedAt());
            yaml.set(path + ".volume", c.getVolume());
            yaml.set(path + ".cost", c.getCost());
            yaml.set(path + ".state", c.getState().name());
            yaml.set(path + ".upkeep-paid-until", c.getUpkeepPaidUntil());
            yaml.set(path + ".daily-upkeep-cost", c.getDailyUpkeepCost());
            yaml.set(path + ".outline-enabled", c.isOutlineEnabled());
            yaml.set(path + ".chunk-claim", c.isChunkClaim());
            List<String> trustedUuids = new ArrayList<>();
            for (UUID uuid : c.getTrustedPlayers()) trustedUuids.add(uuid.toString());
            yaml.set(path + ".trusted-players", trustedUuids);
            yaml.set(path + ".team-access-enabled", c.isTeamAccessEnabled());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save claims: " + e.getMessage());
        }
    }

    public void addClaim(ClaimData claim) {
        claims.add(claim);
    }

    public List<ClaimData> getClaims() {
        return Collections.unmodifiableList(claims);
    }

    public ClaimData getClaimAt(Location loc) {
        for (ClaimData c : claims) {
            if (c.contains(loc)) return c;
        }
        return null;
    }

    public ClaimData getClaimByAnchor(Location loc) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "";
        for (ClaimData c : claims) {
            Location anchor = c.getAnchor();
            if (anchor.getBlockX() == loc.getBlockX()
                    && anchor.getBlockY() == loc.getBlockY()
                    && anchor.getBlockZ() == loc.getBlockZ()
                    && c.getWorld().equals(worldName)) {
                return c;
            }
        }
        return null;
    }

    public boolean overlaps(Location pos1, Location pos2, String world) {
        int minX1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX1 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY1 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ1 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        for (ClaimData c : claims) {
            if (!c.getWorld().equals(world)) continue;
            int minX2 = Math.min(c.getPos1().getBlockX(), c.getPos2().getBlockX());
            int maxX2 = Math.max(c.getPos1().getBlockX(), c.getPos2().getBlockX());
            int minY2 = Math.min(c.getPos1().getBlockY(), c.getPos2().getBlockY());
            int maxY2 = Math.max(c.getPos1().getBlockY(), c.getPos2().getBlockY());
            int minZ2 = Math.min(c.getPos1().getBlockZ(), c.getPos2().getBlockZ());
            int maxZ2 = Math.max(c.getPos1().getBlockZ(), c.getPos2().getBlockZ());

            if (minX1 <= maxX2 && maxX1 >= minX2
                    && minY1 <= maxY2 && maxY1 >= minY2
                    && minZ1 <= maxZ2 && maxZ1 >= minZ2) {
                return true;
            }
        }
        return false;
    }

    public void removeClaim(UUID id) {
        claims.removeIf(c -> c.getId().equals(id));
    }

    private Location readLoc(ConfigurationSection s, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (s == null) return new Location(w, 0, 64, 0);
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
    }

    private void writeLoc(YamlConfiguration yaml, String path, Location loc) {
        yaml.set(path + ".x", loc.getBlockX());
        yaml.set(path + ".y", loc.getBlockY());
        yaml.set(path + ".z", loc.getBlockZ());
    }
}
