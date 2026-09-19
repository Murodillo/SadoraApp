package uz.sadora.app.ui.components

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun systemReducesMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
