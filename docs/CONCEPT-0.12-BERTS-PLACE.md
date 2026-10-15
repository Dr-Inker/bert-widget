# 0.12 concept: Bert's Place (revised after critique round 1: Bert's day)

Owner, 15 October 2026: "think more like a human and find that creative spark that pulls away from generic AI
results." This note picks one idea and the rules that keep it honest. Prototype and critique come before the build.

**Status: shipped.** 0.12.0 went live on berthalla.io and to Google Play on 15 October 2026, after both critique
rounds, a device check on the S25 and an independent review (`docs/evidence/2026-10-15/release-0.12.0/README.md`).
Still open from round 2: Bert is outlined while the painted scene isn't, and one view shows three boundary materials.

## What makes 0.11 look generic (my own critique)

1. **One skeleton for every screen.** Each is a stack of rounded dark cards, and each card has the same anatomy: a
   small capital eyebrow ("APP COLOURS", "PHONE THEMES", "$BERT"), a bold heading, a grey paragraph, then a button.
2. **Slogan copy.** "A little BERT. Everywhere." "Your words. Bert's face." "Follow your nose." "Make your phone
   unmistakably BERT." The cadence is two clipped beats. The screens explain themselves instead of being something.
3. **Bert is a picture inside a card.** He walks across one 136-pixel strip. His mood is an italic word ("content").
   Six drawn mood poses exist, and Home shows them as a thumbnail.
4. **Brand kit applied literally.** Dark violet plus Outfit plus glass pills is what most Web3 sites look like now.
5. **No centre.** The app is price, holdings, alerts, a game, cards, wallpapers, widgets and NFTs: a toolbox, with
   nothing that is a reason to open it tomorrow.

## The idea

**The app opens onto Bert's place: his garden, his cottage, his hills, at your real time of day.** You don't read
about Bert; you visit him. Price and tools are still there, the way a newspaper sits on a kitchen table, but the
place comes first.

It comes from Bert's own world. The Godot scene the banner was cut from already has:
- a stone cottage with a lit window and chimney smoke;
- rolling hills, a winding road, a cherry branch and the sun;
- a soil cross-section with roots under the gate path.

The app has only ever shown a 600×136 slice of it. Its terrain is lit by a shader, so real dawn, dusk and night can be
rendered from the scene rather than faked with a tint.

## Rules that keep it from turning into a template

1. **The scene is never a card.** It runs full width from the top of Home, with no rounded box and no border. Every
   other surface is drawn as a thing from Bert's world, or is plain text on the ground.
2. **Four real times of day**, rendered from the Godot scene: morning, day, dusk and night. They follow the phone's
   local clock: night has the cottage window lit and stars; dawn has the sun low behind the hills. No overlay tints.
3. **Bert's pose is his mood.** The six drawn moods replace the italic mood word, and a sleeping pose is added for
   night. Bert stands on the path at the gate; he is not inside a frame.
4. **One gesture: a pat.**
   - Tap Bert: he reacts with a pose change, a small hop and one pixel heart, and the phone gives a light tap.
   - The first pat each day stamps a paw print on this month's calendar, drawn in his garden's style.
   - There are no streak-loss threats, no counts in red, and nothing to buy.
5. **Copy in his voice, or none.**
   - No two-beat taglines and no screen describes itself.
   - Where words are needed they are specific: "woofmornin. the kettle's on." "it's late. bert's asleep, the window's
     still lit." (Short, lowercase, Bert's register from @bertcoincto.)
6. **The price is quiet.** It is a single line in the soil band under the scene (BERT $0.0154, ▲1.6% today) that
   opens Market. No card, no sparkline on Home. The tools keep their screens; Home stops being a dashboard.
7. **The widget is a window, not a tile.**
   - The Bert widget becomes a small live view of his place: the same time of day, his mood pose, and the tiny lit
     cottage window at night.
   - Text is limited to one short line. The price widgets stay as they are, for people who want numbers.

## Not doing

- No daily rewards, coins, XP or "come back or lose your streak". That fails Play policy, and it isn't Bert.
- No new nav, no onboarding carousel, no confetti, no glow on everything.
- Not throwing away 0.11's colours: violet and Outfit stay for the tools screens and controls. The place has its own
  palette, the painted scene's.

## Art needed

| Piece | Source | Notes |
|---|---|---|
| Home scene, 4 times of day | Godot R2 scene on the Shadow PC, relit (exclusive slot) | Portrait crop: cottage, path, gate and soil. Bert drawn by the app |
| Widget scene, 4 times, 2 sizes | Same renders, cropped | 2×2 and 4×2 |
| Bert asleep | PixelLab inpaint on F/10, or hand-drawn with the workbench | The only new pose |
| Pat reaction: hop and heart | Workbench; 2–3 frames | Pixel heart in his hat red |
| Paw stamp and month calendar | Workbench | Drawn like the garden fence, not a UI grid |

## Process

1. Mockups from the real assets, made with programmatic stand-ins for the times of day. They are rough on purpose;
   the critique is about the idea and the layout.
2. **Critique round 1:** Fable and Grok, separately, on the screenshots. Neither built it, and the question is "where
   does this still look AI-made?"
3. Fix, then real art (Godot relight, sleeping pose, pat frames).
4. Build 0.12 on `v012-place`, then **critique round 2** on real device captures, because round-2 critiques catch
   regressions that round-1 fixes created.
5. Owner look on the S25, then release.

