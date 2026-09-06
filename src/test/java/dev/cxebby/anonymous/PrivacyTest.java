package dev.cxebby.anonymous;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class PrivacyTest {
    private final UUID ordinary = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();

    @Test void globalOffWinsOverIndividualFeatures() {
        var enabled = EnumSet.allOf(Setting.class);
        enabled.remove(Setting.SCRAMBLING);
        var state = new ViewState(enabled, Set.of(), Set.of());
        for (Setting setting : Setting.values()) assertFalse(state.mask(ordinary, setting));
    }

    @Test void checkOnlyBypassesTheChosenViewer() {
        var state = new ViewState(EnumSet.allOf(Setting.class), Set.of(staff), Set.of());
        for (Setting setting : Setting.values()) {
            assertTrue(state.mask(ordinary, setting));
            assertFalse(state.mask(staff, setting));
        }
    }

    @Test void individualSwitchesRemainIndependent() {
        var state = new ViewState(Set.of(Setting.SCRAMBLING, Setting.CHAT), Set.of(), Set.of());
        assertTrue(state.mask(ordinary, Setting.CHAT));
        assertFalse(state.mask(ordinary, Setting.SKINS));
        assertFalse(state.maskProfile(ordinary));
    }

    @Test void locatorProtectionMasksRawProfileNames() {
        var state = new ViewState(Set.of(Setting.SCRAMBLING, Setting.LOCATOR), Set.of(), Set.of());
        assertTrue(state.maskProfile(ordinary));
        assertFalse(state.mask(ordinary, Setting.TAB));
    }

    @Test void nametagsAloneMaskRawProfileNames() {
        var state = new ViewState(Set.of(Setting.SCRAMBLING, Setting.NAMETAGS), Set.of(staff), Set.of());
        assertTrue(state.maskProfile(ordinary));
        assertFalse(state.mask(ordinary, Setting.TAB));
        assertFalse(state.mask(ordinary, Setting.LOCATOR));
        assertFalse(state.maskProfile(staff));
    }

    @Test void nametagTeamShowsExactlyFourObfuscatedLetters() {
        var team = PacketBridge.team(java.util.List.of(PacketBridge.MASK));
        assertEquals(net.minecraft.world.scores.Team.Visibility.ALWAYS, team.getNameTagVisibility());
        assertEquals(Set.of(PacketBridge.MASK), Set.copyOf(team.getPlayers()));
        assertFalse(team.canSeeFriendlyInvisibles());
        var name = team.getFormattedName(net.minecraft.network.chat.Component.literal(PacketBridge.MASK));
        var letters = new StringBuilder();
        net.minecraft.util.StringDecomposer.iterateFormatted(name, net.minecraft.network.chat.Style.EMPTY,
                (index, style, codePoint) -> {
                    assertTrue(style.isObfuscated());
                    letters.appendCodePoint(codePoint);
                    return true;
                });
        assertEquals("XXXX", letters.toString());
    }

    @Test void settingTextHasNoDashes() {
        for (Setting setting : Setting.values()) {
            assertFalse(setting.title.matches(".*[\u002d\u2013\u2014].*"));
            assertFalse(setting.description.matches(".*[\u002d\u2013\u2014].*"));
        }
        assertEquals("Scramble nametags", Setting.NAMETAGS.title);
    }

    @Test void snapshotsCannotChangeFromAnotherThread() {
        var enabled = EnumSet.allOf(Setting.class);
        var checks = new HashSet<UUID>();
        var names = new HashSet<String>();
        names.add("Alice");
        var state = new ViewState(enabled, checks, names);
        enabled.clear(); checks.add(ordinary); names.clear();
        assertTrue(state.mask(ordinary, Setting.CHAT));
        assertEquals(Set.of("Alice"), state.names());
        assertThrows(UnsupportedOperationException.class, () -> state.checks().add(staff));
    }

    @Test void displayIsExactlyFourObfuscatedLetters() {
        assertEquals("XXXX", PlainTextComponentSerializer.plainText().serialize(PrivacyListener.MASK));
        assertEquals(TextDecoration.State.TRUE, PrivacyListener.MASK.decoration(TextDecoration.OBFUSCATED));
        assertEquals("\u00a7kXXXX", PacketBridge.MASK);
        assertNull(PrivacyListener.MASK.hoverEvent());
        assertNull(PrivacyListener.MASK.clickEvent());
    }

    @Test void obfuscationDoesNotReachTheMessageBody() {
        var line = PrivacyListener.chatLine(PrivacyListener.MASK, "hello");
        assertEquals("<XXXX> hello", PlainTextComponentSerializer.plainText().serialize(line));
        assertNotEquals(TextDecoration.State.TRUE, line.decoration(TextDecoration.OBFUSCATED));
        assertNotEquals(TextDecoration.State.TRUE, line.children().getLast().decoration(TextDecoration.OBFUSCATED));
    }

    @Test void denialIsExactPlainRedText() {
        assertEquals("You do not have permission to use this command.", PlainTextComponentSerializer.plainText().serialize(AnonymousCommand.DENIED));
        assertEquals(NamedTextColor.RED, AnonymousCommand.DENIED.color());
        assertTrue(AnonymousCommand.DENIED.children().isEmpty());
    }

    @Test void operatorsAlwaysHaveAccess() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.isOp()).thenReturn(true);
        when(sender.hasPermission(AnonymousCommand.PERMISSION)).thenReturn(false);
        assertTrue(AnonymousCommand.allowed(sender));
    }

    @Test void permissionWorksWithoutOperatorStatus() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(AnonymousCommand.PERMISSION)).thenReturn(true);
        assertTrue(AnonymousCommand.allowed(sender));
    }

    @Test void ordinaryPlayersHaveNoAccess() {
        assertFalse(AnonymousCommand.allowed(mock(CommandSender.class)));
    }

    @Test void discordTitleIsExactlyBoldGray() {
        var title = SettingsMenu.DISCORD_TITLE;
        assertEquals("Join my Discord!", PlainTextComponentSerializer.plainText().serialize(title));
        assertEquals(NamedTextColor.GRAY, title.color());
        assertEquals(TextDecoration.State.TRUE, title.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.FALSE, title.decoration(TextDecoration.ITALIC));
        assertTrue(title.children().isEmpty());
        assertNull(title.clickEvent());
    }

    @Test void discordInviteIsExactlyBoldBlue() {
        var invite = SettingsMenu.DISCORD_INVITE;
        assertEquals("discord.gg/Z7fYhESTH", PlainTextComponentSerializer.plainText().serialize(invite));
        assertEquals(NamedTextColor.BLUE, invite.color());
        assertEquals(TextDecoration.State.TRUE, invite.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.FALSE, invite.decoration(TextDecoration.ITALIC));
        assertTrue(invite.children().isEmpty());
    }

    @Test void discordInviteOpensTheCorrectHttpsAddress() {
        var event = SettingsMenu.DISCORD_INVITE.clickEvent();
        assertNotNull(event);
        assertEquals(net.kyori.adventure.text.event.ClickEvent.Action.OPEN_URL, event.action());
        assertEquals("https://discord.gg/Z7fYhESTH", event.value());
    }

    @Test void discordMessagesHaveNoBracketsDashesOrPrefix() {
        for (var line : java.util.List.of(SettingsMenu.DISCORD_TITLE, SettingsMenu.DISCORD_INVITE)) {
            String text = PlainTextComponentSerializer.plainText().serialize(line);
            assertTrue(text.chars().noneMatch(c -> c == '[' || c == ']' || c == '-'
                    || c == '\u2013' || c == '\u2014'));
            assertFalse(text.contains("AnonymousSMP"));
            assertFalse(text.contains("\n"));
        }
    }

    @Test void guiContainsAllNineSettingsInUniqueSlots() {
        assertEquals(9, Setting.values().length);
        Set<Integer> slots = new HashSet<>();
        for (Setting setting : Setting.values()) {
            assertTrue(slots.add(setting.slot));
            assertTrue(setting.slot >= 0 && setting.slot < 45);
            assertNotEquals(4, setting.slot);
            assertSame(setting, Setting.at(setting.slot));
            assertFalse(setting.description.isBlank());
        }
        assertNull(Setting.at(-1));
        assertNull(Setting.at(4));
        assertNull(Setting.at(49));
    }
}
