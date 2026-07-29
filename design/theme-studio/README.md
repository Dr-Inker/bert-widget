# BERT Theme Studio artwork

Production artwork for the Theme Studio. Every pack has a clock-safe Lock master and a quieter, icon-safe Home master. Production PNGs are rendered at 1440×3200 into `android/app/src/main/res/drawable-nodpi`; lightweight JPEG previews keep the Compose gallery responsive.

## Packs

- `generated/mayor-purple-{lock,home}-v2-source.png`: seamless vivid `bert.global` portrait treatments
- `woofhub-night-{lock,home}.svg`: editable dark `woofhub.com` treatments
- `generated/berthalla-nights-{lock,home}-v2-source.png`: vertically extended moonlit `berthalla.io` treatments

The older Mayor and Berthalla SVG compositions are retained as revision history, but are not production inputs. The V2 raster masters remove the landscape-source boundaries that were visible in V1.

## Source provenance

- `sources/bert-mayor-official.png` was served publicly by `bert.global` at `framerusercontent.com/images/3aLB5eoA6lFqSfok88rL6hS2cQ.png`.
- `sources/woofhub-hero-official.svg` was served publicly by `woofhub.com` at `/landing-page/hero-mobile.svg`.
- `sources/berthalla-hero-official.jpg` is the higher-resolution original supplied by the project owner in `/opt/uploads/grok_1784582230896.jpg`.

These are official project-controlled brand assets, retained here so release builds are reproducible and do not download artwork at runtime.

## Rendering

Render the Woofhub SVGs at their native 1440×3200 size. Scale-and-center-crop the V2 raster masters to 1440×3200 without stretching, then create a lightweight Lock Screen preview. Verify all artwork against common centered, fill, and launcher-cropped wallpaper presentations before release.

The working Figma acceptance board is `BERT Phone Themes — S-tier Review`. It overlays Android-style clocks, notifications, icon grids, docks, and BERT widgets on the exact production exports to expose collisions before release.
