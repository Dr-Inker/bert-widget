package global.bert.widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The footer on every screen: version, and the way into privacy, feedback and licences. */
@Composable
internal fun AboutFooter() {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
        Text("BERT · v${BuildConfig.VERSION_NAME} · About, privacy & feedback", color = Muted, fontSize = 12.sp)
    }
    if (open) AboutSheet { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSheet(close: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = close, containerColor = Panel, contentColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("About BERT", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
            Text("A fan app for Bert, a real Pomeranian, and the pack around him, made by Berthalla. Its daily updates come from " +
                "Berthalla, not from Bert’s official account. It never holds keys, connects a wallet, signs anything or trades, " +
                "and your holdings, cards and alerts stay on this phone. If you add a wallet address in My Bert, it stays on this " +
                "phone too, and is sent only to Magic Eden and the Solana network to find your Berts, never to Berthalla.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Muted, fontSize = 13.sp)

            AboutRow("Privacy policy", "What stays on your phone and what the app fetches.") { openBERTLink(context, BERTLink.PRIVACY) }
            AboutRow("Send feedback", "Report a bug or suggest something on GitHub.") { openBERTLink(context, BERTLink.FEEDBACK) }
            AboutRow("Official BERT on X", "@bertcoincto, Bert’s main account.") { openBERTLink(context, BERTLink.OFFICIAL_X) }
            AboutRow("bert.global", "The official BERT website.") { openBERTLink(context, BERTLink.STORY) }
            AboutRow("Talk to the pack", "The BERT community on Telegram.") { openBERTLink(context, BERTLink.COMMUNITY) }
            AboutRow("Source code", "Every permission and network request, in the open.") { openBERTLink(context, BERTLink.SOURCE) }

            SectionLabel("OPEN-SOURCE LICENCES")
            Text("BERT is built with Android Jetpack (AndroidX Activity, Compose, Material 3, Glance, WorkManager and their " +
                "AndroidX dependencies), Kotlin and kotlinx.coroutines. Each is © its authors and used under the Apache License 2.0.",
                color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            TextButton(onClick = { openBERTLink(context, BERTLink.APACHE_LICENSE) }, contentPadding = PaddingValues(0.dp)) {
                Text("Read the Apache License 2.0 ↗", color = AccentText)
            }
            Text("Text is set in Outfit, © The Outfit Project Authors, used under the SIL Open Font License 1.1.",
                color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            TextButton(onClick = { openBERTLink(context, BERTLink.OFL_LICENSE) }, contentPadding = PaddingValues(0.dp)) {
                Text("Read the Open Font License ↗", color = AccentText)
            }
            Text("BERT market figures are informational only and are not financial advice.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun AboutRow(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = PanelStrong)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$title ↗", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
