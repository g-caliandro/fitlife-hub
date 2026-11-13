package com.lifehub.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.lifehub.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LhSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object LhRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val card = 24.dp
    val item = 16.dp
    val smShape = RoundedCornerShape(sm)
    val mdShape = RoundedCornerShape(md)
    val lgShape = RoundedCornerShape(lg)
    val cardShape = RoundedCornerShape(card)
    val itemShape = RoundedCornerShape(item)
    val fullShape = RoundedCornerShape(percent = 50)
    val pill = RoundedCornerShape(percent = 50)
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/** Heights from design 1.3. */
object LhSize {
    val buttonSm = 36.dp
    val buttonMd = 48.dp
    val buttonLg = 56.dp
    val input = 44.dp
    val listRow = 56.dp
    val setRow = 48.dp
    val topBar = 56.dp
    val bottomBar = 64.dp
}

/**
 * Typography. Styles carry no color (callers use LocalContentColor) except [captionMuted],
 * the composable variant that applies mutedForeground.
 */
object LhType {
    private const val TNUM = "tnum"
    private val ps = PlatformTextStyle(includeFontPadding = false)
    private fun st(size: Int, line: Int, w: FontWeight, tnum: Boolean = false) = TextStyle(
        fontFamily = Poppins, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = 0.sp,
        fontFeatureSettings = if (tnum) TNUM else null, platformStyle = ps,
    )
    val display = st(40, 48, FontWeight.Bold, true)
    val timer = st(56, 64, FontWeight.SemiBold, true)
    val screenTitle = st(26, 34, FontWeight.Medium)
    val title = st(20, 28, FontWeight.SemiBold)
    val section = st(20, 28, FontWeight.Medium)
    val heading = st(16, 24, FontWeight.SemiBold)
    val stat = st(24, 32, FontWeight.SemiBold, true)
    val bodyLarge = st(16, 24, FontWeight.Normal)
    val body = st(14, 20, FontWeight.Normal)
    val label = st(14, 20, FontWeight.Medium)
    val labelSmall = st(12, 16, FontWeight.Medium)
    val caption = st(12, 16, FontWeight.Normal)

    /** Tabular-number variant of any style (for numbers that change). */
    fun tnum(s: TextStyle) = s.copy(fontFeatureSettings = TNUM)

    val captionMuted: TextStyle
        @Composable get() = caption.copy(color = LhTheme.colors.mutedForeground)
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)
