# Bert art on this server, and Draw Bert — inventory 2026-10-12

Contact sheets are in the session scratchpad: `/tmp/claude-0/-opt/2b21dd72-78d2-49c9-8fc4-4439f38c4603/scratchpad/art/`.
They are temporary.

**Before writing about the real dog, follow the canon handling rules in the Berthalla repo.** Product copy is
character-first.

## Real photos

There is only one: the red-hat and blue duffle-coat shot. The app holds it as `bert_widget_portrait.jpg` (480×640),
the banner, the token and `bert_icon.png`. Daily photo rotation needs photos from the owner.

## Official cartoon (bert.global style)

- `/opt/bert-buybot/assets/bert_sprite.png` (1073×1200, transparent) is the best clean cut-out: full body, smiling.
- `design/theme-studio/sources/bert-mayor-official.png` is the smug grin, but its background is baked in.
- There is no official expression set.

## Pixel art (owner-made, PixelLab)

- MMO portraits `/opt/bert-mmo/client/assets/bert/portraits/bert_portrait_{happy,excited,neutral,tired}.png`
  (128px, transparent). These cover four moods.
- `/opt/bert-horde/assets/icon_foreground.png` (432px face). It is the best "Meet Bert" pixel portrait.
- Platformer sprites are in `/opt/bert-platformer/assets/expedition/bert{Idle,Bark,Jump,Walk}.png` (48px frames).

## NFT-generator busts

`/opt/nft-generator-release/apps/web/public/review/bert-*-v1.png` (2048px, transparent) are clean flat busts with no hat.

## Avoid

- **AI-look art:** the Grok/Gemini cozy scenes in `/opt/berthalla/oneofone_images` and the wallpapers
  (Berthalla Nights / Moonlight).
- **Gemini-watermarked files:** `/opt/uploads/Bert_multi_shade.png`, the BertBot turnaround sheet, and
  oneofone/60.png.
- **Non-Bert characters:** the demon cat and the cast guests.
- **Screenshots:** the Instagram, LinkedIn and One UI screenshots in `/opt/uploads`.

## Mood gaps

Anxious, smug, melancholy and giddy have no clean on-model art. Filling them needs new art: PixelLab pixel
portraits that match the MMO set.

## Draw Bert

- **Engine:** web Studio `/opt/berthalla-studio` (FastAPI :4720, berthalla.io/studio/) and Telegram BertBot `/db`.
  Both use xAI Grok Imagine with the canonical sheet as reference. About $0.065 per image, 2048px output in about
  20 seconds.
- **Studio API:** `GET /studio/api/status`; `POST /studio/api/draw {"prompt"}` returns
  `{"image_url":"gens/<hex>.png","remaining"}`. Errors come back as HTTP 200 with a body. Files are pruned after
  24 hours.
- **Limits:** 3 per visitor per day, keyed on an IP hash plus a cookie, and 100 per day globally (about $7).
- **Not safe for native use as it stands.** There is no app auth, a client can reset its own quota, and app users
  could use up the shared global cap and close the web Studio.
- **v1:** deep-link to the Studio.
- **Native version later:** needs its own endpoint, Play Integrity or an install token, a per-install quota and
  a separate cap.
