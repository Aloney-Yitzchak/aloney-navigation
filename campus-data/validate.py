#!/usr/bin/env python3
"""Checks the campus data files before they are uploaded (T05).

    python validate.py            # check, print errors and warnings
    python validate.py --write    # also fill in length_m in edges.csv
    python validate.py --complete # every room must have an entrance (before upload)

Exit code 0 = no errors (warnings are allowed), 1 = errors.
"""

import argparse
import sys

import campus


def main(argv=None):
    p = argparse.ArgumentParser(description="בדיקת נתוני הקמפוס")
    p.add_argument("--folder", default=campus.HERE, help="התיקייה עם קובצי ה-CSV")
    p.add_argument("--write", action="store_true", help="חישוב length_m וכתיבה ל-edges.csv")
    p.add_argument("--complete", action="store_true", help="כל חדר חייב נקודת כניסה")
    args = p.parse_args(argv)

    data = campus.load(args.folder)
    if args.write and not data.read_errors:
        changed = campus.compute_lengths(data)
        campus.save_edges(data, args.folder)
        print(f"length_m עודכן ב-{changed} קשתות")

    rep = campus.check(data, require_complete=args.complete)

    mapped = sum(1 for r in data.rooms if r.entrance_node_id is not None)
    total = sum(e.length_m or 0 for e in data.edges)
    entrances = sum(1 for n in data.nodes if n.kind == "entrance")
    print(f"{len(data.nodes)} נקודות ({entrances} כניסות), {len(data.edges)} קשתות, "
          f"{total:.0f} מ' שבילים, {mapped}/{len(data.rooms)} חדרים ממופים")

    for w in rep.warnings:
        print(f"  אזהרה: {w}")
    for e in rep.errors:
        print(f"  שגיאה: {e}")
    print("תקין" if rep.ok else f"{len(rep.errors)} שגיאות")
    return 0 if rep.ok else 1


if __name__ == "__main__":
    sys.exit(main())
