package com.chestclaims;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class SoundUtil {

    private SoundUtil() {}

    public static void play(ChestClaimsPlugin plugin, Player player, String key) {
        String path = "sounds." + key;
        if (!plugin.getConfig().getBoolean(path + ".enabled", true)) return;

        String soundName = plugin.getConfig().getString(path + ".sound", "");
        if (soundName == null || soundName.isEmpty()) return;

        float volume = (float) plugin.getConfig().getDouble(path + ".volume", 1.0);
        float pitch  = (float) plugin.getConfig().getDouble(path + ".pitch",  1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown sound '" + soundName + "' in config at " + path + ".sound");
        }
    }
}
