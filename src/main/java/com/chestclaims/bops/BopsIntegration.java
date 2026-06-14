package com.chestclaims.bops;

import com.bops.api.BopsAPI;

import java.util.UUID;

/**
 * Only instantiated when the Bops plugin is confirmed present.
 * Keeping Bops types isolated here prevents NoClassDefFoundError when Bops is absent.
 */
class BopsIntegration {

    boolean has(UUID uuid, double amount) { return BopsAPI.has(uuid, amount); }
    boolean withdraw(UUID uuid, double amount) { return BopsAPI.withdraw(uuid, amount); }
    double getBalance(UUID uuid) { return BopsAPI.getBalance(uuid); }
}
