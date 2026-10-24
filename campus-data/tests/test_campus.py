"""Unit tests for the campus data tools (T05; the early half of AT-03).

    cd campus-data && python -m unittest -v
"""

import contextlib
import io
import tempfile
import textwrap
import unittest
from pathlib import Path

import campus
import gpx_to_nodes
import validate

# 0.001° of latitude = R·π/180·0.001 = 111.195 m
LAT, LNG = 32.49, 34.95


def write(folder, nodes="", edges="", rooms=""):
    folder = Path(folder)
    (folder / "nodes.csv").write_text("id,lat,lng,kind,label\n" + textwrap.dedent(nodes), encoding="utf-8")
    (folder / "edges.csv").write_text("from_id,to_id,length_m\n" + textwrap.dedent(edges), encoding="utf-8")
    (folder / "rooms.csv").write_text("number,name,owner,entrance_node_id,plan_year\n"
                                      + textwrap.dedent(rooms), encoding="utf-8")


# a small valid campus: 1 - 2 - 3, with room 20 at entrance 3
GOOD_NODES = """\
    1,32.4900,34.9500,junction,צומת
    2,32.4903,34.9500,junction,צומת
    3,32.4903,34.9504,entrance,כניסה 20
"""
GOOD_EDGES = """\
    1,2,
    2,3,
"""
GOOD_ROOMS = """\
    20,ספרייה,,3,tashpaz
"""


class DistanceTest(unittest.TestCase):

    def test_same_point_is_zero(self):
        self.assertAlmostEqual(campus.distance_m(LAT, LNG, LAT, LNG), 0)

    def test_north_one_thousandth_degree(self):
        self.assertAlmostEqual(campus.distance_m(LAT, LNG, LAT + 0.001, LNG), 111.195, places=2)

    def test_east_is_shorter_by_cos_latitude(self):
        # 111.195 · cos(32.49°) = 93.791 m
        self.assertAlmostEqual(campus.distance_m(LAT, LNG, LAT, LNG + 0.001), 93.791, places=2)

    def test_matches_the_java_implementation(self):
        # the same inputs as NodeTest.diagonal_isPythagoras in the app
        d = campus.distance_m(32.490, 34.950, 32.491, 34.951)
        self.assertAlmostEqual(d, 145.47, places=1)


class ComponentsTest(unittest.TestCase):

    def test_connected_graph_is_one_component(self):
        edges = [campus.Edge(1, 2), campus.Edge(2, 3)]
        self.assertEqual(campus.components([1, 2, 3], edges), [[1, 2, 3]])

    def test_disconnected_graph(self):
        edges = [campus.Edge(1, 2), campus.Edge(3, 4)]
        self.assertEqual(len(campus.components([1, 2, 3, 4, 5], edges)), 3)

    def test_edges_count_both_ways(self):
        edges = [campus.Edge(2, 1), campus.Edge(3, 2)]
        self.assertEqual(len(campus.components([1, 2, 3], edges)), 1)


class CheckTest(unittest.TestCase):

    def run_check(self, nodes=GOOD_NODES, edges=GOOD_EDGES, rooms=GOOD_ROOMS, complete=False):
        with tempfile.TemporaryDirectory() as d:
            write(d, nodes, edges, rooms)
            data = campus.load(d)
            campus.compute_lengths(data)
            return campus.check(data, require_complete=complete)

    def test_good_campus_passes(self):
        rep = self.run_check(complete=True)
        self.assertEqual(rep.errors, [])

    def test_disconnected_node_is_an_error(self):
        rep = self.run_check(nodes=GOOD_NODES + "    4,32.4910,34.9510,junction,בודדת\n")
        self.assertTrue(any("לא קשיר" in e for e in rep.errors))

    def test_duplicate_node_id(self):
        rep = self.run_check(nodes=GOOD_NODES + "    3,32.4905,34.9505,junction,\n")
        self.assertTrue(any("פעמיים" in e for e in rep.errors))

    def test_edge_to_missing_node(self):
        rep = self.run_check(edges=GOOD_EDGES + "    3,9,\n")
        self.assertTrue(any("9 לא קיימת" in e for e in rep.errors))

    def test_same_edge_twice_in_reverse(self):
        rep = self.run_check(edges=GOOD_EDGES + "    2,1,\n")
        self.assertTrue(any("פעמיים" in e for e in rep.errors))

    def test_long_edge_is_a_warning(self):
        nodes = GOOD_NODES + "    4,32.4913,34.9504,junction,רחוקה\n"  # ~111 m north of 3
        rep = self.run_check(nodes=nodes, edges=GOOD_EDGES + "    3,4,\n")
        self.assertEqual(rep.errors, [])
        self.assertTrue(any("ארוכה" in w for w in rep.warnings))

    def test_room_entrance_must_be_an_entrance(self):
        rep = self.run_check(rooms="    20,ספרייה,,2,tashpaz\n")
        self.assertTrue(any("junction ולא entrance" in e for e in rep.errors))

    def test_room_entrance_must_exist(self):
        rep = self.run_check(rooms="    20,ספרייה,,99,tashpaz\n")
        self.assertTrue(any("99 לא קיימת" in e for e in rep.errors))

    def test_unmapped_room_is_warning_until_complete(self):
        rooms = GOOD_ROOMS + "    21,חדר הכלה,,,tashpaz\n"
        self.assertEqual(self.run_check(rooms=rooms).errors, [])
        self.assertTrue(any("בלי נקודת כניסה" in e for e in self.run_check(rooms=rooms, complete=True).errors))

    def test_bad_kind(self):
        rep = self.run_check(nodes=GOOD_NODES.replace("junction,צומת\n    2", "door,צומת\n    2"))
        self.assertTrue(any("kind" in e for e in rep.errors))

    def test_hebrew_gershayim_in_names(self):
        # ביה"ס and נעל"ה must survive a load and a save
        with tempfile.TemporaryDirectory() as d:
            write(d, GOOD_NODES, GOOD_EDGES, '    7,"מחסן מזכירות ביה""ס",,,tashpaz\n')
            data = campus.load(d)
            self.assertEqual(data.rooms[0].name, 'מחסן מזכירות ביה"ס')
            campus.save_rooms(data.rooms, d)
            self.assertEqual(campus.load(d).rooms[0].name, 'מחסן מזכירות ביה"ס')

    def test_wrong_header(self):
        with tempfile.TemporaryDirectory() as d:
            write(d, GOOD_NODES, GOOD_EDGES, GOOD_ROOMS)
            Path(d, "edges.csv").write_text("a,b\n1,2\n", encoding="utf-8")
            rep = campus.check(campus.load(d))
        self.assertTrue(any("כותרת" in e for e in rep.errors))


