package org.example.project.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Type scale from "TIPOGRAFIKA". Seven steps — nothing outside this list. */
@Immutable
data class SadoraTypography(
    val display: TextStyle = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em,
    ),
    val h1: TextStyle = TextStyle(
        fontSize = 27.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
        letterSpacing = (-0.02).em,
    ),
    val h2: TextStyle = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
    ),
    val h3: TextStyle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    ),
    val body: TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    /** Uppercase eyebrow labels — always paired with wide tracking. */
    val caption: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp,
        letterSpacing = 0.14.em,
    ),
    /** Big numerals: calories, cycle day, scores. */
    val data: TextStyle = TextStyle(
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 48.sp,
        letterSpacing = (-0.03).em,
    ),
) {
    /** Body copy that should not leave a lonely last word. */
    val bodyPretty: TextStyle get() = body.copy(textAlign = TextAlign.Start)
}

val LocalSadoraTypography = staticCompositionLocalOf { SadoraTypography() }
