package com.chestclaims.command;

import com.chestclaims.ChestClaimsPlugin;
import com.chestclaims.listener.ClaimSetupListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClaimsCommand implements CommandExecutor, TabCompleter {

    private final ChestClaimsPlugin plugin;
    private final ClaimSetupListener setupListener;

    public ClaimsCommand(ChestClaimsPlugin plugin, ClaimSetupListener setupListener) {
        this.plugin = plugin;
        this.setupListener = setupListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(parse("&7Usage: /claims cancel | /claims reload | /claims cycle"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("chestclaims.admin")) {
                sender.sendMessage(parse("&cYou do not have permission to use this command."));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(parse("&aChestClaims config reloaded."));
            return true;
        }

        if (sub.equals("cycle")) {
            if (!sender.hasPermission("chestclaims.admin")) {
                sender.sendMessage(parse("&cYou do not have permission to use this command."));
                return true;
            }
            plugin.getUpkeepManager().runMidnightCycle();
            sender.sendMessage(parse("&aUpkeep cycle triggered."));
            return true;
        }

        if (sub.equals("cancel")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            if (!setupListener.hasSession(player.getUniqueId())) {
                player.sendMessage(parse(plugin.getConfig().getString("messages.no-session",
                        "&cYou are not in claim setup mode.")));
                return true;
            }
            setupListener.cancelSession(player);
            return true;
        }

        sender.sendMessage(parse("&7Usage: /claims cancel | /claims reload | /claims cycle"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("chestclaims.admin")) return Arrays.asList("cancel", "reload", "cycle");
            return Collections.singletonList("cancel");
        }
        return Collections.emptyList();
    }

    private Component parse(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
