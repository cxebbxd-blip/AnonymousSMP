package dev.cxebby.anonymous;

import java.util.Set;
import java.util.UUID;

record ViewState(Set<Setting> enabled, Set<UUID> checks, Set<String> names) {
    static final ViewState OFF = new ViewState(Set.of(), Set.of(), Set.of());

    ViewState {
        enabled = Set.copyOf(enabled);
        checks = Set.copyOf(checks);
        names = Set.copyOf(names);
    }

    boolean active(Setting setting) {
        return enabled.contains(Setting.SCRAMBLING) && enabled.contains(setting);
    }

    boolean mask(UUID viewer, Setting setting) {
        return active(setting) && !checks.contains(viewer);
    }

    boolean maskProfile(UUID viewer) {
        return mask(viewer, Setting.NAMETAGS) || mask(viewer, Setting.TAB) || mask(viewer, Setting.LOCATOR);
    }
}
