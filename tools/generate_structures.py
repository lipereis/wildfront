"""Generates the structure templates in src/main/resources/data/wildfront/structure.

Structure templates are gzipped binary NBT, which cannot be reviewed or merged as text. This
script is the source of truth for them: edit the layout here and run

    python tools/generate_structures.py

from the project root, then commit the regenerated .nbt files. Only the Python standard library is
needed. Positions that are not listed are left out of the template, so the terrain there is kept.
"""

import gzip
import struct
from pathlib import Path

# Data version of the Minecraft release the templates are written for (26.3).
# Templates with an older version still load; the game upgrades them when reading.
DATA_VERSION = 5023

OUTPUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/wildfront/structure"

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


class Int(int):
    """Marks a Python int as an NBT int tag."""


def _string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _tag_type(value):
    if isinstance(value, Int):
        return TAG_INT
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, list):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    raise TypeError(f"unsupported NBT value: {value!r}")


def _payload(value):
    if isinstance(value, Int):
        return struct.pack(">i", value)
    if isinstance(value, str):
        return _string(value)
    if isinstance(value, list):
        element_type = _tag_type(value[0]) if value else TAG_END
        return struct.pack(">bi", element_type, len(value)) + b"".join(_payload(element) for element in value)
    if isinstance(value, dict):
        entries = b"".join(struct.pack(">b", _tag_type(v)) + _string(k) + _payload(v) for k, v in value.items())
        return entries + struct.pack(">b", TAG_END)
    raise TypeError(f"unsupported NBT value: {value!r}")


def nbt_file(root):
    return struct.pack(">b", TAG_COMPOUND) + _string("") + _payload(root)


class Template:
    def __init__(self, size):
        self.size = size
        self.palette = []
        self.blocks = {}

    def _state(self, name, properties):
        # Since Minecraft 26.x block states in templates use lowercase "id" and "properties";
        # older releases used "Name" and "Properties", which 26.x silently reads as air.
        entry = {"id": name}
        if properties:
            entry["properties"] = {key: str(value) for key, value in sorted(properties.items())}
        if entry not in self.palette:
            self.palette.append(entry)
        return self.palette.index(entry)

    def set(self, x, y, z, name, nbt=None, **properties):
        """Places a block; a later call for the same position replaces the earlier one."""
        if not all(0 <= c < s for c, s in zip((x, y, z), self.size)):
            raise ValueError(f"({x}, {y}, {z}) is outside the template size {self.size}")
        block = {"pos": [Int(x), Int(y), Int(z)], "state": Int(self._state(name, properties))}
        if nbt:
            block["nbt"] = nbt
        self.blocks[(x, y, z)] = block

    def write(self, path):
        root = {
            "DataVersion": Int(DATA_VERSION),
            "size": [Int(c) for c in self.size],
            "palette": self.palette,
            # Sorted bottom to top, so blocks are placed onto the ones supporting them.
            "blocks": [self.blocks[key] for key in sorted(self.blocks, key=lambda p: (p[1], p[0], p[2]))],
            "entities": [],
        }
        path.parent.mkdir(parents=True, exist_ok=True)
        # mtime=0 keeps the output byte-identical between runs.
        with gzip.GzipFile(path, "wb", mtime=0) as file:
            file.write(nbt_file(root))
        print(f"wrote {path.relative_to(OUTPUT.parents[4])}: {len(self.blocks)} blocks, {len(self.palette)} block states")


SIZE = (7, 4, 7)
LOOT_CHEST = {"id": "minecraft:chest", "LootTable": "wildfront:structure/abandoned_camp"}
# None of these may be a block that vegetation grows on (dirt, coarse dirt, grass, podzol, mud,
# moss: the game's `supports_vegetation` block tag). World generation plants trees after
# structures, and on such a block a tree takes root in the middle of the camp.
GROUND = {"c": "minecraft:packed_mud", "g": "minecraft:gravel", "m": "minecraft:mossy_cobblestone", "s": "minecraft:cobblestone"}


def new_layout(ground, size=SIZE):
    """Starts a camp layout (7 x 4 x 7 unless `size` says otherwise) from its ground layer.

    Layer y = 0 replaces the top block of the terrain; everything else stands on it. `ground` is
    one row per block north to south, one cell per block west to east; a space keeps the terrain.

    Every abandoned camp layout must contain one unlit campfire and one chest with the camp loot
    table: the game tests look for both to find a camp and to check that it generated.
    """
    layout = Template(size)
    for z, row in enumerate(ground):
        for x, cell in enumerate(row):
            if cell != " ":
                layout.set(x, 0, z, GROUND[cell])
    return layout


