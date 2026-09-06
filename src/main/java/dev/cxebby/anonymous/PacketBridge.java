package dev.cxebby.anonymous;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.connection.PaperCommonConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard;
import org.bukkit.entity.Player;

final class PacketBridge {
    static final String MASK = "\u00a7kXXXX";
    static final String TEAM = "anonymous_smp";
    private static final String HANDLER = "anonymous_smp";
    private final AnonymousSMP plugin;
    private final Field configurationHandle;
    private final Constructor<ClientboundSetPlayerTeamPacket> teamConstructor;
    private final Method refreshSelf;
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pendingSelf = new java.util.HashSet<>();

    PacketBridge(AnonymousSMP plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        configurationHandle = PaperCommonConnection.class.getDeclaredField("handle");
        configurationHandle.setAccessible(true);
        teamConstructor = ClientboundSetPlayerTeamPacket.class.getDeclaredConstructor(String.class, int.class,
                Optional.class, Collection.class);
        teamConstructor.setAccessible(true);
        refreshSelf = CraftPlayer.class.getDeclaredMethod("refreshPlayer");
        refreshSelf.setAccessible(true);
    }

    void attach(PlayerConfigurationConnection connection) {
        try {
            var handle = (ServerCommonPacketListenerImpl) configurationHandle.get(connection);
            attach(connection.getProfile().getId(), handle.connection.channel);
        } catch (ReflectiveOperationException ex) {
            connection.disconnect(net.kyori.adventure.text.Component.text("AnonymousSMP could not protect this connection."));
            plugin.getLogger().log(Level.SEVERE, "Could not protect a configuring connection", ex);
        }
    }

    void attach(Player player) {
        attach(player.getUniqueId(), ((CraftPlayer) player).getHandle().connection.connection.channel);
    }

    private void attach(UUID viewer, Channel channel) {
        channels.put(viewer, channel);
        Runnable install = () -> {
            if (channel.pipeline().get(HANDLER) == null)
                channel.pipeline().addBefore("packet_handler", HANDLER, new PrivacyHandler(viewer));
        };
        if (channel.eventLoop().inEventLoop()) install.run();
        else channel.eventLoop().submit(install).syncUninterruptibly();
    }

    void forget(UUID id) { channels.remove(id); pendingSelf.remove(id); }

