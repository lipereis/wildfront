# Changelog

## 0.3.0

### Added

- **Bandit camps.** A new, rarer kind of camp that is still occupied: a burning fire inside a log
  palisade, two tents and a chest, guarded by two or three pillagers and vindicators depending on
  the difficulty. The guards appear when a player first comes near, stay at their camp, and do
  not come back once defeated: the camp is then cleared for good. The chest holds better loot
  than an abandoned camp's, always including a worn weapon or piece of armour. Bandit camps
  generate at least 400 blocks from the world origin, away from villages, in newly generated
  chunks only.
- Config section `hostileCamps`: switch guards off, set how close a player must come, how many
  guards a camp has, and a server-wide limit on living guards.
- Commands `/wildfront camp list` and `/wildfront camp reset`.
- Data packs can give `wildfront:flat_ground_jigsaw` structures a `min_distance_from_origin`.

### Known limitations

- A camp counts its guards by their deaths. Switching to peaceful is handled: the camp is empty
  until the difficulty is raised and then gets guards again. Guards removed any other way without
  dying leave the camp listed as occupied; `/wildfront camp reset` repairs it.
- The chest is not locked and can be looted without fighting.
- How the fight plays, and how far guards follow a fleeing player, has only been checked by
  automated tests, not by play.

## 0.2.1

### Changed

- **Abandoned camps are about three times as common.** Candidate spots are now 12 chunks apart
  instead of 24. In the test world that is roughly one camp per 640 by 640 blocks, up from one
  per 1,100 by 1,100.

### Fixed

- **Trees no longer grow in the middle of a camp.** The camp's ground was coarse dirt, which
  trees can take root in, so in forests a trunk often stood between the fire and the chest. The
  ground is now packed mud. Trees still grow right beside camps.

- **Loot now comes in the amounts it was meant to.** The loot tables were written in the format
  of older Minecraft versions; 26.3 still loaded them but silently dropped the part that sets
  stack sizes, enchants books and wears tools. Since 0.1.0 every entry therefore gave a single
  item, "enchanted books" were plain books and tools were as new. Caches and camp chests now
  give, for example, 2 to 6 iron ingots, real enchanted books and worn tools. Containers that
  were already opened keep what they had.

The camp changes affect newly generated chunks only.

## 0.2.0

### Added

- **Three more abandoned camp layouts.** Besides the lean-to camp there are now a sagging wool
  tent, a broken-down cart with its load still on it, and the corner of a fallen stone hut. Every
  camp still has a cold fire pit and one chest of travel supplies. Which layout a camp gets is
  random; the lean-to and the tent are slightly more common. Only newly generated chunks are
  affected.

## 0.1.1

### Fixed

- **Abandoned camps no longer sit in a dip.** Camps were placed one block too low, and the terrain
  around them was then lowered to match, leaving each camp in a shallow pit, or in a squared notch
  when on a hillside. They now stand on the surface.
- **Camps no longer generate on steep ground or in water.** They use a new structure type,
  `wildfront:flat_ground_jigsaw`, that skips spots where the terrain varies by more than three
  blocks or where water stands on the ground. Hilly and watery regions therefore have fewer camps.

Both changes affect newly generated chunks only; camps that already exist stay as they are.

## 0.1.0 — first release

For Minecraft 26.3 with Fabric Loader 0.19.5 or newer and Fabric API. Requires Java 25.

### Added

- **Dynamic event system.** Every few seconds the server gives each survival or adventure player a
  chance to have an event triggered. Events have their own cooldown, a chance per check, and
  conditions (distance from world spawn, time of day, weather). A global cooldown spaces out all
  events for a player. Players standing together share one occurrence instead of each getting
  their own. Cooldowns are saved with the player.
- **Mysterious Discovery event.** A forgotten cache appears on the surface near an exploring
  player, who is told which direction to look. The cache is marked by a column of particles and a
  chime, contains supplies and one valuable item, and disappears if nobody opens it in time.
  Grass or mycelium that died under a cache is put back when the cache is removed, so no bare
  patch marks the spot.
- **Abandoned camp structure.** A small deserted traveller's camp with a chest of travel supplies,
  generating in forests, taigas, savannas and plains. Only newly generated chunks are affected.
- **Configuration** in `config/wildfront.json`: event frequency, cooldowns, conditions, cache
  distance, lifetime and limit. Invalid values fall back to their defaults and are reported; a
  file that cannot be parsed is never overwritten.
- **Commands** for operators: `/wildfront event list|trigger|reset` and `/wildfront reload`.

### Known limitations

- Abandoned camps have a single layout; only their rotation varies. (Three more layouts were added in 0.2.0.)
- How often camps generate is changed with a data pack, not the config file.
- The weather condition uses the world's weather and does not account for biomes where it never
  rains.
- Anyone may open or break a forgotten cache; it is not reserved for the player who triggered it.
- The mod is needed on the server only for gameplay; clients without it see English text.
- Tested on a dedicated server with a single connected player only; not yet with several players.
