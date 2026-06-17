package com.chestclaims;

import com.chestclaims.bops.BopsHook;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.teams.TeamsHook;
import com.chestclaims.command.ClaimsCommand;
import com.chestclaims.listener.ClaimBorderListener;
import com.chestclaims.listener.ClaimOutlineTask;
import com.chestclaims.listener.ClaimProtectionListener;
import com.chestclaims.listener.ClaimSetupListener;
import com.chestclaims.listener.ClaimInteractListener;
import com.chestclaims.listener.ChunkSelectorListener;
import com.chestclaims.listener.GUIListener;
import com.chestclaims.listener.PluginItemProtectionListener;
import com.chestclaims.upkeep.UpkeepManager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

public class ChestClaimsPlugin extends JavaPlugin {

    private ClaimStorage claimStorage;
    private ClaimSetupListener setupListener;
    private UpkeepManager upkeepManager;
    private ClaimHologramManager hologramManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        claimStorage = new ClaimStorage(this);
        claimStorage.load();

        // Scan for claims whose anchor block no longer exists and remove them before
        // the hologram manager spawns holograms or the outline task starts drawing.
        java.util.List<ClaimData> orphaned = claimStorage.removeClaimsWithMissingAnchors();
        for (ClaimData c : orphaned) {
            getLogger().warning("Startup: removed orphaned claim " + c.getId()
                    + " owned by " + c.getOwnerName()
                    + " — anchor block missing at "
                    + c.getAnchor().getBlockX() + "," + c.getAnchor().getBlockY() + "," + c.getAnchor().getBlockZ()
                    + " in world '" + c.getWorld() + "'.");
        }
        if (!orphaned.isEmpty()) {
            getLogger().info("Startup: removed " + orphaned.size() + " invalid claim(s) with missing anchor blocks.");
        }

        upkeepManager = new UpkeepManager(this, claimStorage);

        hologramManager = new ClaimHologramManager(this, claimStorage);
        hologramManager.init();

        BopsHook.init(this);
        TeamsHook.init(this);

        setupListener = new ClaimSetupListener(this, claimStorage);
        ChunkSelectorListener chunkSelectorListener = new ChunkSelectorListener(this, claimStorage);

        getServer().getPluginManager().registerEvents(setupListener, this);
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this, claimStorage), this);
        getServer().getPluginManager().registerEvents(
                new GUIListener(this, claimStorage, setupListener, chunkSelectorListener), this);
        getServer().getPluginManager().registerEvents(
                new ClaimInteractListener(this, claimStorage, setupListener), this);
        getServer().getPluginManager().registerEvents(
                new ClaimBorderListener(this, claimStorage, setupListener), this);
        getServer().getPluginManager().registerEvents(chunkSelectorListener, this);
        getServer().getPluginManager().registerEvents(new PluginItemProtectionListener(this), this);

        // Cancel setup sessions whose player stops holding shears (fallback for inventory manipulation)
        new BukkitRunnable() {
            @Override
            public void run() { setupListener.tickCheck(); }
        }.runTaskTimer(this, 10L, 5L);

        // Server-wide claim outline (owner-toggled, visible to nearby players)
        int outlineTicks = getConfig().getInt("claim-outline.update-ticks", 20);
        new ClaimOutlineTask(this, claimStorage).runTaskTimer(this, 20L, outlineTicks);

        // Schedule automatic midnight upkeep cycle
        upkeepManager.init();

        ClaimsCommand cmd = new ClaimsCommand(this, setupListener);
        getCommand("claims").setExecutor(cmd);
        getCommand("claims").setTabCompleter(cmd);

        getLogger().info("ChestClaims v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (upkeepManager  != null) upkeepManager.shutdown();
        if (hologramManager != null) hologramManager.shutdown();
        if (claimStorage   != null) claimStorage.save();
        getLogger().info("ChestClaims disabled.");
    }

    public UpkeepManager getUpkeepManager() { return upkeepManager; }
    public ClaimHologramManager getHologramManager() { return hologramManager; }
}
