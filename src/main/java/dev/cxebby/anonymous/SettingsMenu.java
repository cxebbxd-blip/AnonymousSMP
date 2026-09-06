package dev.cxebby.anonymous;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

final class SettingsMenu implements Listener {
    static final Component DISCORD_TITLE = text("Join my Discord!", NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
    static final Component DISCORD_INVITE = text("discord.gg/Z7fYhESTH", NamedTextColor.BLUE)
            .decorate(TextDecoration.BOLD).clickEvent(ClickEvent.openUrl("https://discord.gg/Z7fYhESTH"));
    private final AnonymousSMP plugin;
    private final ItemStack head;

    SettingsMenu(AnonymousSMP plugin) {
        this.plugin = plugin;
        head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.displayName(text("AnonymousSMP", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        meta.lore(List.of(text("Made by Cxebby", NamedTextColor.GRAY),
                text("discord.gg/Z7fYhESTH", NamedTextColor.GRAY)));
        SkinData.applyAuthor(meta);
        head.setItemMeta(meta);
    }

    static boolean isOpen(Player player) { return player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder; }

    void open(Player player) {
        MenuHolder holder = new MenuHolder();
        holder.inventory = Bukkit.createInventory(holder, 45, Component.text("AnonymousSMP"));
        paint(holder.inventory);
        player.openInventory(holder.inventory);
    }

    void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (inventory.getHolder() instanceof MenuHolder) paint(inventory);
        }
    }

    private void paint(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        inventory.setItem(4, head);
        for (Setting setting : Setting.values()) {
            boolean enabled = plugin.enabled(setting);
            ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.RED_DYE);
            item.editMeta(meta -> {
                meta.displayName(text(setting.title, NamedTextColor.WHITE));
                meta.lore(List.of(text(setting.description, NamedTextColor.GRAY), Component.empty(),
                        text("Enabled: " + (enabled ? "YES" : "NO"),
                                enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
            });
            inventory.setItem(setting.slot, item);
        }
    }

    static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!AnonymousCommand.allowed(player)) {
            player.sendMessage(AnonymousCommand.DENIED);
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return;
        if (event.getRawSlot() == 4) {
            player.sendMessage(DISCORD_TITLE);
            player.sendMessage(DISCORD_INVITE);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        Setting setting = Setting.at(event.getRawSlot());
        if (setting == null) return;
        plugin.set(setting, !plugin.enabled(setting));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) player.closeInventory();
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
