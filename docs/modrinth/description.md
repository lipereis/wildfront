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

Small, long-deserted traveller's camps generate in forests, taigas, savannas, plains and meadows, on dry and level ground. Each has a cold fire pit and a chest of travel supplies such as food, coal, torches, a worn tool, a compass or a map. Around them you find one of four scenes: a collapsed lean-to, a sagging tent, a broken-down cart with its load still on it, or the corner of a fallen stone hut.

## Bandit camps

Rarer, and still occupied: a burning fire inside a log palisade, two tents and a chest, held by two or three human guards, archers and swordsmen, depending on the difficulty. The guards appear when you first come near. Defeat them and the camp is cleared for good; they never come back. The chest holds better loot than an abandoned camp's, always including a worn weapon or piece of armour. Spot one guard and the whole camp hears the alarm. Some camps are led by a **Camp Chief**, a boss with a health bar, a diamond sword and a war cry, who guards a barred stash of diamonds and emeralds that opens only once the camp is cleared. Bandit camps keep at least 400 blocks from the world origin and away from villages, and stay empty on peaceful.

Camps only generate in **newly generated chunks**. Existing terrain is never changed.

## The Forgotten Temple

Deep underground, reached only by caving or mining, lies a buried temple. Murals of glyphs and candles hint at the order in which to wake four glyphs; wake them right and the sealed sanctum opens. A fifth glyph, hidden behind vines, leads to a secret reliquary with rarer treasure. Mistakes cost nothing but starting over, and the sealed chambers cannot be dug or blown into.

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

- **Needed on server and client.** All gameplay runs on the server, but the mod adds a creature of its own, so everyone who joins needs the mod too.
- **Lightweight.** Nothing scans blocks or loads chunks on a timer. Players are checked once every ten seconds, which takes less than a microsecond each.
- **Vanilla blocks only.** Caches and camps are made of vanilla blocks, which stay in the world if the mod is removed.
- Requires [Fabric API](https://modrinth.com/mod/fabric-api).

## Known limitations

- The weather condition uses the world's weather and does not know about biomes where it never rains.
- Anyone may open or break a forgotten cache; it is not reserved for the player it appeared for.
- This is a first release: it has been tested in singleplayer and by automated tests on a headless server, but not yet on a busy multiplayer server or together with large modpacks.

Source code, issue tracker and full documentation: [github.com/lipereis/wildfront](https://github.com/lipereis/wildfront)
