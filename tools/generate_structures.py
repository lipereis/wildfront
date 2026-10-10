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


def abandoned_camp():
    """A long-deserted traveller's camp: cold fire pit, collapsed lean-to, supplies left behind.

    7 x 4 x 7. Layer y = 0 replaces the top block of the terrain; everything else stands on it.
    """
    camp = Template((7, 4, 7))

    # Trampled ground: a rough disc around the fire pit, with gravel and moss where it wore through.
    ground = [
        "  ccc  ",
        " cgccc ",
        "ccccmcc",
        "cccccgc",
        "cmccccc",
        " ccgcc ",
        "  ccc  ",
    ]
    names = {"c": "minecraft:coarse_dirt", "g": "minecraft:gravel", "m": "minecraft:mossy_cobblestone"}
    for z, row in enumerate(ground):
        for x, cell in enumerate(row):
            if cell != " ":
                camp.set(x, 0, z, names[cell])

    # Fire pit, long cold, with two logs pulled up as seats.
    camp.set(3, 1, 3, "minecraft:campfire", lit="false")
    camp.set(5, 1, 3, "minecraft:spruce_log", axis="z")
    camp.set(3, 1, 1, "minecraft:spruce_log", axis="x")

    # Lean-to in the south-west corner: four posts, a roof that has partly fallen in, a bedroll of hay.
    for x, z in ((0, 4), (2, 4), (0, 6), (2, 6)):
        camp.set(x, 1, z, "minecraft:spruce_fence")
    for x, z in ((0, 4), (1, 4), (2, 4), (0, 5), (1, 5), (0, 6), (1, 6)):
        camp.set(x, 2, z, "minecraft:spruce_slab", type="bottom")
    camp.set(1, 1, 5, "minecraft:hay_block", axis="x")
    camp.set(2, 1, 5, "minecraft:cobweb")

    # What was left behind.
    camp.set(1, 1, 1, "minecraft:chest", facing="south",
             nbt={"id": "minecraft:chest", "LootTable": "wildfront:structure/abandoned_camp"})
    camp.set(5, 1, 1, "minecraft:crafting_table")
    camp.set(5, 1, 5, "minecraft:skeleton_skull", rotation="6")

    camp.write(OUTPUT / "abandoned_camp" / "camp.nbt")


if __name__ == "__main__":
    abandoned_camp()