## Critique round 1 (15 October): Fable and Grok, separately

Both called the first draft a costume change. The places they converged:

- **Four lighting states of one picture is a filter, not a place.**
  - Fable: "nobody reopens an app to see the sky turn blue".
  - Grok: "change what exists".
- **The paw trail was a streak bar.** Left to right, greyed future slots, a ring on today, and a count ("12 times this
  month"). That is Duolingo with paws, and it broke our own "no counts" rule.
- **The speech bubble was the card we said we'd kill.** It had a rounded dark box and a grey meta line, and its tail
  pointed at soil.
- **The greeting over the sky was a weather-app overlay.** It stacked four text layers in one corner, and the date was
  grey on a pale sky, which fails contrast.
- **The price line was "ashamed UI".** A ticker with a chevron, pretending to be soil.
- **Other faults:**
  - The lower 40% was empty.
  - The nav lost its icons with nothing gained.
  - The widget caption sat where Google Photos puts captions.
  - The window was lit at 13:40.
  - Bert was the smallest thing on screen.
  - The crop threw away the cherry branch and the low sun, the two most distinctive painted elements.
- **Grok on the copy:** a domestic object followed by a smug ruling becomes a template too. Keep only lines that fail
  if you remove the hat, the hoodie or the mayor bit.

## The revised idea: Bert's day

**Home answers "what is Bert doing right now?"** He keeps a routine, like a real dog. The time of day sets where he is;
his mood (Berthalla's, which already follows the chart) sets how he stands. The scenery is the backdrop to the
routine, not the feature.

| Local time | Where Bert is | What changes in the place |
|---|---|---|
| 06–09 | At the gate, first out ("gate's open. that was policy.") | Low sun behind the hills; the lamp goes out |
| 09–12 | On the path, on his rounds; his lead appears in the restless pose | Full day |
| 12–15 | Sun-nap on the grass (new pose: asleep) | Full day, bright |
| 15–18 | Gate duty: on the wall end, waiting for the post | Light goes golden |
| 18–21 | On the cottage step | Dusk: the lamp is lit and the hills fade |
| 21–06 | **Not in the garden. He's inside asleep.** | Near-black; one lit window, stars, and the hedge as a silhouette |

### What lives in the place (no cards)

- **The noticeboard.** A small board nailed to the garden wall carries the day's Berthalla update, pinned over a torn
  corner of yesterday's. Tap it to read the whole note. If the feed is stale, the board says so in his voice ("no post
  today. the postman's on strike.") rather than showing an old note as new.
- **The price tag.**
  - A crooked paper tag nailed to the gate post: "$0.0154", in a small pixel hand, ugly on purpose. Tap it for
    Market.
  - No percent and no colour coding; how Bert stands already says how the day is going.
- **Paw prints on the path.**
  - Each day you visit leaves one print, stamped on the garden path in the order you came: overlapping, one smudged
    where he sat.
  - No future slots, no count and no ring.
  - At the start of a month the path is clean, and nothing is lost.
- **A pat.**
  - Tap Bert, or anywhere on the path near him: he leans in and his hat slips a pixel. He doesn't fix it.
  - A light haptic tick, and no heart.
  - Pat him five times in a row and he steps half off the path and looks at your hand.
- **At night, tap the window:** it flicks off, then comes back on after two seconds. "you woke the mayor." That is the
  screenshot.
- **One line of Bert's voice**, lettered on the soil band under the scene, with no logo and no date. The slot picks
  the line, from a bank written to pass Grok's test (it must fail without the hat, the hoodie or the mayor).

### Composition

- **Crop.** Gate, wall and path in the foreground, the cottage small on the hill, and the cherry branch top left.
  Bert at about 1.6× the first mockup, so he is the largest thing on screen. The scene runs down through the soil
  cross-section.
- **Below the fold.** The Lost Trail, the tournament and the rest of Home stay below, on the deep soil. The nav keeps
  its icons.
- **Status bar.** Its icons switch with the time of day so they stay readable on the sky.

### Widget

- A stamp of the same scene and slot, refreshed when the slot changes, with no text overlay. The scene says the time.
- At night it shows the dark garden and the lit window.
- The price widgets stay for people who want numbers.

### New art (revised)

| Piece | How |
|---|---|
| Six times of day | A Godot relight of the R2 scene on the Shadow PC; night is mostly darkness, not a tint |
| Asleep pose (sun-nap) | A PixelLab inpaint on F/10, plus a check by eye |
| Hat-slip lean (pat) | Two or three frames, workbench |
| Noticeboard with a torn note; price tag on the post; paw print | Workbench, at the scene's own pixel scale |
| Lit window on/off | A crop of the rendered scene |

## Critique round 2 (15 October): Fable, on real host renders

The headline: the routine wasn't on screen, because Bert stood in the same spot in four of six slots. That was fixed
in `ec3e030`:
- Bert moves through the day and walks his rounds.
- The gate opens The Lost Trail.
- The price is in pixel chalk, and the scene ends in dithered pixel steps.
- The Lost Trail card and the official chips are de-carded.
- The voice bank was rewritten.
- The widget crops were fixed.

Owner art rulings from the same day: no wall ending in mid-air; no soil roots; window light has to read as 3D on 2D,
so stepped shading and no blur glows.

Still open, considered and deferred:
- Bert is outlined while the painted scene isn't. That is the F/10 art the owner approved; re-styling him is a
  separate art decision.
- The picket fence, gate and wall are three boundary materials in one view.
