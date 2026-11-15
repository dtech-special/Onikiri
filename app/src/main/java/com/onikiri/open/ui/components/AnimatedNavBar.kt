package com.onikiri.open.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

@Immutable
data class NavDestination(
    @param:StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AnimatedNavigationBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    NavigationBar {
        destinations.forEachIndexed { index, destination ->
            key(index) {
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.18f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "navIconScale"
                )
                val title = stringResource(destination.titleRes)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(index) },
                    icon = {
                        Crossfade(
                            targetState = selected,
                            animationSpec = tween(220),
                            label = "navIcon"
                        ) { isSelected ->
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = title,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            )
                        }
                    },
                    label = { Text(title) }
                )
            }
        }
    }
}
