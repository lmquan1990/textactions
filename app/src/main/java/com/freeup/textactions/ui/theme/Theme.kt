package com.freeup.textactions.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class ExpressiveColors(
    val titleColor: Color,
    val goldColor: Color,
    val surfaceContainer: Color,
    val iconContainer: Color,
    val iconTint: Color
)

val LocalExpressiveColors = staticCompositionLocalOf {
    ExpressiveColors(
        titleColor = Color.Unspecified,
        goldColor = Color.Unspecified,
        surfaceContainer = Color.Unspecified,
        iconContainer = Color.Unspecified,
        iconTint = Color.Unspecified
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkBackground,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurface,
    outline = DarkOutlined,
    outlineVariant = Color(0xFF49454F)
)

private val LightColorScheme = lightColorScheme(
    primary = LightExpressiveGreen,
    surface = LightSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    background = LightBackground,
    surfaceContainerHigh = LightBackground,
    surfaceContainerLow = LightSurface,
    surfaceContainerLowest = LightSurface,
    outlineVariant = Color(0xFFC4C6D0)
)

@Composable
fun TextActionsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    transparentSystemBars: Boolean = false,
    windowBackgroundColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // Dynamic color: giữ tương phản nhẹ giữa nền và card
    // Non-dynamic: dùng palette warm-cream
    val colorScheme = if (useDynamicColor) {
        if (darkTheme) {
            // Dark mode: giữ background đen, surface dùng surfaceContainerHighest
            baseScheme.copy(
                background = DarkBackground,
                surface = baseScheme.surfaceContainerHighest
            )
        } else {
            // Light mode: background hơi đậm hơn card để card trắng hơn nền
            baseScheme.copy(
                background = baseScheme.surfaceContainerHigh,
                surface = baseScheme.surfaceContainerLow,
                surfaceContainerLow = baseScheme.surfaceContainerLow,
                surfaceContainerLowest = baseScheme.surfaceContainerLowest
            )
        }
    } else {
        baseScheme
    }

    val expressiveColors = if (useDynamicColor) {
        ExpressiveColors(
            titleColor = colorScheme.primary,
            goldColor = colorScheme.tertiary,
            surfaceContainer = colorScheme.surface,
            iconContainer = colorScheme.surface,
            iconTint = colorScheme.onSurfaceVariant
        )
    } else if (darkTheme) {
        ExpressiveColors(
            titleColor = DarkOnSurface,
            goldColor = DarkRecommendedGold,
            surfaceContainer = DarkSurfaceContainerHigh,
            iconContainer = DarkSurfaceVariant,
            iconTint = DarkOnSurfaceVariant
        )
    } else {
        ExpressiveColors(
            titleColor = TealDark,
            goldColor = LightRecommendedGold,
            surfaceContainer = TealLight,
            iconContainer = Color(0xFFFFFFFF), // White background for action cards
            iconTint = TealIcon
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            val decorView = window.decorView
            val bgArgb = (windowBackgroundColor ?: colorScheme.background).toArgb()
            // Status bar luôn transparent; nền cửa sổ = nền app để tránh sọc trắng
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.setBackgroundDrawable(ColorDrawable(bgArgb))
            if (transparentSystemBars) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                @Suppress("DEPRECATION")
                window.navigationBarColor = bgArgb
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val insetsController = WindowCompat.getInsetsController(window, decorView)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExpressiveColors provides expressiveColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Card/container nên dùng surface để luôn nổi bật hơn nền ở light, và tối hơn nền ở dark.
fun elevatedCardColor(colorScheme: ColorScheme): Color {
    return colorScheme.surface
}
