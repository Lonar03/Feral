# Feral
A minecraft mod designed for Hypixel minigames. fabric/oneclient support. not forge

## Installation

- Install [Ornithe](https://ornithemc.net) (Fabric Loader for old versions) for Minecraft 1.8.9.
- Install [Ornithe Standard Libraries](https://modrinth.com/mod/osl) 0.23.1 or newer.
- Drag the Feral jar into your mods folder and launch the game.
- If you want the scripting API to work, make sure the game runs on a JDK (not just a JRE), e.g. [Liberica](https://bell-sw.com/pages/downloads/?version=java-8&vtabs=true).

## Building

- `./gradlew spotlessApply build` builds `build/libs/feral-<version>+mc1.8.9.jar`. Plain `./gradlew build` fails if the code isn't formatted.
- `./gradlew runClient` starts the game with Feral in a dev environment.
- Gradle runs on Java 25 and the game on Java 8. Both are found (or downloaded) automatically.
- Minecraft names come from Ornithe's [Feather](https://github.com/OrnitheMC/feather) mappings.

## Credits

Feral is built on top of **Raven bS**. The base code, modules, the click GUI and the scripting API come from there.

- [Raven bS](https://codeberg.org/strangerrrrs/raven-bS) by Strangerrrs and The_Frontman, MIT licensed (see `LICENSE_raven-bs`)
- [Raven scripting documentation](https://blowsy.gitbook.io/raven) for the scripting API
