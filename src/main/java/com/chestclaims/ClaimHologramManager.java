package com.chestclaims;

import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.upkeep.UpkeepManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ClaimHologramManager {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;
    private final Map<UUID, TextDisplay> holograms = new HashMap<>();
    private final Map<UUID, Set<UUID>>   visibleTo = new HashMap<>();

    public ClaimHologramManager(ChestClaimsPlugin plugin, ClaimStorage claimStorage) {
        this.plugin       = plugin;
        this.claimStorage = claimStorage;
    }

    public void init() {
        if (!plugin.getConfig().getBoolean("completed-hologram.enabled", true)) return;
        for (ClaimData claim : claimStorage.getClaims()) {
            spawnHologram(claim);
        }
        startProximityTask();
    }

    public void onClaimCreated(ClaimData claim) {
        if (!plugin.getConfig().getBoolean("completed-hologram.enabled", true)) return;
        spawnHologram(claim);
    }

    public void onClaimDeleted(UUID claimId) {
        visibleTo.remove(claimId);
        TextDisplay display = holograms.remove(claimId);
        if (display != null && display.isValid()) display.remove();
    }

    public void shutdown() {
        for (TextDisplay display : holograms.values()) {
            if (display.isValid()) display.remove();
        }
        holograms.clear();
        visibleTo.clear();
    }

    private void spawnHologram(ClaimData claim) {
        TextDisplay existing = holograms.get(claim.getId());
        if (existing != null && existing.isValid()) return;

        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return;

        double heightOffset = plugin.getConfig().getDouble("completed-hologram.height-offset", 1.35);
        Location anchor = claim.getAnchor();
        Location loc = new Location(world,
                anchor.getBlockX() + 0.5,
                anchor.getBlockY() + 1.0 + heightOffset,
                anchor.getBlockZ() + 0.5);

        TextDisplay display = world.spawn(loc, TextDisplay.class, d -> {
            d.text(buildText(claim));
            d.setBillboard(Display.Billboard.CENTER);
            d.setGravity(false);
            d.setPersistent(false);
            d.setDefaultBackground(false);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setVisibleByDefault(false);
        });

        holograms.put(claim.getId(), display);
    }

    private void startProximityTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double viewDist   = plugin.getConfig().getDouble("completed-hologram.view-distance-blocks", 5.0);
                double viewDistSq = viewDist * viewDist;

                for (Map.Entry<UUID, TextDisplay> entry : holograms.entrySet()) {
                    UUID claimId        = entry.getKey();
                    TextDisplay display = entry.getValue();
                    if (!display.isValid()) continue;

                    ClaimData claim = findClaim(claimId);
                    if (claim != null) display.text(buildText(claim));

                    Location  dispLoc   = display.getLocation();
                    World     dispWorld = dispLoc.getWorld();
                    if (dispWorld == null) continue;

                    Set<UUID> seers = visibleTo.computeIfAbsent(claimId, k -> new HashSet<>());

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        UUID playerUuid = player.getUniqueId();
                        boolean inRange = player.getWorld().equals(dispWorld)
                                && player.getLocation().distanceSquared(dispLoc) <= viewDistSq;
                        boolean wasSeen = seers.contains(playerUuid);

                        if (inRange && !wasSeen) {
                            player.showEntity(plugin, display);
                            seers.add(playerUuid);
                        } else if (!inRange && wasSeen) {
                            player.hideEntity(plugin, display);
                            seers.remove(playerUuid);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }

    private ClaimData findClaim(UUID claimId) {
        for (ClaimData c : claimStorage.getClaims()) {
            if (c.getId().equals(claimId)) return c;
        }
        return null;
    }

    private Component buildText(ClaimData claim) {
        String line1Tmpl = plugin.getConfig().getString(
                "completed-hologram.line1", "&6&l{owner}'s Claim");
        Component c1 = parse(line1Tmpl.replace("{owner}", resolveOwnerDisplay(claim)));

        String hintTmpl = plugin.getConfig().getString(
                "completed-hologram.line3", "&7Right-click chest to manage");
        Component hint = parse(hintTmpl);

        boolean upkeepEnabled = plugin.getConfig().getBoolean("upkeep.enabled", false);
        if (!upkeepEnabled) {
            return c1
                    .append(Component.newline()).append(parse("&7Upkeep Disabled"))
                    .append(Component.newline()).append(hint);
        }

        Component rateLine   = buildDailyRateLine(claim);
        Component statusLine = buildUpkeepStatusLine(claim);
        return c1
                .append(Component.newline()).append(rateLine)
                .append(Component.newline()).append(statusLine)
                .append(Component.newline()).append(hint);
    }

    /**
     * Returns the display name for the hologram ownership line.
     * Currently returns the individual owner's name.
     * When team support is implemented, check team membership here and return the team name
     * so the hologram shows "Team: BopSquad" instead of the player name.
     */
    private String resolveOwnerDisplay(ClaimData claim) {
        // Future: if (teamManager != null) { String team = teamManager.getTeam(claim.getOwnerUuid()); if (team != null) return team; }
        return claim.getOwnerName();
    }

    private Component buildDailyRateLine(ClaimData claim) {
        double daily     = claim.getDailyUpkeepCost();
        String formatted = NumberFormat.getNumberInstance(Locale.US).format(daily);
        String cycleName = plugin.getConfig().getString("upkeep.cycle-name", "Day");
        return parse("&7" + formatted + " Bops / " + cycleName);
    }

    private Component buildUpkeepStatusLine(ClaimData claim) {
        if (claim.getState() == ClaimState.INACTIVE) return parse("&cInactive");
        UpkeepManager um = plugin.getUpkeepManager();
        if (um != null && um.isPaymentDue(claim.getId())) return parse("&ePayment Due");
        return parse("&aActive");
    }

    private Component parse(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
