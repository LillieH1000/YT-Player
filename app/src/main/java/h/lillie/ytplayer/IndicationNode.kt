package h.lillie.ytplayer

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode

class IndicationNode: Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        this@draw.drawContent()
    }
}