package com.fithealthzone.bandsongbook.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    private object Dark {
        val BgDeep = Color(0xFF0E0E0E)
        val BgSurface = Color(0xFF131313)
        val BgCard = Color(0xFF1A1A1A)
        val BgCardHover = Color(0xFF262626)
        val BorderGlass = Color(0x26494847)
        val BorderGlassStrong = Color(0x52494847)
        val Primary = Color(0xFF84ADFF)
        val PrimaryLight = Color(0xFFADC6FF)
        val PrimaryGlow = Color(0x4084ADFF)
        val Teal = Color(0xFF0070EA)
        val TextWhite = Color(0xFFF1F4FA)
        val TextLight = Color(0xFFDDE3F0)
        val TextMuted = Color(0xFFAFB7C6)
        val TextDim = Color(0xFF727A89)
        val Error = Color(0xFFD7383B)
    }

    private object Light {
        val BgDeep = Color(0xFFE6EBF6)
        val BgSurface = Color(0xFFF2F5FC)
        val BgCard = Color(0xFFFFFFFF)
        val BgCardHover = Color(0xFFE9EEFA)
        val BorderGlass = Color(0x1F3C435A)
        val BorderGlassStrong = Color(0x3D3C435A)
        val Primary = Color(0xFF2B67D9)
        val PrimaryLight = Color(0xFF4E7FE5)
        val PrimaryGlow = Color(0x332B67D9)
        val Teal = Color(0xFF0B78E3)
        val TextWhite = Color(0xFF10131B)
        val TextLight = Color(0xFF1D2533)
        val TextMuted = Color(0xFF4F5D73)
        val TextDim = Color(0xFF6D7B92)
        val Error = Color(0xFFB6282A)
    }

    var isDark by mutableStateOf(true)
        private set

    var BgDeep by mutableStateOf(Dark.BgDeep)
        private set
    var BgSurface by mutableStateOf(Dark.BgSurface)
        private set
    var BgCard by mutableStateOf(Dark.BgCard)
        private set
    var BgCardHover by mutableStateOf(Dark.BgCardHover)
        private set
    var BorderGlass by mutableStateOf(Dark.BorderGlass)
        private set
    var BorderGlassStrong by mutableStateOf(Dark.BorderGlassStrong)
        private set
    var Primary by mutableStateOf(Dark.Primary)
        private set
    var PrimaryLight by mutableStateOf(Dark.PrimaryLight)
        private set
    var PrimaryGlow by mutableStateOf(Dark.PrimaryGlow)
        private set
    var Teal by mutableStateOf(Dark.Teal)
        private set
    var TextWhite by mutableStateOf(Dark.TextWhite)
        private set
    var TextLight by mutableStateOf(Dark.TextLight)
        private set
    var TextMuted by mutableStateOf(Dark.TextMuted)
        private set
    var TextDim by mutableStateOf(Dark.TextDim)
        private set
    var Error by mutableStateOf(Dark.Error)
        private set

    fun applyTheme(dark: Boolean) {
        isDark = dark
        if (dark) {
            BgDeep = Dark.BgDeep
            BgSurface = Dark.BgSurface
            BgCard = Dark.BgCard
            BgCardHover = Dark.BgCardHover
            BorderGlass = Dark.BorderGlass
            BorderGlassStrong = Dark.BorderGlassStrong
            Primary = Dark.Primary
            PrimaryLight = Dark.PrimaryLight
            PrimaryGlow = Dark.PrimaryGlow
            Teal = Dark.Teal
            TextWhite = Dark.TextWhite
            TextLight = Dark.TextLight
            TextMuted = Dark.TextMuted
            TextDim = Dark.TextDim
            Error = Dark.Error
        } else {
            BgDeep = Light.BgDeep
            BgSurface = Light.BgSurface
            BgCard = Light.BgCard
            BgCardHover = Light.BgCardHover
            BorderGlass = Light.BorderGlass
            BorderGlassStrong = Light.BorderGlassStrong
            Primary = Light.Primary
            PrimaryLight = Light.PrimaryLight
            PrimaryGlow = Light.PrimaryGlow
            Teal = Light.Teal
            TextWhite = Light.TextWhite
            TextLight = Light.TextLight
            TextMuted = Light.TextMuted
            TextDim = Light.TextDim
            Error = Light.Error
        }
    }
}

private val StageDarkScheme = darkColorScheme(
    primary = Color(0xFF84ADFF),
    onPrimary = Color(0xFF0A1222),
    primaryContainer = Color(0xFF1E3A6A),
    onPrimaryContainer = Color(0xFFDDE8FF),
    secondary = Color(0xFF6D9CFF),
    tertiary = Color(0xFF0070EA),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFE3E7F2),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFE3E7F2),
    surfaceVariant = Color(0xFF22262E),
    onSurfaceVariant = Color(0xFFA9B1BF),
    outline = Color(0x38494847),
    outlineVariant = Color(0x26494847),
    error = Color(0xFFD7383B),
    errorContainer = Color(0xFF4B1E23),
    onErrorContainer = Color(0xFFFFD9DC)
)

private val StageLightScheme = lightColorScheme(
    primary = Color(0xFF2B67D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF12284E),
    secondary = Color(0xFF3C6FD7),
    tertiary = Color(0xFF0B78E3),
    background = Color(0xFFF2F5FC),
    onBackground = Color(0xFF111521),
    surface = Color(0xFFF2F5FC),
    onSurface = Color(0xFF111521),
    surfaceVariant = Color(0xFFE1E8F8),
    onSurfaceVariant = Color(0xFF4D5A71),
    outline = Color(0x4A3C435A),
    outlineVariant = Color(0x2A3C435A),
    error = Color(0xFFB6282A),
    errorContainer = Color(0xFFFCDDDD),
    onErrorContainer = Color(0xFF41070A)
)

private val StageTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    )
)

private val StageShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun BandSongbookTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    AppColors.applyTheme(darkTheme)
    MaterialTheme(
        colorScheme = if (darkTheme) StageDarkScheme else StageLightScheme,
        typography = StageTypography,
        shapes = StageShapes,
        content = content
    )
}

@Composable
fun FrostedBackground(content: @Composable BoxScope.() -> Unit) {
    val stageGradient = Brush.verticalGradient(
        colors = listOf(
            AppColors.BgDeep,
            AppColors.BgSurface,
            AppColors.BgDeep
        )
    )
    val glowGradient = Brush.radialGradient(
        colors = listOf(
            AppColors.PrimaryGlow,
            Color.Transparent
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(stageGradient)
            .background(glowGradient),
        content = content
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = modifier
            .clip(shape)
            .border(1.dp, AppColors.BorderGlass, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = AppColors.BgCard)
    ) {
        Box(
            modifier = Modifier.padding(1.dp),
            contentAlignment = Alignment.CenterStart,
            content = content
        )
    }
}

@Composable
fun KeyBadge(key: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(AppColors.Primary.copy(alpha = if (AppColors.isDark) 0.2f else 0.14f))
            .border(1.dp, AppColors.Primary.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = key.uppercase(),
            color = AppColors.PrimaryLight,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
