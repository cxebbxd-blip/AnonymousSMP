package dev.cxebby.anonymous;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

final class SkinData {
    private static PropertyMap steve;
    private static ProfileProperty author;

    static void load(AnonymousSMP plugin) throws IOException {
        Properties data = new Properties();
        try (InputStream input = plugin.getResource("skins.properties")) {
            if (input == null) throw new IOException("Missing bundled skin data");
            data.load(input);
        }
        String value = required(data, "steve.value");
        String signature = required(data, "steve.signature");
        steve = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value, signature)));
        author = new ProfileProperty("textures", required(data, "author.value"), required(data, "author.signature"));
    }

    private static String required(Properties data, String key) throws IOException {
        String value = data.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing " + key);
        return value;
    }

    static PropertyMap steve() { return steve; }

    static void applyAuthor(SkullMeta meta) {
        var profile = Bukkit.createProfile(UUID.fromString("619cd8fd-5d1b-44c9-889e-784f14841e3d"), "Cxebby");
        profile.setProperty(author);
        meta.setPlayerProfile(profile);
    }
}
