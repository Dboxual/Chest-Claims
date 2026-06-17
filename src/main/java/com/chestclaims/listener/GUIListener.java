package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.SoundUtil;
import com.chestclaims.bops.BopsHook;
import com.chestclaims.claim.ClaimData;
import com.chestclaims.claim.ClaimSession;
import com.chestclaims.claim.ClaimState;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.claim.OutlineColor;
import com.chestclaims.gui.ChunkAddConfirmGUI;
import com.chestclaims.gui.ChunkAddConfirmHolder;
import com.chestclaims.gui.ClaimAccessGUI;
import com.chestclaims.gui.ClaimConfirmGUI;
import com.chestclaims.gui.ClaimGuiHolder;
import com.chestclaims.gui.ClaimManageGUI;
import com.chestclaims.gui.ClaimManageHolder;
import com.chestclaims.gui.ClaimTypeHolder;
import com.chestclaims.gui.ClaimTypeSelectGUI;
import com.chestclaims.gui.OutlineSettingsGUI;
import com.chestclaims.gui.TrustedPlayerSelectGUI;
import com.chestclaims.teams.TeamsHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GUIListener implements Listener {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;
    private final ClaimSetupListener setupListener;
    private final ChunkSelectorListener chunkSelectorListener;

    public GUIListener(ChestClaimsPlugin plugin, ClaimStorage claimStorage,
                       ClaimSetupListener setupListener,
                       ChunkSelectorListener chunkSelectorListener) {
        this.plugin                = plugin;
        this.claimStorage          = claimStorage;
        this.setupListener         = setupListener;
        this.chunkSelectorListener = chunkSelectorListener;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Object holder = event.getInventory().getHolder();
        if (holder instanceof ClaimTypeHolder ch) {
            event.setCancelled(true);
            handleTypeSelectClick(event.getRawSlot(), player, ch);
        } else if (holder instanceof ClaimGuiHolder) {
            event.setCancelled(true);
            handleConfirmClick(event.getRawSlot(), player);
        } else if (holder instanceof ClaimManageHolder mh) {
            event.setCancelled(true);
            handleManageClick(event.getRawSlot(), player, mh);
        } else if (holder instanceof ChunkAddConfirmHolder cah) {
            event.setCancelled(true);
            handleChunkAddConfirmClick(event.getRawSlot(), player, cah);
        }
    }

    // ── type selector GUI ──────────────────────────────────────────────────

    private void handleTypeSelectClick(int slot, Player player, ClaimTypeHolder holder) {
        if (slot == ClaimTypeSelectGUI.CUSTOM_CLAIM_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> setupListener.startCustomSession(player, holder.getAnchor()));
        } else if (slot == ClaimTypeSelectGUI.CHUNK_CLAIM_SLOT) {
            if (!plugin.getConfig().getBoolean("chunk-claims.enabled", true)) {
                player.sendMessage(parse("&cChunk claims are not enabled on this server."));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> setupListener.startChunkClaimSession(player, holder.getAnchor()));
        }
    }

    // ── confirm GUI (initial purchase) ────────────────────────────────────

    private void handleConfirmClick(int slot, Player player) {
        if (slot == ClaimConfirmGUI.CONFIRM_SLOT) {
            player.closeInventory();
            executeConfirm(player, player.getUniqueId());
        } else if (slot == ClaimConfirmGUI.CANCEL_SLOT) {
            player.closeInventory();
            SoundUtil.play(plugin, player, "setup-cancelled");
            setupListener.cancelSession(player);
        }
    }

    private void executeConfirm(Player player, UUID uuid) {
        ClaimSession session = setupListener.getSession(uuid);
        if (session == null || !session.isComplete()) {
            player.sendMessage(parse("&cSession expired. Please start claim setup again."));
            return;
        }

        boolean isChunk = session.getClaimType() == ClaimSession.ClaimType.CHUNK;

        if (!session.containsAnchor()) {
            SoundUtil.play(plugin, player, "invalid-action");
            player.sendMessage(parse(plugin.getConfig().getString("messages.anchor-outside",
                    "&cThe claim chest must be inside the selected region.")));
            return;
        }

        String worldName = session.getAnchor().getWorld() != null
                ? session.getAnchor().getWorld().getName() : "";
        if (claimStorage.overlaps(session.getPos1(), session.getPos2(), worldName)) {
            SoundUtil.play(plugin, player, "invalid-action");
            player.sendMessage(parse(plugin.getConfig().getString("messages.claim-overlap",
                    "&cThat area overlaps with an existing claim.")));
            return;
        }

        if (!isChunk) {
            long maxVolume = plugin.getConfig().getLong("claims.max-volume", 50000L);
            if (session.getVolume() > maxVolume) {
                SoundUtil.play(plugin, player, "invalid-action");
                player.sendMessage(parse(plugin.getConfig().getString("messages.volume-exceeded",
                                "&cSelection too large! Max: &e{max} blocks.")
                        .replace("{max}", String.valueOf(maxVolume))));
                setupListener.cancelSession(player);
                return;
            }
        }

        double cost, dailyUpkeepCost;
        if (isChunk) {
            cost            = plugin.getConfig().getDouble("chunk-claims.claim-cost-bops", 15.0);
            dailyUpkeepCost = plugin.getConfig().getDouble("chunk-claims.upkeep-bops-per-day", 2.0);
        } else {
            double pricePerBlock    = plugin.getConfig().getDouble("claims.price-per-block", 0.02);
            double dailyCostPercent = plugin.getConfig().getDouble("upkeep.daily-cost-percent-of-purchase", 2.0);
            cost            = session.getVolume() * pricePerBlock;
            dailyUpkeepCost = cost * (dailyCostPercent / 100.0);
        }

        if (!BopsHook.has(uuid, cost)) {
            SoundUtil.play(plugin, player, "invalid-action");
            double balance = BopsHook.getBalance(uuid);
            player.sendMessage(parse(plugin.getConfig().getString("messages.insufficient-funds",
                            "&cInsufficient Bops. Need &e{cost}, have &e{balance}.")
                    .replace("{cost}", String.format("%.2f", cost))
                    .replace("{balance}", String.format("%.2f", balance))));
            return;
        }

        BopsHook.withdraw(uuid, cost);

        ClaimData claim = new ClaimData(
                UUID.randomUUID(),
                uuid,
                player.getName(),
                worldName,
                session.getAnchor(),
                session.getPos1(),
                session.getPos2(),
                System.currentTimeMillis(),
                session.getVolume(),
                cost
        );
        claim.setDailyUpkeepCost(dailyUpkeepCost);
        if (isChunk) {
            claim.setChunkClaim(true);
            int cx = session.getAnchor().getBlockX() >> 4;
            int cz = session.getAnchor().getBlockZ() >> 4;
            claim.addClaimedChunk(cx + "," + cz);
        }

        claimStorage.addClaim(claim);
        claimStorage.save();
        plugin.getHologramManager().onClaimCreated(claim);
        setupListener.removeSession(uuid);

        player.sendMessage(parse(plugin.getConfig().getString("messages.confirmed",
                        "&aClaim confirmed! Charged &e{cost} Bops.")
                .replace("{cost}", String.format("%.2f", cost))));
        SoundUtil.play(plugin, player, "claim-confirmed");
    }

    // ── manage / outline-settings / delete-confirm / access GUI ──────────

    private void handleManageClick(int slot, Player player, ClaimManageHolder holder) {
        ClaimData claim = holder.getClaim();

        if (holder.getScreen() == ClaimManageHolder.Screen.MANAGE) {
            if (!claim.getOwnerUuid().equals(player.getUniqueId())) return;

            if (slot == ClaimManageGUI.OUTLINE_SETTINGS_SLOT) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> OutlineSettingsGUI.open(player, claim, plugin));

            } else if (slot == ClaimManageGUI.ADD_CHUNK_SLOT
                    && claim.isChunkClaim()
                    && claim.getState() == ClaimState.ACTIVE) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> chunkSelectorListener.startSelection(player, claim));

            } else if (slot == ClaimManageGUI.MANAGE_ACCESS_SLOT) {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> ClaimAccessGUI.open(player, claim, plugin));
            }
            return;
        }

        if (holder.getScreen() == ClaimManageHolder.Screen.OUTLINE_SETTINGS) {
            if (!claim.getOwnerUuid().equals(player.getUniqueId())) return;
            handleOutlineSettingsClick(slot, player, claim);
            return;
        }

        if (holder.getScreen() == ClaimManageHolder.Screen.ACCESS) {
            if (!claim.getOwnerUuid().equals(player.getUniqueId())) return;
            handleAccessClick(slot, player, claim);
            return;
        }

        if (holder.getScreen() == ClaimManageHolder.Screen.SELECT_PLAYER) {
            if (!claim.getOwnerUuid().equals(player.getUniqueId())) return;
            handleSelectPlayerClick(slot, player, holder);
            return;
        }

        if (holder.getScreen() != ClaimManageHolder.Screen.DELETE_CONFIRM) return;
        if (!claim.getOwnerUuid().equals(player.getUniqueId())) return;

        if (slot == ClaimManageGUI.DELETE_YES_SLOT) {
            player.closeInventory();
            deleteClaim(player, claim);
        } else if (slot == ClaimManageGUI.DELETE_NO_SLOT) {
            player.closeInventory();
        }
    }

    private void handleOutlineSettingsClick(int slot, Player player, ClaimData claim) {
        if (slot == OutlineSettingsGUI.TOGGLE_SLOT) {
            claim.setOutlineEnabled(!claim.isOutlineEnabled());
            claimStorage.save();
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> OutlineSettingsGUI.open(player, claim, plugin));
            return;
        }

        if (slot == OutlineSettingsGUI.BACK_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> ClaimManageGUI.openManage(player, claim, plugin));
            return;
        }

        OutlineColor[] colors = OutlineSettingsGUI.COLOR_ORDER;
        for (int i = 0; i < colors.length; i++) {
            if (slot == OutlineSettingsGUI.FIRST_COLOR_SLOT + i) {
                claim.setOutlineColor(colors[i].rgb);
                claimStorage.save();
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> OutlineSettingsGUI.open(player, claim, plugin));
                return;
            }
        }
    }

    // ── chunk add confirm GUI ──────────────────────────────────────────────

    private void handleChunkAddConfirmClick(int slot, Player player, ChunkAddConfirmHolder holder) {
        if (slot == ChunkAddConfirmGUI.CONFIRM_SLOT) {
            player.closeInventory();
            executeChunkAdd(player, holder.getClaim(), holder.getTargetChunkX(), holder.getTargetChunkZ());
        } else if (slot == ChunkAddConfirmGUI.CANCEL_SLOT) {
            player.closeInventory();
            chunkSelectorListener.removeSelectorItem(player);
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-selector-cancelled",
                    "&cChunk selection cancelled.")));
        }
    }

    private void executeChunkAdd(Player player, ClaimData claim, int cx, int cz) {
        UUID uuid       = player.getUniqueId();
        String chunkKey = cx + "," + cz;

        if (!claim.getOwnerUuid().equals(uuid)) return;

        if (claim.getClaimedChunks().contains(chunkKey)) {
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-already-in-claim",
                    "&cThat chunk is already part of your claim.")));
            chunkSelectorListener.removeSelectorItem(player);
            return;
        }

        if (!isAdjacentToClaimChunks(claim, cx, cz)) {
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-not-adjacent",
                    "&cThat chunk is not connected to your existing claim.")));
            chunkSelectorListener.removeSelectorItem(player);
            return;
        }

        if (claimStorage.isChunkClaimed(cx, cz, claim.getWorld(), claim.getId())) {
            player.sendMessage(parse(plugin.getConfig().getString(
                    "messages.chunk-already-claimed",
                    "&cThat chunk is already claimed by someone else.")));
            chunkSelectorListener.removeSelectorItem(player);
            return;
        }

        double cost = plugin.getConfig().getDouble("chunk-claims.claim-cost-bops", 15.0);
        if (!BopsHook.has(uuid, cost)) {
            double balance = BopsHook.getBalance(uuid);
            SoundUtil.play(plugin, player, "invalid-action");
            player.sendMessage(parse(plugin.getConfig().getString("messages.insufficient-funds",
                            "&cInsufficient Bops. Need &e{cost}, have &e{balance}.")
                    .replace("{cost}", String.format("%.2f", cost))
                    .replace("{balance}", String.format("%.2f", balance))));
            chunkSelectorListener.removeSelectorItem(player);
            return;
        }

        BopsHook.withdraw(uuid, cost);

        claim.addClaimedChunk(chunkKey);

        // Expand the bounding box to include the new chunk
        World world = claim.getAnchor().getWorld();
        if (world != null) {
            Location oldP1 = claim.getPos1();
            Location oldP2 = claim.getPos2();
            int newMinX = Math.min(oldP1.getBlockX(), cx * 16);
            int newMinZ = Math.min(oldP1.getBlockZ(), cz * 16);
            int newMaxX = Math.max(oldP2.getBlockX(), cx * 16 + 15);
            int newMaxZ = Math.max(oldP2.getBlockZ(), cz * 16 + 15);
            claim.setPos1(new Location(world, newMinX, oldP1.getBlockY(), newMinZ));
            claim.setPos2(new Location(world, newMaxX, oldP2.getBlockY(), newMaxZ));
        }

        // Upkeep scales with chunk count
        double upkeepPerChunk = plugin.getConfig().getDouble("chunk-claims.upkeep-bops-per-day", 2.0);
        claim.setDailyUpkeepCost(claim.getClaimedChunks().size() * upkeepPerChunk);

        claimStorage.save();
        chunkSelectorListener.removeSelectorItem(player);

        String costFmt = NumberFormat.getNumberInstance(Locale.US).format(cost);
        player.sendMessage(parse(plugin.getConfig().getString("messages.chunk-added",
                        "&aChunk ({cx}, {cz}) added to your claim! Charged &e{cost} Bops&a.")
                .replace("{cx}", String.valueOf(cx))
                .replace("{cz}", String.valueOf(cz))
                .replace("{cost}", costFmt)));
        SoundUtil.play(plugin, player, "claim-confirmed");
    }

    private boolean isAdjacentToClaimChunks(ClaimData claim, int newCx, int newCz) {
        for (String key : claim.getClaimedChunks()) {
            String[] parts = key.split(",");
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            if (Math.abs(cx - newCx) + Math.abs(cz - newCz) == 1) return true;
        }
        return false;
    }

    // ── access GUI ────────────────────────────────────────────────────────

    private void handleAccessClick(int slot, Player player, ClaimData claim) {
        if (slot == ClaimAccessGUI.BACK_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> ClaimManageGUI.openManage(player, claim, plugin));
            return;
        }

        if (slot == ClaimAccessGUI.TEAM_TOGGLE_SLOT) {
            if (!TeamsHook.isAvailable()) return;
            claim.setTeamAccessEnabled(!claim.isTeamAccessEnabled());
            claimStorage.save();
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> ClaimAccessGUI.open(player, claim, plugin));
            return;
        }

        if (slot == ClaimAccessGUI.ADD_TRUSTED_SLOT) {
            if (claim.getTrustedPlayers().size() >= 9) {
                player.sendMessage(parse("&cThis claim already has 9 trusted players (max)."));
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> ClaimAccessGUI.open(player, claim, plugin));
                return;
            }
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> TrustedPlayerSelectGUI.open(player, claim, plugin));
            return;
        }

        if (slot >= ClaimAccessGUI.TRUSTED_ROW_START && slot < ClaimAccessGUI.TRUSTED_ROW_START + 9) {
            int index = slot - ClaimAccessGUI.TRUSTED_ROW_START;
            List<UUID> trusted = claim.getTrustedPlayers();
            if (index < trusted.size()) {
                UUID removed = trusted.remove(index);
                claimStorage.save();
                OfflinePlayer op = Bukkit.getOfflinePlayer(removed);
                String name = op.getName() != null ? op.getName() : removed.toString().substring(0, 8);
                player.sendMessage(parse("&e" + name + " &chas been removed from trusted players."));
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin,
                        () -> ClaimAccessGUI.open(player, claim, plugin));
            }
        }
    }

    private void handleSelectPlayerClick(int slot, Player player, ClaimManageHolder holder) {
        ClaimData claim = holder.getClaim();

        if (slot == TrustedPlayerSelectGUI.BACK_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> ClaimAccessGUI.open(player, claim, plugin));
            return;
        }

        if (slot == TrustedPlayerSelectGUI.PREVIOUS_PAGE_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> TrustedPlayerSelectGUI.open(player, claim, plugin, holder.getPage() - 1));
            return;
        }

        if (slot == TrustedPlayerSelectGUI.NEXT_PAGE_SLOT) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin,
                    () -> TrustedPlayerSelectGUI.open(player, claim, plugin, holder.getPage() + 1));
            return;
        }

        if (slot < 0 || slot >= holder.getSelectablePlayers().size()) return;

        UUID targetUuid = holder.getSelectablePlayers().get(slot);
        Player target = Bukkit.getPlayer(targetUuid);
        player.closeInventory();

        if (target == null) {
            player.sendMessage(parse("&cThat player is no longer online."));
            Bukkit.getScheduler().runTask(plugin,
                    () -> TrustedPlayerSelectGUI.open(player, claim, plugin, holder.getPage()));
            return;
        }

        if (target.getUniqueId().equals(claim.getOwnerUuid())) {
            player.sendMessage(parse("&cYou cannot trust the claim owner."));
            Bukkit.getScheduler().runTask(plugin,
                    () -> TrustedPlayerSelectGUI.open(player, claim, plugin, holder.getPage()));
            return;
        }

        if (claim.getTrustedPlayers().contains(target.getUniqueId())) {
            player.sendMessage(parse("&e" + target.getName() + " &cis already trusted."));
            Bukkit.getScheduler().runTask(plugin,
                    () -> TrustedPlayerSelectGUI.open(player, claim, plugin, holder.getPage()));
            return;
        }

        if (claim.getTrustedPlayers().size() >= 9) {
            player.sendMessage(parse("&cThis claim already has 9 trusted players (max)."));
            Bukkit.getScheduler().runTask(plugin,
                    () -> ClaimAccessGUI.open(player, claim, plugin));
            return;
        }

        claim.getTrustedPlayers().add(target.getUniqueId());
        claimStorage.save();
        player.sendMessage(parse("&a" + target.getName() + " &ais now trusted on this claim."));
        Bukkit.getScheduler().runTask(plugin,
                () -> ClaimAccessGUI.open(player, claim, plugin));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private void deleteClaim(Player player, ClaimData claim) {
        plugin.getUpkeepManager().cancelCountdown(claim.getId());
        claimStorage.removeClaim(claim.getId());
        plugin.getHologramManager().onClaimDeleted(claim.getId());
        claimStorage.save();
        var anchor = claim.getAnchor();
        if (anchor.getWorld() != null) {
            anchor.getWorld().getBlockAt(anchor).breakNaturally();
        }
        player.sendMessage(parse(plugin.getConfig().getString("messages.claim-deleted",
                "&aClaim deleted.")));
    }

    private Component parse(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
