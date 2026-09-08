package global.bert.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

internal enum class BERTSymbol { HOME, EXPLORE, CREATE, TOOLS, PLAY, MUSIC, PACK, STORY, HEART, ARROW }

/** One stroke weight and coordinate system; decorative icons inherit the control's text label. */
@Composable
internal fun BERTIcon(symbol: BERTSymbol, color: Color = AccentText, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val line = Stroke(width = 1.8f, cap = StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>, close: Boolean = false): Path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.first, p.second) else lineTo(p.first, p.second) }
            if (close) close()
        }
        scale(size.width / 24, size.height / 24, pivot = Offset.Zero) {
            when (symbol) {
                BERTSymbol.HOME -> {
                    drawPath(path(3f to 10f, 12f to 3f, 21f to 10f), color, style = line)
                    drawPath(path(5f to 9f, 5f to 21f, 10f to 21f, 10f to 15f, 14f to 15f, 14f to 21f, 19f to 21f, 19f to 9f), color, style = line)
                }
                BERTSymbol.EXPLORE -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = line)
                    drawPath(path(16f to 8f, 14f to 14f, 8f to 16f, 10f to 10f, close = true), color, style = line)
                }
                BERTSymbol.CREATE -> {
                    drawPath(path(14f to 4f, 5f to 4f, 4f to 5f, 4f to 20f, 20f to 20f, 20f to 11f), color, style = line)
                    drawPath(path(11f to 10f, 18f to 3f, 21f to 6f, 14f to 13f, 10f to 14f, close = true), color, style = line)
                }
                BERTSymbol.TOOLS -> {
                    listOf(6f, 12f, 18f).forEach { x -> drawLine(color, Offset(x, 3f), Offset(x, 21f), 1.8f, StrokeCap.Round) }
                    listOf(Offset(6f, 8f), Offset(12f, 16f), Offset(18f, 9f)).forEach {
                        drawCircle(Navy, 2.6f, it); drawCircle(color, 2.6f, it, style = line)
                    }
                }
                BERTSymbol.PLAY -> drawPath(path(7f to 3f, 21f to 12f, 7f to 21f, close = true), color, style = line)
                BERTSymbol.MUSIC -> {
                    drawPath(path(9f to 18f, 9f to 6f, 20f to 3f, 20f to 15f), color, style = line)
                    drawOval(color, Offset(3f, 16f), Size(6f, 5f)); drawOval(color, Offset(14f, 13f), Size(6f, 5f))
                }
                BERTSymbol.PACK -> {
                    drawCircle(color, 3.5f, Offset(9f, 7f), style = line)
                    drawArc(color, 180f, 180f, false, Offset(2f, 14f), Size(14f, 12f), style = line)
                    drawArc(color, -90f, 180f, false, Offset(14f, 4f), Size(6f, 6f), style = line)
                    drawArc(color, -90f, 90f, false, Offset(11f, 14f), Size(11f, 12f), style = line)
                }
                BERTSymbol.HEART -> drawPath(Path().apply {
                    moveTo(12f, 21f); cubicTo(-6f, 9f, 6f, -3f, 12f, 6f)
                    cubicTo(18f, -3f, 30f, 9f, 12f, 21f)
                }, color, style = line)
                BERTSymbol.STORY -> {
                    drawPath(path(5f to 3f, 19f to 3f, 19f to 21f, 5f to 21f, close = true), color, style = line)
                    drawLine(color, Offset(9f, 8f), Offset(15f, 8f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(9f, 13f), Offset(15f, 13f), 1.8f, StrokeCap.Round)
                }
                BERTSymbol.ARROW -> {
                    drawPath(path(5f to 19f, 19f to 5f, 7f to 5f), color, style = line)
                    drawLine(color, Offset(19f, 5f), Offset(19f, 17f), 1.8f, StrokeCap.Round)
                }
            }
        }
    }
}
