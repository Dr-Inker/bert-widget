# BERT NFT collection: facts for "My Bert" (researched 2026-10-12, no API key)

## Collection

- **Identity:** "Bert", symbol `$BERT` ("The official NFT project from the $BERT team"). 3,333 minted; 3,331 exist
  (2 burned).
- **Standard:** MPL Core.
- **Collection account:** `AYh4yso4fZUVHEMMpS3wXxwZt1TgYnbFPb5c4y5LDFFS`, owned by
  `CoREENxT6tW1HoK8ypY1SxRMZTcVPm7R94rH4PZNhX7d`.
- **Marketplaces:** Magic Eden symbol `bert_coin`, plus Tensor `bert`.
- **Art:** `https://green-advanced-mule-774.mypinata.cloud/ipfs/bafybeibas2bpfgih2j5dko5w7fiufwgmpqvoznzji3nif2xjix736kybpy/{N}.png`
  - About 632px PNGs, CORS open, with an immutable cache.
  - `?img-width=256&img-format=webp` resizes the image. Pinata provides this, so keep a fallback to the original.
- **Traits:** `Background`, `Hands`, `Base`, `Clothing`, `Eyes`, `Mouth`, `Head`, `Pet`, `Unique`, plus `1 of 1` on 33
  assets.
  - Background has five solid colours: Green, Yellow, Blue, Pink and Red.
  - It also has four scenes: Forest, Heaven, Moon and City.

## Wallet lookup

1. **Magic Eden.** Call
   `GET https://api-mainnet.magiceden.dev/v2/wallets/{wallet}/tokens?collection_symbol=bert_coin&offset=0&limit=100`.
   - It returns a JSON array of `{mintAddress, owner, name, image, attributes[], listStatus}`.
   - It covers NFTs that are owned and NFTs that are listed for sale.
   - Rate limit headers say 180. Terms are unverified.
2. **Staked NFTs (escrow).** Magic Eden misses these, and so does a plain owner lookup.
   - The staking program is `BcTJUjVtpYZ2mozwHxGZdJRfQbEfCoZyEqwus8W2cajq` (`bert_staking_sc`).
   - The vault is `mu5h6byDpZBzugjEuswVSUTqEshhmi6M5C1nwZhB8T1` and holds 1,141 assets (34%).
   - To find a staker's NFTs, call `getProgramAccounts` with a memcmp filter on discriminator
     `[202,171,178,46,38,48,31,36]` at offset 0 and the staker at offset 8. Each position has `asset` at offset 98.
   - Confirm `getAsset` reports `ownership.owner` as the vault. Don't trust `status`. If an asset has more than one
     position, the latest `deposit_time` wins.
3. **DAS on the public RPC.** `searchAssets` with grouping on the collection works without a key, but it is
   undocumented and not for production.

## Risks

- **Licence.** bert.global's terms grant no NFT display rights. Showing a holder's own NFT is low risk, but get the
  team's written OK.
- **Play.** A read-only viewer sells nothing and awards nothing, so it is probably undeclared, but check the
  Financial features form. Never gate rewards on holding, and never promote earnings.
- **Privacy.** The wallet address goes to Magic Eden and the Solana RPC. Update the privacy page and Data safety.
