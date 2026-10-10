**Wildfront makes the overworld a little less predictable.** While you are out exploring, something may appear nearby: a forgotten cache, marked by a faint column of light, that you have a few minutes to find. And in new terrain you will come across abandoned camps whose owners left their supplies behind.

It adds to vanilla survival without replacing any of it: no new dimensions, no new ores, no progression to grind. Everything it places is made of vanilla blocks.

## Mysterious Discovery

Every so often, a player who is far enough from world spawn gets a message such as:

> *You sense that something unusual has appeared to the north-east...*

Somewhere 24 to 48 blocks away in that direction, a **forgotten cache** now stands on the surface. Up close it is marked by a column of white particles and a soft chime.

- Open it and it is yours: a few stacks of supplies (iron, gold, emeralds, bread, arrows, experience bottles, ender pearls) and one valuable find (diamonds, an enchanted book, a name tag, a saddle, a spyglass or a golden apple).
- Ignore it and it quietly disappears after ten minutes, leaving the ground as it was.
- Players travelling together share one discovery instead of each getting their own.
- By default it is rare: at most once every ten minutes per player, and usually less often.

## Abandoned camps

Small, long-deserted traveller's camps generate in forests, taigas, savannas, plains and meadows: a cold fire pit, a collapsed lean-to with a hay bedroll, a crafting table, and a chest of travel supplies such as food, coal, torches, a worn tool, a compass or a map.

Camps only generate in **newly generated chunks**. Existing terrain is never changed.

## Configurable

`config/wildfront.json` is created on first start:

- how often events are checked, and the chance per check
- cooldown per event and a global cooldown between any two events
- conditions: minimum distance from spawn, time of day, weather
- how far away caches appear, how long they stay, and how many may exist at once

Invalid values fall back to their defaults and are reported in the log; a file with a typo is never overwritten. Apply changes with `/wildfront reload`.

How often camps generate, where, and what their chest contains can be changed with a data pack. The loot of the forgotten cache is an ordinary loot table too.

## Commands

For operators:

| Command | Effect |
|---|---|
| `/wildfront event list` | Shows the events, your remaining cooldowns, and which conditions you are still waiting for |
| `/wildfront event trigger <event> [player]` | Triggers an event now |
| `/wildfront event reset [player]` | Clears a player's event cooldowns |
| `/wildfront reload` | Reloads the config file and reports problems |

To see the mod right away: `/wildfront event trigger mysterious_discovery`, and `/locate structure wildfront:abandoned_camp`.

## Good to know

- **Server-side gameplay.** On a server, only the server needs the mod; players without it see the messages in English. In singleplayer, install it as usual.
- **Lightweight.** Nothing scans blocks or loads chunks on a timer. Players are checked once every ten seconds, which takes less than a microsecond each.
- **Safe to remove.** Caches and camps are vanilla blocks. Removing the mod leaves them in place and nothing else behind.
- Requires [Fabric API](https://modrinth.com/mod/fabric-api).

## Known limitations

- All camps share one layout; only their rotation differs.
- The weather condition uses the world's weather and does not know about biomes where it never rains.
- Anyone may open or break a forgotten cache; it is not reserved for the player it appeared for.
- This is a first release: it has been tested in singleplayer and by automated tests on a headless server, but not yet on a busy multiplayer server or together with large modpacks.

Source code, issue tracker and full documentation: [github.com/lipereis/wildfront](https://github.com/lipereis/wildfront)
