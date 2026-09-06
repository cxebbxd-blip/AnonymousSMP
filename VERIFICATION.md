# Verification

Version 1.0.2 targets Paper 1.21.11 and Java 21.

## Current build

Run `./gradlew clean build` with Java 21. GitHub Actions runs this same command and publishes the JAR and Java test reports. There is no Node.js or Python requirement.

The Java tests cover masking rules, permissions, the nametag team, chat formatting, GUI slots, Discord message styles and URL actions, and both bundled Mojang texture signatures.

## Earlier live server checks

The 1.0.2 release also passed separate automated checks against a disposable Paper server with multiple headless clients. Those covered startup, staff view isolation, visible scrambled nametags, announcements, GUI toggles, both credits head mouse buttons, private Discord messages, and restoration.

The external live server test helpers were removed from the current source tree to keep this repository focused on the Java plugin. The current workflow runs the Java tests only; it does not repeat those earlier live server checks. The helpers remain available in Git history.

These checks are not a graphical playtest and do not guarantee compatibility with every plugin or client mod. The plugin implementation and bundled resources are unchanged by the repository cleanup.
