package com.ouor.mycoupon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Placeholder palette; the real tokens come from docs/design/design-system.md (UI developer). */
object AppColors {
    val review = Color(0xFFE8710A)   // 확인 필요 (orange)
    val soon = Color(0xFFD93025)     // 임박 D-7..오늘 (red)
    val ended = Color(0xFF80868B)    // 만료·사용 완료 (grey)
}

@Composable
fun MyCouponTheme(forceLight: Boolean = false, content: @Composable () -> Unit) {
    val dark = !forceLight && isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}
