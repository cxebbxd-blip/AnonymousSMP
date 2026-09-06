package dev.cxebby.anonymous;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

final class LocatorProtection implements Listener {
    private final AnonymousSMP plugin;
    private final Map<UUID, Boolean> originals = new HashMap<>();

    LocatorProtection(AnonymousSMP plugin) {
        this.plugin = plugin;
        var section = plugin.getConfig().getConfigurationSection("locator-restore");
        if (section != null) for (String key : section.getKeys(false)) {
            try { originals.put(UUID.fromString(key), section.getBoolean(key)); }
            catch (IllegalArgumentException ex) { plugin.getLogger().warning("Ignoring invalid saved world UUID: " + key); }
        }
    }

    void apply() {
        if (!plugin.state().active(Setting.LOCATOR)) {
            restore();
            return;
        }
        for (World world : Bukkit.getWorlds()) protect(world);
    }

    private void protect(World world) {
        if (!originals.containsKey(world.getUID())) {
            originals.put(world.getUID(), Boolean.TRUE.equals(world.getGameRuleValue(GameRules.LOCATOR_BAR)));
            save();
        }
        world.setGameRule(GameRules.LOCATOR_BAR, false);
    }

    void restore() {
        for (World world : Bukkit.getWorlds()) {
            Boolean original = originals.remove(world.getUID());
            if (original != null) world.setGameRule(GameRules.LOCATOR_BAR, original);
        }
        save();
    }

    private void save() {
        plugin.getConfig().set("locator-restore", null);
        originals.forEach((id, value) -> plugin.getConfig().set("locator-restore." + id, value));
        plugin.saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRuleChange(WorldGameRuleChangeEvent event) {
        if (plugin.state().active(Setting.LOCATOR) && event.getGameRule().equals(GameRules.LOCATOR_BAR)
                && Boolean.parseBoolean(event.getValue())) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (plugin.state().active(Setting.LOCATOR)) protect(event.getWorld());
        else {
            Boolean original = originals.remove(event.getWorld().getUID());
            if (original != null) {
                event.getWorld().setGameRule(GameRules.LOCATOR_BAR, original);
                save();
            }
        }
    }
}
