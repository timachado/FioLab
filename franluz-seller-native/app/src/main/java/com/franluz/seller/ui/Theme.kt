package com.franluz.seller.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ivory = Color(0xFFFFF8EF)
val Paper = Color(0xFFFFFFFF)
val Brown = Color(0xFF573225)
val Gold = Color(0xFFC89A45)
val Terracotta = Color(0xFFC98067)
val Ink = Color(0xFF302723)
val Muted = Color(0xFF7C706B)
val Soft = Color(0xFFF6EEE7)
val Green = Color(0xFF28745A)
val Red = Color(0xFFB95046)

private val FranLuzColors = lightColorScheme(
    primary = Brown,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Brown,
    tertiary = Terracotta,
    background = Ivory,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    error = Red,
    onError = Color.White
)

@Composable
fun FranLuzSellerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FranLuzColors, content = content)
}
