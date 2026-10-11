"""Create or promote a Firebase Authentication account to RescueFarm admin.

Run this only from a trusted machine with Firebase Admin permissions. The
password is entered interactively and is never written to this script or the
Firestore profile.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
from getpass import getpass

import firebase_admin
from firebase_admin import auth, credentials, firestore


DEFAULT_PROJECT_ID = "nhom7-rescuefarm"
DEFAULT_ADMIN_EMAIL = "admin@gmail.com"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Create a RescueFarm admin login and its Firestore profile."
    )
    parser.add_argument("--project", default=DEFAULT_PROJECT_ID)
    parser.add_argument("--email", default=DEFAULT_ADMIN_EMAIL)
    args = parser.parse_args()

    password = getpass("Admin password (Firebase requires at least 6 characters): ")
    if len(password) < 6:
        parser.error("Firebase requires passwords to be at least 6 characters; 'admin' is too short.")
    confirmation = getpass("Confirm admin password: ")
    if password != confirmation:
        parser.error("Passwords do not match.")

    try:
        firebase_admin.initialize_app(
            credentials.ApplicationDefault(), {"projectId": args.project}
        )
        try:
            user = auth.get_user_by_email(args.email)
            user = auth.update_user(
                user.uid,
                password=password,
                display_name="RescueFarm Admin",
                disabled=False,
            )
            print(f"Updated Firebase Auth account for {args.email}.")
        except auth.UserNotFoundError:
            user = auth.create_user(
                email=args.email,
                password=password,
                display_name="RescueFarm Admin",
                disabled=False,
            )
            print(f"Created Firebase Auth account for {args.email}.")

        db = firestore.client()
        profile_ref = db.collection("users").document(user.uid)
        existing = profile_ref.get()
        previous = existing.to_dict() or {}
        now = datetime.now(timezone.utc)
        profile = {
            "id": user.uid,
            "email": args.email,
            "fullName": "RescueFarm Admin",
            "phone": previous.get("phone", ""),
            "avatarUrl": previous.get("avatarUrl", ""),
            "role": "ADMIN",
            "status": "ACTIVE",
            "latitude": previous.get("latitude", 0.0),
            "longitude": previous.get("longitude", 0.0),
            "createdAt": previous.get("createdAt") or now,
            "updatedAt": now,
        }
        profile_ref.set(profile, merge=True)
        print(f"Admin profile is active in {args.project}/users/{user.uid}.")
        print("Sign in through the app with the email and password entered above.")
        return 0
    except Exception as error:
        print(f"Admin provisioning failed: {error}")
        print(
            "Check Application Default Credentials and ensure the account has "
            "Firebase Authentication Admin and Firestore write permissions."
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
