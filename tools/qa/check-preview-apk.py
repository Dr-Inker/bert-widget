#!/usr/bin/env python3
"""Inspect the built preview APK itself, independently of Gradle configuration."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile


def run(*command):
    return subprocess.check_output([str(part) for part in command], text=True)


def build_tools():
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        properties = Path(__file__).resolve().parents[2] / "android/local.properties"
        for line in properties.read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
    if not sdk:
        raise RuntimeError("Set ANDROID_SDK_ROOT or sdk.dir in android/local.properties")
    versions = [p for p in (Path(sdk) / "build-tools").iterdir()
                if re.fullmatch(r"\d+\.\d+\.\d+", p.name) and (p / "aapt2").is_file() and (p / "apksigner").is_file()]
    return max(versions, key=lambda p: tuple(map(int, p.name.split("."))))


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path)
    args = parser.parse_args()
    sdk_tools = args.build_tools or build_tools()
    badging = run(sdk_tools / "aapt2", "dump", "badging", args.apk)
    manifest = run(sdk_tools / "aapt2", "dump", "xmltree", args.apk, "--file", "AndroidManifest.xml")
    resources = run(sdk_tools / "aapt2", "dump", "resources", args.apk)
    signature = run(sdk_tools / "apksigner", "verify", "--print-certs", args.apk)
    check("package: name='global.bert.widget.preview'" in badging, "APK must have its own preview application ID")
    check("application-label:'BERT Preview'" in badging, "APK must be visibly labelled BERT Preview")
    check(re.search(r"versionName='[^']+-preview'", badging), "Version must identify the preview")
    check("minSdkVersion:'26'" in badging and "targetSdkVersion:'36'" in badging, "Unexpected SDK contract")
    check("launchable-activity: name='global.bert.widget.MainActivity'" in badging, "BERT entry point missing")
    check("application-debuggable" in badging, "This gate accepts a debug review build only")
    check(re.search(r":usesCleartextTraffic\([^)]*\)=false", manifest), "Preview must reject cleartext HTTP")
    check(re.search(r":allowBackup\([^)]*\)=false", manifest), "Preview data must remain outside OS app backup")
    authorities = re.findall(r':authorities\([^)]*\)="([^"]+)"', manifest)
    check("global.bert.widget.preview.captions" in authorities, "Caption provider must use the preview namespace")
    check(all(a.startswith("global.bert.widget.preview.") for a in authorities), "Provider authority collides with another app")
    check('"androidx.activity.ComponentActivity"' not in manifest, "Host-test activity must not ship in the preview")
    for label in ["BERT Preview · Compact", "BERT Preview · Market", "BERT Preview · Bert"]:
        check(label in resources, "Preview widget label missing: " + label)
    check("CN=Android Debug" in signature, "Preview must use the Android debug signing identity")
    with zipfile.ZipFile(args.apk) as apk:
        dex = b"\n".join(apk.read(name) for name in apk.namelist() if re.fullmatch(r"classes\d*\.dex", name))
    endpoints = ["https://berthalla.io/widget/api/quote", "https://berthalla.io/widget/api/history", "https://berthalla.io/status.json",
                 "https://api.dexscreener.com/token-pairs/v1/solana/", "https://api.geckoterminal.com/api/v2"]
    for endpoint in endpoints:
        check(endpoint.encode() in dex, "Public endpoint missing from packaged code: " + endpoint)
    check(b"10.0.2.2:8787" not in dex, "Emulator fixture endpoint found in packaged code")
    result = {
        "result": "PASS", "apk": str(args.apk), "apk_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "package": "global.bert.widget.preview", "label": "BERT Preview", "endpoints": endpoints,
        "caption_authority": "global.bert.widget.preview.captions", "cleartext_allowed": False,
        "signature_verified": True, "signer": "Android Debug", "build_tools": str(sdk_tools),
        "scope": "Artifact inspection only. No installation, device journeys, release approval or publication."
    }
    output = args.apk.parent / "preview-inspection.json"
    output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
