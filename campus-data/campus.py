"""Shared code for the campus data files (T05, T06).

Reads nodes.csv, edges.csv and rooms.csv, and holds the distance formula
and the graph checks, so validate.py, upload_to_firestore.py and the tests
all use exactly the same logic.

Only the Python standard library is used.
"""

import csv
import math
from collections import deque
from dataclasses import dataclass, field
from pathlib import Path

EARTH_RADIUS_M = 6_371_000  # SDD 9.2
LONG_EDGE_M = 80            # an edge longer than this probably skips a junction
KINDS = ("entrance", "junction")

HERE = Path(__file__).resolve().parent
NODES_HEADER = ["id", "lat", "lng", "kind", "label"]
EDGES_HEADER = ["from_id", "to_id", "length_m"]
ROOMS_HEADER = ["number", "name", "owner", "entrance_node_id", "plan_year"]


# ---------------------------------------------------------------- geometry

def distance_m(lat1, lng1, lat2, lng2):
    """Distance in metres, treating the campus as flat (SDD 9.2).

    x = Δλ · cos(φ̄) · R,  y = Δφ · R,  d = √(x² + y²)
    """
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = phi2 - phi1
    d_lambda = math.radians(lng2 - lng1)
    x = d_lambda * math.cos((phi1 + phi2) / 2)
    return EARTH_RADIUS_M * math.hypot(x, d_phi)


# ---------------------------------------------------------------- data

@dataclass
class Node:
    id: int
    lat: float
    lng: float
    kind: str
    label: str = ""


@dataclass
class Edge:
    from_id: int
    to_id: int
    length_m: float | None = None


@dataclass
class Room:
    number: str
    name: str
    owner: str
    entrance_node_id: int | None
    plan_year: str


@dataclass
class Campus:
    nodes: list[Node] = field(default_factory=list)
    edges: list[Edge] = field(default_factory=list)
    rooms: list[Room] = field(default_factory=list)
    # problems found while reading (bad numbers, wrong header…)
    read_errors: list[str] = field(default_factory=list)


def _read_rows(path, header, errors):
    path = Path(path)
    if not path.exists():
        errors.append(f"{path.name}: הקובץ לא קיים")
        return []
    # utf-8-sig: accepts a file saved from Excel with a BOM
    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if reader.fieldnames != header:
            errors.append(f"{path.name}: שורת הכותרת צריכה להיות {','.join(header)}")
            return []
        rows = []
        for line, row in enumerate(reader, start=2):
            if not any((v or "").strip() for v in row.values()):
                continue  # empty line
            rows.append((line, {k: (v or "").strip() for k, v in row.items()}))
        return rows


def _int(value, where, errors):
    try:
        return int(value)
    except ValueError:
        errors.append(f"{where}: '{value}' אינו מספר שלם")
        return None


def _float(value, where, errors):
    try:
        return float(value)
    except ValueError:
        errors.append(f"{where}: '{value}' אינו מספר")
        return None


def load(folder=HERE):
    """Reads the three CSV files from a folder."""
    folder = Path(folder)
    campus = Campus()
    err = campus.read_errors

    for line, r in _read_rows(folder / "nodes.csv", NODES_HEADER, err):
        where = f"nodes.csv שורה {line}"
        nid = _int(r["id"], where, err)
        lat = _float(r["lat"], where, err)
        lng = _float(r["lng"], where, err)
        if None not in (nid, lat, lng):
            campus.nodes.append(Node(nid, lat, lng, r["kind"], r["label"]))

    for line, r in _read_rows(folder / "edges.csv", EDGES_HEADER, err):
        where = f"edges.csv שורה {line}"
        a = _int(r["from_id"], where, err)
        b = _int(r["to_id"], where, err)
        length = _float(r["length_m"], where, err) if r["length_m"] else None
        if None not in (a, b):
            campus.edges.append(Edge(a, b, length))

    for line, r in _read_rows(folder / "rooms.csv", ROOMS_HEADER, err):
        where = f"rooms.csv שורה {line}"
        entrance = _int(r["entrance_node_id"], where, err) if r["entrance_node_id"] else None
        campus.rooms.append(Room(r["number"], r["name"], r["owner"], entrance, r["plan_year"]))

    return campus


