# Railroad IDE

![Railroad IDE Logo](/src/main/resources/assets/railroad/images/logo.png)

![Static Badge](https://img.shields.io/badge/java-21-orange?logo=openjdk&label=Java)
![Static Badge](https://img.shields.io/badge/JavaFX-grey?logo=openjdk)

Railroad IDE is a JavaFX-powered IDE built to streamline Minecraft mod development on Forge, Fabric, and NeoForge. The
project focuses on fast setup, discoverable tooling, and ergonomic workflows tailored to mod authors.

## Features

- **Minecraft-first workflow** — Dedicated tooling for Forge, Fabric, and NeoForge mod projects.
- **Project scaffolding** — Ready-to-use templates to spin up new mods in minutes.
- **Community-built** — Created by modders for modders, shaped by active feedback from the Railroad community.
- **Modding-focused vision** — Roadmap prioritises content creation, debugging, and asset tooling specific to Minecraft
  projects.

## Why Java?

Minecraft, its modding APIs, and the bulk of modding tooling are written in Java. By building Railroad IDE in Java,
every modder can immediately dive into the codebase, extend it, and benefit from the mature JVM ecosystem (Gradle,
JavaFX, bytecode tooling, and Forge/Fabric/NeoForge integrations). Java keeps the development stack aligned with the
community’s day-to-day workflow.

## Future Plans

Railroad IDE is still growing. The roadmap focuses on closing the gap between a general-purpose IDE and what Minecraft
modders actually need:

- In-IDE texture and model editors for quick asset iteration.
- Audio visualizer to inspect and balance custom sound assets.
- Structure and NBT editors with live previews.
- Mixin debugging with inline mixin visualisation.
- Guided project creation for Forge, Fabric, and NeoForge mods.
- Modding-aware code inspections (e.g., catching inconsistent `modid` usage).
- Visual GUI creator tailored for Minecraft screens.
- JSON authoring helpers for recipes, loot tables, biomes, and more.

## Requirements

- Java 21 (Temurin, Oracle, or any compatible JDK)
- Git
- Gradle 8 or newer — or use the included Gradle wrapper scripts
- Optional: IntelliJ IDEA with the Lombok plugin for the best contributor experience

## Quick Start

```sh
git clone https://github.com/Railroad-Team/Railroad.git
cd Railroad
./gradlew runShadow
```

On Windows, run `gradlew.bat runShadow`.

## Building

Use the bundled Gradle wrapper to build the shaded application JAR:

```sh
./gradlew shadowJar
```

You’ll find the artifact under `build/libs/`. See `BUILDING.md` for detailed setup instructions (IDE configuration,
Lombok, manual environment tweaks, and troubleshooting tips).

## Community & Support

- Website: [railroadide.dev](https://railroadide.dev)
- Discord: Join the Railroad section in [TurtyWurty’s server](https://discord.turtywurty.dev)
- Bugs & feature requests: [create an issue](https://github.com/Railroad-Team/Railroad/issues)

## Contributing

We welcome contributions of all sizes! Start by reading the [`CONTRIBUTING.md`](CONTRIBUTING.md), join the Discord to
discuss ideas, then fork the repo and open a pull request.

## FAQ

### Who started this project?

Railroad IDE was started by [TurtyWurty](https://www.youtube.com/TurtyWurty).

### Is Railroad IDE complete?

Not yet—the IDE is still in active development. Expect rapid iteration, breaking changes, and plenty of opportunities to
help shape the roadmap with the community.

### Is there a website?

Yes: [https://railroadide.dev](https://railroadide.dev).

### Where do I report bugs?

Open a [GitHub issue](https://github.com/Railroad-Team/Railroad/issues) and use the bug report form to share logs,
reproduction steps, and environment details.

### How do I get involved?

1. Join the Discord community section to coordinate with other contributors.
2. Fork this repository and make your changes.
3. Submit a pull request for review.

## License

![The Official GPL v3 Logo](https://www.gnu.org/graphics/gplv3-127x51.png)

Railroad IDE is licensed under the GNU General Public License v3.0 (or later). See [`LICENSE`](LICENSE) for the full
text and distribution terms.
