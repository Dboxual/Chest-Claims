package com.chestclaims.teams;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Reflective bridge to booksaw's BetterTeams plugin.
 * Only instantiated when BetterTeams is confirmed present.
 * All errors are silently swallowed — a failed lookup means "not teammates."
 */
class TeamsIntegration {

    boolean areTeammates(UUID ownerUuid, UUID playerUuid) {
        try {
            Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team");
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
            Method getTeam = teamClass.getMethod("getTeam", OfflinePlayer.class);
            Object ownerTeam = getTeam.invoke(null, owner);
            if (ownerTeam == null) return false;

            Method getMembers = ownerTeam.getClass().getMethod("getMembers");
            Object members = getMembers.invoke(ownerTeam);
            if (members instanceof Map<?, ?> map) {
                return map.containsKey(playerUuid);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