def save_edges(campus, folder=HERE):
    """Writes edges.csv back (used to fill in length_m)."""
    with open(Path(folder) / "edges.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(EDGES_HEADER)
        for e in campus.edges:
            w.writerow([e.from_id, e.to_id, "" if e.length_m is None else f"{e.length_m:.1f}"])


def save_nodes(nodes, folder=HERE):
    with open(Path(folder) / "nodes.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(NODES_HEADER)
        for n in nodes:
            w.writerow([n.id, f"{n.lat:.7f}", f"{n.lng:.7f}", n.kind, n.label])


def save_rooms(rooms, folder=HERE):
    with open(Path(folder) / "rooms.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(ROOMS_HEADER)
        for r in rooms:
            w.writerow([r.number, r.name, r.owner,
                        "" if r.entrance_node_id is None else r.entrance_node_id, r.plan_year])


# ---------------------------------------------------------------- graph

def components(node_ids, edges):
    """Connected components of the graph, largest first (BFS)."""
    neighbours = {n: [] for n in node_ids}
    for e in edges:
        if e.from_id in neighbours and e.to_id in neighbours:
            neighbours[e.from_id].append(e.to_id)
            neighbours[e.to_id].append(e.from_id)
    seen, result = set(), []
    for start in node_ids:
        if start in seen:
            continue
        part, queue = [], deque([start])
        seen.add(start)
        while queue:
            n = queue.popleft()
            part.append(n)
            for m in neighbours[n]:
                if m not in seen:
                    seen.add(m)
                    queue.append(m)
        result.append(sorted(part))
    return sorted(result, key=len, reverse=True)


def compute_lengths(campus):
    """Fills length_m for every edge whose two nodes exist. Returns how many changed."""
    by_id = {n.id: n for n in campus.nodes}
    changed = 0
    for e in campus.edges:
        a, b = by_id.get(e.from_id), by_id.get(e.to_id)
        if a and b:
            length = round(distance_m(a.lat, a.lng, b.lat, b.lng), 1)
            if e.length_m is None or abs(e.length_m - length) > 0.05:
                e.length_m = length
                changed += 1
    return changed


# ---------------------------------------------------------------- checks

@dataclass
class Report:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def ok(self):
        return not self.errors


def check(campus, require_complete=False):
    """All the checks from the T05 definition of done.

    require_complete: every room must have an entrance (used before upload
    and once mapping is done). Without it, an unmapped room is a warning.
    """
    rep = Report(errors=list(campus.read_errors))
    E, W = rep.errors.append, rep.warnings.append

    # nodes
    if not campus.nodes:
        E("אין נקודות ב-nodes.csv")
    by_id = {}
    for n in campus.nodes:
        if n.id in by_id:
            E(f"נקודה {n.id} מופיעה פעמיים")
        by_id[n.id] = n
        if n.kind not in KINDS:
            E(f"נקודה {n.id}: kind צריך להיות entrance או junction, ולא '{n.kind}'")
        if not (-90 <= n.lat <= 90 and -180 <= n.lng <= 180):
            E(f"נקודה {n.id}: קואורדינטות לא חוקיות ({n.lat}, {n.lng})")

    # edges
    pairs = set()
    for e in campus.edges:
        name = f"קשת {e.from_id}-{e.to_id}"
        for end in (e.from_id, e.to_id):
            if end not in by_id:
                E(f"{name}: נקודה {end} לא קיימת")
        if e.from_id == e.to_id:
            E(f"{name}: מחברת נקודה לעצמה")
        key = frozenset((e.from_id, e.to_id))
        if key in pairs:
            E(f"{name}: הקשת מופיעה פעמיים (שבילים דו-כיווניים נשמרים פעם אחת)")
        pairs.add(key)
        a, b = by_id.get(e.from_id), by_id.get(e.to_id)
        if a and b:
            d = distance_m(a.lat, a.lng, b.lat, b.lng)
            if d > LONG_EDGE_M:
                W(f"{name}: {d:.0f} מ' — ארוכה מ-{LONG_EDGE_M} מ', אולי חסר צומת באמצע")
            if e.length_m is not None and abs(e.length_m - d) > 0.5:
                W(f"{name}: length_m={e.length_m} אבל המרחק המחושב {d:.1f} (הריצו עם --write)")
        if e.length_m is None:
            W(f"{name}: length_m ריק (הריצו עם --write)")

    # connectivity
    if campus.nodes:
        parts = components(list(by_id), campus.edges)
        if len(parts) > 1:
            for part in parts[1:]:
                shown = ", ".join(map(str, part[:8])) + ("…" if len(part) > 8 else "")
                E(f"הגרף לא קשיר: {len(part)} נקודות מנותקות מהשאר ({shown})")

    # rooms
    numbers = set()
    used_entrances = set()
    unmapped = []
    for r in campus.rooms:
        if not r.number:
            E("חדר בלי מספר ב-rooms.csv")
        if "/" in r.number:
            E(f"חדר {r.number}: '/' אסור במספר החדר (מזהה מסמך ב-Firestore)")
        if r.number in numbers:
            E(f"חדר {r.number} מופיע פעמיים")
        numbers.add(r.number)
        if not r.plan_year:
            E(f"חדר {r.number}: plan_year ריק")
        if r.entrance_node_id is None:
            unmapped.append(r.number)
            continue
        used_entrances.add(r.entrance_node_id)
        n = by_id.get(r.entrance_node_id)
        if n is None:
            E(f"חדר {r.number}: נקודת הכניסה {r.entrance_node_id} לא קיימת")
        elif n.kind != "entrance":
            E(f"חדר {r.number}: נקודה {n.id} היא {n.kind} ולא entrance")

    if unmapped:
        (E if require_complete else W)(
            f"{len(unmapped)} חדרים בלי נקודת כניסה: {', '.join(unmapped)}")

    for n in campus.nodes:
        if n.kind == "entrance" and n.id not in used_entrances:
            W(f"נקודה {n.id} ({n.label}) מסומנת entrance אבל אף חדר לא משתמש בה")

    return rep
