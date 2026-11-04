package com.ouor.mycoupon.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
 * Each option is a tab for TalkBack ("사용 가능 5, 선택됨"). Designer 11-04: the visible track is 40dp (34dp thumb + 3dp),
 * and each tab's touch area still runs the full 48dp row height — the 4dp above and below are invisible touch margin (P2).
 * [small] = the overflow fallback (§2.3).
 */
@Composable
fun SegmentedFilter(options: List<Pair<String, Int?>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val st = MaterialTheme.statusColors
    val label = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
    val margin = 4.dp
    Row(modifier.heightIn(min = 48.dp).height(IntrinsicSize.Min).selectableGroup()
        .drawBehind { drawRoundRect(st.track, Offset(0f, margin.toPx()), Size(size.width, size.height - 2 * margin.toPx()), CornerRadius(10.dp.toPx())) }
        .padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, (text, count) ->
            val on = i == selected
            val face = if (on) Modifier.shadow(1.dp, MaterialTheme.shapes.small, clip = false).clip(MaterialTheme.shapes.small).background(st.thumb)
                       else Modifier
            // the touch area is the whole column (48dp); no ripple outside the drawn track
            Box(Modifier.fillMaxHeight().selectable(on, interactionSource = null, indication = null, role = Role.Tab, onClick = { onSelect(i) })
                .padding(vertical = margin + 3.dp), contentAlignment = Alignment.Center) {
                Row(face.heightIn(min = 34.dp).padding(horizontal = if (small) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // owner 11-04: one Text so the count shares the label's size and baseline; only colour + Medium differ
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(buildAnnotatedString {
                        append(text)
                        count?.let { append(" "); withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Medium)) { append("$it") } }
                    }, style = (if (on) label else label.copy(fontWeight = FontWeight.Medium)).copy(fontFeatureSettings = "tnum"),
                        color = if (on) MaterialTheme.colorScheme.onSurface else muted, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