def lean_to():
    """Cold fire pit with two log seats, a collapsed lean-to with a hay bedroll, supplies left behind."""
    camp = new_layout([
        "  ccc  ",
        " cgccc ",
        "ccccmcc",
        "cccccgc",
        "cmccccc",
        " ccgcc ",
        "  ccc  ",
    ])

    camp.set(3, 1, 3, "minecraft:campfire", lit="false")
    camp.set(5, 1, 3, "minecraft:spruce_log", axis="z")
    camp.set(3, 1, 1, "minecraft:spruce_log", axis="x")

    # Lean-to in the south-west corner: four posts and a roof that has partly fallen in.
    for x, z in ((0, 4), (2, 4), (0, 6), (2, 6)):
        camp.set(x, 1, z, "minecraft:spruce_fence")
    for x, z in ((0, 4), (1, 4), (2, 4), (0, 5), (1, 5), (0, 6), (1, 6)):
        camp.set(x, 2, z, "minecraft:spruce_slab", type="bottom")
    camp.set(1, 1, 5, "minecraft:hay_block", axis="x")
    camp.set(2, 1, 5, "minecraft:cobweb")

    camp.set(1, 1, 1, "minecraft:chest", nbt=LOOT_CHEST, facing="south")
    camp.set(5, 1, 1, "minecraft:crafting_table")
    camp.set(5, 1, 5, "minecraft:skeleton_skull", rotation="6")

    camp.write(OUTPUT / "abandoned_camp" / "camp.nbt")


def tent():
    """A sagging wool tent open to the south, a cold fire in front of it, a cauldron and a chest."""
    camp = new_layout([
        " cccc  ",
        "ccccgc ",
        "cccccc ",
        "ccmcccc",
        " cccgcc",
        "  ccccc",
        "   ccc ",
    ])

    # Tent in the north-west: two walls leaning onto a ridge, closed at the back, one panel gone.
    for z in (0, 1, 2):
        camp.set(0, 1, z, "minecraft:light_gray_wool")
        camp.set(1, 2, z, "minecraft:light_gray_wool")
        if z != 2:
            camp.set(2, 1, z, "minecraft:light_gray_wool")
    camp.set(1, 1, 0, "minecraft:light_gray_wool")
    camp.set(1, 1, 1, "minecraft:hay_block", axis="z")
    camp.set(2, 1, 2, "minecraft:cobweb")

    camp.set(3, 1, 4, "minecraft:campfire", lit="false")
    camp.set(5, 1, 4, "minecraft:spruce_log", axis="z")
    camp.set(4, 1, 1, "minecraft:chest", nbt=LOOT_CHEST, facing="south")
    camp.set(5, 1, 6, "minecraft:cauldron")

    camp.write(OUTPUT / "abandoned_camp" / "tent.nbt")


def cart():
    """A broken-down cart with its load still on it, and the fire its owner lit beside it."""
    camp = new_layout([
        "       ",
        " ccgcc ",
        "ccccccc",
        "cgcccgc",
        "ccccccc",
        " cmccc ",
        "  ccc  ",
    ])

    # Cart bed on four posts; one board and one post have given way at the east end.
    for x, z in ((1, 2), (3, 2), (1, 3), (3, 3)):
        camp.set(x, 1, z, "minecraft:spruce_fence")
    for x in (1, 2, 3):
        for z in (2, 3):
            if (x, z) != (3, 3):
                camp.set(x, 2, z, "minecraft:spruce_slab", type="bottom")
    # Shafts pointing east, one dropped to the ground.
    camp.set(4, 2, 2, "minecraft:spruce_fence")
    camp.set(4, 1, 3, "minecraft:spruce_fence")

    # The load: the chest still on the cart, a bale that slid off.
    camp.set(1, 3, 2, "minecraft:chest", nbt=LOOT_CHEST, facing="south")
    camp.set(2, 3, 3, "minecraft:hay_block", axis="y")
    camp.set(5, 1, 4, "minecraft:hay_block", axis="x")

    camp.set(3, 1, 5, "minecraft:campfire", lit="false")
    camp.set(1, 1, 5, "minecraft:spruce_log", axis="x")
    camp.set(5, 1, 1, "minecraft:skeleton_skull", rotation="10")

    camp.write(OUTPUT / "abandoned_camp" / "cart.nbt")


