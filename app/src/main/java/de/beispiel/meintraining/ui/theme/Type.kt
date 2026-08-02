package de.beispiel.meintraining.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Benannte Textstile – im UI-Code wird nur darüber zugegriffen, nie über rohe sp-Werte. */
object AppTextStyles {

    val Title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 1.sp
    )

    val TabLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )

    val ColumnLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )

    val ExerciseName = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
    )

    val ChipText = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )

    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
}

val AppTypography = Typography(
    titleLarge = AppTextStyles.Title,
    labelLarge = AppTextStyles.TabLabel,
    labelMedium = AppTextStyles.ChipText,
    labelSmall = AppTextStyles.ColumnLabel,
    bodyMedium = AppTextStyles.ExerciseName,
    bodySmall = AppTextStyles.Body
)
