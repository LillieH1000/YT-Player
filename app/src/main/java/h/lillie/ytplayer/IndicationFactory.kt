package h.lillie.ytplayer

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.node.DelegatableNode

object IndicationFactory: IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return IndicationNode()
    }

    override fun equals(other: Any?): Boolean {
        return other === this
    }

    override fun hashCode(): Int {
        return -1
    }
}