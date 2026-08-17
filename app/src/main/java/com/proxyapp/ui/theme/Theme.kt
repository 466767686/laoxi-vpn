package com.proxyapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import com.proxyapp.R

/** 全局字体：苹方粗体 */
val PingFangBold = FontFamily(Font(R.font.pingfang_bold))

private val PingFangTypography = run {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = PingFangBold),
        displayMedium = base.displayMedium.copy(fontFamily = PingFangBold),
        displaySmall = base.displaySmall.copy(fontFamily = PingFangBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = PingFangBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = PingFangBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = PingFangBold),
        titleLarge = base.titleLarge.copy(fontFamily = PingFangBold),
        titleMedium = base.titleMedium.copy(fontFamily = PingFangBold),
        titleSmall = base.titleSmall.copy(fontFamily = PingFangBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = PingFangBold),
        bodyMedium = base.bodyMedium.copy(fontFamily = PingFangBold),
        bodySmall = base.bodySmall.copy(fontFamily = PingFangBold),
        labelLarge = base.labelLarge.copy(fontFamily = PingFangBold),
        labelMedium = base.labelMedium.copy(fontFamily = PingFangBold),
        labelSmall = base.labelSmall.copy(fontFamily = PingFangBold)
    )
}

@Composable
fun ProxyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = PingFangTypography,
        content = content
    )
}
