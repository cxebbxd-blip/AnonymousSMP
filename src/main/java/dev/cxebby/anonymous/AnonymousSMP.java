package dev.cxebby.anonymous;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnonymousSMP extends JavaPlugin {
    private final Set<Setting> settings = EnumSet.noneOf(Setting.class);
    private final Set<UUID> checks = new HashSet<>();
    private volatile ViewState state = ViewState.OFF;
    private PacketBridge packets;
    private LocatorProtection locator;
    private SettingsMenu menu;
    private boolean refreshQueued;

    @Override
    public void onEnable() {
        if (!Bukkit.getMinecraftVersion().equals("1.21.11")) {
            getLogger().severe("AnonymousSMP requires Paper 1.21.11.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        for (Setting setting : Setting.values()) {
            if (getConfig().getBoolean(setting.key)) settings.add(setting);
        }
        try {
            SkinData.load(this);
            packets = new PacketBridge(this);
        } catch (Exception ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Cannot initialize the 1.21.11 packet bridge", ex);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        locator = new LocatorProtection(this);
        menu = new SettingsMenu(this);
        AnonymousCommand command = new AnonymousCommand(this);
        Objects.requireNonNull(getCommand("anonymous")).setExecutor(command);
        getCommand("anonymous").setTabCompleter(command);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(new PrivacyListener(this), this);
        Bukkit.getPluginManager().registerEvents(locator, this);
        publish();
        for (Player player : Bukkit.getOnlinePlayers()) packets.attach(player);
        locator.apply();
        queueRefresh();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            packets.flushSelf();
            Set<UUID> revoked = checks.stream().filter(id -> {
                Player player = Bukkit.getPlayer(id);
                return player == null || !AnonymousCommand.allowed(player);
            }).collect(Collectors.toSet());
            if (!revoked.isEmpty()) {
                checks.removeAll(revoked);
                publish();
                for (UUID id : revoked) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) packets.refresh(player);
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void onDisable() {
        state = ViewState.OFF;
        if (menu != null) menu.closeAll();
        if (locator != null) locator.restore();
        if (packets != null) {
            for (Player player : Bukkit.getOnlinePlayers()) packets.refresh(player);
            packets.close();
        }
        checks.clear();
    }

    ViewState state() { return state; }
    SettingsMenu menu() { return menu; }
    PacketBridge packets() { return packets; }
    boolean enabled(Setting setting) { return settings.contains(setting); }

    void set(Setting setting, boolean value) {
        if (value) settings.add(setting); else settings.remove(setting);
        getConfig().set(setting.key, value);
        saveConfig();
        publish();
        locator.apply();
        menu.refresh();
        if (setting == Setting.SCRAMBLING || setting == Setting.SKINS || setting == Setting.NAMETAGS
                || setting == Setting.TAB || setting == Setting.LOCATOR) queueRefresh();
    }

    boolean toggleCheck(Player player) {
        boolean now = checks.add(player.getUniqueId());
        if (!now) checks.remove(player.getUniqueId());
        publish();
        packets.refresh(player);
        return now;
    }

    void forget(Player player) {
        checks.remove(player.getUniqueId());
        packets.forget(player.getUniqueId());
        publish();
    }

    void publish() {
        state = new ViewState(settings, checks, Bukkit.getOnlinePlayers().stream()
                .map(Player::getName).collect(Collectors.toSet()));
    }

    void queueRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        Bukkit.getScheduler().runTask(this, () -> {
            refreshQueued = false;
            if (!isEnabled()) return;
            publish();
            for (Player player : Bukkit.getOnlinePlayers()) packets.refresh(player);
        });
    }
}
