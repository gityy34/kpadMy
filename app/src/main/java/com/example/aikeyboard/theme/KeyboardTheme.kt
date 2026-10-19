package com.example.aikeyboard.theme

import android.graphics.Color

enum class KeyStyle { BORDERED, BORDERLESS }

data class KeyboardTheme(
    val id: String,
    val name: String,
    val backgroundColor: Int,
    val keyColor: Int,
    val specialKeyColor: Int,
    val pressedKeyColor: Int,
    val shiftColor: Int,
    val textColor: Int,
    val accentColor: Int,
    val borderColor: Int,
    val keyStyle: KeyStyle = KeyStyle.BORDERED,
    val gradientStart: Int? = null,
    val gradientEnd: Int? = null,
    val backgroundImageUri: String? = null,
) {
    val isGradient: Boolean get() = gradientStart != null && gradientEnd != null

    companion object {
        val SOLID_WHITE = KeyboardTheme(
            id = "solid_white", name = "Solid White",
            backgroundColor = Color.rgb(243, 244, 246), keyColor = Color.WHITE,
            specialKeyColor = Color.rgb(221, 227, 244), pressedKeyColor = Color.rgb(215, 224, 255),
            shiftColor = Color.rgb(190, 204, 255), textColor = Color.rgb(32, 33, 36),
            accentColor = Color.rgb(83, 111, 220), borderColor = Color.rgb(220, 223, 230),
        )
        val SOLID_BLACK = KeyboardTheme(
            id = "solid_black", name = "Solid Black",
            backgroundColor = Color.rgb(8, 9, 12), keyColor = Color.rgb(27, 29, 34),
            specialKeyColor = Color.rgb(42, 46, 57), pressedKeyColor = Color.rgb(66, 78, 112),
            shiftColor = Color.rgb(70, 84, 132), textColor = Color.WHITE,
            accentColor = Color.rgb(111, 151, 255), borderColor = Color.rgb(72, 77, 91),
        )
        val NEON = KeyboardTheme(
            id = "neon", name = "Neon / Futuristic",
            backgroundColor = Color.rgb(9, 12, 28), keyColor = Color.rgb(22, 28, 54),
            specialKeyColor = Color.rgb(29, 42, 75), pressedKeyColor = Color.rgb(45, 75, 112),
            shiftColor = Color.rgb(48, 74, 122), textColor = Color.rgb(233, 247, 255),
            accentColor = Color.rgb(55, 221, 255), borderColor = Color.rgb(57, 126, 164),
            keyStyle = KeyStyle.BORDERED,
        )

        val BUILT_INS = listOf(SOLID_WHITE, SOLID_BLACK, NEON)
    }
}
