"""Unit tests for merge_osm.py (T05c): geometry, and the entrance steps on a tiny campus.

    cd campus-data && python -m unittest -v
"""

import math
import unittest

import campus
import merge_osm as M


def tiny_campus():
    """A path 0-100 m east along y = 0, junction 10 in the middle with a spoke junction 11 at
    (50, 5) serving two entrances, 1 at (48, 8) and 2 at (52, 8)."""
    def node(i, x, y, kind):
        lat, lng = M.to_ll(x, y)
        return campus.Node(i, lat, lng, kind, f"כניסה {i}" if kind == "entrance" else "")
    nodes = [node(20, 0, 0, "junction"), node(10, 50, 0, "junction"), node(21, 100, 0, "junction"),
             node(11, 50, 5, "junction"), node(1, 48, 8, "entrance"), node(2, 52, 8, "entrance")]
    edges = [campus.Edge(20, 10), campus.Edge(10, 21), campus.Edge(10, 11), campus.Edge(11, 1), campus.Edge(11, 2)]
    return campus.Campus(nodes=nodes, edges=edges, rooms=[])


class GeometryTest(unittest.TestCase):

    def test_closest_point_on_a_segment(self):
        d, q, t = M.closest_on_segment((5, 3), (0, 0), (10, 0))
        self.assertAlmostEqual(d, 3)
        self.assertAlmostEqual(q[0], 5)
        self.assertAlmostEqual(t, 0.5)
        d, q, _ = M.closest_on_segment((-4, 3), (0, 0), (10, 0))   # beyond the end
        self.assertAlmostEqual(d, 5)

    def test_crossing(self):
        x = M.crossing((0, -5), (0, 5), (-5, 0), (5, 0))
        self.assertAlmostEqual(x[0], 0)
        self.assertAlmostEqual(x[1], 0)
        self.assertIsNone(M.crossing((0, 0), (10, 0), (0, 1), (10, 1)))   # parallel
        self.assertIsNone(M.crossing((0, 0), (10, 0), (10, 0), (10, 5)))   # touching at an end

    def test_metres_round_trip(self):
        lat, lng = M.to_ll(123.4, -56.7)
        x, y = M.to_m(lat, lng)
        self.assertAlmostEqual(x, 123.4, places=3)
        self.assertAlmostEqual(y, -56.7, places=3)


class EntranceStepsTest(unittest.TestCase):

    def test_shared_spoke_is_removed_and_each_entrance_gets_its_own_straight_link(self):
        g = M.Graph(tiny_campus())
        report = []
        M.relink_entrances_step1(g, report)
        self.assertNotIn(11, g.pos)                      # the spoke served only the entrances
        M.entrances_step4(g, buildings=[], report=report)
        for e in (1, 2):
            (j,) = g.adj[e]                              # exactly one link each
            self.assertEqual("junction", g.kind[j])
            self.assertAlmostEqual(g.pos[j][1], 0, places=6)          # on the path
            self.assertAlmostEqual(g.pos[j][0], g.pos[e][0], places=6)  # straight down: perpendicular
        self.assertNotEqual(next(iter(g.adj[1])), next(iter(g.adj[2])))

    def test_an_entrance_moves_at_most_half_a_metre_towards_the_wall(self):
        g = M.Graph(tiny_campus())
        M.relink_entrances_step1(g, [])
        before = g.pos[1]
        wall = [(40, 10), (60, 10), (60, 20), (40, 20)]  # the wall is 2 m north of entrance 1
        M.entrances_step4(g, buildings=[wall], report=[])
        moved = math.dist(before, g.pos[1])
        self.assertLessEqual(moved, M.MAX_MOVE_M)
        self.assertGreater(g.pos[1][1], before[1])       # towards the wall

    def test_a_junction_on_a_path_is_kept_even_if_an_entrance_hung_off_it(self):
        data = tiny_campus()
        data.edges = [campus.Edge(20, 10), campus.Edge(10, 21), campus.Edge(10, 1)]
        g = M.Graph(data)
        M.relink_entrances_step1(g, [])
        self.assertIn(10, g.pos)                          # still between 20 and 21



