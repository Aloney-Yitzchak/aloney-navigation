#!/usr/bin/env python3
"""Uploads the campus data from the CSV files to Firestore (T06).

The service account key stays OUTSIDE the repo. Point to it with the
standard environment variable:

    export GOOGLE_APPLICATION_CREDENTIALS=~/keys/aloney-navigation-key.json
    python upload_to_firestore.py --dry-run     # check and show what would change
    python upload_to_firestore.py               # upload

Steps:
  1. validate.py checks run first; invalid data is never uploaded.
  2. nodes, edges and rooms are written in batches; documents that are no
     longer in the CSV files are deleted.
  3. meta/version is bumped LAST, so phones sync only after the upload is complete (T08).

To test against the local emulator instead of the real project:
    export FIRESTORE_EMULATOR_HOST=localhost:8080
"""

import argparse
import os
import sys

import campus

PROJECT_ID = "aloney-navigation"
BATCH_SIZE = 400  # Firestore allows up to 500 writes per batch
COLLECTIONS = ("nodes", "edges", "rooms")


def build_documents(data):
    """CSV rows -> {collection: {doc id: fields}}. Field names match the Java classes (camelCase)."""
    by_id = {n.id: n for n in data.nodes}
    docs = {
        "nodes": {str(n.id): {"lat": n.lat, "lng": n.lng} for n in data.nodes},
        "edges": {},
        "rooms": {},
    }
    for i, e in enumerate(data.edges, start=1):
        a, b = by_id[e.from_id], by_id[e.to_id]
        length = round(campus.distance_m(a.lat, a.lng, b.lat, b.lng), 1)
        docs["edges"][str(i)] = {"fromId": e.from_id, "toId": e.to_id, "length": length}
    for r in data.rooms:
        docs["rooms"][r.number] = {
            "name": r.name or None,
            "owner": r.owner or None,
            "entranceNodeId": r.entrance_node_id,
            "planYear": r.plan_year,
        }
    return docs


def connect():
    import firebase_admin
    from firebase_admin import firestore

    if os.environ.get("FIRESTORE_EMULATOR_HOST"):
        # the emulator needs no key
        from google.auth.credentials import AnonymousCredentials
        return firestore.firestore.Client(project=PROJECT_ID, credentials=AnonymousCredentials())
    if not os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"):
        sys.exit("חסר GOOGLE_APPLICATION_CREDENTIALS: הנתיב לקובץ המפתח (מחוץ למאגר!)")
    if not firebase_admin._apps:
        firebase_admin.initialize_app(options={"projectId": PROJECT_ID})
    return firestore.client()


def plan_changes(db, docs, collections):
    """Which documents will be written and which deleted, per collection."""
    plan = {}
    for name in collections:
        existing = {d.id for d in db.collection(name).list_documents()}
        plan[name] = {"write": docs[name], "delete": sorted(existing - set(docs[name]))}
    return plan


def apply(db, plan):
    from firebase_admin import firestore

    ops = []
    # parents before children: nodes, then edges and rooms that point at them
    for name in COLLECTIONS:
        if name in plan:
            ops += [("set", db.collection(name).document(i), f) for i, f in plan[name]["write"].items()]
    for name in reversed(COLLECTIONS):
        if name in plan:
            ops += [("delete", db.collection(name).document(i), None) for i in plan[name]["delete"]]

    for start in range(0, len(ops), BATCH_SIZE):
        batch = db.batch()
        for kind, ref, fields in ops[start:start + BATCH_SIZE]:
            batch.set(ref, fields) if kind == "set" else batch.delete(ref)
        batch.commit()

    version_ref = db.collection("meta").document("version")
    old = version_ref.get()
    number = (old.get("number") if old.exists else 0) + 1
    version_ref.set({"number": number, "updatedAt": firestore.SERVER_TIMESTAMP})
    return number


def main(argv=None):
    p = argparse.ArgumentParser(description="העלאת נתוני הקמפוס ל-Firestore")
    p.add_argument("--folder", default=campus.HERE)
    p.add_argument("--dry-run", action="store_true", help="בדיקה והצגת השינויים בלי לכתוב")
    p.add_argument("--allow-unmapped", action="store_true",
                   help="להעלות גם חדרים בלי כניסה (לפני שהמיפוי הושלם)")
    p.add_argument("--only", choices=COLLECTIONS, action="append",
                   help="להעלות רק אוסף אחד (אפשר לחזור). למשל --only nodes --only edges "
                        "כדי לא לדרוס שינויים שאנשי הצוות עשו בחדרים")
    args = p.parse_args(argv)

    data = campus.load(args.folder)
    report = campus.check(data, require_complete=not args.allow_unmapped)
    for w in report.warnings:
        print(f"  אזהרה: {w}")
    if not report.ok:
        for e in report.errors:
            print(f"  שגיאה: {e}")
        print("הנתונים לא תקינים, ולכן לא הועלו. תקנו והריצו את validate.py.")
        return 1

    docs = build_documents(data)
    collections = args.only or list(COLLECTIONS)
    db = connect()
    plan = plan_changes(db, docs, collections)
    for name in collections:
        print(f"{name}: {len(plan[name]['write'])} לכתיבה, {len(plan[name]['delete'])} למחיקה"
              + (f" ({', '.join(plan[name]['delete'][:10])})" if plan[name]["delete"] else ""))

    if args.dry_run:
        print("(dry run — שום דבר לא נכתב)")
        return 0
    number = apply(db, plan)
    print(f"הועלה. meta/version = {number}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
