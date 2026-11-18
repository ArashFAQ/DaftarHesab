package com.arash.daftar

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

internal val LocalDark = staticCompositionLocalOf { false }

@OptIn(ExperimentalTextApi::class)
private fun vazir(w: FontWeight) = Font(
    R.font.vazirmatn, w,
    variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))
)

private val Vazir = FontFamily(
    vazir(FontWeight.Normal), vazir(FontWeight.Medium), vazir(FontWeight.SemiBold),
    vazir(FontWeight.Bold), vazir(FontWeight.ExtraBold)
)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Vazir),
        displayMedium = t.displayMedium.copy(fontFamily = Vazir),
        displaySmall = t.displaySmall.copy(fontFamily = Vazir),
        headlineLarge = t.headlineLarge.copy(fontFamily = Vazir),
        headlineMedium = t.headlineMedium.copy(fontFamily = Vazir),
        headlineSmall = t.headlineSmall.copy(fontFamily = Vazir),
        titleLarge = t.titleLarge.copy(fontFamily = Vazir),
        titleMedium = t.titleMedium.copy(fontFamily = Vazir),
        titleSmall = t.titleSmall.copy(fontFamily = Vazir),
        bodyLarge = t.bodyLarge.copy(fontFamily = Vazir),
        bodyMedium = t.bodyMedium.copy(fontFamily = Vazir),
        bodySmall = t.bodySmall.copy(fontFamily = Vazir),
        labelLarge = t.labelLarge.copy(fontFamily = Vazir),
        labelMedium = t.labelMedium.copy(fontFamily = Vazir),
        labelSmall = t.labelSmall.copy(fontFamily = Vazir)
    )
}

/** رنگ بدهی (قرمز) و پرداخت/طلبکار (سبز) */
object Money {
    val debtLight = Color(0xFFC62828)
    val debtDark = Color(0xFFEF9A9A)
    val payLight = Color(0xFF2E7D32)
    val payDark = Color(0xFFA5D6A7)
}

@Composable
fun debtColor(): Color = if (LocalDark.current) Money.debtDark else Money.debtLight

@Composable
fun payColor(): Color = if (LocalDark.current) Money.payDark else Money.payLight

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF80CBC4),
        onPrimary = Color(0xFF00332E),
        primaryContainer = Color(0xFF004D45),
        onPrimaryContainer = Color(0xFFB2DFDB),
    ) else lightColorScheme(
        primary = Color(0xFF00796B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2DFDB),
        onPrimaryContainer = Color(0xFF00201C),
        background = Color(0xFFF5F6F8),
        surface = Color(0xFFF5F6F8),
        surfaceContainer = Color.White,
        surfaceContainerLow = Color.White,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDark provides dark) {
            content()
        }
    }
}
