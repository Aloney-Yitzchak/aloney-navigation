"""Upload test against the Firestore emulator (T06). Skipped unless the emulator runs:

    cd firestore-tests && npx firebase emulators:exec --only firestore \
        --project demo-aloney-navigation --config ../firebase.json \
        "cd ../campus-data && python -m unittest tests.test_upload -v"
"""

import contextlib
import io
import os
import tempfile
import unittest

from tests.test_campus import GOOD_EDGES, GOOD_NODES, GOOD_ROOMS, write

EMULATOR = os.environ.get("FIRESTORE_EMULATOR_HOST")


@unittest.skipUnless(EMULATOR, "needs the Firestore emulator (FIRESTORE_EMULATOR_HOST)")
class UploadTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        os.environ.setdefault("GCLOUD_PROJECT", "demo-aloney-navigation")
        import upload_to_firestore
        upload_to_firestore.PROJECT_ID = "demo-aloney-navigation"
        cls.up = upload_to_firestore
        cls.db = upload_to_firestore.connect()

    def setUp(self):
        for name in ("nodes", "edges", "rooms", "meta"):
            for d in self.db.collection(name).list_documents():
                d.delete()

    def run_upload(self, *args, nodes=GOOD_NODES, edges=GOOD_EDGES, rooms=GOOD_ROOMS):
        with tempfile.TemporaryDirectory() as d:
            write(d, nodes, edges, rooms)
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = self.up.main(["--folder", d, *args])
            return code, out.getvalue()

    def ids(self, name):
        return sorted(d.id for d in self.db.collection(name).list_documents())

    def test_upload_writes_everything_and_bumps_version(self):
        code, _ = self.run_upload()
        self.assertEqual(code, 0)
        self.assertEqual(self.ids("nodes"), ["1", "2", "3"])
        self.assertEqual(self.ids("edges"), ["1", "2"])
        room = self.db.document("rooms/20").get().to_dict()
        self.assertEqual(room, {"name": "ספרייה", "owner": None, "entranceNodeId": 3, "planYear": "tashpaz"})
        self.assertEqual(self.db.document("edges/2").get().get("length"), 37.5)
        self.assertEqual(self.db.document("meta/version").get().get("number"), 1)
        self.run_upload()
        self.assertEqual(self.db.document("meta/version").get().get("number"), 2)

    def test_dry_run_writes_nothing(self):
        code, out = self.run_upload("--dry-run")
        self.assertEqual(code, 0)
        self.assertIn("dry run", out)
        self.assertEqual(self.ids("nodes"), [])
        self.assertFalse(self.db.document("meta/version").get().exists)

    def test_invalid_data_is_refused(self):
        code, out = self.run_upload(edges="    1,2,\n")  # node 3 is cut off
        self.assertEqual(code, 1)
        self.assertEqual(self.ids("nodes"), [])

    def test_unmapped_rooms_need_a_flag(self):
        rooms = GOOD_ROOMS + "    21,חדר הכלה,,,tashpaz\n"
        self.assertEqual(self.run_upload(rooms=rooms)[0], 1)
        self.assertEqual(self.run_upload("--allow-unmapped", rooms=rooms)[0], 0)
        self.assertIsNone(self.db.document("rooms/21").get().get("entranceNodeId"))

    def test_removed_documents_are_deleted(self):
        self.run_upload("--allow-unmapped", rooms=GOOD_ROOMS + "    21,חדר הכלה,,,tashpaz\n")
        self.run_upload()
        self.assertEqual(self.ids("rooms"), ["20"])

    def test_only_one_collection(self):
        self.run_upload("--only", "nodes")
        self.assertEqual(self.ids("nodes"), ["1", "2", "3"])
        self.assertEqual(self.ids("rooms"), [])


if __name__ == "__main__":
    unittest.main()
