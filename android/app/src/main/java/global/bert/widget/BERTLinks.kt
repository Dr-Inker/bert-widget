package global.bert.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

// Curated destinations; no URL from an activity payload is launched.
internal enum class BERTLink(val url: String, val launchLabel: String) {
    FLAPPY("https://t.me/FlappyBertOfficialBot", "Open in Telegram"),
    DRAW("https://berthalla.io/studio/", "Open in browser"),
    MUSIC("https://berthalla.io/music/", "Open in browser"),
    WOOFHUB("https://woofhub.com/", "Open in browser"),
    COMMUNITY("https://t.me/BERTCOINCTO", "Open in Telegram"),
    DISPATCHES("https://x.com/Berthalla_bot", "Open on X"),
    STORY("https://www.bert.global/", "Open in browser"),
}

internal fun openBERTLink(context: Context, link: BERTLink) {
    openExternalUrl(context, link.url)
}

internal fun openBERTMarketLink(context: Context, url: String) {
    val validated = global.bert.widget.data.BERTQuoteRepository.requireValidDexScreenerPairUrl(url)
    openExternalUrl(context, validated)
}

private fun openExternalUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Install a browser or the destination app to open this link.", Toast.LENGTH_LONG).show()
    }
}
