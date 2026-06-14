package com.chestclaims.teams;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class TeamsHook {

    private static TeamsIntegration integration;

    public static void init(Plugin plugin) {
        Plugin teams = Bukkit.getPluginManager().getPlugin("BetterTeams");
        if (teams != null && teams.isEnabled()) {
            try {
                integration = new TeamsIntegration();
                plugin.getLogger().info("BetterTeams integration active.");
            } catch (Throwable e) {
                plugin.getLogger().warning("BetterTeams found but integration failed: " + e.getMessage());
            }
        }
    }

    public static boolean isAvailable() { return integration != null; }

    public static boolean areTeammates(UUID ownerUuid, UUID playerUuid) {
        return integration != null && integration.areTeammates(ownerUuid, playerUuid);
    }
}
