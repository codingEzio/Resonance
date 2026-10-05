package dev.resonance

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalTechnical = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
internal val LocalDisplay = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

@OptIn(ExperimentalTextApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ResonanceTheme(
    theme: String,
    face: String,
    materialYou: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val activity = androidx.activity.compose.LocalActivity.current
    SideEffect {
        activity?.let {
            androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView)
                .apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
        }
    }
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val pixel =
        remember(resources) {
            FontFamily(
                android.graphics.Typeface.CustomFallbackBuilder(
                        android.graphics.fonts.FontFamily.Builder(
                                android.graphics.fonts.Font.Builder(resources, R.font.geist_pixel)
                                    .build()
                            )
                            .build()
                    )
                    .addCustomFallback(
                        android.graphics.fonts.FontFamily.Builder(
                                android.graphics.fonts.Font.Builder(resources, R.font.compact)
                                    .build()
                            )
                            .build()
                    )
                    .setSystemFallback("sans-serif")
                    .build()
            )
        }
    val geist = FontFamily(Font(R.font.geist))
    val geistMono = FontFamily(Font(R.font.geist_mono))
    val compact = FontFamily(Font(R.font.compact))
    val doto =
        FontFamily(
            Font(
                R.font.doto,
                weight = FontWeight.Bold,
                variationSettings = FontVariation.Settings(FontVariation.weight(700)),
            )
        )
    val roboto = FontFamily(Font(R.font.roboto))
    val normal =
        when (face) {
            "pixel" -> pixel
            "compact" -> compact
            "doto" -> doto
            "roboto" -> roboto
            "system" -> FontFamily.SansSerif
            else -> geist
        }
    val mono =
        when (face) {
            "pixel" -> pixel
            "compact" -> compact
            "doto" -> doto
            "roboto" -> FontFamily(Font(R.font.roboto_mono))
            "system" -> FontFamily.Monospace
            else -> geistMono
        }
    val display = if (face in listOf("nothing", "pixel")) doto else normal
    val dense = if (face == "roboto") FontFamily(Font(R.font.roboto_condensed)) else normal
    val background = if (dark) Color(0xFF111111) else Color(0xFFF5F4F0)
    val foreground = if (dark) Color(0xFFF5F4F0) else Color(0xFF171717)
    val surface = if (dark) Color(0xFF202020) else Color(0xFFEAE9E5)
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFFFF6B63),
                background = background,
                surface = background,
                surfaceVariant = surface,
                onBackground = foreground,
                onSurface = foreground,
                onSurfaceVariant = Color(0xFFB9B9B5),
                secondary = foreground,
            )
        else
            lightColorScheme(
                primary = Color(0xFFB72C25),
                background = background,
                surface = background,
                surfaceVariant = surface,
                onBackground = foreground,
                onSurface = foreground,
                onSurfaceVariant = Color(0xFF60605D),
                secondary = foreground,
            )
    val neutralColors =
        colors.copy(
            onPrimary = if (dark) Color(0xFF171717) else Color.White,
            primaryContainer = surface,
            onPrimaryContainer = foreground,
            secondaryContainer = surface,
            onSecondaryContainer = foreground,
            tertiary = foreground,
            tertiaryContainer = surface,
            onTertiaryContainer = foreground,
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = background,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surface,
            outline = if (dark) Color(0xFF858581) else Color(0xFF777773),
            outlineVariant = if (dark) Color(0xFF373735) else Color(0xFFD0CFCB),
        )
    val base = Typography()
    val weight = if (face == "doto") FontWeight.Bold else FontWeight.Normal
    val type =
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = display),
            displayMedium = base.displayMedium.copy(fontFamily = display),
            displaySmall = base.displaySmall.copy(fontFamily = display),
            headlineLarge = base.headlineLarge.copy(fontFamily = normal, fontWeight = weight),
            headlineMedium = base.headlineMedium.copy(fontFamily = normal, fontWeight = weight),
            headlineSmall = base.headlineSmall.copy(fontFamily = normal, fontWeight = weight),
            titleLarge = base.titleLarge.copy(fontFamily = normal, fontWeight = weight),
            titleMedium = base.titleMedium.copy(fontFamily = normal, fontWeight = weight),
            titleSmall = base.titleSmall.copy(fontFamily = normal, fontWeight = weight),
            bodyLarge = base.bodyLarge.copy(fontFamily = normal, fontWeight = weight),
            bodyMedium = base.bodyMedium.copy(fontFamily = normal, fontWeight = weight),
            bodySmall = base.bodySmall.copy(fontFamily = normal, fontWeight = weight),
            labelLarge = base.labelLarge.copy(fontFamily = dense),
            labelMedium = base.labelMedium.copy(fontFamily = dense),
            labelSmall = base.labelSmall.copy(fontFamily = dense),
        )
    val context = LocalContext.current
    val motion = rememberMotionPolicy()
    val materialColors =
        if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(
        LocalTechnical provides mono,
        LocalDisplay provides display,
        LocalMotionPolicy provides motion,
    ) {
        if (materialYou)
            MaterialExpressiveTheme(
                colorScheme = materialColors,
                typography = type,
                motionScheme = MotionScheme.expressive(),
                content = content,
            )
        else
            MaterialTheme(
                colorScheme = neutralColors,
                typography = type,
                shapes =
                    Shapes(
                        small = RoundedCornerShape(8.dp),
                        medium = RoundedCornerShape(16.dp),
                        large = RoundedCornerShape(24.dp),
                    ),
                content = content,
            )
    }
}

@Composable
internal fun ScreenHeading(title: String, kicker: String? = null) {
    Column(
        Modifier.padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (kicker != null) SectionLabel(kicker)
        Text(title, style = MaterialTheme.typography.headlineMedium, lineHeight = 36.sp)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = LocalTechnical.current,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
