package dev.cxebby.anonymous;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

final class AnonymousCommand implements TabExecutor {
    static final String PERMISSION = "anonymous.admin";
    static final Component DENIED = Component.text(
            "You do not have permission to use this command.", NamedTextColor.RED);
    private static final List<String> COMMANDS = List.of("settings", "scramble", "unscramble", "check");
    private final AnonymousSMP plugin;

    AnonymousCommand(AnonymousSMP plugin) { this.plugin = plugin; }

    static boolean allowed(CommandSender sender) {
        return sender.isOp() || sender.hasPermission(PERMISSION);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!allowed(sender)) {
            sender.sendMessage(DENIED);
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("/anonymous settings | scramble | unscramble | check", NamedTextColor.GRAY));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "scramble", "unscramble" -> {
                boolean enabled = args[0].equalsIgnoreCase("scramble");
                plugin.set(Setting.SCRAMBLING, enabled);
                sender.sendMessage(Component.text("Scrambling " + (enabled ? "enabled." : "disabled."),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "settings", "check" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used in game.", NamedTextColor.RED));
                    return true;
                }
                if (args[0].equalsIgnoreCase("settings")) plugin.menu().open(player);
                else {
                    boolean enabled = plugin.toggleCheck(player);
                    player.sendMessage(Component.text("Real name view " + (enabled ? "enabled for you." : "disabled for you."),
                            enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text("/anonymous settings | scramble | unscramble | check", NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!allowed(sender) || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return COMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
