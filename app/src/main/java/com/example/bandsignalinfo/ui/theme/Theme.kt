package com.example.bandsignalinfo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightTMobile = lightColorScheme(
    primary = TMobileLight,
    onPrimary = TMobileLightOn,
    primaryContainer = TMobileLightContainer,
    onPrimaryContainer = TMobileLightOnContainer,
    secondary = TMobileLightSecondary,
    secondaryContainer = TMobileLightSecondaryContainer,
    onSecondaryContainer = TMobileLightOnSecondaryContainer,
    surfaceVariant = TMobileLightSurfaceVariant,
)

private val DarkTMobile = darkColorScheme(
    primary = TMobileDark,
    onPrimary = TMobileDarkOn,
    primaryContainer = TMobileDarkContainer,
    onPrimaryContainer = TMobileDarkOnContainer,
    secondary = TMobileDarkSecondary,
    secondaryContainer = TMobileDarkSecondaryContainer,
    onSecondaryContainer = TMobileDarkOnSecondaryContainer,
    surfaceVariant = TMobileDarkSurfaceVariant,
)

private val LightATT = lightColorScheme(
    primary = ATTLight,
    onPrimary = ATTLightOn,
    primaryContainer = ATTLightContainer,
    onPrimaryContainer = ATTLightOnContainer,
    secondary = ATTLightSecondary,
    secondaryContainer = ATTLightSecondaryContainer,
    onSecondaryContainer = ATTLightOnSecondaryContainer,
    surfaceVariant = ATTLightSurfaceVariant,
)

private val DarkATT = darkColorScheme(
    primary = ATTDark,
    onPrimary = ATTDarkOn,
    primaryContainer = ATTDarkContainer,
    onPrimaryContainer = ATTDarkOnContainer,
    secondary = ATTDarkSecondary,
    secondaryContainer = ATTDarkSecondaryContainer,
    onSecondaryContainer = ATTDarkOnSecondaryContainer,
    surfaceVariant = ATTDarkSurfaceVariant,
)

private val LightVerizon = lightColorScheme(
    primary = VerizonLight,
    onPrimary = VerizonLightOn,
    primaryContainer = VerizonLightContainer,
    onPrimaryContainer = VerizonLightOnContainer,
    secondary = VerizonLightSecondary,
    secondaryContainer = VerizonLightSecondaryContainer,
    onSecondaryContainer = VerizonLightOnSecondaryContainer,
    surfaceVariant = VerizonLightSurfaceVariant,
)

private val DarkVerizon = darkColorScheme(
    primary = VerizonDark,
    onPrimary = VerizonDarkOn,
    primaryContainer = VerizonDarkContainer,
    onPrimaryContainer = VerizonDarkOnContainer,
    secondary = VerizonDarkSecondary,
    secondaryContainer = VerizonDarkSecondaryContainer,
    onSecondaryContainer = VerizonDarkOnSecondaryContainer,
    surfaceVariant = VerizonDarkSurfaceVariant,
)

private val LightDefault = lightColorScheme(
    primary = DefaultLight,
    onPrimary = DefaultLightOn,
    primaryContainer = DefaultLightContainer,
    onPrimaryContainer = DefaultLightOnContainer,
    secondary = DefaultLightSecondary,
    secondaryContainer = DefaultLightSecondaryContainer,
    onSecondaryContainer = DefaultLightOnSecondaryContainer,
    surfaceVariant = DefaultLightSurfaceVariant,
)

private val DarkDefault = darkColorScheme(
    primary = DefaultDark,
    onPrimary = DefaultDarkOn,
    primaryContainer = DefaultDarkContainer,
    onPrimaryContainer = DefaultDarkOnContainer,
    secondary = DefaultDarkSecondary,
    secondaryContainer = DefaultDarkSecondaryContainer,
    onSecondaryContainer = DefaultDarkOnSecondaryContainer,
    surfaceVariant = DefaultDarkSurfaceVariant,
)

fun carrierColorScheme(operator: String, darkTheme: Boolean): ColorScheme {
    val op = operator.lowercase()
    return when {
        op.contains("t-mobile") || op.contains("tmobile") ||
        op.contains("metro") || op.contains("sprint") ->
            if (darkTheme) DarkTMobile else LightTMobile

        op.contains("at&t") || op.contains("att") || op.contains("firstnet") ->
            if (darkTheme) DarkATT else LightATT

        op.contains("verizon") ->
            if (darkTheme) DarkVerizon else LightVerizon

        else -> if (darkTheme) DarkDefault else LightDefault
    }
}

@Composable
fun BandSignalInfoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkDefault else LightDefault,
        typography = Typography,
        content = content
    )
}
