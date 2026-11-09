package com.triparchive.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.triparchive.R

val Pretendard =
    FontFamily(
        Font(R.font.pretendard_thin, FontWeight.Thin),
        Font(R.font.pretendard_light, FontWeight.Light),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_bold, FontWeight.Bold),
        Font(R.font.pretendard_extra_bold, FontWeight.ExtraBold),
        Font(R.font.pretendard_black, FontWeight.Black),
        Font(R.font.pretendard_medium, FontWeight.Normal),
    )

private val DefaultTypography = Typography()

val TripArchiveTypography =
    Typography(
        displayLarge = DefaultTypography.displayLarge.copy(fontFamily = Pretendard),
        displayMedium = DefaultTypography.displayMedium.copy(fontFamily = Pretendard),
        displaySmall = DefaultTypography.displaySmall.copy(fontFamily = Pretendard),
        headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = Pretendard),
        headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = Pretendard),
        headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = Pretendard),
        titleLarge = DefaultTypography.titleLarge.copy(fontFamily = Pretendard),
        titleMedium = DefaultTypography.titleMedium.copy(fontFamily = Pretendard),
        titleSmall = DefaultTypography.titleSmall.copy(fontFamily = Pretendard),
        bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = Pretendard),
        bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = Pretendard),
        bodySmall = DefaultTypography.bodySmall.copy(fontFamily = Pretendard),
        labelLarge = DefaultTypography.labelLarge.copy(fontFamily = Pretendard),
        labelMedium = DefaultTypography.labelMedium.copy(fontFamily = Pretendard),
        labelSmall = DefaultTypography.labelSmall.copy(fontFamily = Pretendard),
    )
