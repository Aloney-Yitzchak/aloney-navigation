#!/usr/bin/env python3
"""Gives (or removes) staff write access to the campus data (T06, NFR-10).

The security rules allow a write only when the signed-in user has the custom
claim staff == true. The staff member first creates an account in the app
(T24) or in Firebase Console → Authentication; then:

    # in Cloud Shell, or after `gcloud auth application-default login`:
    python set_staff_claim.py teacher@a-y.org.il            # grant
    python set_staff_claim.py teacher@a-y.org.il --remove   # revoke
    python set_staff_claim.py --list                        # who has access

The claim reaches the phone the next time the user signs in (or within an hour).
"""

import argparse
import sys

PROJECT_ID = "aloney-navigation"


def init():
    import firebase_admin

    # Application Default Credentials: Cloud Shell, `gcloud auth application-default login`,
    # or GOOGLE_APPLICATION_CREDENTIALS pointing at a key OUTSIDE the repo.
    if not firebase_admin._apps:
        firebase_admin.initialize_app(options={"projectId": PROJECT_ID})


def set_staff(email, staff):
    from firebase_admin import auth

    user = auth.get_user_by_email(email)
    claims = dict(user.custom_claims or {})
    if staff:
        claims["staff"] = True
    else:
        claims.pop("staff", None)
    auth.set_custom_user_claims(user.uid, claims or None)
    return user.uid


def list_staff():
    from firebase_admin import auth

    return [u.email for u in auth.list_users().iterate_all() if (u.custom_claims or {}).get("staff")]


def main(argv=None):
    p = argparse.ArgumentParser(description="הרשאת כתיבה לאנשי צוות")
    p.add_argument("email", nargs="?")
    p.add_argument("--remove", action="store_true", help="הסרת ההרשאה")
    p.add_argument("--list", action="store_true", help="רשימת אנשי הצוות עם הרשאה")
    args = p.parse_args(argv)
    if not args.list and not args.email:
        p.error("צריך כתובת מייל, או --list")

    init()
    if args.list:
        for email in list_staff():
            print(email)
        return 0
    uid = set_staff(args.email, not args.remove)
    print(f"{args.email} ({uid}): staff = {not args.remove}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