def ruin():
    """The corner of a fallen stone hut that someone camped in: two broken walls, a hearth, a chest."""
    camp = new_layout([
        "smsss  ",
        "mccgcc ",
        "sccccc ",
        "scgcccc",
        "sccccc ",
        " cccc  ",
        "  cc   ",
    ])

    # North and west walls, two blocks high where they still stand, with a gap where a window was.
    north = {0: 3, 1: 2, 2: 2, 3: 1, 4: 2}
    west = {1: 2, 2: 1, 3: 2, 4: 1}
    mossy = {(0, 1, 0), (2, 2, 0), (4, 1, 0), (0, 2, 1), (0, 1, 3)}
    for x, height in north.items():
        for y in range(1, height + 1):
            camp.set(x, y, 0, "minecraft:mossy_cobblestone" if (x, y, 0) in mossy else "minecraft:cobblestone")
    for z, height in west.items():
        for y in range(1, height + 1):
            camp.set(0, y, z, "minecraft:mossy_cobblestone" if (0, y, z) in mossy else "minecraft:cobblestone")

    camp.set(1, 1, 1, "minecraft:chest", nbt=LOOT_CHEST, facing="south")
    camp.set(3, 1, 1, "minecraft:furnace", facing="south", lit="false")
    camp.set(1, 2, 1, "minecraft:cobweb")
    camp.set(3, 1, 3, "minecraft:campfire", lit="false")
    camp.set(5, 1, 3, "minecraft:spruce_log", axis="z")
    camp.set(2, 1, 5, "minecraft:hay_block", axis="x")

    camp.write(OUTPUT / "abandoned_camp" / "ruin.nbt")


BANDIT_SIZE = (9, 5, 9)
BANDIT_CHEST = {"id": "minecraft:chest", "LootTable": "wildfront:camp/bandit_small"}


def bandit_small():
    """A small occupied camp: a burning fire between two tents, a palisade, a practice target.

    9 x 5 x 9. Unlike an abandoned camp the fire is lit: smoke and light give the camp away from a
    distance. Its guards are not part of the template; the mod spawns them when a player comes
    near. A bandit camp layout must contain one lit campfire in its middle, one chest with the
    bandit loot table and one target block, which is how the game tests recognise it.
    """
    camp = new_layout([
        "  ccccc  ",
        " ccgcccc ",
        "ccccccccc",
        "cccccgccc",
        "ccccccccc",
        "ccgcccccc",
        "ccccccgcc",
        " ccccccc ",
        "  ccccc  ",
    ], BANDIT_SIZE)

    camp.set(4, 1, 4, "minecraft:campfire", lit="true")

    # Tent in the north-west, open to the south; tent in the south-east, open to the north.
    for z in (1, 2, 3):
        camp.set(1, 1, z, "minecraft:brown_wool")
        camp.set(3, 1, z, "minecraft:brown_wool")
        camp.set(2, 2, z, "minecraft:brown_wool")
    camp.set(2, 1, 1, "minecraft:brown_wool")
    camp.set(2, 1, 2, "minecraft:hay_block", axis="z")
    for z in (5, 6, 7):
        camp.set(5, 1, z, "minecraft:brown_wool")
        camp.set(7, 1, z, "minecraft:brown_wool")
        camp.set(6, 2, z, "minecraft:brown_wool")
    camp.set(6, 1, 7, "minecraft:brown_wool")
    camp.set(6, 1, 6, "minecraft:hay_block", axis="z")

    # Palisade of upright logs along the west side and the north-east corner, with gaps to walk through.
    for x, z in ((0, 3), (0, 4), (0, 5), (5, 0), (6, 0), (8, 2), (8, 3)):
        camp.set(x, 1, z, "minecraft:spruce_log", axis="y")
        camp.set(x, 2, z, "minecraft:spruce_log", axis="y")
    camp.set(0, 3, 4, "minecraft:spruce_log", axis="y")
    camp.set(6, 3, 0, "minecraft:spruce_log", axis="y")

    # Stores by the north-east tent side: the loot chest and a barrel; a lantern on a post.
    camp.set(6, 1, 2, "minecraft:chest", nbt=BANDIT_CHEST, facing="south")
    camp.set(5, 1, 2, "minecraft:barrel", facing="up")
    camp.set(7, 1, 1, "minecraft:spruce_fence")
    camp.set(7, 2, 1, "minecraft:lantern", hanging="false")

    # Practice corner in the south-west: a target on a bale, a log seat by the fire.
    camp.set(1, 1, 7, "minecraft:hay_block", axis="y")
    camp.set(1, 2, 7, "minecraft:target")
    camp.set(2, 1, 5, "minecraft:spruce_log", axis="x")

    camp.write(OUTPUT / "bandit_camp" / "small_1.nbt")


