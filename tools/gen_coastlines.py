#!/usr/bin/env python3
"""Convert Natural Earth 110m coastline GeoJSON into the compact binary format
read by WorldMapBackground (app/src/main/res/raw/coastlines.bin).

Usage:
    curl -sLo coast.geojson https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_coastline.geojson
    python3 tools/gen_coastlines.py coast.geojson app/src/main/res/raw/coastlines.bin

Format, big-endian: repeated [point_count: int16][lon*90: int16, lat*90: int16]...
Antarctica is dropped; polylines are lightly simplified (Douglas-Peucker).
"""
import json
import struct
import sys

EPSILON = 0.15  # degrees; 110m data is already coarse, this only trims collinear runs
SCALE = 90      # int16 = degrees * 90  (resolution ~0.011 deg)


def rdp(points, eps):
    if len(points) < 3:
        return points
    ax, ay = points[0]
    bx, by = points[-1]
    dx, dy = bx - ax, by - ay
    norm = (dx * dx + dy * dy) ** 0.5
    dmax, idx = 0.0, 0
    for i in range(1, len(points) - 1):
        px, py = points[i]
        if norm == 0:
            d = ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
        else:
            d = abs(dx * (ay - py) - dy * (ax - px)) / norm
        if d > dmax:
            dmax, idx = d, i
    if dmax > eps:
        return rdp(points[: idx + 1], eps)[:-1] + rdp(points[idx:], eps)
    return [points[0], points[-1]]


def main(src, dst):
    gj = json.load(open(src))
    lines = []
    for f in gj["features"]:
        geom = f["geometry"]
        parts = [geom["coordinates"]] if geom["type"] == "LineString" else geom["coordinates"]
        for part in parts:
            pts = [(lon, lat) for lon, lat in part]
            if all(lat < -60 for _, lat in pts):  # Antarctica — cropped from the map
                continue
            pts = rdp(pts, EPSILON)
            if len(pts) >= 2:
                lines.append(pts)

    out = bytearray()
    total = 0
    for pts in lines:
        out += struct.pack(">h", len(pts))
        for lon, lat in pts:
            out += struct.pack(">hh", round(lon * SCALE), round(lat * SCALE))
        total += len(pts)
    open(dst, "wb").write(out)
    print(f"{len(lines)} polylines, {total} points, {len(out)} bytes -> {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
