#!/usr/bin/env python3
"""Adds the points recorded on the campus walk to nodes.csv (T05).

Input: a GPX file of waypoints (GPS Logger, OsmAnd, Locus…) or a CSV export
with columns lat, lon (or lng / longitude) and name; an "accuracy" column is
used when present.

How to name the points on the phone:
  כניסה 20          an entrance; the numbers after the word are the rooms behind it
  כניסה 12 ש13      one entrance shared by two rooms
  צומת              a junction between paths (any other name is a junction too)

A name recorded twice (because the accuracy was poor) is averaged into one point.

    python gpx_to_nodes.py walk.gpx                # preview, writes nothing
    python gpx_to_nodes.py walk.gpx --write        # adds to nodes.csv
    python gpx_to_nodes.py walk.gpx --write --link-rooms   # also fills rooms.csv
"""

import argparse
import csv
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

import campus

ENTRANCE_WORDS = ("כניסה", "entrance", "e")
MAX_ACCURACY_M = 5      # stand still until the phone reports better than this
SAME_POINT_M = 10       # two recordings of one name further apart than this = a mistake
TOO_CLOSE_M = 2         # a new point this close to an existing one is probably a duplicate


@dataclass
class Waypoint:
    lat: float
    lng: float
    name: str
    accuracy: float | None = None


def read_gpx(path):
    root = ET.parse(path).getroot()
    ns = root.tag[: root.tag.index("}") + 1] if root.tag.startswith("{") else ""
    points = []
    for wpt in root.iter(f"{ns}wpt"):
        name = wpt.findtext(f"{ns}name", default="").strip()
        acc = wpt.findtext(f"{ns}hdop")  # some loggers store accuracy here
        points.append(Waypoint(float(wpt.get("lat")), float(wpt.get("lon")), name,
                               float(acc) if acc else None))
    return points


def read_csv(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        cols = {c.lower().strip(): c for c in reader.fieldnames or []}

        def col(*names):
            for n in names:
                if n in cols:
                    return cols[n]
            return None

        lat, lng = col("lat", "latitude"), col("lon", "lng", "longitude")
        name, acc = col("name", "desc", "description"), col("accuracy", "acc")
        if not lat or not lng:
            raise ValueError("ב-CSV חייבות להיות עמודות lat ו-lon")
        return [Waypoint(float(r[lat]), float(r[lng]), (r[name] or "").strip() if name else "",
                         float(r[acc]) if acc and r[acc] else None) for r in reader]


def read_points(path):
    path = Path(path)
    return read_gpx(path) if path.suffix.lower() == ".gpx" else read_csv(path)


def parse_name(name):
    """'כניסה 12 ש13' -> ('entrance', ['12', 'ש13']); 'צומת' -> ('junction', [])."""
    words = name.split()
    if words and words[0].lower() in ENTRANCE_WORDS:
        return "entrance", words[1:]
    return "junction", []


def merge_repeats(points, warnings):
    """Averages points that share a name (a point recorded twice). Unnamed points stay apart."""
    groups, order = {}, []
    for i, p in enumerate(points):
        key = p.name or f"#{i}"
        if key not in groups:
            groups[key] = []
            order.append(key)
        groups[key].append(p)
    merged = []
    for key in order:
        g = groups[key]
        lat = sum(p.lat for p in g) / len(g)
        lng = sum(p.lng for p in g) / len(g)
        spread = max(campus.distance_m(lat, lng, p.lat, p.lng) for p in g)
        if spread > SAME_POINT_M:
            warnings.append(f"'{key}': {len(g)} הקלטות במרחק עד {spread:.0f} מ' זו מזו — לבדוק")
        accs = [p.accuracy for p in g if p.accuracy is not None]
        merged.append(Waypoint(lat, lng, g[0].name, min(accs) if accs else None))
    return merged


def convert(points, existing_nodes, rooms):
    """Returns (new nodes, {room number: node id}, warnings)."""
    warnings = []
    for p in points:
        if p.accuracy is not None and p.accuracy > MAX_ACCURACY_M:
            warnings.append(f"'{p.name}': דיוק {p.accuracy:.0f} מ' — גרוע מ-{MAX_ACCURACY_M} מ'")
    points = merge_repeats(points, warnings)

    next_id = max((n.id for n in existing_nodes), default=0) + 1
    room_numbers = {r.number for r in rooms}
    new_nodes, links = [], {}
    for p in points:
        kind, room_list = parse_name(p.name)
        for n in existing_nodes + new_nodes:
            d = campus.distance_m(p.lat, p.lng, n.lat, n.lng)
            if d < TOO_CLOSE_M:
                warnings.append(f"'{p.name}' רחוקה {d:.1f} מ' בלבד מנקודה {n.id} — אולי כפילות")
                break
        node = campus.Node(next_id, round(p.lat, 7), round(p.lng, 7), kind, p.name)
        new_nodes.append(node)
        next_id += 1
        for number in room_list:
            if number not in room_numbers:
                warnings.append(f"'{p.name}': חדר {number} לא קיים ב-rooms.csv")
            elif number in links:
                warnings.append(f"חדר {number} מופיע בשתי כניסות; נשמרה הראשונה")
            else:
                links[number] = node.id
    return new_nodes, links, warnings


def main(argv=None):
    ap = argparse.ArgumentParser(description="המרת נקודות GPS ל-nodes.csv")
    ap.add_argument("file", help="קובץ GPX או CSV מהטלפון")
    ap.add_argument("--folder", default=campus.HERE)
    ap.add_argument("--write", action="store_true", help="הוספה ל-nodes.csv")
    ap.add_argument("--link-rooms", action="store_true",
                    help="מילוי entrance_node_id ב-rooms.csv לפי שמות הנקודות")
    args = ap.parse_args(argv)

    data = campus.load(args.folder)
    new_nodes, links, warnings = convert(read_points(args.file), data.nodes, data.rooms)

    for n in new_nodes:
        print(f"{n.id:>4}  {n.kind:<9} {n.lat:.7f}, {n.lng:.7f}  {n.label}")
    print(f"{len(new_nodes)} נקודות חדשות, {len(links)} חדרים מקושרים")
    for w in warnings:
        print(f"  אזהרה: {w}")

    if args.write:
        campus.save_nodes(data.nodes + new_nodes, args.folder)
        print("nodes.csv עודכן")
        if args.link_rooms:
            for r in data.rooms:
                if r.number in links:
                    r.entrance_node_id = links[r.number]
            campus.save_rooms(data.rooms, args.folder)
            print("rooms.csv עודכן")
    else:
        print("(תצוגה מקדימה — הוסיפו --write כדי לשמור)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
