"""Unit tests for ml/make_buildings.py and ml/seed_photo_tasks.py (T20).

    cd campus-data && python -m unittest -v
"""

import csv
import sys
import unittest
from pathlib import Path

ML = Path(__file__).resolve().parents[2] / "ml"
sys.path.insert(0, str(ML))
import make_buildings  # noqa: E402
import seed_photo_tasks  # noqa: E402


class BuildingListTest(unittest.TestCase):

    def test_rooms_sort_by_their_number(self):
        rooms = ["ש9", "10-11", "8", "ש15", "14", "1"]
        self.assertEqual(["1", "8", "ש9", "10-11", "14", "ש15"], sorted(rooms, key=make_buildings.room_key))

    def test_niqqud_is_removed(self):
        self.assertEqual("חדר אוכל", make_buildings.plain("חֲדַר אוֹכֶל"))

    def test_the_committed_list_matches_labels_txt(self):
        rows = list(csv.DictReader(open(ML / "buildings.csv", encoding="utf-8")))
        labels = (ML / "labels.txt").read_text(encoding="utf-8").split()
        self.assertEqual([r["label"] for r in rows] + ["other"], labels)
        self.assertEqual(len(labels), len(set(labels)))
        for r in rows:
            self.assertRegex(r["label"], r"^building_\d\d$")
            self.assertTrue(32.50 < float(r["lat"]) < 32.52 and 34.99 < float(r["lng"]) < 35.02, r)

    def test_photo_tasks_keep_the_order_and_add_other_last(self):
        rows = [{"label": "building_01", "name": "א", "note": "", "lat": "32.51", "lng": "35.0"},
                {"label": "building_02", "name": "ב", "note": "חדרים: 1", "lat": "32.51", "lng": "35.0"}]
        docs = seed_photo_tasks.documents(rows, 30)
        self.assertEqual(["building_01", "building_02", "other"], list(docs))
        self.assertEqual(1, docs["building_01"]["order"])
        self.assertIsNone(docs["building_01"]["note"])
        self.assertEqual(1000, docs["other"]["order"])
        self.assertNotIn("taken", docs["building_02"])   # the counts are the phones' business


if __name__ == "__main__":
    unittest.main()
