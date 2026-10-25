#!/usr/bin/env python3
"""The buildings the model learns to recognise (T20), from the campus data and OpenStreetMap.

    python ml/make_buildings.py          # writes ml/buildings.csv and ml/labels.txt

A building is on the list if a room's entrance is in it (or at most 10 m from it), or if it has
a name on OSM inside the village (dining hall, library, sports hall…). Each gets a fixed label,
building_01, building_02…, ordered from north to south. The label is the folder name in the
dataset and the class of the model; the name is only for people and can be changed in the app.
The centre (lat, lng) is what T22 compares with the user's position (SDD 10.1).

Uses the OSM files that campus-data/merge_osm.py --download fetched (campus-data/osm/).
Map data © OpenStreetMap contributors, ODbL.
"""

import csv
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
CAMPUS = HERE.parent / "campus-data"
sys.path.insert(0, str(CAMPUS))
import campus  # noqa: E402
import merge_osm as M  # noqa: E402

NEAR_M = 10
SKIP_TYPES = {"garage", "shed", "roof"}
OTHER = ("other", "אחר: שבילים, עצים, שמיים", "כל מה שאינו מבנה: שבילים, דשא, עצים, שמיים, גדרות")


def osm_buildings(folder):
    root = ET.parse(folder / "map.osm").getroot()
    pts = {n.get("id"): M.to_m(float(n.get("lat")), float(n.get("lon"))) for n in root.iter("node")}
    out = []
    for w in root.iter("way"):
        tags = {t.get("k"): t.get("v") for t in w.iter("tag")}
        if "building" not in tags:
            continue
        poly = [pts[nd.get("ref")] for nd in w.iter("nd") if nd.get("ref") in pts]
        if len(poly) > 3:
            if poly[0] == poly[-1]:
                poly = poly[:-1]
            out.append({"id": w.get("id"), "name": tags.get("name", ""), "type": tags["building"], "poly": poly})
    return out


def room_key(number):
    """"ש9" after "8", "10-11" after "9": by the number in it, then as text."""
    digits = "".join(c if c.isdigit() else " " for c in number).split()
    return (int(digits[0]) if digits else 0, number)


def plain(text):
    """Without niqqud (OSM has "חֲדַר אוֹכֶל")."""
    return "".join(c for c in text if not "\u0591" <= c <= "\u05c7")


def centre(poly):
    return sum(p[0] for p in poly) / len(poly), sum(p[1] for p in poly) / len(poly)


def main():
    data = campus.load(CAMPUS)
    nodes = {n.id: n for n in data.nodes}
    buildings = osm_buildings(CAMPUS / "osm")
    boundary = M.read_osm(CAMPUS / "osm")[3]

    rooms_in = defaultdict(list)
    for r in data.rooms:
        if r.entrance_node_id is None:
            continue
        n = nodes[r.entrance_node_id]
        p = M.to_m(n.lat, n.lng)
        best = min(buildings, key=lambda b: 0 if M.inside(p, b["poly"]) else M.distance_to_polygon(p, b["poly"]))
        if M.inside(p, best["poly"]) or M.distance_to_polygon(p, best["poly"]) <= NEAR_M:
            rooms_in[best["id"]].append(r)

    chosen = []
    for b in buildings:
        named = b["name"] and b["type"] not in SKIP_TYPES and M.inside(centre(b["poly"]), boundary)
        if b["id"] in rooms_in or named:
            chosen.append(b)
    chosen.sort(key=lambda b: (-round(centre(b["poly"])[1] / 10), centre(b["poly"])[0]))   # north to south, west to east

    rows = []
    for k, b in enumerate(chosen, start=1):
        rooms = sorted(rooms_in.get(b["id"], []), key=lambda r: room_key(r.number))
        numbers = [r.number for r in rooms]
        if b["type"] == "dormitory":
            name = f"פנימייה {plain(b['name'])}"
        elif b["name"]:
            name = plain(b["name"])
        elif len(numbers) > 1:
            name = f"מבנה {numbers[0]}–{numbers[-1]}"
        else:
            name = f"מבנה {numbers[0]}"
        note = ("חדרים: " + ", ".join(numbers)) if numbers else ""
        lat, lng = M.to_ll(*centre(b["poly"]))
        rows.append({"label": f"building_{k:02d}", "name": name, "lat": f"{lat:.7f}", "lng": f"{lng:.7f}",
                     "rooms": " ".join(numbers), "note": note, "osm_way": b["id"]})

    with open(HERE / "buildings.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]), lineterminator="\n")
        w.writeheader()
        w.writerows(rows)
    labels = [r["label"] for r in rows] + [OTHER[0]]
    (HERE / "labels.txt").write_text("\n".join(labels) + "\n", encoding="utf-8")
    print(f"{len(rows)} מבנים + other → ml/buildings.csv, ml/labels.txt")
    for r in rows:
        print(f"  {r['label']}  {r['name']}  ({r['rooms'] or '—'})")


if __name__ == "__main__":
    main()
