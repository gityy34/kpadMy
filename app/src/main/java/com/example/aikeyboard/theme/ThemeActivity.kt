package com.example.aikeyboard.theme

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * The single Theme destination used by both Tools -> Theme and Settings -> Themes & Appearance.
 * Catalog selection is temporary until Apply is pressed.
 */
class ThemeActivity : Activity() {
    private lateinit var store: ThemeStore
    private var original = KeyboardTheme.SOLID_WHITE
    private var selected = KeyboardTheme.SOLID_WHITE
    private var showingEditor = false
    private val expanded = mutableMapOf<ThemeCategory, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ThemeStore(this)
        original = store.activeTheme()
        selected = original
        ThemeCategory.values().forEach { expanded[it] = true }
        showCatalog()
    }

    private fun showCatalog() {
        showingEditor = false
        selected = store.activeTheme()
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = pageBackground(selected)
            contentDescription = "Theme catalog"
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        content.addView(header("Theme") { finish() })
        content.addView(label("My themes", true), marginParams(0, 16, 0, 8))

        val custom = store.customThemes()
        val myThemes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addThemeRows(myThemes, listOf(createThemeCard()))
        addThemeRows(myThemes, custom)
        content.addView(myThemes)

        val categories = listOf(
            ThemeCategory.DEFAULT to "Default",
            ThemeCategory.COLOURS to "Colours",
            ThemeCategory.LANDSCAPES to "Landscapes / Images",
            ThemeCategory.LIGHT_GRADIENT to "Light gradient",
            ThemeCategory.DARK_GRADIENT to "Dark gradient",
        )
        categories.forEach { (category, title) ->
            val themes = store.themesFor(category)
            if (themes.isEmpty()) return@forEach
            content.addView(sectionHeader(title, category), marginParams(0, 20, 0, 8))
            if (expanded[category] == true) addThemeRows(content, themes)
        }

        scroll.addView(content)
        setContentView(scroll)
    }

    private fun showEditor(theme: KeyboardTheme) {
        showingEditor = true
        selected = theme.copy(
            keyStyle = original.keyStyle,
            isBuiltIn = theme.isBuiltIn,
            isDeletable = theme.isDeletable,
        )
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            background = pageBackground(selected)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        content.addView(header("Theme preview") { showCatalog() })
        content.addView(ThemePreviewView(this).also {
            it.setTheme(selected)
            it.contentDescription = "Live keyboard preview"
            it.tag = PREVIEW_TAG
        }, LinearLayout.LayoutParams(-1, dp(250)).apply { topMargin = dp(16) })

        val borders = Switch(this).apply {
            text = "Key borders"
            textSize = 17f
            setTextColor(selected.textColor)
            isChecked = selected.keyStyle == KeyStyle.BORDERED
            buttonTintList = ColorStateList.valueOf(selected.accentColor)
            setOnCheckedChangeListener { _, checked ->
                selected = selected.copy(keyStyle = if (checked) KeyStyle.BORDERED else KeyStyle.BORDERLESS)
                findPreview(content)?.setTheme(selected)
            }
        }
        content.addView(borders, marginParams(0, 18, 0, 2))

        val imageButton = actionButton("Choose background image")
        imageButton.setOnClickListener { chooseImage() }
        content.addView(imageButton, marginParams(0, 10, 0, 0))

        val name = EditText(this).apply {
            hint = "Custom theme name"
            setTextColor(selected.textColor)
            setHintTextColor(withAlpha(selected.textColor, 150))
            setSingleLine(true)
            backgroundTintList = ColorStateList.valueOf(selected.accentColor)
            contentDescription = "Custom theme name"
        }
        content.addView(name, marginParams(0, 12, 0, 0))

        val saveCopy = actionButton("Save as My Theme")
        saveCopy.setOnClickListener {
            val safeName = name.text?.toString()?.trim().orEmpty()
                .ifBlank { selected.name.ifBlank { "My Theme" } }
            selected = store.saveCustom(selected.copy(
                id = "custom_" + safeName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_'),
                name = safeName,
                isBuiltIn = false,
                isDeletable = true,
            ))
            name.setText(selected.name)
            findPreview(content)?.setTheme(selected)
            Toast.makeText(this, "Theme saved in My themes", Toast.LENGTH_SHORT).show()
        }
        content.addView(saveCopy, marginParams(0, 8, 0, 0))

        if (selected.isDeletable && !selected.id.startsWith("draft_")) {
            val delete = actionButton("Delete theme")
            delete.setOnClickListener { confirmDelete(selected) }
            content.addView(delete, marginParams(0, 8, 0, 0))
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val cancel = actionButton("Cancel")
        cancel.setOnClickListener {
            selected = original
            showCatalog()
        }
        val apply = actionButton("Apply")
        apply.setOnClickListener {
            store.apply(selected)
            original = store.activeTheme()
            finish()
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        actions.addView(apply, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        content.addView(actions, marginParams(0, 20, 0, 0))

        scroll.addView(content)
        setContentView(scroll)
    }

    private fun createThemeCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedSurface(selected, selected.accentColor)
            contentDescription = "Create a custom theme"
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setOnClickListener {
                val draft = selected.copy(
                    id = "draft_custom",
                    name = "My Theme",
                    isBuiltIn = false,
                    isDeletable = true,
                    category = ThemeCategory.MY_THEMES,
                )
                showEditor(draft)
            }
        }
        val plus = TextView(this).apply {
            text = "+"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(selected.accentColor)
        }
        card.addView(plus, LinearLayout.LayoutParams(-1, dp(68)))
        card.addView(TextView(this).apply {
            text = "Create"
            gravity = Gravity.CENTER
            setTextColor(selected.textColor)
            textSize = 12f
        }, LinearLayout.LayoutParams(-1, dp(22)))
        return card
    }

    private fun addThemeRows(parent: LinearLayout, themes: List<*>) {
        val cards = themes.mapNotNull { item ->
            when (item) {
                is KeyboardTheme -> themeCard(item)
                is View -> item
                else -> null
            }
        }
        if (cards.isEmpty()) return
        var row: LinearLayout? = null
        cards.forEachIndexed { index, card ->
            if (index % columns() == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, marginParams(0, 0, 0, 8))
            }
            row?.addView(card, LinearLayout.LayoutParams(0, dp(108), 1f).apply {
                leftMargin = if (index % columns() == 0) 0 else dp(4)
                rightMargin = if (index % columns() == columns() - 1) 0 else dp(4)
            })
        }
        val remainder = cards.size % columns()
        if (remainder != 0) {
            repeat(columns() - remainder) {
                row?.addView(SpaceView(this), LinearLayout.LayoutParams(0, dp(108), 1f).apply {
                    leftMargin = dp(4)
                })
            }
        }
    }

    private fun themeCard(theme: KeyboardTheme): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = roundedSurface(theme, if (theme.id == selected.id) selected.accentColor else theme.borderColor)
            contentDescription = theme.name + " theme"
            setOnClickListener { showEditor(theme) }
        }
        val preview = ThemePreviewView(this).apply {
            setTheme(theme)
            isClickable = false
        }
        card.addView(preview, LinearLayout.LayoutParams(-1, dp(76)))
        card.addView(TextView(this).apply {
            text = if (theme.id == selected.id) "✓ " + theme.name else theme.name
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setTextColor(selected.textColor)
        }, LinearLayout.LayoutParams(-1, dp(25)))
        return card
    }

    private fun sectionHeader(title: String, category: ThemeCategory): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = title + " section"
            setOnClickListener {
                expanded[category] = expanded[category] != true
                showCatalog()
            }
        }
        row.addView(label(title, true), LinearLayout.LayoutParams(0, dp(36), 1f))
        row.addView(TextView(this).apply {
            text = if (expanded[category] == true) "⌃" else "⌄"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(selected.textColor)
        }, LinearLayout.LayoutParams(dp(42), dp(36)))
        return row
    }

    private fun header(title: String, onBack: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = "‹"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(selected.textColor)
            contentDescription = "Back"
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(56)))
        row.addView(TextView(this).apply {
            text = title
            textSize = 27f
            setTextColor(selected.textColor)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        return row
    }

    private fun label(text: String, accent: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = if (accent) 15f else 16f
        setTextColor(if (accent) selected.accentColor else selected.textColor)
    }

    private fun actionButton(text: String): Button = Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(selected.textColor)
        background = roundedSurface(selected, selected.borderColor)
        backgroundTintList = null
        minHeight = dp(48)
        stateListAnimator = null
    }

    private fun roundedSurface(theme: KeyboardTheme, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            setColor(withAlpha(theme.keyColor, 235))
            setStroke(dp(1), stroke)
        }

    private fun pageBackground(theme: KeyboardTheme): GradientDrawable =
        GradientDrawable(
            if (theme.isGradient) GradientDrawable.Orientation.TL_BR else GradientDrawable.Orientation.TOP_BOTTOM,
            if (theme.isGradient) intArrayOf(theme.gradientStart ?: theme.backgroundColor, theme.gradientEnd ?: theme.backgroundColor)
            else intArrayOf(theme.backgroundColor, theme.backgroundColor),
        )

    private fun findPreview(root: ViewGroup): ThemePreviewView? {
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is ThemePreviewView) return child
            if (child is ViewGroup) findPreview(child)?.let { return it }
        }
        return null
    }

    private fun chooseImage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selected = selected.copy(
                id = if (selected.isBuiltIn) "draft_custom" else selected.id,
                name = if (selected.isBuiltIn) "Imported Image" else selected.name,
                backgroundImageUri = uri.toString(),
                category = ThemeCategory.LANDSCAPES,
                isBuiltIn = false,
                isDeletable = true,
            )
            showEditor(selected)
        }
    }

    private fun confirmDelete(theme: KeyboardTheme) {
        AlertDialog.Builder(this)
            .setTitle("Delete " + theme.name + "?")
            .setMessage("This removes the saved custom theme from this device.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Delete") { _, _ ->
                store.deleteCustom(theme.id)
                original = store.activeTheme()
                selected = original
                showCatalog()
            }
            .show()
    }

    override fun onBackPressed() {
        if (showingEditor) {
            selected = original
            showCatalog()
        } else {
            super.onBackPressed()
        }
    }

    private fun columns(): Int = (resources.displayMetrics.widthPixels / dp(116)).coerceIn(2, 4)

    private fun marginParams(left: Int, top: Int, right: Int, bottom: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private class SpaceView(context: android.content.Context) : View(context)

    companion object {
        private const val IMAGE_REQUEST = 401
        private const val PREVIEW_TAG = "theme_preview"
    }
}
