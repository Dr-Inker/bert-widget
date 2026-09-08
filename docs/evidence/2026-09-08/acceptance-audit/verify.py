#!/usr/bin/env python3
"""Recheck evidence provenance and native availability, without certifying the app.

Run from any directory. Exit 0 means the existing host evidence is intact;
device journeys remain UNVERIFIED regardless of this script's exit status.
No emulator, app installation, test suite, or release is started.
"""

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET


REPO = Path(__file__).resolve().parents[4]
EVIDENCE = REPO / "docs/evidence/2026-09-08/host-f62c961"


def command(argv, env=None):
    try:
        result = subprocess.run(argv, cwd=REPO, env=env, capture_output=True,
                                text=True, timeout=30, check=False)
        return {"argv": argv, "exit_code": result.returncode,
                "stdout": result.stdout, "stderr": result.stderr}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"argv": argv, "exit_code": None, "error": str(error)}


def git(*args):
    result = command(["git", *args])
    if result["exit_code"] != 0:
        raise RuntimeError(result)
    return result["stdout"].strip()


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    manifest = json.loads((EVIDENCE / "results.json").read_text())
    source = manifest["source_sha"]
    git("merge-base", "--is-ancestor", source, "HEAD")
    changes = git("diff", "--name-only", source, "HEAD").splitlines()
    unexpected = [p for p in changes if not p.startswith("docs/")
                  and p not in ("RESEARCH.md", "android/README.md")]
    mismatches = [name for name, digest in manifest["evidence_sha256"].items()
                  if not (EVIDENCE / name).is_file()
                  or sha256(EVIDENCE / name) != digest]
    junit = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    xml_files = sorted(EVIDENCE.glob("TEST-*.xml"))
    for path in xml_files:
        suite = ET.parse(path).getroot()
        for key in junit:
            junit[key] += int(suite.attrib.get(key, "0"))
    apk = REPO / manifest["local_review_apk"]
    checks = {
        "clean_working_tree": not git("status", "--porcelain"),
        "application_and_gate_sources_unchanged": not unexpected,
        "all_recorded_evidence_hashes_match": not mismatches,
        "junit_counts_match_manifest": junit == manifest["android_tests"],
        "junit_has_tests_and_no_failures_errors_or_skips": junit["tests"] > 0
            and all(junit[k] == 0 for k in ("failures", "errors", "skipped")),
        "preview_apk_hash_matches": apk.is_file()
            and sha256(apk) == manifest["preview_apk_sha256"],
    }
    env = dict(os.environ, ANDROID_USER_HOME="/opt/bert-widget-qa/android-home",
               ANDROID_SDK_HOME="/opt/bert-widget-qa", TMPDIR="/opt/bert-widget-qa")
    diagnostics = {
        "dev_kvm_exists": Path("/dev/kvm").exists(),
        "acceleration": command(["/opt/android-sdk/emulator/emulator", "-accel-check"], env),
        "adb": command(["/opt/android-sdk/platform-tools/adb", "devices", "-l"], env),
    }
    report = {
        "audit_sha": git("rev-parse", "HEAD"),
        "auditor_sha256": sha256(Path(__file__)),
        "host_source_sha": source,
        "host_gate_command": manifest["command"],
        "manifest_sha256": sha256(EVIDENCE / "results.json"),
        "evidence_files_checked": len(manifest["evidence_sha256"]),
        "junit_files_checked": len(xml_files), "junit": junit,
        "preview_apk": manifest["local_review_apk"],
        "preview_apk_sha256": sha256(apk) if apk.is_file() else None,
        "changed_paths_since_host_gate": changes,
        "unexpected_changed_paths": unexpected, "hash_mismatches": mismatches,
        "checks": checks, "provenance_audit": "PASS" if all(checks.values()) else "FAIL",
        "native_diagnostics": diagnostics,
        "native_journeys": "UNVERIFIED", "s_tier_acceptance": "UNVERIFIED",
        "scope": "Existing evidence integrity only. No app or device journey is executed.",
    }
    print(json.dumps(report, indent=2))
    return 0 if all(checks.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
