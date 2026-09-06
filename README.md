# AnonymousSMP

A lightweight anonymity plugin for Paper 1.21.11 by Cxebby. Requires Java 21. No additional plugin, client mod, resource pack, or database is required.

## Install

Place `AnonymousSMP.jar` in your server's `plugins` directory and restart the server. Global scrambling starts off. When updating, remove the old JAR first and keep `plugins/AnonymousSMP/config.yml`.

## Commands

| Command | Action |
| --- | --- |
| `/anonymous settings` | Open the settings inventory |
| `/anonymous scramble` | Enable global scrambling |
| `/anonymous unscramble` | Disable scrambling and restore normal identities |
| `/anonymous check` | Toggle real identities only for the staff member running it |

All commands require `anonymous.admin`. Operators automatically have access, but still need `/anonymous check` to reveal identities. The private check view clears on disconnect and is revoked when permission is lost.

## Settings

The single inventory has nine toggles: global scrambling, Steve skins, nametags, TAB, chat, join/quit messages, death messages, advancements, and locator protection. Each toggle has a gray description and a green YES or red NO state. Changes save immediately and play the vanilla button sound.

Names appear as exactly four obfuscated letters in TAB, chat, and above players. Skins use a bundled signed classic Steve texture without capes. Enabled announcement filters hide join, quit, death, and advancement messages from ordinary players; checking staff receive the originals. Locator protection disables the vanilla locator bar and restores its previous state when disabled.

Click Cxebby's credits head to close the inventory and receive two chat lines:

**Join my Discord!** in bold gray, followed by **discord.gg/Z7fYhESTH** in bold blue. The second line opens the invite when clicked. There are no brackets or plugin prefixes. The message is sent only to the person who clicked.

## Build

Use Java 21:

```sh
./gradlew clean build
```

On Windows, use `gradlew.bat clean build`. The wrapper selects Gradle 9.3.1. The output is `build/libs/AnonymousSMP.jar`.

The plugin uses the Paper API with a small version specific packet adapter for 1.21.11. Java source and tests contain no explanatory comments. The original Gradle launcher license headers are preserved.

## Verification

The Gradle build runs unit tests and verifies both bundled texture signatures against Mojang's public keys. The separate `verification` harness launches a disposable localhost Paper server with synthetic offline accounts and checks the packets received by multiple headless clients.

```sh
python verification/assets.py
./gradlew clean build
npm install --no-save mineflayer@4.39.0
node verification/smoke.js
```

Run the harness in a disposable development directory, not a live server directory. Use Node.js 22 and Python 3. It writes under `verification/server` and `verification/results` and accepts the Minecraft EULA for that test server. It does not run inside the plugin. See [VERIFICATION.md](VERIFICATION.md) for the tested scope.

## Privacy limits

This provides gameplay masking, not guaranteed anonymity against arbitrary client mods. Actual UUIDs remain visible. Cached identities, UUID lookups, earlier observations, and external services may recover real names or skins. Other plugins' commands, custom messages, books, items, sidebars, and player supplied text are not universally redacted. Other TAB, chat, skin, and nametag plugins need compatibility testing.

The nametag, TAB, and locator switches share the outgoing anonymous profile name. Disabling the nametag override removes its custom team, but the profile name may remain scrambled while TAB or locator masking is still enabled. Global unscramble restores the ordinary view.

## License and support

Source is available under the [MIT license](LICENSE). Third party notices are in [NOTICE](NOTICE). Minecraft textures and the Gradle wrapper retain their original ownership and licensing. The running plugin makes no external API requests.

[Discord](https://discord.gg/Z7fYhESTH) · [Source code](https://github.com/cxebbxd-blip/AnonymousSMP) · [Report a bug](https://github.com/cxebbxd-blip/AnonymousSMP/issues)
