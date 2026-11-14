package global.bert.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import global.bert.widget.BERTEntry
import global.bert.widget.MainActivity
import global.bert.widget.R
import global.bert.widget.data.BERTActivity
import global.bert.widget.data.BERTActivityRepository
import global.bert.widget.data.MyBertRepository
import global.bert.widget.data.MyBertStore

/**
 * Bert's day: a note from Bert, on paper (0.15). Pixel Bert acts the feed's mood with a handwritten "feeling …", his latest
 * post is in his handwriting under a "FROM BERT · 6:45 AM" eyebrow, and the tournament is chalked on a slate. Paper and
 * slate are fixed materials, so the note looks the same under every theme pack. No market data. Tapping opens Home.
 */
class BERTDailyWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val revision = currentState(REVISION) ?: 0
            val activity = remember(revision) { BERTActivityRepository(context).load() }
            val myBert = remember(revision) { WidgetBert.load(context) }
            NoteContent(revision, activity, myBert)
        }
    }
}

/** Smallest text on any widget: freshness, status, mood and event lines never go below 10sp. */
internal const val FRESHNESS_SP = 10f

/**
 * The holder's own Bert (My Bert) in place of the photo: the picture kept on the phone, at most [MAX_PX] a side,
 * so it works offline and stays well inside the launcher's bitmap budget.
 */
internal class WidgetBert(val number: Int, val picture: Bitmap) {
    val description get() = "Your Bert #$number"

    companion object {
        private const val MAX_PX = 384

        fun load(context: Context): WidgetBert? {
            val chosen = MyBertStore(context).chosen() ?: return null
            val file = MyBertStore(context).imageFile
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            val picture = MyBertRepository.decodeBounded(bytes, MAX_PX) ?: return null
            return WidgetBert(chosen.bert.number, picture)
        }
    }
}

@Composable
private fun NoteContent(revision: Int, activity: BERTActivity?, myBert: WidgetBert?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val now = System.currentTimeMillis()
    val use24Hour = DateFormat.is24HourFormat(context)
    val configuration = context.resources.configuration
    // Drawn once per new feed, resize, text size or clock format; the countdown's hour is a key so a re-render shows it moved.
    val plan = remember(revision, activity, myBert, size, use24Hour, configuration.fontScale, configuration.densityDpi, activity?.isStaleAt(now), now / 3_600_000) {
        planNote(context, size.width.value, size.height.value, activity, now, myBert, use24Hour)
    }
    val d = plan.density
    fun Int.asDp() = (this / d).dp
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.bert_note_paper)).cornerRadius(20.dp)
        .clickable(actionStartActivity<MainActivity>(entryParameters(BERTEntry.HOME)))) {
        if (plan.wide) Row(GlanceModifier.fillMaxSize()) {
            Box(GlanceModifier.width(plan.panelWidth.asDp()).fillMaxHeight().background(ImageProvider(R.drawable.bert_note_panel)),
                contentAlignment = Alignment.BottomCenter) {
                Column(GlanceModifier.padding(bottom = plan.bottomPad.asDp()), horizontalAlignment = Alignment.CenterHorizontally) {
                    NotePieceView(plan.bert, d)
                    plan.caption?.let { Spacer(GlanceModifier.height(plan.captionGap.asDp())); NotePieceView(it, d) }
                }
            }
            Spacer(GlanceModifier.width(plan.columnGap.asDp()))
            Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(top = plan.pad.asDp(), bottom = plan.bottomPad.asDp(), end = plan.pad.asDp())) {
                plan.stamp?.let { NotePieceView(it, d); Spacer(GlanceModifier.height(plan.postGap.asDp())) }
                plan.post?.let { NotePieceView(it, d) }
                Spacer(GlanceModifier.defaultWeight())
                plan.slate?.let { NotePieceView(it, d) }
            }
        } else Column(GlanceModifier.fillMaxSize().padding(start = plan.pad.asDp(), top = plan.pad.asDp(), end = plan.pad.asDp(), bottom = plan.bottomPad.asDp())) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                NotePieceView(plan.bert, d)
                if (!plan.captionBelow && (plan.caption != null || plan.stamp != null)) {
                    Spacer(GlanceModifier.width(plan.columnGap.asDp()))
                    Column {
                        plan.caption?.let { NotePieceView(it, d) }
                        plan.stamp?.let { if (plan.caption != null) Spacer(GlanceModifier.height(plan.captionGap.asDp())); NotePieceView(it, d) }
                    }
                }
            }
            // Where the mood and the time do not fit beside Bert, they go on their own lines under him.
            if (plan.captionBelow) {
                plan.caption?.let { Spacer(GlanceModifier.height(plan.captionGap.asDp())); NotePieceView(it, d) }
                plan.stamp?.let { Spacer(GlanceModifier.height(plan.captionGap.asDp())); NotePieceView(it, d) }
            }
            plan.post?.let { Spacer(GlanceModifier.height(plan.postGap.asDp())); NotePieceView(it, d) }
            Spacer(GlanceModifier.defaultWeight())
            plan.slate?.let { NotePieceView(it, d) }
        }
    }
}

/**
 * One piece of the note, exactly its bitmap's size and shown unscaled (scaleType center), so neither the handwriting nor
 * pixel Bert is ever resampled, on any launcher or Android version. Words carry their full text for TalkBack.
 */
@Composable
private fun NotePieceView(piece: NotePiece, density: Float) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, if (piece.description == null) R.layout.widget_note_art else R.layout.widget_note_words).apply {
        setImageViewBitmap(R.id.note_image, piece.bitmap)
        piece.description?.let { setContentDescription(R.id.note_image, it) }
    }
    // A quarter pixel over: before Android 12 Glance turns the dp back into px and truncates, which at some densities
    // came out a pixel short and clipped pixel Bert's last row. Android 12+ rounds, so the size stays exact there.
    AndroidRemoteViews(views, GlanceModifier.width(((piece.width + 0.25f) / density).dp).height(((piece.height + 0.25f) / density).dp))
}
