#!/usr/bin/env python3
"""Adds the youth village's paths from OpenStreetMap, and gives every entrance its own straight
link to the path (T05c).

    python merge_osm.py --download            # fetch the OSM data for the village into osm/
    python merge_osm.py                       # merge, write nodes.csv / edges.csv, print a report
    python merge_osm.py --dry-run             # only the report
    python merge_osm.py --preview out.png     # also draw the result (needs Pillow)

What it does, in order:

1. **Entrance links.** Each entrance loses its old link. Today several entrances often share
   one "spoke" junction off the path. A spoke junction that is left serving nothing is removed.
2. **The existing paths stay** (the first mapping).
3. **OSM paths are added**: every walkable way (footway, path, steps, service, track, streets…)
   inside the village boundary (OSM relation 21252221, plus 15 m). Shape points are thinned
   where the way runs straight.
   - An OSM segment that runs within 4 m of an existing path along its whole length is the same
     path, drawn twice: it is left out.
   - An OSM path that crosses an existing one gets a junction where they cross.
   - An OSM path that ends within 8 m of an existing path is linked to it.
   - A group of OSM paths that still touches nothing is linked by its closest point, if within
     15 m; otherwise it is dropped (and reported).
4. **Entrances** move at most 0.5 m towards the nearest building wall on OSM (MAX_MOVE_M).
   Each then gets one straight link to the nearest point of a path. If that point is not
   already a junction, the path is split there. The ids of the entrances, and so the rooms
   assigned to them, do not change.

The lengths are recomputed and the result is checked with campus.check().

Map data © OpenStreetMap contributors, ODbL (https://www.openstreetmap.org/copyright).
Only the Python standard library is used (Pillow for --preview).
"""

import argparse
import math
import sys
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

import campus

HERE = Path(__file__).resolve().parent
OSM_DIR = HERE / "osm"
VILLAGE_RELATION = 21252221
BBOX = (34.9995, 32.5065, 35.0080, 32.5140)   # min lng, min lat, max lng, max lat

WALKABLE = {"footway", "path", "steps", "pedestrian", "service", "track", "residential",
            "living_street", "unclassified", "tertiary", "corridor"}
BOUNDARY_MARGIN_M = 15
SAME_PATH_M = 4          # an OSM segment this close to an existing path all along is a duplicate
LINK_END_M = 8           # an OSM path ending this close to an existing one is linked to it
LINK_GROUP_M = 15        # an isolated group of OSM paths is linked if this close
STRAIGHT_DEG = 8         # thin out shape points where the way turns less than this…
MAX_SEGMENT_M = 40       # …but keep a point at least this often
MAX_MOVE_M = 0.5         # an entrance moves at most this far towards its building's wall
SNAP_TO_NODE_M = 0.6     # an entrance link ending this close to a junction uses that junction
SPOKE_STUB_M = 6         # a stub off a through path is removed only if it is this short
SAME_POINT_M = 1.0       # two junctions this close are one point (T05d)
LINK_DEAD_END_M = 6      # a dead end this close to another path is linked to it (T05d)

LAT0 = 32.5115
LNG0 = 35.0040
KY = campus.EARTH_RADIUS_M * math.pi / 180
KX = KY * math.cos(math.radians(LAT0))


def to_m(lat, lng):
    return (lng - LNG0) * KX, (lat - LAT0) * KY


def to_ll(x, y):
    return LAT0 + y / KY, LNG0 + x / KX


# ---------------------------------------------------------------- geometry in metres