# The Forgotten Temple: an underground temple with a glyph puzzle. 21 x 8 x 21.
#
# Rooms, inside measures (x, y, z from the template's north-west corner, y up):
#   corridor      x 9..11, y 1..3, z 18..20   open to the south edge: the way in from the rock
#   entrance hall x 5..15, y 1..5, z 12..16   four glyph murals with 1 to 4 lit candles: the clue
#   glyph hall    x 4..16, y 1..6, z 5..10    four pedestals, a glyph and a button on each
#   sanctum       x 7..13, y 1..4, z 1..3     main reward, behind a crying obsidian seal
#   reliquary     x 1..2,  y 1..2, z 7..8     optional secret, behind an obsidian seal under vines
#
# The mod reads the puzzle from the blocks, so the layout must keep to these rules, which the game
# tests check for every variant:
# - Each of the four pedestal glyphs appears exactly once on a pedestal (with a button on its
#   south side) and once in a mural, where the number of candles gives its place in the order.
# - The fifth glyph appears once in a mural without candles and once beside the secret seal, with
#   a button on it. It is on no pedestal.
# - The glyph blocks, crying obsidian and obsidian are used for nothing else.
# - The sanctum and the reliquary are enclosed in reinforced deepslate apart from their seals.
TEMPLE_SIZE = (21, 8, 21)
GLYPHS = {
    "A": "minecraft:chiseled_stone_bricks",
    "B": "minecraft:chiseled_tuff_bricks",
    "C": "minecraft:chiseled_deepslate",
    "D": "minecraft:chiseled_red_sandstone",
}
SECRET_GLYPH = "minecraft:chiseled_polished_blackstone"
SANCTUM_CHEST = {"id": "minecraft:chest", "LootTable": "wildfront:dungeon/temple_sanctum"}
RELIQUARY_CHEST = {"id": "minecraft:chest", "LootTable": "wildfront:dungeon/temple_reliquary"}
PEDESTAL_XS = (6, 8, 12, 14)
# Mural spots in the entrance hall: (glyph in the wall, plinth and candle in front of it).
MURALS = (((4, 2, 13), (5, 13)), ((4, 2, 15), (5, 15)), ((16, 2, 13), (15, 13)), ((16, 2, 15), (15, 15)))


def _brick(x, y, z):
    """Old stone bricks: mostly plain, some mossy or cracked, always the same at a position."""
    roll = (x * 73856093 ^ y * 19349663 ^ z * 83492791) % 100
    if roll < 22:
        return "minecraft:mossy_stone_bricks"
    if roll < 36:
        return "minecraft:cracked_stone_bricks"
    return "minecraft:stone_bricks"


