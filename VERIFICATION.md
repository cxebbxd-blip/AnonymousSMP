# Verification

Version 1.0.2 targets Paper 1.21.11 and Java 21. Each published build should be checked using the repository's Gradle tests and disposable live server harness.

The unit tests cover masking rules, permissions, the nametag team, chat formatting, GUI slots, Discord message styles and URL actions, and Mojang texture signatures. The live harness covers startup, multiple clients, staff view isolation, visible scrambled nametags, announcements, GUI toggles, both credits head mouse buttons, private Discord messages, and restoration.

These are automated headless server and packet tests, not a graphical playtest. They do not guarantee compatibility with every plugin or client mod. The GitHub Actions run and downloadable verification report identify the exact tested revision and JAR checksum.
