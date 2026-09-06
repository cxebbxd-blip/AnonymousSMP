package dev.cxebby.anonymous;

import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;

final class PrivacyListener implements Listener {
    static final Component MASK = Component.text("XXXX", NamedTextColor.WHITE).decorate(TextDecoration.OBFUSCATED);
    private final AnonymousSMP plugin;

    PrivacyListener(AnonymousSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void configure(AsyncPlayerConnectionConfigureEvent event) {
        plugin.packets().attach(event.getConnection());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void join(PlayerJoinEvent event) {
        plugin.publish();
        plugin.packets().attach(event.getPlayer());
        if (plugin.state().active(Setting.JOIN_QUIT)) {
            staffMessage(event.joinMessage());
            event.joinMessage(null);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) plugin.packets().refresh(event.getPlayer());
            for (Player viewer : Bukkit.getOnlinePlayers()) plugin.packets().teams(viewer);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void quit(PlayerQuitEvent event) {
        if (plugin.state().active(Setting.JOIN_QUIT)) {
            staffMessage(event.quitMessage());
            event.quitMessage(null);
        }
        plugin.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        if (!plugin.state().active(Setting.CHAT)) return;
        event.setCancelled(true);
        String name = event.getPlayer().getName();
        String body = PlainTextComponentSerializer.plainText().serialize(event.message());
        Set<Audience> audiences = Set.copyOf(event.viewers());
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component real = chatLine(Component.text(name), body);
            Component hidden = chatLine(MASK, body);
            for (Audience audience : audiences) {
                if (audience instanceof Player player) {
                    if (player.isOnline()) player.sendMessage(plugin.state().mask(player.getUniqueId(), Setting.CHAT) ? hidden : real);
                } else audience.sendMessage(real);
            }
        });
    }

    static Component chatLine(Component name, String body) {
        return Component.text("<", NamedTextColor.WHITE).append(name).append(Component.text("> " + body));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        if (!plugin.state().active(Setting.DEATHS)) return;
        staffMessage(event.deathMessage());
        event.deathMessage(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void advancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.state().active(Setting.ADVANCEMENTS)) return;
        staffMessage(event.message());
        event.message(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void ping(PaperServerListPingEvent event) {
        if (plugin.state().active(Setting.TAB) || plugin.state().active(Setting.LOCATOR)) {
                event.setHidePlayers(true);
        }
    }

    private void staffMessage(Component message) {
        if (message == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.state().checks().contains(player.getUniqueId()) && AnonymousCommand.allowed(player)) player.sendMessage(message);
        }
    }
}
