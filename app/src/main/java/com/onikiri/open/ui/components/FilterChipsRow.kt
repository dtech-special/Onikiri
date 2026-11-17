package com.onikiri.open.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Immutable
data class FilterOption(val key: String, val label: String)

/** Horizontal row of filter chips: All, Installed and one chip per connected repository. */
@Composable
fun FilterChipsRow(
    options: List<FilterOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options, key = { it.key }) { option ->
            val selected = option.key == selectedKey
            FilterChip(
                selected = selected,
                onClick = { onSelect(option.key) },
                label = { Text(option.label, maxLines = 1) },
                leadingIcon = if (selected) ({
                    Icon(AppIcons.check(), contentDescription = null, modifier = Modifier.size(18.dp))
                }) else null
            )
        }
    }
}
