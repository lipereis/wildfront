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


def new_layout(ground):
    """Starts a 7 x 4 x 7 camp layout from its ground layer.

    Layer y = 0 replaces the top block of the terrain; everything else stands on it. `ground` is
    seven rows (north to south) of seven cells (west to east); a space keeps the terrain.

    Every layout must contain one unlit campfire and one chest with the camp loot table: the game
    tests look for both to find a camp and to check that it generated.
    """
    layout = Template(SIZE)
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


LAYOUTS = (lean_to, tent, cart, ruin)

if __name__ == "__main__":
    for layout in LAYOUTS:
        layout()
