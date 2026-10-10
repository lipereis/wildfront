# Wildfront

A Minecraft Java Edition mod (Fabric) about dynamic exploration: rare world events, mysterious
discoveries and abandoned places that make the world feel unpredictable and alive, without
replacing vanilla survival.

**Status:** Milestone 2 — dynamic event system with one event, *Mysterious Discovery*, which
notifies a player who is out exploring that something unusual happened nearby.

## Versions

| Component     | Version       |
|---------------|---------------|
| Minecraft     | 26.3          |
| Fabric Loader | 0.19.5        |
| Fabric API    | 0.162.0+26.3  |
| Fabric Loom   | 1.18.4        |
| Gradle        | 9.7.1 (wrapper) |
| Java          | JDK 25        |

Versions live in [gradle.properties](gradle.properties).

## Setup

1. Install JDK 25:

   ```
   winget install EclipseAdoptium.Temurin.25.JDK
   ```

2. Make sure `JAVA_HOME` points at the JDK 25 install directory and open a new terminal.
   `java -version` must report 25.
3. Gradle does not need to be installed; the wrapper (`gradlew.bat`) downloads it.

The first build downloads Gradle, Minecraft and all dependencies, which takes several minutes.

## Build

```
.\gradlew.bat build
```

Runs the unit tests and writes the mod jar to `build/libs/wildfront-<version>.jar`.

To run only the tests:

```
.\gradlew.bat test
```

## Development client

```
.\gradlew.bat runClient
```

Starts Minecraft 26.3 with Wildfront loaded. The game directory is `run/`.
To confirm the mod loaded, look for these lines in the console or in `run/logs/latest.log`:

```
[Wildfront] Registered 1 event(s): [mysterious_discovery]
[Wildfront] Initialized (version 0.1.0, environment CLIENT)
[Wildfront] Client initialized
```

A dedicated development server is available with `.\gradlew.bat runServer`
(accept the EULA in `run/eula.txt` on first start).

## Configuration

`config/wildfront.json` (in the dev environment: `run/config/wildfront.json`) is created on first
start. Apply edits with `/wildfront reload` or by restarting.

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `eventsEnabled` | `true` | | Master switch for automatic events |
| `checkIntervalSeconds` | `10` | 1 – 3600 | How often online players are checked |
| `mysteriousDiscovery.enabled` | `true` | | Whether the event triggers on its own |
| `mysteriousDiscovery.cooldownSeconds` | `600` | 0 – 604800 | Minimum time between two triggers for the same player |
| `mysteriousDiscovery.triggerChance` | `0.05` | 0 – 1 | Chance per check, once the cooldown is over, that the event triggers |
| `mysteriousDiscovery.minDistanceFromSpawn` | `128` | 0 – 1000000 | Horizontal distance in blocks from world spawn required |

A missing, mistyped or out-of-range value is replaced by its default and reported in the log. A file
that is not valid JSON is left untouched and defaults are used for everything.

Mysterious Discovery triggers only for living players in survival or adventure mode in the
overworld. Cooldowns are stored per player and survive death, relogging and restarts.

## Commands

All require operator permission (game master level; cheats enabled in singleplayer).

| Command | Effect |
|---------|--------|
| `/wildfront event list` | Shows events, their settings and your remaining cooldown |
| `/wildfront event trigger <event> [player]` | Triggers an event now, ignoring cooldown, chance and conditions, and starts its cooldown |
| `/wildfront event reset [player]` | Clears the player's event cooldowns |
| `/wildfront reload` | Reloads the config file and reports problems |

## Testing the event in a world

1. `.\gradlew.bat runClient`, create a **survival** world with **cheats allowed**.
2. `/wildfront event trigger mysterious_discovery` — a purple chat message and an action-bar
   message appear, and the log shows `Event 'mysterious_discovery' triggered for ...`.
3. To see automatic triggering quickly, set this in `run/config/wildfront.json` and run
   `/wildfront reload`:

   ```json
   {
     "checkIntervalSeconds": 1,
     "mysteriousDiscovery": { "cooldownSeconds": 15, "triggerChance": 1, "minDistanceFromSpawn": 0 }
   }
   ```

   The event then triggers once every 15 seconds. `/wildfront event list` shows the countdown.
4. Restore the defaults afterwards (delete the file and run `/wildfront reload`).

## Project layout

```
src/main/java/dev/wildfront/
  Wildfront.java       mod entrypoint
  event/               WorldEvent (definition), EventScheduler (rules), EventManager (server hook)
  event/type/          concrete events
  registry/            central registration of events and data attachments
  config/              config file loading and validation
  command/             /wildfront commands
src/main/resources/    fabric.mod.json, data and assets
src/client/java/dev/wildfront/client   client-only code
src/test/java/         JUnit tests
```

## License

MIT, see [LICENSE](LICENSE).
