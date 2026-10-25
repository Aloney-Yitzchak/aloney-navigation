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


if __name__ == "__main__":
    unittest.main()
