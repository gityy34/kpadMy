package com.example.aikeyboard.theme

import android.graphics.Color

enum class KeyStyle { BORDERED, BORDERLESS }

enum class ThemeCategory {
    DEFAULT,
    COLOURS,
    LANDSCAPES,
    LIGHT_GRADIENT,
    DARK_GRADIENT,
    MY_THEMES,
}

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
    val category: ThemeCategory = ThemeCategory.DEFAULT,
    val isBuiltIn: Boolean = true,
    val isDeletable: Boolean = false,
) {
    val isGradient: Boolean get() = gradientStart != null && gradientEnd != null
    val hasImage: Boolean get() = !backgroundImageUri.isNullOrBlank()

    companion object {
        val SOLID_WHITE = solid("solid_white", "Solid White", Color.rgb(243, 244, 246), Color.WHITE, Color.rgb(221, 227, 244), Color.rgb(215, 224, 255), Color.rgb(190, 204, 255), Color.rgb(32, 33, 36), Color.rgb(83, 111, 220), Color.rgb(220, 223, 230))
        val SOLID_BLACK = solid("solid_black", "Solid Black", Color.rgb(8, 9, 12), Color.rgb(27, 29, 34), Color.rgb(42, 46, 57), Color.rgb(66, 78, 112), Color.rgb(70, 84, 132), Color.WHITE, Color.rgb(111, 151, 255), Color.rgb(72, 77, 91))
        val NEON = solid("neon", "Neon / Futuristic", Color.rgb(9, 12, 28), Color.rgb(22, 28, 54), Color.rgb(29, 42, 75), Color.rgb(45, 75, 112), Color.rgb(48, 74, 122), Color.rgb(233, 247, 255), Color.rgb(55, 221, 255), Color.rgb(57, 126, 164))

        val SOFT_WHITE = solid("soft_white", "Soft White", Color.rgb(250, 249, 247), Color.WHITE, Color.rgb(232, 235, 241), Color.rgb(218, 226, 247), Color.rgb(204, 216, 250), Color.rgb(36, 38, 44), Color.rgb(76, 104, 200), Color.rgb(224, 226, 231), ThemeCategory.COLOURS)
        val CHARCOAL = solid("charcoal", "Charcoal", Color.rgb(35, 38, 43), Color.rgb(50, 54, 61), Color.rgb(65, 71, 81), Color.rgb(86, 96, 119), Color.rgb(78, 89, 112), Color.WHITE, Color.rgb(138, 170, 255), Color.rgb(83, 89, 101), ThemeCategory.COLOURS)
        val BLUE = solid("blue", "Ocean Blue", Color.rgb(224, 235, 255), Color.rgb(247, 250, 255), Color.rgb(207, 222, 252), Color.rgb(185, 207, 255), Color.rgb(164, 191, 255), Color.rgb(24, 48, 91), Color.rgb(48, 105, 210), Color.rgb(194, 209, 238), ThemeCategory.COLOURS)
        val GREEN = solid("green", "Leaf Green", Color.rgb(229, 244, 233), Color.rgb(249, 255, 250), Color.rgb(205, 232, 211), Color.rgb(180, 222, 190), Color.rgb(158, 207, 171), Color.rgb(30, 65, 38), Color.rgb(41, 135, 67), Color.rgb(194, 220, 199), ThemeCategory.COLOURS)
        val TEAL = solid("teal", "Deep Teal", Color.rgb(10, 74, 77), Color.rgb(22, 104, 106), Color.rgb(28, 126, 128), Color.rgb(45, 158, 155), Color.rgb(48, 155, 151), Color.WHITE, Color.rgb(111, 231, 218), Color.rgb(56, 148, 146), ThemeCategory.COLOURS)
        val RED = solid("red", "Warm Red", Color.rgb(93, 21, 28), Color.rgb(183, 39, 45), Color.rgb(211, 63, 66), Color.rgb(232, 92, 88), Color.rgb(226, 84, 82), Color.WHITE, Color.rgb(255, 175, 166), Color.rgb(224, 99, 96), ThemeCategory.COLOURS)
        val CYAN = solid("cyan", "Bright Cyan", Color.rgb(211, 243, 248), Color.rgb(239, 252, 255), Color.rgb(174, 224, 234), Color.rgb(143, 207, 223), Color.rgb(125, 198, 216), Color.rgb(21, 59, 69), Color.rgb(25, 135, 170), Color.rgb(170, 216, 225), ThemeCategory.COLOURS)

        val LIGHT_PINK = gradient("light_pink", "Rose Mist", Color.rgb(255, 231, 241), Color.rgb(255, 249, 244), Color.rgb(255, 255, 255), Color.rgb(252, 219, 233), Color.rgb(246, 196, 220), Color.rgb(68, 34, 52), Color.rgb(204, 88, 143), Color.rgb(237, 192, 213), ThemeCategory.LIGHT_GRADIENT)
        val LIGHT_BLUE = gradient("light_blue", "Sky Wash", Color.rgb(220, 238, 255), Color.rgb(248, 253, 255), Color.rgb(255, 255, 255), Color.rgb(205, 228, 252), Color.rgb(179, 208, 246), Color.rgb(25, 53, 86), Color.rgb(62, 124, 213), Color.rgb(191, 216, 244), ThemeCategory.LIGHT_GRADIENT)
        val LIGHT_MINT = gradient("light_mint", "Mint Air", Color.rgb(220, 249, 238), Color.rgb(247, 255, 247), Color.rgb(255, 255, 255), Color.rgb(205, 239, 222), Color.rgb(172, 222, 193), Color.rgb(25, 66, 43), Color.rgb(52, 153, 93), Color.rgb(193, 222, 204), ThemeCategory.LIGHT_GRADIENT)
        val LIGHT_LAVENDER = gradient("light_lavender", "Lavender Cloud", Color.rgb(235, 226, 255), Color.rgb(251, 248, 255), Color.rgb(255, 255, 255), Color.rgb(221, 211, 250), Color.rgb(195, 181, 239), Color.rgb(48, 35, 78), Color.rgb(118, 81, 205), Color.rgb(215, 204, 241), ThemeCategory.LIGHT_GRADIENT)
        val LIGHT_SUNSET = gradient("light_sunset", "Soft Sunset", Color.rgb(255, 232, 196), Color.rgb(255, 248, 227), Color.rgb(255, 255, 255), Color.rgb(251, 220, 183), Color.rgb(240, 193, 143), Color.rgb(74, 48, 24), Color.rgb(204, 123, 42), Color.rgb(237, 204, 169), ThemeCategory.LIGHT_GRADIENT)
        val LIGHT_PEACH = gradient("light_peach", "Peach Cream", Color.rgb(255, 222, 209), Color.rgb(255, 249, 243), Color.rgb(255, 255, 255), Color.rgb(249, 207, 191), Color.rgb(235, 175, 155), Color.rgb(79, 42, 31), Color.rgb(207, 102, 73), Color.rgb(238, 193, 176), ThemeCategory.LIGHT_GRADIENT)

        val DARK_PURPLE = gradient("dark_purple", "Amethyst Night", Color.rgb(48, 10, 76), Color.rgb(14, 42, 82), Color.rgb(91, 45, 119), Color.rgb(56, 77, 136), Color.rgb(88, 66, 148), Color.WHITE, Color.rgb(205, 154, 255), Color.rgb(120, 93, 172), ThemeCategory.DARK_GRADIENT)
        val DARK_SLATE = gradient("dark_slate", "Slate Night", Color.rgb(16, 20, 28), Color.rgb(55, 61, 72), Color.rgb(75, 82, 95), Color.rgb(96, 110, 137), Color.rgb(87, 99, 126), Color.WHITE, Color.rgb(161, 188, 255), Color.rgb(88, 95, 108), ThemeCategory.DARK_GRADIENT)
        val DARK_TEAL = gradient("dark_teal", "Teal Night", Color.rgb(5, 48, 52), Color.rgb(20, 79, 80), Color.rgb(34, 102, 102), Color.rgb(45, 134, 132), Color.rgb(47, 137, 132), Color.WHITE, Color.rgb(111, 235, 218), Color.rgb(57, 135, 134), ThemeCategory.DARK_GRADIENT)
        val DARK_BLUE = gradient("dark_blue", "Blue Hour", Color.rgb(8, 28, 72), Color.rgb(14, 67, 104), Color.rgb(40, 86, 131), Color.rgb(50, 105, 164), Color.rgb(48, 112, 179), Color.WHITE, Color.rgb(128, 199, 255), Color.rgb(70, 118, 170), ThemeCategory.DARK_GRADIENT)
        val DARK_BURGUNDY = gradient("dark_burgundy", "Burgundy Night", Color.rgb(64, 10, 29), Color.rgb(92, 26, 48), Color.rgb(116, 39, 63), Color.rgb(156, 58, 81), Color.rgb(145, 51, 77), Color.WHITE, Color.rgb(255, 160, 183), Color.rgb(156, 77, 101), ThemeCategory.DARK_GRADIENT)
        val DARK_OCEAN = gradient("dark_ocean", "Deep Ocean", Color.rgb(5, 32, 52), Color.rgb(5, 72, 92), Color.rgb(26, 92, 112), Color.rgb(47, 125, 143), Color.rgb(44, 123, 143), Color.WHITE, Color.rgb(125, 232, 255), Color.rgb(57, 129, 147), ThemeCategory.DARK_GRADIENT)

        // Original gradient presets that provide an image-like landscape feel without external assets.
        val LANDSCAPE_AURORA = gradient("landscape_aurora", "Aurora Glow", Color.rgb(16, 55, 75), Color.rgb(89, 34, 103), Color.rgb(48, 104, 137), Color.rgb(76, 86, 153), Color.rgb(74, 112, 170), Color.WHITE, Color.rgb(178, 232, 255), Color.rgb(81, 110, 145), ThemeCategory.LANDSCAPES)
        val LANDSCAPE_SUNSET = gradient("landscape_sunset", "Sunset Horizon", Color.rgb(90, 31, 67), Color.rgb(221, 99, 45), Color.rgb(145, 71, 89), Color.rgb(211, 105, 103), Color.rgb(192, 82, 91), Color.WHITE, Color.rgb(255, 203, 145), Color.rgb(175, 93, 99), ThemeCategory.LANDSCAPES)
        val LANDSCAPE_OCEAN = gradient("landscape_ocean", "Coastal Blue", Color.rgb(4, 54, 89), Color.rgb(24, 139, 160), Color.rgb(28, 104, 132), Color.rgb(44, 151, 169), Color.rgb(46, 153, 174), Color.WHITE, Color.rgb(145, 241, 255), Color.rgb(55, 145, 161), ThemeCategory.LANDSCAPES)

        val BUILT_INS = listOf(
            SOLID_WHITE, SOLID_BLACK, NEON,
            SOFT_WHITE, CHARCOAL, BLUE, GREEN, TEAL, RED, CYAN,
            LIGHT_PINK, LIGHT_BLUE, LIGHT_MINT, LIGHT_LAVENDER, LIGHT_SUNSET, LIGHT_PEACH,
            DARK_PURPLE, DARK_SLATE, DARK_TEAL, DARK_BLUE, DARK_BURGUNDY, DARK_OCEAN,
            LANDSCAPE_AURORA, LANDSCAPE_SUNSET, LANDSCAPE_OCEAN,
        )

        private fun solid(
            id: String,
            name: String,
            background: Int,
            key: Int,
            special: Int,
            pressed: Int,
            shift: Int,
            text: Int,
            accent: Int,
            border: Int,
            category: ThemeCategory = ThemeCategory.DEFAULT,
        ) = KeyboardTheme(id, name, background, key, special, pressed, shift, text, accent, border, category = category)

        private fun gradient(
            id: String,
            name: String,
            start: Int,
            end: Int,
            key: Int,
            special: Int,
            pressed: Int,
            text: Int,
            accent: Int,
            border: Int,
            category: ThemeCategory,
        ) = KeyboardTheme(id, name, blend(start, end), key, special, pressed, pressed, text, accent, border, gradientStart = start, gradientEnd = end, category = category)

        private fun blend(first: Int, second: Int): Int =
            Color.rgb(
                (Color.red(first) + Color.red(second)) / 2,
                (Color.green(first) + Color.green(second)) / 2,
                (Color.blue(first) + Color.blue(second)) / 2,
            )
    }
}
