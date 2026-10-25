#!/usr/bin/env python3
"""Draws what the AR chevrons (T18c) look like through the phone's camera, without a phone.

    python docs/design/tools/ar_chevrons_preview.py   # writes docs/design/ar-chevrons-projection.png

The same chevron as app/.../ui/ArGroundArrows.java, on a route that goes 4.5 m ahead and then
turns right, seen from a phone 1.3 m above the floor, tilted 10° and 30° down. Uses a simple
pinhole camera, like ARCore's projection matrix. Needs Pillow.
"""
import math
from PIL import Image, ImageDraw

W, H = 540, 900
A, B, C = (-0.45, 0.15), (0, -0.30), (0, -0.08)
D, E, F = (-0.45, 0.37), (0.45, 0.15), (0.45, 0.37)
CHEVRON = [A, B, C, A, C, D, B, E, F, B, F, C]          # four triangles, x/z in metres
ROUTE = [((0, -1.5), 0), ((0, -2.7), 0), ((0, -3.9), 0), ((0.9, -4.5), -90), ((2.1, -4.5), -90)]


def project(p, pitch_deg):
    x, y, z = p[0], p[1] - 1.3, p[2]
    a = math.radians(pitch_deg)
    yc = y * math.cos(a) - z * math.sin(a)
    zc = y * math.sin(a) + z * math.cos(a)
    if zc >= -0.1:
        return None
    f = 1.2 * W / 2
    return W / 2 + f * x / -zc, H / 2 - f * yc / -zc


def main():
    out = Image.new("RGB", (W * 2, H))
    for k, pitch in enumerate([10, 30]):
        img = Image.new("RGB", (W, H), (90, 90, 80))
        d = ImageDraw.Draw(img)
        for (cx, cz), yaw in ROUTE:
            r = math.radians(yaw)
            pts = []
            for vx, vz in CHEVRON:                       # Matrix.rotateM(yaw, 0, 1, 0)
                x = math.cos(r) * vx + math.sin(r) * vz
                z = -math.sin(r) * vx + math.cos(r) * vz
                pts.append(project((cx + x, 0.02, cz + z), pitch))
            for i in range(0, len(pts), 3):
                tri = pts[i:i + 3]
                if None not in tri:
                    d.polygon(tri, fill=(245, 125, 0))
        out.paste(img, (k * W, 0))
    out.save("docs/design/ar-chevrons-projection.png")


if __name__ == "__main__":
    main()
