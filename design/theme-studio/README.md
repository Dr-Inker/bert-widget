# BERT Theme Studio artwork

Production artwork for the Theme Studio. Every pack has a clock-safe Lock master and a quieter, icon-safe Home master. Production masters are rendered at 1440×3200 and shipped as WebP (quality 92) in `android/app/src/main/res/raw`, streamed to Android's wallpaper service without decoding; lightweight JPEG previews keep the Compose gallery responsive.

## Packs

- `generated/mayor-purple-{lock,home}-v2-source.png`: seamless vivid `bert.global` portrait treatments
- `woofhub-night-{lock,home}.svg`: editable dark `woofhub.com` treatments
- `generated/berthalla-nights-{lock,home}-v2-source.png`: vertically extended moonlit `berthalla.io` treatments

The older Mayor and Berthalla SVG compositions are retained as revision history, but are not production inputs. The V2 raster masters remove the landscape-source boundaries that were visible in V1.

- BERT Navy and BERT Violet (`wallpaper_bert_{navy,violet}_*`, redrawn 24 Oct 2026): HTML masters, assets and render
  steps live outside the repo in `/opt/bert-widget-qa/wallpaper-sources-navy-violet/` (one asset is from the private
  brand kit). Home masters carry no wordmark: on phones the dock covers that band.

- Mayor Purple home (0.13.2, 24 Oct 2026): the bottom-left `BERT` wordmark sat in the phone's dock band, so it was
  erased from `generated/mayor-purple-home-v2-source.png` by `tools/erase-mayor-home-wordmark.py` (rebuilds the purple
  gradient and the halftone lattice, 12.43 × 13.45 px, and never touches Bert's pixels). The shipped WebP replaces only
  that area of the previous WebP with the edited master (bicubic cover-crop to 1440×3200, feathered), then WebP q92 m6,
  so Bert stays pixel-identical to the approved art.

## Source provenance

- `sources/bert-mayor-official.png` was served publicly by `bert.global` at `framerusercontent.com/images/3aLB5eoA6lFqSfok88rL6hS2cQ.png`.
- `sources/woofhub-hero-official.svg` was served publicly by `woofhub.com` at `/landing-page/hero-mobile.svg`.
- `sources/berthalla-hero-official.jpg` is the higher-resolution original supplied by the project owner in `/opt/uploads/grok_1784582230896.jpg`.

These are official project-controlled brand assets, retained here so release builds are reproducible and do not download artwork at runtime.

## Rendering

Render the Woofhub SVGs at their native 1440×3200 size. Scale-and-center-crop the V2 raster masters to 1440×3200 without stretching, then create a lightweight Lock Screen preview. Verify all artwork against common centered, fill, and launcher-cropped wallpaper presentations before release.

The working Figma acceptance board is `BERT Phone Themes — S-tier Review`. It overlays Android-style clocks, notifications, icon grids, docks, and BERT widgets on the exact production exports to expose collisions before release.
