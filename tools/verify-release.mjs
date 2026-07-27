#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const EXPECTED_PACKAGE = "global.bert.widget";
const EXPECTED_CERTIFICATE = "f911463d6c89bf6927ac1ef9230412ba73ef2791ddb97c4ee4d457c082da995c";

const [apkArg, manifestArg, previousCodeArg] = process.argv.slice(2);
if (!apkArg || !manifestArg) {
  fail("Usage: node tools/verify-release.mjs <apk> <release.json> [previous-version-code]");
}

const apk = resolve(apkArg);
const manifestPath = resolve(manifestArg);
const sdkRoot = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME || "/opt/android-sdk";
const buildTools = process.env.ANDROID_BUILD_TOOLS_VERSION || "36.0.0";
const apksigner = resolve(sdkRoot, "build-tools", buildTools, "apksigner");
const aapt = resolve(sdkRoot, "build-tools", buildTools, "aapt");

let release;
try {
  release = JSON.parse(readFileSync(manifestPath, "utf8"));
} catch (error) {
  fail(`Cannot read release manifest: ${error.message}`);
}

const signature = run(apksigner, ["verify", "--verbose", "--print-certs", apk]);
if (!signature.includes("Verifies\n")) fail("APK signature verification failed");
const certificate = capture(signature, /Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})/i, "signing certificate").toLowerCase();
if (certificate !== EXPECTED_CERTIFICATE) fail(`Unexpected signing certificate: ${certificate}`);

const badging = run(aapt, ["dump", "badging", apk]);
const packageName = capture(badging, /package: name='([^']+)'/, "package name");
const versionCode = Number(capture(badging, /versionCode='(\d+)'/, "version code"));
const versionName = capture(badging, /versionName='([^']+)'/, "version name");
if (packageName !== EXPECTED_PACKAGE) fail(`Unexpected package: ${packageName}`);
if (versionCode !== release.versionCode) fail(`Version code mismatch: APK ${versionCode}, manifest ${release.versionCode}`);
if (versionName !== release.version) fail(`Version name mismatch: APK ${versionName}, manifest ${release.version}`);
if (release.signingCertificateSha256?.toLowerCase() !== certificate) fail("Release manifest certificate does not match APK");

const previousCode = previousCodeArg === undefined ? null : Number(previousCodeArg);
if (previousCode !== null && (!Number.isSafeInteger(previousCode) || versionCode <= previousCode)) {
  fail(`Version code ${versionCode} must be greater than previous version code ${previousCodeArg}`);
}

const digest = createHash("sha256").update(readFileSync(apk)).digest("hex");
if (digest !== release.sha256?.toLowerCase()) fail(`SHA-256 mismatch: APK ${digest}, manifest ${release.sha256}`);

const manifestTree = run(aapt, ["dump", "xmltree", apk, "AndroidManifest.xml"]);
if (!/android:usesCleartextTraffic[^\n]*0x0/.test(manifestTree)) fail("Effective manifest does not explicitly disable cleartext traffic");

console.log(`Release verified: ${packageName} v${versionName} (${versionCode})`);
console.log(`APK SHA-256: ${digest}`);
console.log(`Certificate SHA-256: ${certificate}`);

function run(command, args) {
  try {
    return execFileSync(command, args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
  } catch (error) {
    const detail = error.stderr?.trim() || error.message;
    fail(`${command} failed: ${detail}`);
  }
}

function capture(value, pattern, label) {
  const match = value.match(pattern);
  if (!match) fail(`Could not determine ${label}`);
  return match[1];
}

function fail(message) {
  console.error(`release verification failed: ${message}`);
  process.exit(1);
}
