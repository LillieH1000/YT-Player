package h.lillie.ytplayer

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DrawModifierNode

class IndicationNode: Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        scale(
            scale = 1f,
            pivot = Offset.Zero
        ) {
            this@draw.drawContent()
        }
    }
}