def _room(t, x0, y0, z0, x1, y1, z1):
    """Walls, floor and ceiling around an inside space, which is filled with air. Walls that would
    lie outside the template are left out: a room touching its edge is open to the rock there."""
    for x in range(x0 - 1, x1 + 2):
        for y in range(y0 - 1, y1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if not all(0 <= c < size for c, size in zip((x, y, z), t.size)):
                    continue
                inside = x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1
                if inside:
                    t.set(x, y, z, "minecraft:air")
                elif (x, y, z) not in t.blocks or y == y0 - 1:
                    t.set(x, y, z, "minecraft:stone_bricks" if y == y0 - 1 else _brick(x, y, z))


def _air(t, x0, y0, z0, x1, y1, z1):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                t.set(x, y, z, "minecraft:air")


def _vault_shell(t, x0, y0, z0, x1, y1, z1):
    """Encloses an inside space in reinforced deepslate, which no explosion or piston moves."""
    for x in range(x0 - 1, x1 + 2):
        for y in range(y0 - 1, y1 + 2):
            for z in range(z0 - 1, z1 + 2):
                if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1:
                    t.set(x, y, z, "minecraft:air")
                else:
                    t.set(x, y, z, "minecraft:reinforced_deepslate")


def forgotten_temple(variant, pedestals, murals):
    """One variant of the temple.

    `pedestals` names the glyph on each pedestal from west to east, `murals` the glyph and its
    number of candles at each mural spot. The solution is the glyphs in order of their candles.
    """
    t = Template(TEMPLE_SIZE)
    _room(t, 9, 1, 18, 11, 3, 20)     # corridor
    _room(t, 5, 1, 12, 15, 5, 16)     # entrance hall
    _room(t, 4, 1, 5, 16, 6, 10)      # glyph hall
    _air(t, 9, 1, 17, 11, 3, 17)      # corridor to hall
    _air(t, 9, 1, 11, 11, 3, 11)      # hall to glyph hall

    # Floors of plain stone bricks; a trail of moss leads from the doorway to the vines.
    for x, z in ((9, 10), (8, 10), (7, 10), (6, 10), (5, 10), (5, 9), (5, 8), (5, 7)):
        t.set(x, 0, z, "minecraft:mossy_stone_bricks")

    # The sanctum: reinforced deepslate all round, its seal of crying obsidian facing the glyph hall.
    _vault_shell(t, 7, 1, 1, 13, 4, 3)
    for x in range(9, 12):
        for y in range(1, 4):
            t.set(x, y, 4, "minecraft:crying_obsidian")
    for x in (8, 12):
        t.set(x, 1, 1, "minecraft:chest", nbt=SANCTUM_CHEST, facing="south")
    t.set(10, 4, 2, "minecraft:soul_lantern", hanging="true")
    t.set(10, 1, 1, "minecraft:candle", candles="3", lit="true")

    # The reliquary: reinforced deepslate, its obsidian seal in the glyph hall's west wall.
    _vault_shell(t, 1, 1, 7, 2, 2, 8)
    for z in (7, 8):
        for y in (1, 2):
            t.set(3, y, z, "minecraft:obsidian")
    for z in (6, 9):
        for y in range(0, 4):
            t.set(3, y, z, _brick(3, y, z))
    t.set(1, 1, 7, "minecraft:chest", nbt=RELIQUARY_CHEST, facing="east")

    # The fifth glyph above the seal, with a button, half hidden by vines.
    t.set(3, 3, 7, SECRET_GLYPH)
    t.set(4, 3, 7, "minecraft:stone_button", face="wall", facing="east", powered="false")
    for y, z in ((1, 7), (2, 7), (1, 8), (2, 8), (3, 8), (4, 7), (4, 8), (5, 7)):
        t.set(4, y, z, "minecraft:vine", west="true", up="false", north="false", south="false", east="false")

    # Pedestals: polished tuff, the glyph, an unlit candle on top and a button on the south side.
    for x, glyph in zip(PEDESTAL_XS, pedestals):
        t.set(x, 1, 8, "minecraft:polished_tuff")
        t.set(x, 2, 8, GLYPHS[glyph])
        t.set(x, 3, 8, "minecraft:candle", candles="1", lit="false")
        t.set(x, 2, 9, "minecraft:stone_button", face="wall", facing="south", powered="false")

    # Murals: the glyph in the wall, lit candles in front, as many as its place in the order.
    for ((gx, gy, gz), (px, pz)), (glyph, candles) in zip(MURALS, murals):
        t.set(gx, gy, gz, GLYPHS[glyph])
        t.set(px, 1, pz, "minecraft:polished_tuff")
        t.set(px, 2, pz, "minecraft:candle", candles=str(candles), lit="true")
    # The fifth glyph's mural: an empty plinth.
    t.set(7, 2, 11, SECRET_GLYPH)
    t.set(7, 1, 12, "minecraft:polished_tuff")

    t.set(10, 4, 12, "minecraft:oak_wall_sign", facing="south", nbt={
        "id": "minecraft:sign",
        "front_text": {"messages": ["Wake the four", "as the flames", "count them.", "One more sleeps."]},
        "back_text": {"messages": ["", "", "", ""]},
    })

    t.set(10, 3, 19, "minecraft:lantern", hanging="true")
    t.set(10, 5, 14, "minecraft:soul_lantern", hanging="true")
    t.set(10, 6, 7, "minecraft:soul_lantern", hanging="true")
    for x, y, z in ((5, 5, 12), (15, 5, 16), (16, 6, 5), (4, 6, 10)):
        t.set(x, y, z, "minecraft:cobweb")

    t.write(OUTPUT / "forgotten_temple" / f"{variant}.nbt")


def forgotten_temple_1():
    forgotten_temple("variant_1", "ABCD", (("A", 3), ("B", 1), ("C", 4), ("D", 2)))


def forgotten_temple_2():
    forgotten_temple("variant_2", "CADB", (("D", 1), ("A", 4), ("B", 2), ("C", 3)))


def forgotten_temple_3():
    forgotten_temple("variant_3", "BDAC", (("C", 2), ("D", 3), ("A", 1), ("B", 4)))



# The Mechanist's Vault: a workshop shed on the surface over a buried control room. 15 x 18 x 15;
# the shed's floor, at y = 14, is the ground layer.
#
#   shed           x 5..7, y 15..16, z 11..13   door to the south; a ladder down in its floor
#   shaft          x 6, y 1..14, z 12           ladder; a niche halfway down holds the test switch
#   control room   x 3..9, y 1..4, z 7..11      five levers on copper blocks, six gauges on the north wall
#   vault          x 11..13, y 1..3, z 8..10    main reward, behind an obsidian door in the east wall
#   compartment    x 1, y 1..2, z 8..9          secret, behind crying obsidian in the west wall
#
# Each lever flips a set of gauges (copper bulbs). The mark above each gauge (glowstone or black
# concrete) is the target; the row of floor tiles in front of the gauges (sea lantern or smooth
# basalt) is the maintenance pattern that, with the test switch on, opens the compartment. The
# mod knows each lever by the copper block under it and each gauge by the polished stone under
# it, never by position. CIRCUITS must match VaultCircuit.VARIANTS in the mod; the game tests
# check every variant against it.
VAULT_SIZE = (15, 18, 15)
LEVER_MOUNTS = ("minecraft:waxed_copper_block", "minecraft:waxed_exposed_copper", "minecraft:waxed_weathered_copper",
                "minecraft:waxed_oxidized_copper", "minecraft:waxed_cut_copper")
GAUGE_MARKERS = ("minecraft:polished_andesite", "minecraft:polished_diorite", "minecraft:polished_granite",
                 "minecraft:polished_tuff", "minecraft:polished_deepslate", "minecraft:polished_blackstone")
TEST_MOUNT = "minecraft:waxed_chiseled_copper"
VAULT_CHEST = {"id": "minecraft:chest", "LootTable": "wildfront:dungeon/vault_main"}
COMPARTMENT_CHEST = {"id": "minecraft:chest", "LootTable": "wildfront:dungeon/vault_compartment"}
CIRCUITS = {
    "variant_1": {"target": {1, 3, 5}, "maintenance": {0, 2, 3}},
    "variant_2": {"target": {1, 2, 5}, "maintenance": {1, 3, 4}},
    "variant_3": {"target": {0, 1, 2, 4}, "maintenance": {0, 1, 2, 5}},
}


def _box(t, x0, y0, z0, x1, y1, z1, name):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                t.set(x, y, z, name)


def _sign(t, x, y, z, facing, lines):
    t.set(x, y, z, "minecraft:oak_wall_sign", facing=facing, nbt={
        "id": "minecraft:sign",
        "front_text": {"messages": list(lines)},
        "back_text": {"messages": ["", "", "", ""]},
    })


def mechanists_vault(variant, lever_order, gauge_order):
    """One variant: `lever_order` gives the lever on each desk spot from west to east, `gauge_order`
    the gauge on each spot of the gauge wall from west to east."""
    circuit = CIRCUITS[variant]
    t = Template(VAULT_SIZE)

    # Control room: deepslate tiles all round, smooth stone floor.
    _box(t, 2, 0, 6, 10, 5, 12, "minecraft:deepslate_tiles")
    _box(t, 3, 0, 7, 9, 0, 11, "minecraft:smooth_stone")
    _box(t, 3, 1, 7, 9, 4, 11, "minecraft:air")

    # The vault: reinforced deepslate, its obsidian door facing the control room.
    _vault_shell(t, 11, 1, 8, 13, 3, 10)
    _box(t, 10, 1, 8, 10, 2, 9, "minecraft:obsidian")
    t.set(13, 1, 8, "minecraft:chest", nbt=VAULT_CHEST, facing="west")
    t.set(13, 1, 10, "minecraft:chest", nbt=VAULT_CHEST, facing="west")
    t.set(12, 3, 9, "minecraft:lantern", hanging="true")

    # The compartment: reinforced deepslate, its crying obsidian door in the control room's west wall.
    _vault_shell(t, 1, 1, 8, 1, 2, 9)
    _box(t, 2, 1, 8, 2, 2, 9, "minecraft:crying_obsidian")
    t.set(1, 1, 8, "minecraft:chest", nbt=COMPARTMENT_CHEST, facing="east")

    # Gauge wall: marker, gauge, target mark; the maintenance pattern in the floor in front.
    for x, gauge in zip(range(3, 9), gauge_order):
        t.set(x, 2, 6, GAUGE_MARKERS[gauge])
        t.set(x, 3, 6, "minecraft:waxed_copper_bulb", lit="false", powered="false")
        t.set(x, 4, 6, "minecraft:glowstone" if gauge in circuit["target"] else "minecraft:black_concrete")
        t.set(x, 0, 7, "minecraft:sea_lantern" if gauge in circuit["maintenance"] else "minecraft:smooth_basalt")

    # Desk: a lever on each copper block.
    for x, lever in zip(range(4, 9), lever_order):
        t.set(x, 1, 9, LEVER_MOUNTS[lever])
        t.set(x, 2, 9, "minecraft:lever", face="floor", facing="north", powered="false")
    _sign(t, 4, 3, 11, "north", ["Set each gauge", "to the mark", "above it.", ""])
    t.set(6, 4, 9, "minecraft:lantern", hanging="true")
    t.set(9, 4, 11, "minecraft:cobweb")

    # Shaft from the shed down to the control room, with a ladder on its south side.
    for y in range(1, 15):
        t.set(6, y, 12, "minecraft:ladder", facing="north")
    for y in range(5, 15):
        for x, z in ((5, 12), (7, 12), (6, 11), (6, 13)):
            t.set(x, y, z, "minecraft:deepslate_tiles")
    for y in range(1, 5):
        t.set(6, y, 13, "minecraft:deepslate_tiles")
    # The test switch, in a niche halfway down, with its note.
    t.set(5, 8, 12, "minecraft:lever", face="wall", facing="east", powered="false")
    t.set(4, 8, 12, TEST_MOUNT)
    t.set(5, 9, 12, "minecraft:air")
    t.set(4, 9, 12, "minecraft:deepslate_tiles")
    _sign(t, 5, 9, 12, "east", ["TEST MODE", "Set the gauges", "as the floor", "shows."])

    # The shed on the surface.
    _box(t, 4, 14, 10, 8, 14, 14, "minecraft:cobblestone")
    _box(t, 4, 15, 10, 8, 16, 14, "minecraft:cobblestone")
    _box(t, 5, 15, 11, 7, 16, 13, "minecraft:air")
    _box(t, 4, 17, 10, 8, 17, 14, "minecraft:spruce_planks")
    t.set(6, 14, 12, "minecraft:ladder", facing="north")
    t.set(6, 15, 14, "minecraft:air")
    t.set(6, 16, 14, "minecraft:air")
    t.set(5, 15, 11, "minecraft:anvil", facing="east")
    t.set(7, 16, 13, "minecraft:lantern", hanging="true")

    t.write(OUTPUT / "mechanists_vault" / f"{variant}.nbt")


def mechanists_vault_1():
    mechanists_vault("variant_1", (0, 1, 2, 3, 4), (0, 1, 2, 3, 4, 5))


def mechanists_vault_2():
    mechanists_vault("variant_2", (3, 0, 4, 1, 2), (2, 5, 0, 3, 1, 4))


def mechanists_vault_3():
    mechanists_vault("variant_3", (1, 4, 2, 0, 3), (4, 0, 5, 1, 3, 2))


LAYOUTS = (lean_to, tent, cart, ruin, bandit_small, forgotten_temple_1, forgotten_temple_2, forgotten_temple_3,
           mechanists_vault_1, mechanists_vault_2, mechanists_vault_3)

if __name__ == "__main__":
    for layout in LAYOUTS:
        layout()
