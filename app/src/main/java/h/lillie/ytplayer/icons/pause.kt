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
val pause: ImageVector
  get() {
    if (_pause != null) {
      return _pause!!
    }
    _pause =
      ImageVector.Builder(
          name = "pause",
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
            moveTo(14f, 19f)
            verticalLineTo(5f)
            horizontalLineToRelative(4f)
            verticalLineTo(19f)
            horizontalLineTo(14f)
            close()
            moveTo(6f, 19f)
            verticalLineTo(5f)
            horizontalLineToRelative(4f)
            verticalLineTo(19f)
            horizontalLineTo(6f)
            close()
          }
        }
        .build()
    return _pause!!
  }

private var _pause: ImageVector? = null