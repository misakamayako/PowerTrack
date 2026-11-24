package com.misakamayako.powertrack.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 色板：深色优先，健身房灯光下对比度高。
 */
object PtColors {
    // 主色：哑铃片橙
    val Orange10 = Color(0xFF3A1B00)
    val Orange30 = Color(0xFF8A4B00)
    val Orange40 = Color(0xFFB36000)
    val Orange80 = Color(0xFFFFB95C)
    val Orange90 = Color(0xFFFFDDB3)

    // 次色：器械青
    val Teal10 = Color(0xFF00201C)
    val Teal30 = Color(0xFF005046)
    val Teal40 = Color(0xFF006B5F)
    val Teal80 = Color(0xFF55DBC8)
    val Teal90 = Color(0xFF71F8E4)

    val Neutral6 = Color(0xFF0E0E11)
    val Neutral10 = Color(0xFF1A1A1E)
    val Neutral20 = Color(0xFF2C2C31)
    val Neutral30 = Color(0xFF44474E)
    val Neutral90 = Color(0xFFE3E2E6)
    val Neutral95 = Color(0xFFF3F0F4)

    val Success = Color(0xFF4CAF6E)
    val Warning = Color(0xFFE5A13A)
    val Danger = Color(0xFFE05A5A)
}

/**
 * 间距 token（4dp 基准）。
 */
object PtSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** §6：拇指区按钮最小高度。 */
    val thumbButtonHeight: Dp = 64.dp

    /** §6：点击区最小高度。 */
    val minTouchTarget: Dp = 56.dp

    val screenPadding: Dp = 16.dp
    val cardCorner: Dp = 20.dp
}

/**
 * 字号 token。重量 / 次数用等宽字体且 ≥ 32sp（§6）。
 */
object PtType {
    val numeric = 34.sp
    val numericLarge = 44.sp
    val title = 22.sp
    val subtitle = 16.sp
    val body = 15.sp
    val caption = 13.sp
    val label = 12.sp
}
