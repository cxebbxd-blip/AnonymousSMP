package dev.cxebby.anonymous;

public enum Setting {
    SCRAMBLING("scrambling", "Global scrambling", "Make everyone anonymous.", 13),
    SKINS("skins", "Steve skins", "Replace player skins and capes.", 20),
    NAMETAGS("nametags", "Scramble nametags", "Show four scrambled letters above players.", 21),
    TAB("tab", "Scramble TAB", "Show four scrambled letters in TAB.", 22),
    CHAT("chat", "Scramble chat", "Hide the sender's name in chat.", 23),
    JOIN_QUIT("join-quit", "Hide join / quit", "Hide join and leave announcements.", 24),
    DEATHS("death-messages", "Hide deaths", "Hide death announcements and killers.", 30),
    ADVANCEMENTS("advancements", "Hide advancements", "Hide advancement announcements.", 31),
    LOCATOR("locator-protection", "Locator protection", "Disable the locator bar in every world.", 32);

    public final String key;
    public final String title;
    public final String description;
    public final int slot;

    Setting(String key, String title, String description, int slot) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.slot = slot;
    }

    public static Setting at(int slot) {
        for (Setting setting : values()) if (setting.slot == slot) return setting;
        return null;
    }
}
