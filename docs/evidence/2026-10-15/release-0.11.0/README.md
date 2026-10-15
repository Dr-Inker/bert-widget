# BERT v0.11.0: BERT Violet (includes 0.10's My Bert)

**LIVE 15 October 2026** (the owner asked for the fast-forward; berthalla HEAD `ec45301`). Public checks:
- `check-page.cjs public` passes at 1440, 768, 390 and 320: no overflow, 6 downloads, 0.11.0.
- Both APK URLs serve `23750b9c…` as `application/vnd.android.package-archive`.
- release.json reports 0.11.0 (26).
- The live `/`, `/app/` and `/app/privacy/` bytes match the repo.
- The new theme images and the Workshop print return 200.

Originally staged 15 October 2026. App source `d235fa9` (branch `v011-brand`); website branch `release-0.11` at `ec45301`, on top
of `release-0.10`. 0.10 was staged but never published on its own; this release carries it. **Live after the owner
runs** `git -C /opt/berthalla merge --ff-only release-0.11`. The classifier treats it as a production deploy.

- **APK 0.11.0 (26):** 6,148,967 bytes, SHA-256 `23750b9cb144eb1e273872ad2ec580eb6e703a1ae3ba5f2cd06fe5e581df6ad7`.
  Pinned certificate `f911463d…`.
- **Play bundle:** SHA-256 `9c8022a73bf62d26582f6edb3179e6bb01446b1849d4f22a825d37f7777b86ab`. It is on the PC in
  `BERT-Play\` with `mapping-0.11.0.txt`. The Play screenshots in `docs/play/screenshots/` are regenerated in violet.
- **Rollback:** `/opt/bert-widget-qa/release-0.10/previous/` holds the live 0.9.2 site files and its berthalla HEAD
  `ccafc5a`.

## Change (owner request, 15 October)

The official BERT brand kit becomes the app's default, and bert.global was compared with it:
- **Colours:** kit surfaces and text; lilac for links, labels and selected borders; violet only as a fill or border.
- **Type and wordmark:** Outfit at five static weights, and the kit's gradient wordmark.
- **Buttons:** glass buttons as on bert.global, with solid violet only for each screen's main action.

The previous look is kept as "Berthalla Navy" (Create → Personalise → App colours). A new BERT Violet widget and
wallpaper pack is the default for new installs, with a lilac mood pill. The Play frame, the widget picker previews and
the website (four theme packs, the Workshop print and the version) all follow. See `docs/BRAND-KIT.md`.

## Gates

- `tools/check-mobile-game.sh` passes at `d235fa9`. Unit tests (228) pass, and contrast covers both schemes, every
  pack's mood pill and the glass buttons.
- `lintRelease`, `assembleRelease` and `bundleRelease` pass.
- `check-apk.py` passes. Its 0.11 checks:
  - The Outfit weights, the brand wordmark, the violet pack and the OFL asset are present.
  - The Thin-default variable font is gone.
  - versionCode is 26.

  Run against the 0.10 APK, it fails.
- `verify:release … 24` passes.
- `check-page.cjs staged` passes at 1440, 768, 390 and 320: no overflow, 6 downloads, 0.11.0 throughout. In staged
  mode it now serves the new `/widget/` assets locally.
- The website gate passes: 956 passed.
- The themes section was looked at by eye at 1440 (four packs in a row), 1000 and 390 (scrolling sideways).

## Device

- The preview build was tried by the owner on the S25 ("I'm happy").
- The signed 0.11.0 APK installed over 0.10.0 in place. It starts in 85 ms and logcat shows no crash. Once the
  phone was unlocked, the release build was captured: it opens in BERT Violet by default (32 ms warm start), and
  Create → Personalise → App colours shows BERT Violet selected with Berthalla Navy beside it.
- My Bert was device-checked in 0.10 (see that release's README).

## Independent review

An Opus code-review agent found no blockers. All its should-fix items and nits were fixed in `eec097c`.
