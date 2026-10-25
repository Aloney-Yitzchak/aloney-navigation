#!/usr/bin/env python3
"""Puts the buildings from ml/buildings.csv on the app's photo list (T20, Firestore photoTasks).

    # in Cloud Shell, like campus-data/upload_to_firestore.py:
    pip install -r campus-data/requirements.txt
    python ml/seed_photo_tasks.py --dry-run     # what would be written
    python ml/seed_photo_tasks.py               # write (default 30 photos per building)
    python ml/seed_photo_tasks.py --target 40

Each building becomes photoTasks/<label> with its name, target, note (its rooms), order, and its
centre (lat, lng) for T22. Writes merge into what is there: a name or target already changed in
the app is overwritten only with --overwrite, and the photo counts ("taken") are never touched.
"""

import argparse
import csv
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "campus-data"))

OTHER = {"label": "other", "name": "אחר: שבילים, עצים, שמיים",
         "note": "כל מה שאינו מבנה: שבילים, דשא, עצים, שמיים, גדרות", "order": 1000}


def documents(rows, target):
    docs = {}
    for k, r in enumerate(rows, start=1):
        docs[r["label"]] = {"name": r["name"], "target": target, "note": r["note"] or None, "order": k,
                            "lat": float(r["lat"]), "lng": float(r["lng"])}
    docs[OTHER["label"]] = {"name": OTHER["name"], "target": target, "note": OTHER["note"], "order": OTHER["order"]}
    return docs


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--target", type=int, default=30, help="photos wanted per building (default 30)")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--overwrite", action="store_true", help="also replace names, targets and notes edited in the app")
    args = ap.parse_args(argv)
    rows = list(csv.DictReader(open(HERE / "buildings.csv", encoding="utf-8")))
    docs = documents(rows, args.target)
    for label, d in docs.items():
        print(f"{label:12} {d['name']}  ({d['target']})")
    if args.dry_run:
        print(f"{len(docs)} משימות צילום (לא נכתב כלום)")
        return 0

    from upload_to_firestore import connect
    db = connect()
    col = db.collection("photoTasks")
    batch = db.batch()
    for label, d in docs.items():
        ref = col.document(label)
        existing = ref.get()
        fields = dict(d)
        if existing.exists and not args.overwrite:
            for k in ("name", "target", "note"):     # keep what staff changed in the app
                if existing.get(k) is not None:
                    fields.pop(k)
        batch.set(ref, fields, merge=True)
    batch.commit()
    print(f"{len(docs)} משימות צילום נכתבו ל-photoTasks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
