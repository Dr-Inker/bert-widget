# BERT v0.10.0: My Bert and the garden gate

Staged 15 October 2026. App source `6bfc76f`; website branch `release-0.10` at `ad34b77` (from `ccafc5a`).
**Live after the owner runs** `git -C /opt/berthalla merge --ff-only release-0.10`. The auto-mode classifier treats
that as a production deploy.

- **APK 0.10.0 (25):** 5,907,161 bytes, SHA-256 `c056882f5f816ab49325d9a7c2839810116cc8d977e52ca508251d4188e3dae3`.
  Byte-identical to rc1, which was checked on the owner's S25.
- **Play bundle:** SHA-256 `3cf55e4c39f703094b26ecb081a46fcf47cd5e9fec1c9051aefb43722e2a1b70`. It is on the PC in
  `BERT-Play\` with `mapping-0.10.0.txt`. The 0.9.2 files are still there; moving them to `superseded-0.9.2\` was
  blocked by the classifier.
- **Rollback:** `/opt/bert-widget-qa/release-0.10/previous/` holds the 0.9.2 app page, privacy page, homepage,
  release.json, APK and Workshop print, plus the berthalla HEAD it came from (`ccafc5a`).

## Change

- **My Bert:** paste a public Solana address to show a Bert you hold, whether in the wallet, listed or staked.
  - It appears on Home (a chip), on the Bert widget (portrait, banner and avatar) and on caption cards, with an
    optional "Match my Bert" widget palette.
  - It is read-only. The address goes only to Magic Eden and the public Solana RPC, and pictures come from the
    collection's Pinata gateway.
  - "Forget this address" deletes everything.
- **Home:** the garden banner is the path to the gate, and Bert has mood stills.
- **Website:**
  - The privacy page has a My Bert section (effective 15 October 2026). The sources table no longer overflows on
    phones; it overflowed 25–95 px before.
  - The app page and FAQ say what My Bert sends. The Workshop print and version are updated.

## Gates

- `tools/check-mobile-game.sh` passes at `6bfc76f`: 227 tests, lint, debug build and the Lost Trail browser check.
- `lintRelease`, `assembleRelease` and `bundleRelease` pass.
- `check-apk.py` passes. It adds 0.10 checks:
  - My Bert's hosts and RPC methods are in the dex.
  - There is no marketplace link.
  - The mood stills are present.
  - Backup is off.
  - versionCode is 25.

  Run against the 0.9.2 APK, the new checks fail, as they should.
- `verify:release … 24` passes: signature, pinned certificate `f911463d…`, version and hash.
- `check-page.cjs staged` passes at 1440, 768, 390 and 320: no overflow, 6 downloads, 0.10.0 throughout.
- The website gate passes: 956 passed, 39 skipped.

## Device (S25 Ultra, Android 16)

The 0.9.2 → 0.10.0 in-place upgrade works, and a cold start takes 117 ms.

My Bert was checked with a real public staker wallet:
- 46 staked Berts were found in about 6 s through the keyboard's Search key.
- A Bert was picked and "Use this Bert" kept it.
- Home shows the "Your Bert #206" chip, and the garden-gate banner renders.
- In Create, the art strip offers My Bert and the card renders with it.
- Forget brings classic Bert back and removes the test address from the phone.

The Bert widget is not placed on the owner's phone, so the host render tests cover it. Device captures are in
`/opt/bert-widget-qa/release-0.10/device/`. They stay out of the repo because they show the owner's status bar.

## Independent review

An Opus code-review agent found no blockers and five should-fix items, all fixed in `39912dc`:
- Forget, classic and Cancel now win over a lookup, choice or download in flight. Store epochs under one lock do this,
  and the race test was mutation-checked.
- The widget refresh runs in an app scope.
- The empty state has no marketplace link.

## After going live

Run `python3`-free public checks: `node docs/evidence/2026-10-15/release-0.10.0/check-page.cjs public`. Then
download both APK URLs and compare them with `c056882f…`.
