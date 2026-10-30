#!/usr/bin/env python3
"""Inject Firebase Phone Auth URL schemes into the built Info.plist.

Reads REVERSED_CLIENT_ID + GOOGLE_APP_ID from GoogleService-Info.plist and
replaces DiaryVault placeholders (or appends) in CFBundleURLTypes.

Firebase Auth stores Bundle CFBundleURLTypes as an IUO (`mainBundleUrlTypes`).
An empty/missing array becomes nil and PhoneAuthProvider.init force-unwraps it.
This script always leaves a non-empty CFBundleURLTypes with real schemes when
the gitignored GoogleService-Info.plist is present.
"""
from __future__ import annotations

import plistlib
import sys


PLACEHOLDER_REVERSED = "com.googleusercontent.apps.DIARYVAULT_REVERSED_CLIENT_ID"
PLACEHOLDER_APP = "app-1-DIARYVAULT_GOOGLE_APP_ID"


def load_plist(path: str) -> dict:
    with open(path, "rb") as f:
        return plistlib.load(f)


def save_plist(path: str, data: dict) -> None:
    with open(path, "wb") as f:
        plistlib.dump(data, f, sort_keys=False)


def schemes_in(url_types: list) -> list[str]:
    out: list[str] = []
    for entry in url_types:
        out.extend(entry.get("CFBundleURLSchemes") or [])
    return out


def replace_or_add(url_types: list, name: str, old_scheme: str | None, new_scheme: str) -> list:
    updated = []
    replaced = False
    for entry in url_types:
        schemes = list(entry.get("CFBundleURLSchemes") or [])
        if old_scheme and old_scheme in schemes:
            schemes = [new_scheme if s == old_scheme else s for s in schemes]
            entry = dict(entry)
            entry["CFBundleURLSchemes"] = schemes
            entry.setdefault("CFBundleURLName", name)
            entry.setdefault("CFBundleTypeRole", "Editor")
            replaced = True
        updated.append(entry)
    existing = schemes_in(updated)
    if new_scheme not in existing:
        updated.append(
            {
                "CFBundleTypeRole": "Editor",
                "CFBundleURLName": name,
                "CFBundleURLSchemes": [new_scheme],
            }
        )
        replaced = True
    elif not replaced:
        # Already present under another entry — keep as-is.
        pass
    return updated


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: inject-firebase-url-schemes.py GoogleService-Info.plist Info.plist", file=sys.stderr)
        return 2

    gs_path, info_path = sys.argv[1], sys.argv[2]
    gs = load_plist(gs_path)
    info = load_plist(info_path)

    reversed_id = (gs.get("REVERSED_CLIENT_ID") or "").strip()
    client_id = (gs.get("CLIENT_ID") or "").strip()
    google_app_id = (gs.get("GOOGLE_APP_ID") or "").strip()

    if (not reversed_id or "REPLACE_ME" in reversed_id) and client_id and "REPLACE_ME" not in client_id:
        reversed_id = ".".join(reversed(client_id.split(".")))

    if not reversed_id or "REPLACE_ME" in reversed_id or "DIARYVAULT_" in reversed_id:
        print(
            "error: GoogleService-Info.plist is missing a real REVERSED_CLIENT_ID "
            "(enable Google sign-in in Firebase Console and re-download the plist)",
            file=sys.stderr,
        )
        # Still ensure CFBundleURLTypes is a non-empty array so Auth.mainBundleUrlTypes is not nil.
        url_types = list(info.get("CFBundleURLTypes") or [])
        if not url_types:
            info["CFBundleURLTypes"] = [
                {
                    "CFBundleTypeRole": "Editor",
                    "CFBundleURLName": "DiaryVault Firebase Auth placeholder",
                    "CFBundleURLSchemes": [PLACEHOLDER_REVERSED],
                }
            ]
            save_plist(info_path, info)
        return 1

    encoded_app = None
    if google_app_id and "REPLACE_ME" not in google_app_id:
        encoded_app = "app-" + google_app_id.replace(":", "-")

    url_types = list(info.get("CFBundleURLTypes") or [])
    url_types = replace_or_add(
        url_types,
        "Firebase Phone Auth REVERSED_CLIENT_ID",
        PLACEHOLDER_REVERSED,
        reversed_id,
    )
    # Drop stale placeholder if still present alongside the real scheme.
    cleaned = []
    for entry in url_types:
        schemes = [s for s in (entry.get("CFBundleURLSchemes") or []) if s != PLACEHOLDER_REVERSED]
        if not schemes and entry.get("CFBundleURLName") == "Firebase Phone Auth REVERSED_CLIENT_ID":
            continue
        entry = dict(entry)
        entry["CFBundleURLSchemes"] = schemes or entry.get("CFBundleURLSchemes") or []
        cleaned.append(entry)
    url_types = cleaned

    if encoded_app:
        url_types = replace_or_add(
            url_types,
            "Firebase Phone Auth Encoded App ID",
            PLACEHOLDER_APP,
            encoded_app,
        )
        cleaned = []
        for entry in url_types:
            schemes = [s for s in (entry.get("CFBundleURLSchemes") or []) if s != PLACEHOLDER_APP]
            if not schemes and entry.get("CFBundleURLName") == "Firebase Phone Auth Encoded App ID":
                continue
            entry = dict(entry)
            entry["CFBundleURLSchemes"] = schemes or entry.get("CFBundleURLSchemes") or []
            cleaned.append(entry)
        url_types = cleaned

    # Absolute last resort: never write an empty CFBundleURLTypes.
    if not url_types:
        url_types = [
            {
                "CFBundleTypeRole": "Editor",
                "CFBundleURLName": "Firebase Phone Auth REVERSED_CLIENT_ID",
                "CFBundleURLSchemes": [reversed_id],
            }
        ]

    info["CFBundleURLTypes"] = url_types
    save_plist(info_path, info)
    print(f"note: CFBundleURLTypes schemes → {schemes_in(url_types)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
