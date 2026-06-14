package com.chestclaims.listener;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.SoundUtil;
import com.chestclaims.claim.ClaimSession;
import com.chestclaims.claim.ClaimStorage;
import com.chestclaims.gui.ClaimConfirmGUI;
import com.chestclaims.gui.ClaimTypeSelectGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ClaimSetupListener implements Listener {

    private final ChestClaimsPlugin plugin;
    private final ClaimStorage claimStorage;
    private final Map<UUID, ClaimSession> sessions     = new HashMap<>();
    private final Map<UUID, TextDisplay>  holograms    = new HashMap<>();
    private final Map<UUID, BukkitTask>   outlineTasks = new HashMap<>();

    public ClaimSetupListener(ChestClaimsPlugin plugin, ClaimStorage claimStorage) {
        this.plugin = plugin;
        this.claimStorage = claimStorage;
    }

    public boolean hasSession(UUID uuid) { return sessions.containsKey(uuid); }
    public ClaimSession getSession(UUID uuid) { return sessions.get(uuid); }

    /**
     * Periodic check called from a repeating task — cancels any session where the
     * player no longer holds shears in their main hand.
     */
    public void tickCheck() {
        List<UUID> toCancel = new ArrayList<>(sessions.keySet());
        for (UUID uuid : toCancel) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;
            if (player.getInventory().getItemInMainHand().getType() != Material.SHEARS) {
                lostShears(player);
            }
        }
    }

    /** Ends the session with a cancellation message. */
    public void cancelSession(Player player) {
        UUID uuid = player.getUniqueId();
        sessions.remove(uuid);
        removeHologram(uuid);
        stopOutline(uuid);
        player.sendMessage(parse(plugin.getConfig().getString("messages.cancelled", "&cClaim setup cancelled.")));
    }

    /** Ends the session with a shears-lost message (no cancellation sound/message). */
    private void lostShears(Player player) {
        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid)) return;
        sessions.remove(uuid);
        removeHologram(uuid);
        stopOutline(uuid);
        player.sendMessage(parse(plugin.getConfig().getString("messages.shears-lost",
                "&cClaim setup cancelled because you stopped holding shears.")));
        SoundUtil.play(plugin, player, "shears-lost-cancelled");
    }

    /** Ends the session silently (on confirm). Returns the removed session. */
    public ClaimSession removeSession(UUID uuid) {
        removeHologram(uuid);
        stopOutline(uuid);
        return sessions.remove(uuid);
    }

    /** Starts a custom claim session after the player selects "Custom Claim" in the type selector. */
    public void startCustomSession(Player player, Location anchor) {
        UUID uuid = player.getUniqueId();
        ClaimSession session = new ClaimSession(uuid, anchor);
        sessions.put(uuid, session);
        spawnHologram(uuid, anchor, false);
        startOutline(player, session);
        player.sendMessage(parse(plugin.getConfig().getString("messages.setup-started",
                "&aSetup started! Left-click=Pos1, Right-click=Pos2. Sneak+F to cancel.")));
        SoundUtil.play(plugin, player, "setup-started");
    }

    /**
     * Starts a chunk claim session — auto-computes pos1/pos2 from the anchor's chunk,
     * shows a green particle preview, and immediately opens the confirm GUI.
     */
    public void startChunkClaimSession(Player player, Location anchor) {
        UUID uuid  = player.getUniqueId();
        World world = anchor.getWorld();

        int chunkX = anchor.getBlockX() >> 4;
        int chunkZ = anchor.getBlockZ() >> 4;
        int minX   = chunkX * 16;
        int minZ   = chunkZ * 16;
        int maxX   = minX + 15;
        int maxZ   = minZ + 15;
        int minY   = world != null ? world.getMinHeight()     : -64;
        int maxY   = world != null ? world.getMaxHeight() - 1 : 319;

        Location pos1 = new Location(world, minX, minY, minZ);
        Location pos2 = new Location(world, maxX, maxY, maxZ);

        ClaimSession session = new ClaimSession(uuid, anchor);
        session.setPos1(pos1);
        session.setPos2(pos2);
        session.setClaimType(ClaimSession.ClaimType.CHUNK);
        sessions.put(uuid, session);

        spawnHologram(uuid, anchor, true);
        startChunkOutline(player, session);

        SoundUtil.play(plugin, player, "confirmation-open");
        ClaimConfirmGUI.open(player, session, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        if (player.getInventory().getItemInMainHand().getType() != Material.SHEARS) return;

        if (!sessions.containsKey(uuid)) {
            // Open type selector: sneak + right-click an unclaimed chest/barrel with shears
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && player.isSneaking()
                    && event.getClickedBlock() != null
                    && isAnchorBlock(event.getClickedBlock().getType())
                    && claimStorage.getClaimByAnchor(event.getClickedBlock().getLocation()) == null) {
                event.setCancelled(true);
                Location anchor = event.getClickedBlock().getLocation();
                SoundUtil.play(plugin, player, "setup-started");
                ClaimTypeSelectGUI.open(player, anchor, plugin);
            }
            return;
        }

        ClaimSession session = sessions.get(uuid);

        // Chunk claim sessions: no pos selection — cancel block actions, allow re-open of confirm on anchor
        if (session.getClaimType() == ClaimSession.ClaimType.CHUNK) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                event.setCancelled(true);
                if (sameBlock(session.getAnchor(), event.getClickedBlock().getLocation())) {
                    SoundUtil.play(plugin, player, "confirmation-open");
                    ClaimConfirmGUI.open(player, session, plugin);
                }
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            return;
        }

        // Custom claim: right-click = pos2 or open confirm; left-click = pos1
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (session.isComplete() && sameBlock(session.getAnchor(), event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                SoundUtil.play(plugin, player, "confirmation-open");
                ClaimConfirmGUI.open(player, session, plugin);
                return;
            }
            event.setCancelled(true);
            session.setPos2(event.getClickedBlock().getLocation());
            sendPosUpdate(player, session, 2);

        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);
            session.setPos1(event.getClickedBlock().getLocation());
            sendPosUpdate(player, session, 1);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!sessions.containsKey(player.getUniqueId())) return;
        if (player.isSneaking()) {
            event.setCancelled(true);
            SoundUtil.play(plugin, player, "setup-cancelled");
            cancelSession(player);
        } else {
            lostShears(player);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!sessions.containsKey(player.getUniqueId())) return;
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem == null || newItem.getType() != Material.SHEARS) {
            lostShears(player);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!sessions.containsKey(player.getUniqueId())) return;
        if (event.getItemDrop().getItemStack().getType() != Material.SHEARS) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.SHEARS) {
            lostShears(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        removeHologram(uuid);
        stopOutline(uuid);
        sessions.remove(uuid);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private boolean isAnchorBlock(Material mat) {
        return mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.BARREL;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ()
                && a.getWorld() != null
                && a.getWorld().equals(b.getWorld());
    }

    private void spawnHologram(UUID playerUuid, Location chestLoc, boolean isChunk) {
        String line1, line2, line3;
        if (isChunk) {
            line1 = plugin.getConfig().getString("chunk-hologram.line1", "&6&lChunk Claim");
            line2 = plugin.getConfig().getString("chunk-hologram.line2", "&7Your chunk is highlighted");
            line3 = plugin.getConfig().getString("chunk-hologram.line3", "&eRight-click the chest to confirm");
        } else {
            line1 = plugin.getConfig().getString("hologram.line1", "&6&lClaim Setup");
            line2 = plugin.getConfig().getString("hologram.line2", "&7Select your area with shears");
            line3 = plugin.getConfig().getString("hologram.line3", "&eRight-click the chest again to confirm");
        }

        Component text = parse(line1)
                .append(Component.newline())
                .append(parse(line2))
                .append(Component.newline())
                .append(parse(line3));

        Location loc = chestLoc.clone().add(0.5, 2.5, 0.5);
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setGravity(false);
            d.setPersistent(false);
        });
        TextDisplay old = holograms.put(playerUuid, display);
        if (old != null) old.remove();
    }

    private void removeHologram(UUID uuid) {
        TextDisplay holo = holograms.remove(uuid);
        if (holo != null) holo.remove();
    }

    private void startOutline(Player player, ClaimSession session) {
        if (!plugin.getConfig().getBoolean("selection-outline.enabled", true)) return;
        stopOutline(player.getUniqueId());

        int   updateTicks = plugin.getConfig().getInt("selection-outline.update-ticks", 10);
        int   maxPerEdge  = plugin.getConfig().getInt("selection-outline.max-particles-per-edge", 15);
        float size        = (float) plugin.getConfig().getDouble("selection-outline.particle-size", 1.0);
        Color color       = parseColor(plugin.getConfig().getString("selection-outline.particle-color", "255,165,0"));

        OutlineTask task = new OutlineTask(player, session::getPos1, session::getPos2, color, size, maxPerEdge);
        BukkitTask bt = task.runTaskTimer(plugin, 2L, updateTicks);
        outlineTasks.put(player.getUniqueId(), bt);
    }

    private void startChunkOutline(Player player, ClaimSession session) {
        if (!plugin.getConfig().getBoolean("selection-outline.enabled", true)) return;
        stopOutline(player.getUniqueId());

        int   updateTicks = plugin.getConfig().getInt("selection-outline.update-ticks", 10);
        int   maxPerEdge  = plugin.getConfig().getInt("selection-outline.max-particles-per-edge", 15);
        float size        = (float) plugin.getConfig().getDouble("selection-outline.particle-size", 1.0);
        Color color       = parseColor(plugin.getConfig().getString("chunk-claims.preview-particle-color", "0,255,128"));

        OutlineTask task = new OutlineTask(player, session::getPos1, session::getPos2, color, size, maxPerEdge);
        BukkitTask bt = task.runTaskTimer(plugin, 2L, updateTicks);
        outlineTasks.put(player.getUniqueId(), bt);
    }

    private void stopOutline(UUID uuid) {
        BukkitTask task = outlineTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private Color parseColor(String rgb) {
        try {
            String[] p = rgb.split(",");
            return Color.fromRGB(Integer.parseInt(p[0].trim()),
                    Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()));
        } catch (Exception e) {
            return Color.ORANGE;
        }
    }

    private void sendPosUpdate(Player player, ClaimSession session, int posNum) {
        SoundUtil.play(plugin, player, posNum == 1 ? "pos1-set" : "pos2-set");
        Location loc = posNum == 1 ? session.getPos1() : session.getPos2();
        String template = posNum == 1
                ? plugin.getConfig().getString("messages.pos1-set", "&aPos1: &e{x}, {y}, {z}")
                : plugin.getConfig().getString("messages.pos2-set", "&aPos2: &e{x}, {y}, {z}");

        player.sendMessage(parse(template
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{y}", String.valueOf(loc.getBlockY()))
                .replace("{z}", String.valueOf(loc.getBlockZ()))));

        if (session.isComplete()) {
            double pricePerBlock = plugin.getConfig().getDouble("claims.price-per-block", 0.02);
            double cost = session.getVolume() * pricePerBlock;
            String volFmt  = NumberFormat.getNumberInstance(Locale.US).format(session.getVolume());
            String costFmt = String.format("%.2f", cost);
            String info = plugin.getConfig().getString("messages.selection-info",
                            "&7Blocks: &e{vol} &7| Price: &e{cost} Bops")
                    .replace("{vol}", volFmt)
                    .replace("{cost}", costFmt);
            player.sendActionBar(parse(info));
        }
    }

    private Component parse(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
