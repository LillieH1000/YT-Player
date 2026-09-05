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
val fit_screen: ImageVector
  get() {
    if (_fit_screen != null) {
      return _fit_screen!!
    }
    _fit_screen =
      ImageVector.Builder(
          name = "fit_screen",
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
            moveTo(20f, 9f)
            verticalLineTo(6f)
            horizontalLineTo(17f)
            verticalLineTo(4f)
            horizontalLineToRelative(3f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(22f, 5.18f, 22f, 6f)
            verticalLineTo(9f)
            horizontalLineTo(20f)
            close()
            moveTo(2f, 9f)
            verticalLineTo(6f)
            quadTo(2f, 5.18f, 2.59f, 4.59f)
            reflectiveQuadTo(4f, 4f)
            horizontalLineTo(7f)
            verticalLineTo(6f)
            horizontalLineTo(4f)
            verticalLineTo(9f)
            horizontalLineTo(2f)
            close()
            moveTo(17f, 20f)
            verticalLineTo(18f)
            horizontalLineToRelative(3f)
            verticalLineTo(15f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(3f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 20f)
            horizontalLineTo(17f)
            close()
            moveTo(4f, 20f)
            quadTo(3.18f, 20f, 2.59f, 19.41f)
            reflectiveQuadTo(2f, 18f)
            verticalLineTo(15f)
            horizontalLineTo(4f)
            verticalLineToRelative(3f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineTo(4f)
            close()
            moveTo(6f, 16f)
            verticalLineTo(8f)
            horizontalLineTo(18f)
            verticalLineToRelative(8f)
            horizontalLineTo(6f)
            close()
          }
        }
        .build()
    return _fit_screen!!
  }

private var _fit_screen: ImageVector? = null