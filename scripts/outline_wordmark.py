import argparse
import json
import re

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


def outline(font_path):
    font = instantiateVariableFont(TTFont(font_path), {"wght": 500, "opsz": 14})
    glyphs = font.getGlyphSet()
    mapping = font.getBestCmap()
    scale = 48 / font["head"].unitsPerEm
    pen = SVGPathPen(glyphs)
    advance = 0
    for character in "events":
        glyph = glyphs[mapping[ord(character)]]
        glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, advance, 36)))
        advance += glyph.width * scale - 0.5
    path = re.sub(r"-?\d+\.\d+", lambda match: f"{float(match.group()):.3f}".rstrip("0").rstrip("."), pen.getCommands())
    return {"width": round(advance + 2, 3), "height": 40, "path": path}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("font")
    arguments = parser.parse_args()
    print(json.dumps(outline(arguments.font)))