class ValidateCliTest(unittest.TestCase):

    def test_write_fills_lengths_and_exit_code(self):
        with tempfile.TemporaryDirectory() as d:
            write(d, GOOD_NODES, GOOD_EDGES, GOOD_ROOMS)
            with contextlib.redirect_stdout(io.StringIO()):
                code = validate.main(["--folder", d, "--write", "--complete"])
            self.assertEqual(code, 0)
            lengths = [e.length_m for e in campus.load(d).edges]
        # 1→2: 0.0003° north = 33.4 m; 2→3: 0.0004° east = 37.5 m
        self.assertEqual(lengths, [33.4, 37.5])

    def test_errors_give_exit_code_1(self):
        with tempfile.TemporaryDirectory() as d:
            write(d, "", "", "")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(validate.main(["--folder", d]), 1)


GPX = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
  <wpt lat="32.4900000" lon="34.9500000"><name>צומת</name></wpt>
  <wpt lat="32.4903000" lon="34.9504000"><name>כניסה 20 21</name></wpt>
  <wpt lat="32.4903002" lon="34.9504002"><name>כניסה 20 21</name></wpt>
  <wpt lat="32.4906000" lon="34.9504000"><name>כניסה 99</name></wpt>
</gpx>
"""


class GpxTest(unittest.TestCase):

    def convert(self, text, suffix=".gpx"):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d, "walk" + suffix)
            path.write_text(text, encoding="utf-8")
            rooms = [campus.Room("20", "ספרייה", "", None, "tashpaz"),
                     campus.Room("21", "חדר הכלה", "", None, "tashpaz")]
            existing = [campus.Node(7, 32.48, 34.94, "junction")]
            return gpx_to_nodes.convert(gpx_to_nodes.read_points(path), existing, rooms)

    def test_gpx_waypoints_become_nodes(self):
        nodes, links, warnings = self.convert(GPX)
        self.assertEqual([n.id for n in nodes], [8, 9, 10])  # continues after the existing 7
        self.assertEqual([n.kind for n in nodes], ["junction", "entrance", "entrance"])

    def test_repeated_name_is_averaged(self):
        nodes, _, _ = self.convert(GPX)
        self.assertAlmostEqual(nodes[1].lat, 32.4903001, places=7)

    def test_entrance_links_its_rooms(self):
        _, links, warnings = self.convert(GPX)
        self.assertEqual(links, {"20": 9, "21": 9})
        self.assertTrue(any("99" in w for w in warnings))

    def test_csv_export_with_accuracy(self):
        csv_text = "lat,lon,name,accuracy\n32.49,34.95,צומת,3\n32.4903,34.9504,כניסה 20,12\n"
        nodes, links, warnings = self.convert(csv_text, ".csv")
        self.assertEqual(len(nodes), 2)
        self.assertTrue(any("דיוק 12" in w for w in warnings))

    def test_parse_name(self):
        self.assertEqual(gpx_to_nodes.parse_name("כניסה 12 ש13"), ("entrance", ["12", "ש13"]))
        self.assertEqual(gpx_to_nodes.parse_name("צומת ליד הספרייה"), ("junction", []))


if __name__ == "__main__":
    unittest.main()
