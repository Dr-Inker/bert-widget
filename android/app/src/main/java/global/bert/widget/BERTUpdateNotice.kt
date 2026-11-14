package global.bert.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import global.bert.widget.data.BERTRelease

/**
 * A newer BERT on the website, for an install that did not come from Google Play. [hiddenOnHome] once the person has
 * hidden Home's row for this version; About still mentions it.
 */
internal class AppUpdate(val release: BERTRelease, val hiddenOnHome: Boolean, val hide: () -> Unit)

/** The open app's update news; null on Play installs, in previews and tests, and when there is nothing newer. */
internal val LocalAppUpdate = compositionLocalOf<AppUpdate?> { null }

/**
 * Home's one quiet row: "BERT 0.14.3 is out", Get it (the website, in the browser) and Hide (for good, for this
 * version). No card, no icon: the same weight as the board row above it.
 */
@Composable
internal fun UpdateRow(update: AppUpdate) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().testTag("update-row"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("BERT ${update.release.version} is out", color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 4.dp))
        TextButton(onClick = { openBERTLink(context, BERTLink.APP) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text("Get it", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = update.hide, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text("Hide", color = Muted)
        }
    }
}
