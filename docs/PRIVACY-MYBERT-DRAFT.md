# Privacy page draft: My Bert (BERT app 0.10)

Draft paragraph for berthalla.io/app/privacy/, to sit with the other "What the app fetches" items. Plain text, ready
to paste; the owner publishes it.

---

### My Bert (optional)

If you hold a Bert from the official BERT NFT collection, you can paste a public Solana wallet address into My Bert to
show that Bert on Home, on your Bert widget and on your caption cards. When you ask it to look (for example with "Find my Berts" or "Refresh my
Berts"), the app sends that address to two services, and only to them: Magic Eden (api-mainnet.magiceden.dev), to list
the Berts the address holds or has listed, and the public Solana network (api.mainnet-beta.solana.com), to find Berts
the address has staked. The app then downloads those Berts' pictures from the collection's own image host
(green-advanced-mule-774.mypinata.cloud). Like any web request, these services see your device's IP address, and their
own privacy policies apply. The address is never sent to Berthalla or to us, and we never receive it. It is kept only on
your phone, in the app's private storage (excluded from backups), together with the latest list of your Berts and
the picture of the one you choose. "Forget this address" in My Bert deletes all of it; uninstalling the app or clearing
its data does the same. My Bert is read-only: it never connects a wallet, never asks for a recovery phrase or private
key, never signs anything, and cannot move or spend anything. It shows no prices or values.

---

Notes for the owner (not for publishing):

- Keep the hosts list in step with `MyBertSources.HOSTS` in the app.
- If the Play Data safety form ends up declaring the wallet address as collected (see `docs/play/LISTING.md`), add one
  sentence here to match: "The Play Store lists this as optional financial info used only for app functionality."
