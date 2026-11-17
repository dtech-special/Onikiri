package com.onikiri.open.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onikiri.open.ui.theme.emphasized

@Stable
class ConnectedAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: Painter? = null,
    val enabled: Boolean = true,
    val primary: Boolean = false,
    val destructive: Boolean = false
)

/**
 * Buttons docked into one group: only the outer corners are fully round and neighbours are
 * separated by a thin gap, in the style of Material 3 Expressive button groups.
 */
@Composable
fun ConnectedActions(actions: List<ConnectedAction>, modifier: Modifier = Modifier) {
    val outer = 20.dp
    val inner = 6.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        actions.forEachIndexed { index, action ->
            val start = if (index == 0) outer else inner
            val end = if (index == actions.lastIndex) outer else inner
            val colors = when {
                action.destructive -> ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
                action.primary -> ButtonDefaults.buttonColors()
                else -> ButtonDefaults.filledTonalButtonColors()
            }
            Button(
                onClick = action.onClick,
                modifier = Modifier.weight(1f),
                enabled = action.enabled,
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                colors = colors,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (action.icon != null) {
                    Icon(painter = action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge.emphasized(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
