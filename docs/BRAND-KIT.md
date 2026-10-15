# BERT brand kit: what the app takes from it (read 15 October 2026)

Source: the official BERT brand kit, v1.0 (July 2026), "extracted from the live Figma". It is private; the owner has
access. Nothing secret is copied here.

## In the app (0.11, branch `v011-brand`)

- **App colours, BERT Violet (default)**, kit "product mode" (`BERTScheme.BERT_VIOLET` in `BERTComponents.kt`):
  - Surfaces: background `#100B16` (bg-deep), card `#1A1125` (s1), strong card `#302042` (s2).
  - Text: white `#FFFFFF`; muted `#CECECE` (the kit says muted text is a colour, never an opacity).
  - Links and labels: lilac `#EABFFF`. The active and selected border is also lilac.
  - Solid action button: violet `#8531D5` with white text (6.2:1). Violet is never used for text (3.1:1 on the
    background); the kit calls it "an accent and a border".
  - Green, red and amber stay as functional status colours. The kit allows green at status size only; red and amber
    carry price-down and warnings.
- **Berthalla Navy** is the app's original navy and orange, kept as the alternative (Create → Personalise → App colours).
- **Type:** Outfit, the kit's only typeface, at five static weights (Regular, Medium, SemiBold, Bold and ExtraBold:
  `res/font/outfit_*.ttf`). They were cut with fontTools from google/fonts' variable `Outfit[wght].ttf`, whose default
  instance is Thin, so no device can fall back to Thin. Licence: SIL OFL 1.1; it ships in
  `assets/licences/OFL-Outfit.txt` and is credited in About. Berthalla Navy keeps
  the system font. Widgets keep the system font (Glance cannot use a bundled font).
- **Wordmark:** `res/drawable/bert_wordmark_brand.xml` is a vector made from the kit's `bert-logo-wordmark.svg`, with
  its white-to-lilac gradient. The kit says "only on dark, never on white"; the app is dark in both schemes.

- **Widget and wallpaper pack, BERT Violet (default for new installs):**
  - Palette: the scheme's surfaces, text and muted colour, with violet `#8531D5` as the accent stripe. The mood pill is
    lilac on the kit's ink `#150C1F`.
  - Wallpapers: rendered from the kit's own SVGs over its violet radial glow, at 1440×3200. The Lock Screen uses the
    stacked "Mayor of Solana" lockup, low enough to keep the clock clear; the Home Screen uses the plain wordmark.
  - Existing users keep their saved pack.

- **Buttons:** as on bert.global, ordinary actions are glass: violet at 20% behind a 1px white border, with white
  text, and a dimmed border when disabled (`actionButtonColors` / `actionButtonBorder`). Solid violet
  (`solidActionButtonColors`) is kept for the single highest-intent action on a screen: Make it a card, Share caption
  card, Use this Bert, Save holdings, Save price alerts, and Review report. Berthalla Navy keeps filled orange
  throughout.

## bert.global compared (15 October 2026)

The official site is a Framer build that follows the kit closely:
- Outfit throughout.
- Surfaces `#21162D`, `#372748` and `#100B16`.
- Violet mostly as a 20% fill behind white-bordered pills (e.g. "Find us on Magic Eden", "Start Staking Now").
- Cartoon Bertram everywhere, and the stacked "Mayor of Solana" lockup on a violet sunburst as the hero.
- One highlight phrase per headline, in `#A548FF` ("BERT Staking **Live Dashboard**").

The site still carries the kit's flagged drift: Aeonik on some labels and Thunder on display numbers.

The app now matches its type, surfaces and buttons. It keeps the red-hat photo as Bert's face (an owner decision) and
does not copy the site's exchange, staking-APR or marketing content (Play policy: no rewards or earnings in the app).

## Rules from the kit worth keeping

- One brand violet, near-black purple surfaces; depth comes from surface steps and 1px borders, never shadows.
- Radii 20 (card), 24 (surface), 32 (control), pill. Controls are rounder than cards. Nothing at 4, 8, 12 or 16.
- Controls: a 1px white border over a 20% fill (violet at 20% for primary; white at 20% for glass). Solid violet only for
  the single highest-intent action on a page.
- Never put lilac text on violet. `#992EFF` is for display type and social headlines; `#8531D5` is for interface.
- Highlight one phrase per headline at most. Labels are uppercase eyebrows; never letterspace body copy.
- Icons: Heroicons v2, solid style only.
- Bertram: red hat and blue hoodie = BERT; purple hat and hoodie = WoofHub. Don't mix them, don't redraw, don't
  recolour.
- Copy: say "Find $BERT", not "Buy $BERT", wherever BERT is not the seller. (The app has no "Buy $BERT" copy.)
- WoofHub, when shown next to BERT, is followed by "Powered by BERT".

## Not yet adopted (candidates)

- The app's radii (16, 20 and 24 today) against the kit's 20/24/32.
- Card borders: bert.global's feature cards carry 1px violet borders; the app's cards are filled panels.
- The kit's Bertram mascot art (primary standing pose and the hat-tip bust) for Meet Bert or empty states.
