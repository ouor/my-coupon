package com.ouor.mycoupon.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ouor.mycoupon.ui.theme.statusColors

/**
 * design-system §6.4 `SegmentedFilter`: grey track + raised thumb, one choice (redesign: BoardUI segmented control).
 * Each option is a tab for TalkBack ("사용 가능 5, 선택됨"); 42dp option + 3dp track padding = the 48dp touch height
 * (P2). [small] = the overflow fallback (§2.3).
 */
@Composable
fun SegmentedFilter(options: List<Pair<String, Int?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val st = MaterialTheme.statusColors
    val label = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
    Row(modifier.heightIn(min = 48.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(MaterialTheme.shapes.medium).background(st.track).padding(3.dp)) {
            options.forEachIndexed { i, (text, count) ->
                val on = i == selected
                val face = if (on) Modifier.shadow(1.dp, MaterialTheme.shapes.small, clip = false).clip(MaterialTheme.shapes.small).background(st.thumb)
                           else Modifier.clip(MaterialTheme.shapes.small)
                Row(face.selectable(on, role = Role.Tab, onClick = { onSelect(i) }).heightIn(min = 42.dp).padding(horizontal = if (small) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(text, style = if (on) label else label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    count?.let {
                        Spacer(Modifier.width(5.dp))
                        Text("$it", style = label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