def closest_on_segment(p, a, b):
    """(distance, point, t) from p to the segment a-b."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    length2 = dx * dx + dy * dy
    t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length2))
    q = (a[0] + t * dx, a[1] + t * dy)
    return math.hypot(p[0] - q[0], p[1] - q[1]), q, t


def crossing(a, b, c, d):
    """Where segment a-b crosses c-d (not at their ends), or None."""
    r = (b[0] - a[0], b[1] - a[1])
    s = (d[0] - c[0], d[1] - c[1])
    den = r[0] * s[1] - r[1] * s[0]
    if abs(den) < 1e-9:
        return None
    t = ((c[0] - a[0]) * s[1] - (c[1] - a[1]) * s[0]) / den
    u = ((c[0] - a[0]) * r[1] - (c[1] - a[1]) * r[0]) / den
    if 0.02 < t < 0.98 and 0.02 < u < 0.98:
        return a[0] + t * r[0], a[1] + t * r[1]
    return None


def inside(p, polygon):
    x, y = p
    result = False
    for (x1, y1), (x2, y2) in zip(polygon, polygon[1:] + polygon[:1]):
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            result = not result
    return result


def distance_to_polygon(p, polygon):
    return min(closest_on_segment(p, a, b)[0] for a, b in zip(polygon, polygon[1:] + polygon[:1]))


def turn_deg(a, b, c):
    h1 = math.atan2(b[1] - a[1], b[0] - a[0])
    h2 = math.atan2(c[1] - b[1], c[0] - b[0])
    return abs(math.degrees((h2 - h1 + math.pi) % (2 * math.pi) - math.pi))


# ---------------------------------------------------------------- OSM

def download():
    OSM_DIR.mkdir(exist_ok=True)
    urls = {
        "map.osm": "https://api.openstreetmap.org/api/0.6/map?bbox=%s,%s,%s,%s" % BBOX,
        "village.osm": f"https://api.openstreetmap.org/api/0.6/relation/{VILLAGE_RELATION}/full",
    }
    for name, url in urls.items():
        req = urllib.request.Request(url, headers={"User-Agent": "aloney-navigation campus-data (school project)"})
        with urllib.request.urlopen(req, timeout=60) as r:
            (OSM_DIR / name).write_bytes(r.read())
        print(f"{name}: {(OSM_DIR / name).stat().st_size // 1024} KB")


def read_osm(folder):
    root = ET.parse(folder / "map.osm").getroot()
    points = {n.get("id"): to_m(float(n.get("lat")), float(n.get("lon"))) for n in root.iter("node")}
    ways, buildings = [], []
    for w in root.iter("way"):
        tags = {t.get("k"): t.get("v") for t in w.iter("tag")}
        refs = [nd.get("ref") for nd in w.iter("nd") if nd.get("ref") in points]
        if tags.get("highway") in WALKABLE and tags.get("access") not in ("private", "no") and len(refs) > 1:
            ways.append((w.get("id"), tags.get("highway"), refs))
        if "building" in tags and len(refs) > 3:
            buildings.append([points[r] for r in refs[:-1]] if refs[0] == refs[-1] else [points[r] for r in refs])
    v = ET.parse(folder / "village.osm").getroot()
    vpoints = {n.get("id"): to_m(float(n.get("lat")), float(n.get("lon"))) for n in v.iter("node")}
    outer = next(w for w in v.iter("way"))
    boundary = [vpoints[nd.get("ref")] for nd in outer.iter("nd")]
    if boundary[0] == boundary[-1]:
        boundary = boundary[:-1]
    return points, ways, buildings, boundary


# ---------------------------------------------------------------- the graph being built

class Graph:
    def __init__(self, data):
        self.pos = {}     # id -> (x, y)
        self.kind = {}
        self.label = {}
        self.adj = defaultdict(set)
        for n in data.nodes:
            self.pos[n.id] = to_m(n.lat, n.lng)
            self.kind[n.id] = n.kind
            self.label[n.id] = n.label
        for e in data.edges:
            self.link(e.from_id, e.to_id)
        self.next_id = max(self.pos) + 1

    def add(self, p, kind="junction", label=""):
        i = self.next_id
        self.next_id += 1
        self.pos[i] = p
        self.kind[i] = kind
        self.label[i] = label
        return i

    def link(self, a, b):
        if a != b:
            self.adj[a].add(b)
            self.adj[b].add(a)

    def unlink(self, a, b):
        self.adj[a].discard(b)
        self.adj[b].discard(a)

    def remove(self, i):
        for j in list(self.adj[i]):
            self.unlink(i, j)
        del self.pos[i], self.kind[i], self.label[i]
        self.adj.pop(i, None)

    def edges(self, only=None):
        seen = set()
        for a, nbrs in self.adj.items():
            for b in nbrs:
                key = (min(a, b), max(a, b))
                if key not in seen and (only is None or (a in only and b in only)):
                    seen.add(key)
                    yield key

    def path_edges(self):
        """Edges between junctions: the paths, without the entrance links."""
        return [(a, b) for a, b in self.edges() if self.kind[a] == "junction" and self.kind[b] == "junction"]

    def nearest_on_paths(self, p, edges):
        best = (math.inf, None, None, None)
        for a, b in edges:
            d, q, t = closest_on_segment(p, self.pos[a], self.pos[b])
            if d < best[0]:
                best = (d, (a, b), q, t)
        return best

    def split(self, a, b, q):
        """A junction at q on the edge a-b (or the end, if q is that close to it)."""
        for end in (a, b):
            if math.dist(q, self.pos[end]) < SNAP_TO_NODE_M:
                return end
        i = self.add(q)
        self.unlink(a, b)
        self.link(a, i)
        self.link(i, b)
        return i


# ---------------------------------------------------------------- the steps

def relink_entrances_step1(g, report):
    """Removes every entrance's old link, and the spoke junctions left serving nothing.

    A spoke is removed only when nothing else is left on it, or when it is a short stub
    (under SPOKE_STUB_M) hanging off a path that goes on both ways. There is no cascade: T05d found that removing
    one stub after another had also removed a stretch of the main path (65–66) whose end
    the entrances hung from.
    """
    spokes = set()
    for e in [i for i in g.pos if g.kind[i] == "entrance"]:
        for j in list(g.adj[e]):
            g.unlink(e, j)
            if g.kind[j] == "junction":
                spokes.add(j)
    removed = 0
    for s in sorted(spokes):
        if s not in g.pos:
            continue
        nbrs = list(g.adj[s])
        if not nbrs or (len(nbrs) == 1 and len(g.adj[nbrs[0]]) >= 3
                        and math.dist(g.pos[s], g.pos[nbrs[0]]) < SPOKE_STUB_M):
            g.remove(s)
            removed += 1
    report.append(f"קישורי כניסה ישנים הוסרו; {removed} צמתי 'חישור' שנשארו בלי שימוש נמחקו")


def osm_graph(points, ways, boundary):
    """OSM ways inside the boundary, as segments between kept points."""
    use = {r for _, _, refs in ways for r in refs
           if inside(points[r], boundary) or distance_to_polygon(points[r], boundary) < BOUNDARY_MARGIN_M}
    count = defaultdict(int)
    for _, _, refs in ways:
        for k, r in enumerate(refs):
            count[r] += 1 if 0 < k < len(refs) - 1 else 2   # ends and shared points are junctions
    segments = []
    for _, _, refs in ways:
        run = []
        for r in refs:
            if r in use:
                run.append(r)
            else:
                if len(run) > 1:
                    segments.append(run)
                run = []
        if len(run) > 1:
            segments.append(run)
    edges = []
    for run in segments:
        kept = [run[0]]
        length = 0.0
        for k in range(1, len(run) - 1):
            length += math.dist(points[run[k - 1]], points[run[k]])
            if (count[run[k]] > 1 or length > MAX_SEGMENT_M
                    or turn_deg(points[kept[-1]], points[run[k]], points[run[k + 1]]) > STRAIGHT_DEG):
                kept.append(run[k])
                length = 0.0
        kept.append(run[-1])
        edges.extend(zip(kept, kept[1:]))
    return edges


def add_osm_step3(g, points, osm_edges, report):
    existing = g.path_edges()

    def near_existing(p, limit):
        return g.nearest_on_paths(p, existing)[0] <= limit

    # 3a: leave out segments that are an existing path drawn twice
    kept, dup_m = [], 0.0
    for a, b in osm_edges:
        pa, pb = points[a], points[b]
        n = max(2, int(math.dist(pa, pb) / 2) + 1)
        samples = [(pa[0] + (pb[0] - pa[0]) * k / (n - 1), pa[1] + (pb[1] - pa[1]) * k / (n - 1)) for k in range(n)]
        if all(near_existing(s, SAME_PATH_M) for s in samples):
            dup_m += math.dist(pa, pb)
        else:
            kept.append((a, b))
    report.append(f"OSM: {len(osm_edges)} קטעים; {len(osm_edges) - len(kept)} ({dup_m:.0f} מ') כפולים לשביל קיים ולא נוספו")

    # 3b: OSM points become junctions
    ids = {}
    for a, b in kept:
        for r in (a, b):
            if r not in ids:
                ids[r] = g.add(points[r])
        g.link(ids[a], ids[b])
    osm_nodes = set(ids.values())

    # 3c: junctions where an OSM path crosses an existing path
    crossings = 0
    for oa, ob in [(ids[a], ids[b]) for a, b in kept]:
        for ea, eb in list(g.path_edges()):
            if ea in osm_nodes or eb in osm_nodes or eb not in g.adj[ea] or ob not in g.adj[oa]:
                continue
            x = crossing(g.pos[oa], g.pos[ob], g.pos[ea], g.pos[eb])
            if x:
                j = g.split(ea, eb, x)
                g.unlink(oa, ob)
                g.link(oa, j)
                g.link(j, ob)
                crossings += 1
                break
    # 3d: OSM path ends close to an existing path are linked to it
    ends = 0
    existing = [e for e in g.path_edges() if e[0] not in osm_nodes and e[1] not in osm_nodes]
    for i in list(osm_nodes):
        if len(g.adj[i]) == 1:
            d, edge, q, _ = g.nearest_on_paths(g.pos[i], existing)
            if edge and d <= LINK_END_M:
                g.link(i, g.split(edge[0], edge[1], q))
                existing = [e for e in g.path_edges() if e[0] not in osm_nodes and e[1] not in osm_nodes]
                ends += 1
    # 3e: groups that still touch nothing
    linked_groups = dropped = 0
    while True:
        parts = groups(g)
        if len(parts) <= 1:
            break
        main, rest = parts[0], parts[1:]
        progress = False
        for part in rest:
            if any(g.kind[i] == "entrance" for i in part):
                continue
            best = (math.inf, None, None)
            main_edges = [(a, b) for a, b in g.path_edges() if a in main and b in main]
            for i in part:
                d, edge, q, _ = g.nearest_on_paths(g.pos[i], main_edges)
                if d < best[0]:
                    best = (d, i, (edge, q))
            if best[0] <= LINK_GROUP_M:
                (edge, q) = best[2]
                g.link(best[1], g.split(edge[0], edge[1], q))
                linked_groups += 1
            else:
                for i in part:
                    g.remove(i)
                dropped += 1
            progress = True
            break
        if not progress:
            break
    report.append(f"OSM: {crossings} הצטלבויות עם שבילים קיימים, {ends} קצוות חוברו, "
                  f"{linked_groups} קבוצות שבילים חוברו, {dropped} קבוצות רחוקות מדי הושמטו")


def groups(g):
    seen, parts = set(), []
    for s in g.pos:
        if s in seen:
            continue
        part, todo = [], [s]
        seen.add(s)
        while todo:
            i = todo.pop()
            part.append(i)
            for j in g.adj[i]:
                if j not in seen:
                    seen.add(j)
                    todo.append(j)
        parts.append(part)
    parts.sort(key=len, reverse=True)
    return [set(p) for p in parts]


def entrances_step4(g, buildings, report):
    moved, moves, links = 0, [], []
    for e in sorted(i for i in g.pos if g.kind[i] == "entrance"):
        p = g.pos[e]
        best = (math.inf, None)
        for poly in buildings:
            for a, b in zip(poly, poly[1:] + poly[:1]):
                d, q, _ = closest_on_segment(p, a, b)
                if d < best[0]:
                    best = (d, q)
        if best[1] is not None and best[0] > 0.05:
            step = min(best[0], MAX_MOVE_M * 0.98)   # a margin for the flat-earth rounding
            if step >= 0.05:
                p = (p[0] + (best[1][0] - p[0]) * step / best[0], p[1] + (best[1][1] - p[1]) * step / best[0])
                g.pos[e] = p
                moved += 1
                moves.append(step)
        d, edge, q, _ = g.nearest_on_paths(p, g.path_edges())
        g.link(e, g.split(edge[0], edge[1], q))
        links.append((d, e))
    links.sort()
    report.append(f"כניסות: {moved} זזו לכיוון קיר המבנה (לכל היותר {MAX_MOVE_M} מ', ממוצע "
                  f"{(sum(moves) / len(moves)) if moves else 0:.2f} מ'); לכל כניסה קישור ישר משלה לשביל")
    report.append("קישורי כניסה: חציון %.1f מ', הארוך ביותר %.1f מ' (כניסה %s)" % (
        links[len(links) // 2][0], links[-1][0], g.label[links[-1][1]]))
    long_links = [(round(d, 1), g.label[e]) for d, e in links if d > 15]
    if long_links:
        report.append(f"קישורים ארוכים מ-15 מ' (כדאי לבדוק בשטח): {long_links}")


def drop_bare_junctions(g):
    """A junction between exactly two others on a straight line adds nothing: merged away."""
    merged = 0
    for i in list(g.pos):
        if g.kind.get(i) != "junction" or len(g.adj[i]) != 2:
            continue
        a, b = list(g.adj[i])
        if b in g.adj[a]:
            continue
        if (turn_deg(g.pos[a], g.pos[i], g.pos[b]) < 1 and g.kind[a] == g.kind[b] == "junction"
                and math.dist(g.pos[a], g.pos[b]) < MAX_SEGMENT_M):
            g.remove(i)
            g.link(a, b)
            merged += 1
    return merged


def merge_close_junctions(g, limit=0.5):
    """Two junctions closer than 50 cm are one point: the second is folded into the first."""
    merged = 0
    changed = True
    while changed:
        changed = False
        for a, b in list(g.edges()):
            if a in g.pos and b in g.pos and g.kind[a] == g.kind[b] == "junction" \
                    and math.dist(g.pos[a], g.pos[b]) < limit:
                keep, drop = (a, b) if a < b else (b, a)
                for n in list(g.adj[drop]):
                    if n != keep:
                        g.link(keep, n)
                g.remove(drop)
                merged += 1
                changed = True
                break
    return merged


# ---------------------------------------------------------------- T05d: repair

def crosses_building(a, b, buildings):
    """Whether the straight line a-b passes through a building (ends excluded)."""
    n = max(3, int(math.dist(a, b) / 0.5))
    for k in range(1, n):
        p = (a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n)
        if any(inside(p, poly) for poly in buildings):
            return True
    return False


def repair(g, buildings, report):
    """Joins what is drawn as one path network but was not connected in the graph:
    junctions within 1 m become one point, crossing paths get a junction, and a dead end
    within 6 m of another path is linked to it (unless the link would cross a building)."""
    # a: one point, not two
    merged = 0
    ids = sorted(i for i in g.pos if g.kind[i] == "junction")
    for k, a in enumerate(ids):
        if a not in g.pos:
            continue
        for b in ids[k + 1:]:
            if b in g.pos and math.dist(g.pos[a], g.pos[b]) < SAME_POINT_M:
                for n in list(g.adj[b]):
                    if n != a:
                        g.link(a, n)
                g.remove(b)
                merged += 1
    # b: a junction where two paths cross
    crossings = 0
    changed = True
    while changed:
        changed = False
        edges = g.path_edges()
        for k, (a, b) in enumerate(edges):
            for c, d in edges[k + 1:]:
                if len({a, b, c, d}) < 4:
                    continue
                x = crossing(g.pos[a], g.pos[b], g.pos[c], g.pos[d])
                if x:
                    j = g.split(a, b, x)
                    j2 = g.split(c, d, x)
                    if j2 != j:
                        for n in list(g.adj[j2]):
                            if n != j:
                                g.link(j, n)
                        g.remove(j2)
                    crossings += 1
                    changed = True
                    break
            if changed:
                break
    # c: dead ends next to another path
    linked = []
    for i in sorted(g.pos):
        if g.kind.get(i) != "junction" or len(g.adj[i]) != 1:
            continue
        own = set(g.adj[i]) | {i}
        candidates = [(a, b) for a, b in g.path_edges() if a not in own and b not in own]
        d, edge, q, _ = g.nearest_on_paths(g.pos[i], candidates)
        if edge and d <= LINK_DEAD_END_M and not crosses_building(g.pos[i], q, buildings):
            g.link(i, g.split(edge[0], edge[1], q))
            linked.append(round(d, 1))
    report.append(f"תיקון הרשת: {merged} צמתים כפולים אוחדו, {crossings} הצטלבויות קיבלו צומת, "
                  f"{len(linked)} קצוות מתים חוברו לשביל סמוך")


def detours(g, buildings=(), limit=2.0, min_m=25):
    """Entrance pairs whose route is more than `limit` times the straight line (and at least
    min_m apart) although no building stands between them: a sign of a missing connection.
    Returns (count, worst examples)."""
    import heapq
    ents = sorted(i for i in g.pos if g.kind[i] == "entrance")
    bad = []
    for s in ents:
        dist = {s: 0.0}
        h = [(0.0, s)]
        while h:
            dd, u = heapq.heappop(h)
            if dd > dist[u]:
                continue
            for v in g.adj[u]:
                nd = dd + math.dist(g.pos[u], g.pos[v])
                if nd < dist.get(v, math.inf):
                    dist[v] = nd
                    heapq.heappush(h, (nd, v))
        for t in ents:
            if t <= s:
                continue
            straight = math.dist(g.pos[s], g.pos[t])
            if (straight >= min_m and dist.get(t, math.inf) > limit * straight
                    and not crosses_building(g.pos[s], g.pos[t], buildings)):
                bad.append((dist.get(t, math.inf) / straight, g.label[s], g.label[t]))
    bad.sort(reverse=True)
    return len(bad), bad[:5]


def split_long_edges(g):
    """Edges longer than MAX_SEGMENT_M get junctions every ≤ 40 m (the app snaps to segments)."""
    split = 0
    for a, b in list(g.edges()):
        length = math.dist(g.pos[a], g.pos[b])
        parts = math.ceil(length / MAX_SEGMENT_M)
        if parts < 2 or "entrance" in (g.kind[a], g.kind[b]):
            continue
        g.unlink(a, b)
        prev = a
        for k in range(1, parts):
            t = k / parts
            i = g.add((g.pos[a][0] + (g.pos[b][0] - g.pos[a][0]) * t, g.pos[a][1] + (g.pos[b][1] - g.pos[a][1]) * t))
            g.link(prev, i)
            prev = i
        g.link(prev, b)
        split += 1
    return split


def to_campus(g, data):
    nodes = []
    for i in sorted(g.pos):
        lat, lng = to_ll(*g.pos[i])
        nodes.append(campus.Node(i, lat, lng, g.kind[i], g.label[i]))
    edges = [campus.Edge(a, b) for a, b in sorted(g.edges())]
    out = campus.Campus(nodes=nodes, edges=edges, rooms=data.rooms)
    campus.compute_lengths(out)
    return out


def preview(g, buildings, boundary, path):
    from PIL import Image, ImageDraw
    xs = [p[0] for p in boundary]
    ys = [p[1] for p in boundary]
    x0, x1, y0, y1 = min(xs) - 20, max(xs) + 20, min(ys) - 20, max(ys) + 20
    scale = 4
    img = Image.new("RGB", (int((x1 - x0) * scale), int((y1 - y0) * scale)), "white")
    d = ImageDraw.Draw(img)

    def px(p):
        return (p[0] - x0) * scale, (y1 - p[1]) * scale

    d.line([px(p) for p in boundary + boundary[:1]], fill=(200, 0, 200), width=3)
    for poly in buildings:
        d.polygon([px(p) for p in poly], fill=(225, 225, 225), outline=(160, 160, 160))
    for a, b in g.edges():
        entrance = "entrance" in (g.kind[a], g.kind[b])
        d.line([px(g.pos[a]), px(g.pos[b])], fill=(230, 60, 0) if entrance else (0, 90, 200), width=2 if entrance else 4)
    for i, p in g.pos.items():
        if g.kind[i] == "entrance":
            x, y = px(p)
            d.ellipse([x - 5, y - 5, x + 5, y + 5], fill=(230, 0, 0))
    img.save(path)
    print(f"preview: {path}")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--download", action="store_true", help="fetch the OSM data into osm/ and stop")
    ap.add_argument("--dry-run", action="store_true", help="only print the report")
    ap.add_argument("--preview", metavar="PNG", help="draw the result")
    ap.add_argument("--osm", default=str(OSM_DIR), help="folder with map.osm and village.osm")
    args = ap.parse_args(argv)
    if args.download:
        download()
        return 0

    data = campus.load()
    points, ways, buildings, boundary = read_osm(Path(args.osm))
    g = Graph(data)
    report = [f"לפני: {len(data.nodes)} נקודות, {len(data.edges)} קשתות"]
    relink_entrances_step1(g, report)
    add_osm_step3(g, points, osm_graph(points, ways, boundary), report)
    repair(g, buildings, report)
    entrances_step4(g, buildings, report)
    report.append(f"צמתים קרובים מ-50 ס\"מ זה לזה אוחדו: {merge_close_junctions(g)}")
    report.append(f"נקודות ביניים מיותרות על קו ישר: {drop_bare_junctions(g)} אוחדו")
    report.append(f"קשתות ארוכות מ-{MAX_SEGMENT_M} מ' חולקו: {split_long_edges(g)}")
    n_bad, worst = detours(g, buildings)
    report.append(f"זוגות כניסות שהמסלול ביניהם ארוך פי 2 ויותר מהקו הישר: {n_bad}"
                  + (f" (הגרועים: {[(round(r, 1), a, b) for r, a, b in worst]})" if n_bad else ""))
    out = to_campus(g, data)
    total = sum(e.length_m for e in out.edges)
    report.append(f"אחרי: {len(out.nodes)} נקודות, {len(out.edges)} קשתות, {total / 1000:.1f} ק\"מ שבילים")
    rep = campus.check(out)
    print("\n".join(report))
    for w in rep.warnings:
        print("אזהרה:", w)
    for e in rep.errors:
        print("שגיאה:", e)
    if args.preview:
        preview(g, buildings, boundary, args.preview)
    if not rep.ok:
        return 1
    if not args.dry_run:
        campus.save_nodes(out.nodes)
        campus.save_edges(out)
        print("nodes.csv ו-edges.csv נכתבו")
    return 0


if __name__ == "__main__":
    sys.exit(main())
