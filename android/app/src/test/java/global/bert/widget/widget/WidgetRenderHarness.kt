package global.bert.widget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import kotlinx.coroutines.runBlocking

/** A real Glance widget composed to RemoteViews, inflated on the host and drawn: the host view tree and its pixels. */
internal class HostRender(val host: FrameLayout, val bitmap: Bitmap)

/**
 * The one place a widget becomes pixels on the host. [BERTWidgetRenderTest] measures the view tree it returns and
 * [BERTWidgetPreviewTest] saves the bitmap as the launcher-picker preview, so previews are the widgets themselves.
 */
@OptIn(ExperimentalGlanceApi::class)
internal fun renderWidgetOnHost(context: Context, widget: GlanceAppWidget, widthDp: Int, heightDp: Int): HostRender {
    val views = runBlocking { widget.compose(context, size = DpSize(widthDp.dp, heightDp.dp)) }
    val density = context.resources.displayMetrics.density
    val host = FrameLayout(context)
    val root = views.apply(context, host)
    host.addView(root)
    val w = (widthDp * density).toInt(); val h = (heightDp * density).toInt()
    host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
    host.layout(0, 0, w, h)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    host.draw(Canvas(bitmap))
    return HostRender(host, bitmap)
}
