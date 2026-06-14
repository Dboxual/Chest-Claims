package com.chestclaims.upkeep;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.bops.BopsHook;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Midnight upkeep cycle — automatically withdraws Bops from owners once per day.
 *
 * Flow:
 *   1. At midnight, gather all ACTIVE claims and group by owner.
 *   2. Sort each owner's claims most-expensive-first.
 *   3. Greedily mark claims affordable vs. cannot-pay based on current balance.
 *   4. Withdraw the affordable total in one Bops call.
 *   5. For cannot-pay claims:
 *        - Owner ONLINE  → 5-minute countdown; auto-pays if balance improves.
 *        - Owner OFFLINE → claim immediately becomes INACTIVE.
 *
 * Payment Due is runtime-only (not persisted). ClaimState stays ACTIVE|INACTIVE only.
 */
public class UpkeepManager {

    // Countdown: check every 5 s for up to 5 min (60 checks)
    private static final long COUNTDOWN_CHECK_TICKS = 5L * 20L;
    private static final int  COUNTDOWN_TOTAL_CHECKS = 60;

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;

    // claimId → active countdown task (online grace only)
    private final Map<UUID, BukkitTask> countdownTasks   = new HashMap<>();
    // claims currently in online countdown — hologram shows "Payment Due"
    private final Set<UUID>             paymentDueClaims = new HashSet<>();

    public UpkeepManager(ChestClaimsPlugin plugin, ClaimStorage claimStorage) {
        this.plugin       = plugin;
        this.claimStorage = claimStorage;
    }

    /** Schedule the midnight cycle. Call once after BopsHook is initialised. */
    public void init() {
        if (!plugin.getConfig().getBoolean("upkeep.enabled", true)) return;
        scheduleMidnightCycle();
    }

    /** Cancel all running countdown tasks on plugin disable. */
    public void shutdown() {
        countdownTasks.values().forEach(BukkitTask::cancel);
        countdownTasks.clear();
        paymentDueClaims.clear();
    }

    /** True if this claim is in an active online-player grace countdown. */
    public boolean isPaymentDue(UUID claimId) {
        return paymentDueClaims.contains(claimId);
    }

    /**
     * Cancels any active countdown for this claim.
     * Must be called when a claim is deleted so the task never touches removed data.
     */
    public void cancelCountdown(UUID claimId) {
        paymentDueClaims.remove(claimId);
        BukkitTask task = countdownTasks.remove(claimId);
        if (task != null) task.cancel();
    }

    // ── scheduling ─────────────────────────────────────────────────────────

    private void scheduleMidnightCycle() {
        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        long delayMs    = ChronoUnit.MILLIS.between(now, nextMidnight);
        long delayTicks = Math.max(1L, delayMs / 50L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            runMidnightCycle();
            // Re-schedule every 24 h (tick drift is acceptable for a daily cycle)
            long dayTicks = 24L * 60L * 60L * 20L;
            plugin.getServer().getScheduler().runTaskTimer(plugin, () -> runMidnightCycle(), dayTicks, dayTicks);
        }, delayTicks);

