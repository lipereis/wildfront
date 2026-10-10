# Wildfront

A Minecraft Java Edition mod (Fabric) about dynamic exploration: rare world events, mysterious
discoveries and abandoned places that make the world feel unpredictable and alive, without
replacing vanilla survival.

**Status:** Milestone 3 — dynamic event system with configurable triggers and cooldowns. One event
exists, *Mysterious Discovery*, which notifies players who are out exploring that something unusual
happened nearby.

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

```json
{
  "eventsEnabled": true,
  "checkIntervalSeconds": 10,
  "globalCooldownSeconds": 120,
  "shareRadius": 48,
  "events": {
    "mysterious_discovery": {
      "enabled": true,
      "cooldownSeconds": 600,
      "triggerChance": 0.05,
      "minDistanceFromSpawn": 128,
      "timeOfDay": "any",
      "weather": "any"
    }
  }
}
```

General settings:

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `eventsEnabled` | `true` | | Master switch for automatic events |
| `checkIntervalSeconds` | `10` | 1 – 3600 | How often online players are checked |
| `globalCooldownSeconds` | `120` | 0 – 604800 | Minimum time between any two events for the same player |
| `shareRadius` | `48` | 0 – 512 | Players this close to a triggering player witness the same event instead of getting their own; 0 disables sharing |

Settings of each event under `events.<event id>` (defaults differ per event; the ones shown are
for `mysterious_discovery`):

| Key | Default | Allowed | Meaning |
|-----|---------|---------|---------|
| `enabled` | `true` | | Whether the event triggers on its own |
| `cooldownSeconds` | `600` | 0 – 604800 | Minimum time between two triggers of this event for the same player |
| `triggerChance` | `0.05` | 0 – 1 | Chance per check, once all cooldowns are over, that the event triggers |
| `minDistanceFromSpawn` | `128` | 0 – 1000000 | Horizontal distance in blocks from world spawn required; 0 for none |
| `timeOfDay` | `"any"` | `any`, `day`, `night` | Night is 13000 – 23000 on the day clock |
| `weather` | `"any"` | `any`, `clear`, `rain`, `thunder` | `rain` includes thunderstorms; biomes without rain are not considered |

A missing, mistyped or out-of-range value is replaced by its default and reported in the log and by
`/wildfront reload`. A file that is not valid JSON is left untouched and defaults are used for
everything. Config files from Milestone 2 used a `mysteriousDiscovery` section; it is no longer
read and must be moved to `events.mysterious_discovery`.

### How triggering works

Every `checkIntervalSeconds` the server looks at each living player in survival or adventure mode.
For such a player an event triggers when, in this order: the event is enabled, the player's global
cooldown is over, the event's own cooldown is over, the chance roll succeeds, and the configured
conditions (distance, time of day, weather) plus the event's own requirements hold. Mysterious
Discovery additionally requires the overworld.

One occurrence is shared: eligible players within `shareRadius` blocks of the triggering player
receive the same event and start the same cooldowns, so a group travelling together gets one event,
not one each. At most one event triggers per player per check. Cooldowns are stored per player and
survive death, relogging and restarts.

## Commands

All require operator permission (game master level; cheats enabled in singleplayer).

| Command | Effect |
|---------|--------|
| `/wildfront event list` | Shows settings, your remaining cooldowns and which conditions you are still waiting for |
| `/wildfront event trigger <event> [player]` | Triggers an event now, ignoring cooldowns, chance and conditions, and starts the cooldowns |
| `/wildfront event reset [player]` | Clears the player's event cooldowns |
| `/wildfront reload` | Reloads the config file and reports problems |

## Testing the event in a world

1. `.\gradlew.bat runClient`, create a **survival** world with **cheats allowed**.
2. `/wildfront event trigger mysterious_discovery` — a purple chat message and an action-bar
   message appear, and the log shows `Event 'mysterious_discovery' triggered for [...]`.
3. To see automatic triggering quickly, replace the content of `run/config/wildfront.json` with
   this and run `/wildfront reload`:

   ```json
   {
     "checkIntervalSeconds": 1,
     "globalCooldownSeconds": 0,
     "events": {
       "mysterious_discovery": { "cooldownSeconds": 15, "triggerChance": 1, "minDistanceFromSpawn": 0 }
     }
   }
   ```

   The event then triggers once every 15 seconds. `/wildfront event list` shows the countdown.
4. Conditions: add `"timeOfDay": "night"` to the event, reload, then `/time set day` (no triggers,
   list shows `waiting for: time of day NIGHT`) and `/time set night` (triggers resume). The same
   works with `"weather": "thunder"` and `/weather thunder`.
5. Global cooldown: set `"globalCooldownSeconds": 40` and reload; triggers are now 40 seconds apart
   although the event cooldown is 15.
6. Restore the defaults afterwards (delete the file and run `/wildfront reload`).

## Project layout

```
src/main/java/dev/wildfront/
  Wildfront.java       mod entrypoint
  event/               WorldEvent (definition), EventScheduler (rules), EventManager (server hook)
  event/trigger/       configurable trigger conditions (distance, time of day, weather)
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
