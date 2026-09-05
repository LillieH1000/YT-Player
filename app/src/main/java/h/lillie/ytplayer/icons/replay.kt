package h.lillie.ytplayer.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val replay: ImageVector
  get() {
    if (_replay != null) {
      return _replay!!
    }
    _replay =
      ImageVector.Builder(
          name = "replay",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.NonZero,
          ) {
            moveTo(8.49f, 21.29f)
            quadTo(6.85f, 20.58f, 5.64f, 19.36f)
            reflectiveQuadTo(3.71f, 16.51f)
            reflectiveQuadTo(3f, 13f)
            horizontalLineTo(5f)
            quadToRelative(0f, 2.92f, 2.04f, 4.96f)
            reflectiveQuadTo(12f, 20f)
            reflectiveQuadToRelative(4.96f, -2.04f)
            quadTo(19f, 15.93f, 19f, 13f)
            quadTo(19f, 10.07f, 16.96f, 8.04f)
            reflectiveQuadTo(12f, 6f)
            horizontalLineTo(11.85f)
            lineTo(13.4f, 7.55f)
            lineTo(12f, 9f)
            lineTo(8f, 5f)
            lineTo(12f, 1f)
            lineToRelative(1.4f, 1.45f)
            lineTo(11.85f, 4f)
            horizontalLineTo(12f)
            quadToRelative(1.88f, 0f, 3.51f, 0.71f)
            quadToRelative(1.64f, 0.71f, 2.85f, 1.93f)
            reflectiveQuadToRelative(1.93f, 2.85f)
            reflectiveQuadTo(21f, 13f)
            reflectiveQuadToRelative(-0.71f, 3.51f)
            reflectiveQuadToRelative(-1.93f, 2.85f)
            reflectiveQuadToRelative(-2.85f, 1.93f)
            reflectiveQuadTo(12f, 22f)
            reflectiveQuadTo(8.49f, 21.29f)
            close()
          }
        }
        .build()
    return _replay!!
  }

private var _replay: ImageVector? = null