"""Generates the mod icon at src/main/resources/assets/wildfront/icon.png.

The icon is 16x16 pixel art scaled to 128x128: a forgotten cache under its column of light at
night. Edit the grid or the palette and run

    python tools/generate_icon.py

from the project root. Only the Python standard library is needed.
"""

import struct
import zlib
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/wildfront/icon.png"
SCALE = 8

PALETTE = {
    ".": (18, 24, 44),     # night sky
    "s": (120, 130, 170),  # star
    "l": (236, 226, 255),  # light, core
    "p": (176, 130, 230),  # light, glow
    "h": (26, 46, 40),     # distant hills
    "g": (44, 92, 52),     # grass
    "d": (30, 62, 38),     # grass, shaded
    "b": (124, 84, 48),    # barrel wood
    "w": (92, 60, 34),     # barrel wood, shaded
    "i": (70, 70, 76),     # barrel hoop
    "t": (150, 106, 62),   # barrel top
}

GRID = [
    "......pllp......",
    ".s....pllp...s..",
    "......pllp......",
    "...s..pllp......",
    "......pllp....s.",
    ".....ppllpp.....",
    "s....ppllpp.....",
    "hh...ppllpp...hh",
    "hhhh.tttttt.hhhh",
    "hhhhhbbbbbwhhhhh",
    "ggggiiiiiiiigggg",
    "gggggbbbbbwggggg",
    "gdggiiiiiiiiggdg",
    "gggggbbbbbwggggg",
    "ggdggggggggggdgg",
    "dggggdgggggdgggg",
]


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def main():
    assert len(GRID) == 16 and all(len(row) == 16 for row in GRID), "the grid must be 16x16"
    size = 16 * SCALE
    rows = []

    for row in GRID:
        line = b"".join(bytes(PALETTE[cell]) * SCALE for cell in row)
        # Each scanline starts with filter type 0 (none).
        rows.extend([b"\x00" + line] * SCALE)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
           + chunk(b"IEND", b""))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(png)
    print(f"wrote {OUTPUT.name}: {size}x{size}, {len(png)} bytes")


if __name__ == "__main__":
    main()