def graph_of(nodes, edges):
    """A Graph from (id, x, y) junctions/entrances in metres and (a, b) edges."""
    ns = [campus.Node(i, *M.to_ll(x, y), kind, f"כניסה {i}" if kind == "entrance" else "")
          for i, x, y, kind in nodes]
    return M.Graph(campus.Campus(nodes=ns, edges=[campus.Edge(a, b) for a, b in edges], rooms=[]))


class RepairTest(unittest.TestCase):
    """T05d: the network drawn on the map must be connected in the graph too."""

    def test_spoke_removal_does_not_cascade_into_the_main_path(self):
        # main path 20-30-40, entrance 1 hangs off 30 through a short spoke 31
        g = graph_of([(20, 0, 0, "junction"), (30, 10, 0, "junction"), (40, 20, 0, "junction"),
                      (31, 10, 2, "junction"), (1, 10, 4, "entrance")],
                     [(20, 30), (30, 40), (30, 31), (31, 1)])
        M.relink_entrances_step1(g, [])
        self.assertNotIn(31, g.pos)
        for j in (20, 30, 40):
            self.assertIn(j, g.pos)
        self.assertIn(40, g.adj[30])

    def test_two_junctions_half_a_metre_apart_become_one(self):
        g = graph_of([(1, 0, 0, "junction"), (2, 10, 0, "junction"),
                      (3, 10.5, 0, "junction"), (4, 20, 0, "junction")],
                     [(1, 2), (3, 4)])
        M.repair(g, [], [])
        ids = [i for i in g.pos if g.adj[i]]
        self.assertEqual(3, len(ids))
        self.assertEqual(1, len([i for i in g.pos if len(g.adj[i]) == 2]))

    def test_crossing_paths_get_a_junction(self):
        g = graph_of([(1, -10, 0, "junction"), (2, 10, 0, "junction"),
                      (3, 0, -10, "junction"), (4, 0, 10, "junction")],
                     [(1, 2), (3, 4)])
        M.repair(g, [], [])
        centre = [i for i in g.pos if len(g.adj[i]) == 4]
        self.assertEqual(1, len(centre))

    def test_a_dead_end_near_a_path_is_linked_to_it(self):
        g = graph_of([(1, 0, 0, "junction"), (2, 20, 0, "junction"),
                      (3, 10, 30, "junction"), (4, 10, 4, "junction")],
                     [(1, 2), (3, 4)])
        M.repair(g, [], [])
        self.assertEqual(2, len(g.adj[4]))

    def test_a_dead_end_is_not_linked_through_a_building(self):
        g = graph_of([(1, 0, 0, "junction"), (2, 20, 0, "junction"),
                      (3, 10, 30, "junction"), (4, 10, 4, "junction")],
                     [(1, 2), (3, 4)])
        wall = [(5, 1), (15, 1), (15, 3), (5, 3)]
        M.repair(g, [wall], [])
        self.assertEqual(1, len(g.adj[4]))


class CampusRoutesTest(unittest.TestCase):
    """T05d regression on the real data: the route to חדר מחשבים (entrance 2) from the east
    used to go round the south (152 m for 44 m straight)."""

    def test_route_to_entrance_2_from_the_east_is_direct(self):
        import heapq
        g = M.Graph(campus.load())
        e2 = next(i for i in g.pos if g.label.get(i) == "כניסה 2")
        s = min(g.pos, key=lambda i: math.dist(g.pos[i], (20, -10)))
        dist = {s: 0.0}
        h = [(0.0, s)]
        while h:
            d, u = heapq.heappop(h)
            if d > dist[u]:
                continue
            for v in g.adj[u]:
                nd = d + math.dist(g.pos[u], g.pos[v])
                if nd < dist.get(v, math.inf):
                    dist[v] = nd
                    heapq.heappush(h, (nd, v))
        self.assertLess(dist[e2], 1.5 * math.dist(g.pos[s], g.pos[e2]))


if __name__ == "__main__":
    unittest.main()
