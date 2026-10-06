package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme

/**
 * Winzige Verlaufskurve ohne Achsen: Nur die Form zählt – ging es stetig bergauf, gab es einen
 * Rückschritt? Die Zahlen dazu stehen daneben im Text.
 *
 * Reine Zierde für TalkBack, deshalb ohne Beschreibung. Bei weniger als zwei Werten gibt es
 * keine Form, und es wird nichts gezeichnet; bei lauter gleichen liegt die Linie in der Mitte.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = AccentBlue
) {
    Canvas(modifier = modifier.size(Dimens.SparklineWidth, Dimens.SparklineHeight)) {
        if (values.size < 2) return@Canvas
        val stroke = Dimens.SparklineStroke.toPx()
        val dot = Dimens.SparklineDot.toPx()
        // Rand in Punktgröße, damit weder Linie noch Endpunkt angeschnitten werden.
        val top = dot
        val usableHeight = size.height - 2 * dot
        val usableWidth = size.width - 2 * dot
        val min = values.min()
        val range = values.max() - min
        val stepX = usableWidth / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            val fraction = if (range == 0.0) 0.5f else ((value - min) / range).toFloat()
            Offset(dot + index * stepX, top + usableHeight * (1f - fraction))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Der jüngste Wert als Punkt: Dort steht die Übung heute.
        drawCircle(color = color, radius = dot, center = points.last())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C222B)
@Composable
private fun SparklinePreview() {
    MeinTrainingTheme {
        Sparkline(values = listOf(50.0, 52.5, 52.5, 55.0, 52.5, 55.0, 57.5, 60.0))
    }
}
