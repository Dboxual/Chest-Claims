package com.chestclaims.bops;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Soft-dependency bridge to the Bops currency plugin.
 * When Bops is absent, all cost checks pass and no charges are applied.
 */
public final class BopsHook {

    private static BopsIntegration integration;

    public static void init(Plugin plugin) {
        Plugin bops = Bukkit.getPluginManager().getPlugin("Bops");
        if (bops != null && bops.isEnabled()) {
            try {
                integration = new BopsIntegration();
                plugin.getLogger().info("Bops integration active.");
            } catch (NoClassDefFoundError e) {
                plugin.getLogger().warning("Bops found but integration failed: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("Bops not found — claims are free (no charging).");
        }
    }

    public static boolean isAvailable() { return integration != null; }

    /** Returns true if the player has sufficient Bops, or if Bops is unavailable. */
    public static boolean has(UUID uuid, double amount) {
        return integration == null || integration.has(uuid, amount);
    }

    /** Withdraws Bops. Returns true on success, or true if Bops is unavailable (no-op). */
    public static boolean withdraw(UUID uuid, double amount) {
        return integration == null || integration.withdraw(uuid, amount);
    }

    public static double getBalance(UUID uuid) {
        return integration == null ? Double.MAX_VALUE : integration.getBalance(uuid);
    }
}