        plugin.getLogger().info("[Upkeep] Next midnight cycle in ~"
                + (delayMs / 3_600_000) + "h " + ((delayMs % 3_600_000) / 60_000) + "m.");
    }

    // ── midnight cycle ──────────────────────────────────────────────────────

    /**
     * Processes automatic upkeep for all active claims.
     * Also exposed as admin command (/claims cycle) for testing.
     */
    public void runMidnightCycle() {
        if (!plugin.getConfig().getBoolean("upkeep.enabled", true)) return;
        plugin.getLogger().info("[Upkeep] Running midnight cycle.");

        // Claims created at or after today's midnight get their first day free —
        // skip them; they will be charged at the following midnight.
        long currentMidnightMs = LocalDate.now().atStartOfDay()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // Group eligible ACTIVE claims by owner
        Map<UUID, List<ClaimData>> byOwner = new HashMap<>();
        for (ClaimData claim : claimStorage.getClaims()) {
            if (claim.getState() != ClaimState.ACTIVE) continue;
            if (paymentDueClaims.contains(claim.getId())) continue; // already in countdown
            if (claim.getCreatedAt() >= currentMidnightMs) continue; // first day free
            byOwner.computeIfAbsent(claim.getOwnerUuid(), k -> new ArrayList<>()).add(claim);
        }

        for (Map.Entry<UUID, List<ClaimData>> entry : byOwner.entrySet()) {
            processOwnerClaims(entry.getKey(), entry.getValue());
        }
    }

    private void processOwnerClaims(UUID ownerUuid, List<ClaimData> claims) {
        // Most expensive first — cheaper claims survive when funds run out
        claims.sort(Comparator.comparingDouble(ClaimData::getDailyUpkeepCost).reversed());

        double balance        = BopsHook.getBalance(ownerUuid);
        double runningBalance = balance;

        List<ClaimData> toPay     = new ArrayList<>();
        List<ClaimData> cannotPay = new ArrayList<>();

        for (ClaimData claim : claims) {
            double cost = claim.getDailyUpkeepCost();
            if (runningBalance >= cost) {
                toPay.add(claim);
                runningBalance -= cost;
            } else {
                cannotPay.add(claim);
            }
        }

        // Single withdrawal for all affordable claims
        if (!toPay.isEmpty()) {
            double totalCharge = toPay.stream().mapToDouble(ClaimData::getDailyUpkeepCost).sum();
            BopsHook.withdraw(ownerUuid, totalCharge);
            long nextMidnight = nextMidnightEpochMs();
            for (ClaimData claim : toPay) {
                claim.setUpkeepPaidUntil(nextMidnight);
            }
            claimStorage.save();
            plugin.getLogger().info("[Upkeep] Charged " + fmtRaw(totalCharge) + " Bops to "
                    + ownerUuid + " for " + toPay.size() + " claim(s).");
        }

        if (cannotPay.isEmpty()) return;

        Player online = Bukkit.getPlayer(ownerUuid);
        if (online != null && online.isOnline()) {
            for (ClaimData claim : cannotPay) {
                startCountdown(online, claim);
            }
        } else {
            for (ClaimData claim : cannotPay) {
                claim.setState(ClaimState.INACTIVE);
                plugin.getLogger().info("[Upkeep] Claim " + claim.getId()
                        + " (" + claim.getOwnerName() + ") → INACTIVE (owner offline, insufficient balance).");
            }
            claimStorage.save();
        }
    }

    // ── online payment countdown ────────────────────────────────────────────

    private void startCountdown(Player player, ClaimData claim) {
        UUID claimId    = claim.getId();
        UUID playerUuid = player.getUniqueId();
        double cost     = claim.getDailyUpkeepCost();

        cancelCountdown(claimId); // replace any prior countdown
        paymentDueClaims.add(claimId);

        player.sendMessage(Component.text(
                "Your claim requires " + fmtBops(cost)
                + " Bops for upkeep. You have 5 minutes to obtain enough Bops or protection will end.",
                NamedTextColor.YELLOW));

        int[] checksLeft = {COUNTDOWN_TOTAL_CHECKS};

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                // Claim was deleted mid-countdown — task cancelled externally
                if (!paymentDueClaims.contains(claimId)) { cancel(); return; }

                Player p = Bukkit.getPlayer(playerUuid);

                // Owner went offline: deactivate immediately
                if (p == null || !p.isOnline()) {
                    paymentDueClaims.remove(claimId);
                    countdownTasks.remove(claimId);
                    claim.setState(ClaimState.INACTIVE);
                    claimStorage.save();
                    plugin.getLogger().info("[Upkeep] Claim " + claimId
                            + " → INACTIVE (owner went offline during countdown).");
                    cancel();
                    return;
                }

                // Owner now has enough — charge and keep ACTIVE
                if (BopsHook.has(playerUuid, cost)) {
                    BopsHook.withdraw(playerUuid, cost);
                    claim.setUpkeepPaidUntil(nextMidnightEpochMs());
                    claimStorage.save();
                    paymentDueClaims.remove(claimId);
                    countdownTasks.remove(claimId);
                    p.sendMessage(Component.text(
                            "Upkeep paid! " + fmtBops(cost) + " Bops deducted. Your claim is protected.",
                            NamedTextColor.GREEN));
                    cancel();
                    return;
                }

                // Countdown expired
                if (--checksLeft[0] <= 0) {
                    paymentDueClaims.remove(claimId);
                    countdownTasks.remove(claimId);
                    claim.setState(ClaimState.INACTIVE);
                    claimStorage.save();
                    p.sendMessage(Component.text(
                            "Your claim has lost protection due to unpaid upkeep.",
                            NamedTextColor.RED));
                    cancel();
                }
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, COUNTDOWN_CHECK_TICKS, COUNTDOWN_CHECK_TICKS);
        countdownTasks.put(claimId, task);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private long nextMidnightEpochMs() {
        return LocalDate.now().plusDays(1).atStartOfDay()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String fmtRaw(double v) {
        return String.format("%.2f", v);
    }

    private static String fmtBops(double v) {
        return NumberFormat.getNumberInstance(Locale.US).format(v);
    }
}
