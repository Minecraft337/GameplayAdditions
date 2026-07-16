# GameplayAdditions

A comprehensive **NeoForge 1.21** mod for Minecraft that adds new mechanics, security features, and quality‑of‑life improvements. Originally ported from the **MC-Plugin** Bukkit plugin ecosystem.

## Features

### Security
- **Authentication system** — Key‑based login with encrypted `.key` files
- **Anti‑Cheat** — 35+ checks: AimAssist, KillAura, Reach, Speed, Flight, Jesus, Scaffold, FastBreak/Place, Timer, AutoClicker, and many more
- **Bot protection** — Captcha and rate‑limiting
- **Code panel** — GUI-based access code entry
- **OP whitelist & blacklist** — Granular permission control

### Mechanics
- **Custom crafting** — 20+ custom recipes (Plasma Cannon, Shoker, Particle Engine, Chunk Loader, etc.)
- **Energy system** — Generators, reactors, cables, batteries, machines (assembler, electric furnace, energy workbench)
- **Environment** — Lightning, magnet, radiation zones
- **Player features** — Elytra boost, leash, vanish, attributes, shield slowness
- **World features** — Antimatter, chunk loaders, concrete buckets, death bell, dragon egg, waypoints, wireless redstone

### Tools & Utility
- **Omniscanner** — Full admin GUI for scanning players, entities, chunks
- **Scanner items** — Metal detector, ore finder, mob finder, portable radar
- **Integrity system** — Tool integrity with piercing, combining, repair
- **Notes system** — Persistent player notes with GUI
- **MOTD, Scoreboard, Tab, BossBar** — Display customization
- **Economy** — Vault integration, income, player join rewards

### Server Administration
- **Maintenance mode**
- **Punishment system** — Ban, mute, warn with database persistence
- **Report system** — Player reports with admin GUI
- **Server overload protection** — Redstone guard, packet guard, entity kill limits
- **Datapack installer**

## Getting Started

### Prerequisites
- NeoForge 1.21 (MDK)
- Java 21+

### Build
```bash
cd GameplayAdditions
./gradlew build
```

The built JAR will be in `build/libs/`. Place it in your Minecraft server's `mods/` folder.

### Development
Open the project in IntelliJ IDEA and run:
```bash
./gradlew runClient   # Start a test client
./gradlew runServer   # Start a test server
```

## Project Structure

```
GameplayAdditions/
├── src/main/java/com/gameplayadditions/
│   ├── mechanics/
│   │   ├── crafting/        — Custom recipe handlers
│   │   ├── environment/     — Lightning, magnet, radiation
│   │   ├── features/        — Blocks, items, movement, world
│   │   ├── particle/        — Particle accelerator system
│   │   └── security/        — Auth, anticheat, botprotect
│   ├── energy/              — Power grid: generators → cables → machines
│   ├── economy/             — Virtual currency, Vault hook
│   ├── command/             — All commands and subcommands
│   ├── module/              — Modular feature toggle system
│   └── util/#                — Shared utilities
├── settings.gradle.kts
└── build.gradle.kts         — NeoForge mod build
```

## License

This project is licensed under the **GNU Affero General Public License v3.0**.  
See the [LICENSE](./LICENSE) file for details.
