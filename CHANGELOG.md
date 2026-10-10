# Changelog

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

- Abandoned camps have a single layout; only their rotation varies.
- How often camps generate is changed with a data pack, not the config file.
- The weather condition uses the world's weather and does not account for biomes where it never
  rains.
- Anyone may open or break a forgotten cache; it is not reserved for the player who triggered it.
- The mod is needed on the server only for gameplay; clients without it see English text.
- Tested on a dedicated server with a single connected player only; not yet with several players.