    void refresh(Player viewer) {
        if (!viewer.isOnline()) return;
        try {
            teams(viewer);
            var handle = ((CraftPlayer) viewer).getHandle();
            handle.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(viewer.getUniqueId())));
            handle.connection.send(ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(handle, true));
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target.equals(viewer) || !viewer.canSee(target)) continue;
                boolean listed = viewer.isListed(target);
                viewer.hidePlayer(plugin, target);
                viewer.showPlayer(plugin, target);
                if (!listed) viewer.unlistPlayer(target);
            }
            pendingSelf.add(viewer.getUniqueId());
            flushSelf();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not refresh an anonymous view", ex);
        }
    }

    void flushSelf() {
        for (var iterator = pendingSelf.iterator(); iterator.hasNext();) {
            Player viewer = Bukkit.getPlayer(iterator.next());
            if (viewer == null) { iterator.remove(); continue; }
            if (viewer.isDead() || viewer.isInsideVehicle() || SettingsMenu.isOpen(viewer)) continue;
            try { refreshSelf.invoke(viewer); }
            catch (ReflectiveOperationException ex) { plugin.getLogger().log(Level.SEVERE, "Could not refresh the local skin", ex); }
            iterator.remove();
        }
    }

    void teams(Player viewer) {
        var connection = ((CraftPlayer) viewer).getHandle().connection;
        connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team(List.of())));
        var scoreboard = ((CraftScoreboard) viewer.getScoreboard()).getHandle();
        for (PlayerTeam original : scoreboard.getPlayerTeams()) {
            connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(original, false));
            if (!original.getPlayers().isEmpty()) connection.send(ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(
                    original, original.getPlayers(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
        ViewState state = plugin.state();
        if (state.mask(viewer.getUniqueId(), Setting.NAMETAGS)) {
            Collection<String> names = state.maskProfile(viewer.getUniqueId()) ? List.of(MASK) : state.names();
            connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(names), true));
        }
    }

    static PlayerTeam team(Collection<String> names) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), TEAM);
        team.setNameTagVisibility(Team.Visibility.ALWAYS);
        team.setSeeFriendlyInvisibles(false);
        team.getPlayers().addAll(names);
        return team;
    }

    ClientboundPlayerInfoUpdatePacket rewriteInfo(UUID viewer, ViewState state, ClientboundPlayerInfoUpdatePacket packet) {
        boolean names = state.maskProfile(viewer);
        boolean tab = state.mask(viewer, Setting.TAB);
        boolean skins = state.mask(viewer, Setting.SKINS);
        if (!names && !tab && !skins) return packet;
        var actions = EnumSet.copyOf(packet.actions());
        if (tab) actions.add(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>(packet.entries().size());
        for (var entry : packet.entries()) {
            GameProfile original = entry.profile();
            GameProfile profile = original == null ? null : new GameProfile(original.id(), names ? MASK : original.name(),
                    skins ? SkinData.steve() : original.properties());
            Component display = tab ? Component.literal("XXXX").withStyle(ChatFormatting.OBFUSCATED) : entry.displayName();
            if (!tab && names && display == null && original != null) display = Component.literal(original.name());
            entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(entry.profileId(), profile, entry.listed(), entry.latency(),
                    entry.gameMode(), display, entry.showHat(), entry.listOrder(), entry.chatSession()));
        }
        return new ClientboundPlayerInfoUpdatePacket(actions, entries);
    }

    private ClientboundSetPlayerTeamPacket rewriteTeam(ViewState state, ClientboundSetPlayerTeamPacket packet)
            throws ReflectiveOperationException {
        List<String> members = packet.getPlayers().stream().filter(name -> !state.names().contains(name)).toList();
        if (members.size() == packet.getPlayers().size()) return packet;
        int method = packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.ADD ? 0
                : packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE ? 1
                : packet.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.ADD ? 3
                : packet.getPlayerAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE ? 4 : 2;
        return teamConstructor.newInstance(packet.getName(), method, packet.getParameters(), members);
    }

    void close() {
        for (Channel channel : channels.values()) {
            Runnable remove = () -> { if (channel.pipeline().get(HANDLER) != null) channel.pipeline().remove(HANDLER); };
            if (channel.eventLoop().inEventLoop()) remove.run();
            else if (channel.isOpen()) channel.eventLoop().submit(remove).syncUninterruptibly();
        }
        channels.clear();
    }

    private final class PrivacyHandler extends ChannelDuplexHandler {
        private final UUID viewer;
        private boolean hasTeam;
        private boolean failed;

        PrivacyHandler(UUID viewer) { this.viewer = viewer; }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            channels.remove(viewer, ctx.channel());
            super.channelInactive(ctx);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            try {
                Object result = rewrite(ctx, message, plugin.state());
                if (result == null) promise.setSuccess();
                else super.write(ctx, result, promise);
            } catch (Exception ex) {
                promise.tryFailure(ex);
                if (!failed) {
                    failed = true;
                    plugin.getLogger().log(Level.SEVERE, "Anonymous packet protection failed; closing the affected connection", ex);
                }
                ctx.close();
            }
        }

        private Object rewrite(ChannelHandlerContext ctx, Object message, ViewState state) throws ReflectiveOperationException {
            if (message instanceof ClientboundBundlePacket bundle) {
                List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
                for (Packet<? super ClientGamePacketListener> packet : bundle.subPackets()) {
                    Object rewritten = rewrite(ctx, packet, state);
                    if (rewritten != null) packets.add(cast(rewritten));
                }
                return new ClientboundBundlePacket(packets);
            }
            if (message instanceof ClientboundPlayerInfoUpdatePacket info) {
                if (state.mask(viewer, Setting.NAMETAGS)) {
                    Collection<String> names = state.maskProfile(viewer) ? List.of(MASK) : info.entries().stream()
                            .map(ClientboundPlayerInfoUpdatePacket.Entry::profile).filter(java.util.Objects::nonNull)
                            .map(GameProfile::name).toList();
                    if (!hasTeam) {
                        ctx.write(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(names), true));
                        hasTeam = true;
                    } else if (!names.isEmpty()) {
                        ctx.write(ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(team(names), names,
                                ClientboundSetPlayerTeamPacket.Action.ADD));
                    }
                }
                return rewriteInfo(viewer, state, info);
            }
            if (message instanceof ClientboundSetPlayerTeamPacket packet) {
                if (packet.getName().equals(TEAM)) {
                    if (packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
                        if (!hasTeam) return null;
                        hasTeam = false;
                    } else if (packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.ADD) {
                        if (hasTeam) ctx.write(ClientboundSetPlayerTeamPacket.createRemovePacket(team(List.of())));
                        hasTeam = true;
                    }
                    return packet;
                }
                if (state.mask(viewer, Setting.NAMETAGS) || state.maskProfile(viewer)) return rewriteTeam(state, packet);
            }
            if (message instanceof ClientboundPlayerCombatKillPacket death && state.mask(viewer, Setting.DEATHS))
                return new ClientboundPlayerCombatKillPacket(death.playerId(), Component.literal("You died."));
            return message;
        }

        @SuppressWarnings("unchecked")
        private Packet<? super ClientGamePacketListener> cast(Object packet) {
            return (Packet<? super ClientGamePacketListener>) packet;
        }
    }
}
