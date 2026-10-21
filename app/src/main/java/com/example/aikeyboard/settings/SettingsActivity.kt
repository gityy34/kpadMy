package com.example.aikeyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.aikeyboard.R
import com.example.aikeyboard.clipboard.ClipboardHistoryStore
import com.example.aikeyboard.learning.PersonalVocabularyStore
import com.example.aikeyboard.theme.KeyboardTheme
import com.example.aikeyboard.theme.ThemeActivity
import com.example.aikeyboard.theme.ThemeStore
import java.util.Locale

/**
 * Original, searchable settings boundary for the keyboard.
 *
 * The index is built in memory once per screen and contains only setting metadata. It never
 * reads the editor, prediction data, or personal vocabulary while the user is typing.
 */
class SettingsActivity : Activity() {
    private lateinit var settings: SharedPreferencesKeyboardSettings
    private lateinit var themeStore: ThemeStore
    private lateinit var screenRoot: LinearLayout
    private lateinit var search: EditText
    private lateinit var bodyScroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var categories: List<SettingsCategory>
    private var activeTheme = KeyboardTheme.SOLID_WHITE
    private var currentCategoryId: String? = null
    private val renderedRows = linkedMapOf<String, View>()
    private lateinit var headerBack: SettingsIconView
    private lateinit var headerTitle: TextView
    private lateinit var searchShell: LinearLayout
    private lateinit var searchIcon: SettingsIconView
    private lateinit var searchClear: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SharedPreferencesKeyboardSettings(this)
        themeStore = ThemeStore(this)
        activeTheme = themeStore.activeTheme()
        categories = buildCategories()

        buildScreen()
        renderHome()

        if (intent.getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            search.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            search.post {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        } else {
            screenRoot.requestFocus()
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::body.isInitialized) {
            activeTheme = themeStore.activeTheme()
            categories = buildCategories()
            val query = search.text?.toString().orEmpty().trim()
            if (query.isBlank()) renderCurrentPage() else renderSearchResults(query)
        }
    }

    override fun onBackPressed() {
        if (currentCategoryId != null) {
            renderHome()
        } else {
            super.onBackPressed()
        }
    }

    private fun buildScreen() {
        screenRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            isFocusableInTouchMode = true
            setPadding(dp(18), dp(10), dp(18), 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerBack = SettingsIconView(this, SettingsIconKind.BACK, showCircle = true).apply {
            contentDescription = "Back"
            setOnClickListener {
                if (currentCategoryId != null) renderHome() else finish()
            }
        }
        header.addView(headerBack, LinearLayout.LayoutParams(dp(44), dp(48)))

        headerTitle = label("AI Keyboard Settings", 23f, activeTheme.textColor).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
            setPadding(dp(8), 0, 0, 0)
        }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        screenRoot.addView(
            header,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ),
        )

        searchShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }
        searchIcon = SettingsIconView(this, SettingsIconKind.SEARCH, showCircle = false)
        searchIcon.contentDescription = "Search"
        searchShell.addView(searchIcon, LinearLayout.LayoutParams(dp(36), dp(40)))

        search = EditText(this).apply {
            hint = getString(R.string.search_settings)
            contentDescription = getString(R.string.search_settings)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(activeTheme.textColor)
            setHintTextColor(mutedColor())
            setPadding(0, 0, 0, 0)
            background = null
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
        }
        searchShell.addView(
            search,
            LinearLayout.LayoutParams(0, dp(44), 1f),
        )

        searchClear = TextView(this).apply {
            text = "×"
            textSize = 22f
            gravity = Gravity.CENTER
            contentDescription = "Clear search"
            visibility = View.GONE
            setOnClickListener {
                search.setText("")
                search.requestFocus()
            }
        }
        searchShell.addView(searchClear, LinearLayout.LayoutParams(dp(36), dp(40)))

        search.setOnFocusChangeListener { _, hasFocus ->
            searchShell.background = roundedSurface(
                cornerDp = 15,
                strokeColor = if (hasFocus) activeTheme.accentColor else activeTheme.borderColor,
            )
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                val query = s?.toString().orEmpty().trim()
                if (query.isBlank()) {
                    renderCurrentPage()
                } else {
                    renderSearchResults(query)
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        screenRoot.addView(
            searchShell,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48),
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(10)
            },
        )

        bodyScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, 0, 0, dp(18))
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        bodyScroll.addView(
            body,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        screenRoot.addView(
            bodyScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(screenRoot)
        applyThemeToScreen()
    }

    private fun renderCurrentPage() {
        val categoryId = currentCategoryId
        when (categoryId) {
            null -> renderHome()
            APPEARANCE_CONTROLS_ID -> renderAppearanceControls()
            KEY_BEHAVIOR_CONTROLS_ID -> renderKeyBehaviorControls()
            else -> renderCategory(categoryId)
        }
    }

    private fun renderHome() {
        currentCategoryId = null
        renderedRows.clear()
        headerTitle.text = "AI Keyboard Settings"
        headerBack.contentDescription = "Close Settings"
        applyThemeToScreen()
        body.removeAllViews()
        categories.forEach { category ->
            body.addView(categoryRow(category), rowParams())
        }
        bodyScroll.scrollTo(0, 0)
    }

    private fun renderCategory(categoryId: String, focusEntryId: String? = null) {
        val category = categories.firstOrNull { it.id == categoryId } ?: return
        currentCategoryId = category.id
        renderedRows.clear()
        headerTitle.text = category.title
        headerBack.contentDescription = "Back to Settings"
        applyThemeToScreen()
        body.removeAllViews()
        addSectionHeader(category.title, category.description)
        category.entries.forEach { entry ->
            val row = settingRow(entry)
            renderedRows[entry.id] = row
            body.addView(row, rowParams())
        }
        bodyScroll.scrollTo(0, 0)
        if (focusEntryId != null) {
            bodyScroll.post {
                renderedRows[focusEntryId]?.let { bodyScroll.smoothScrollTo(0, it.top) }
            }
        }
    }

    private fun renderSearchResults(query: String) {
        currentCategoryId = null
        renderedRows.clear()
        headerTitle.text = "Search Settings"
        headerBack.contentDescription = "Close Settings"
        applyThemeToScreen()
        body.removeAllViews()
        addSectionHeader(
            "Search results",
            "Matching settings for “\${query}”. Tap a result to open its category or action.",
        )
        val normalized = query.lowercase(Locale.ROOT)
        val matches = categories.flatMap { category ->
            category.entries.map { entry ->
                category.id to entry.copy(
                    path = "\${category.title}  ›  \${entry.path}",
                    keywords = "\${category.title} \${category.description} \${entry.keywords}",
                )
            }
        }.filter { entry ->
            listOf(entry.second.title, entry.second.description, entry.second.path, entry.second.keywords)
                .joinToString(" ")
                .lowercase(Locale.ROOT)
                .contains(normalized)
        }.distinctBy { it.second.id }

        if (matches.isEmpty()) {
            body.addView(label("No matching settings", 16f, mutedColor()).apply {
                setPadding(dp(8), dp(28), dp(8), dp(28))
                contentDescription = "No matching settings"
            })
        } else {
            matches.forEach { (categoryId, entry) ->
                val row = settingRow(
                    entry = entry,
                    showPath = true,
                    onRowClick = { openSearchResult(categoryId, entry.id) },
                )
                body.addView(row, rowParams())
            }
        }
        bodyScroll.scrollTo(0, 0)
    }

    private fun openSearchResult(categoryId: String, entryId: String) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(search.windowToken, 0)
        val target = categories.firstOrNull { it.id == categoryId }
            ?.entries
            ?.firstOrNull { it.id == entryId }
        currentCategoryId = null
        search.setText("")
        if (target?.action != null) {
            performAction(target.action)
        } else {
            renderCategory(categoryId, entryId)
        }
    }

    private fun addSectionHeader(title: String, description: String) {
        body.addView(
            label(description, 13f, mutedColor()).apply {
                contentDescription = title
                setPadding(dp(4), dp(7), dp(4), dp(12))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun categoryRow(category: SettingsCategory): View {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = category.title
            setOnClickListener { renderCategory(category.id) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
        }
        val icon = SettingsIconView(
            this,
            categoryIconKind(category.id),
            showCircle = true,
        ).apply {
            iconColor = activeTheme.textColor
            circleColor = activeTheme.keyColor
            contentDescription = category.title
        }
        row.addView(
            icon,
            LinearLayout.LayoutParams(dp(48), dp(56)).apply {
                rightMargin = dp(8)
            },
        )
        row.addView(
            label(category.title, 17f, activeTheme.textColor).apply {
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
                maxLines = 1
            },
            LinearLayout.LayoutParams(0, dp(56), 1f),
        )
        row.addView(
            SettingsChevronView(this).apply {
                lineColor = activeTheme.borderColor
                contentDescription = "Open \${category.title}"
            },
            LinearLayout.LayoutParams(dp(28), dp(56)),
        )
        wrapper.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56),
            ),
        )
        wrapper.addView(
            View(this).apply { setBackgroundColor(dividerColor()) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            ).apply {
                leftMargin = dp(56)
            },
        )
        return wrapper
    }

    private fun settingRow(
        entry: SettingEntry,
        showPath: Boolean = false,
        onRowClick: (() -> Unit)? = null,
    ): View {
        val enabled = entry.available
        val height = if (showPath) 72 else 62
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            alpha = if (enabled) 1f else 0.56f
            contentDescription = "\${entry.title}: \${entry.description}"
        }

        val icon = SettingsIconView(
            this,
            entryIconKind(entry.id),
            showCircle = false,
        ).apply {
            iconColor = if (enabled) activeTheme.accentColor else mutedColor()
            contentDescription = entry.title
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(38), height))

        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
        }
        text.addView(label(entry.title, 15f, activeTheme.textColor).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        text.addView(label(entry.description, 12f, mutedColor()).apply {
            maxLines = if (showPath) 1 else 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        if (showPath) {
            text.addView(label(entry.path, 11f, activeTheme.accentColor).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
        row.addView(text, LinearLayout.LayoutParams(0, height, 1f))

        val toggleKey = entry.toggle
        val action = entry.action
        when {
            onRowClick != null -> {
                row.isClickable = true
                row.isFocusable = true
                row.setOnClickListener { onRowClick.invoke() }
                row.addView(
                    SettingsChevronView(this).apply {
                        lineColor = activeTheme.accentColor
                        contentDescription = "Open setting"
                    },
                    LinearLayout.LayoutParams(dp(28), height),
                )
            }
            toggleKey != null -> {
                val toggle = Switch(this).apply {
                    isChecked = readToggle(toggleKey)
                    isEnabled = enabled
                    thumbTintList = controlTint(
                        enabledChecked = activeTheme.accentColor,
                        enabledUnchecked = activeTheme.textColor,
                    )
                    trackTintList = controlTint(
                        enabledChecked = withAlpha(activeTheme.accentColor, 160),
                        enabledUnchecked = withAlpha(activeTheme.textColor, 92),
                    )
                    contentDescription = entry.title
                    setOnCheckedChangeListener { _, checked -> writeToggle(toggleKey, checked) }
                }
                row.addView(toggle, LinearLayout.LayoutParams(dp(54), height))
                row.setOnClickListener { toggle.performClick() }
            }
            action != null && enabled -> {
                row.isClickable = true
                row.isFocusable = true
                row.setOnClickListener { performAction(action) }
                row.addView(
                    SettingsChevronView(this).apply {
                        lineColor = activeTheme.accentColor
                        contentDescription = "Open setting"
                    },
                    LinearLayout.LayoutParams(dp(28), height),
                )
            }
            !enabled -> {
                row.addView(
                    label("Unavailable", 11f, mutedColor()).apply {
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(dp(76), height),
                )
            }
            else -> {
                row.addView(
                    SettingsIconView(this, SettingsIconKind.INFO, showCircle = false).apply {
                        iconColor = mutedColor()
                    },
                    LinearLayout.LayoutParams(dp(28), height),
                )
            }
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        wrapper.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height,
            ),
        )
        wrapper.addView(
            View(this).apply { setBackgroundColor(dividerColor()) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            ).apply {
                leftMargin = dp(46)
            },
        )
        return wrapper
    }

    private fun performAction(action: SettingAction?) {
        when (action) {
            SettingAction.OPEN_THEMES -> startActivity(Intent(this, ThemeActivity::class.java))
            SettingAction.OPEN_APPEARANCE_CONTROLS -> renderAppearanceControls()
            SettingAction.OPEN_KEY_BEHAVIOR_CONTROLS -> renderKeyBehaviorControls()
            SettingAction.RESET_TOOLBAR -> {
                settings.setToolbarOrder(KeyboardSettings.DEFAULT_TOOLBAR_ORDER)
                Toast.makeText(this, "Toolbar layout reset", Toast.LENGTH_SHORT).show()
                renderCurrentPage()
            }
            SettingAction.CLEAR_PERSONAL_DATA -> confirmClear()
            SettingAction.CLEAR_CLIPBOARD_RECENT -> confirmClipboardClear(ClipboardClearScope.RECENT)
            SettingAction.CLEAR_CLIPBOARD_PINNED -> confirmClipboardClear(ClipboardClearScope.PINNED)
            SettingAction.CLEAR_CLIPBOARD_ALL -> confirmClipboardClear(ClipboardClearScope.ALL)
            SettingAction.OPEN_KEYBOARD_SETUP -> startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
            null -> Unit
        }
    }

    private fun readToggle(toggle: ToggleKey): Boolean = when (toggle) {
        ToggleKey.PERSONALIZED_SUGGESTIONS -> settings.personalizedSuggestionsEnabled()
        ToggleKey.GLIDE_TYPING -> settings.glideTypingEnabled()
        ToggleKey.SHOW_LANGUAGE_SWITCH -> settings.showLanguageSwitchKey()
        ToggleKey.CLIPBOARD_HISTORY -> settings.clipboardHistoryEnabled()
        ToggleKey.NUMBER_ROW -> settings.numberRowEnabled()
        ToggleKey.ALTERNATE_CHARACTERS -> settings.alternateCharactersEnabled()
    }

    private fun writeToggle(toggle: ToggleKey, value: Boolean) {
        when (toggle) {
            ToggleKey.PERSONALIZED_SUGGESTIONS -> settings.setPersonalizedSuggestionsEnabled(value)
            ToggleKey.GLIDE_TYPING -> settings.setGlideTypingEnabled(value)
            ToggleKey.SHOW_LANGUAGE_SWITCH -> settings.setShowLanguageSwitchKey(value)
            ToggleKey.CLIPBOARD_HISTORY -> settings.setClipboardHistoryEnabled(value)
            ToggleKey.NUMBER_ROW -> settings.setNumberRowEnabled(value)
            ToggleKey.ALTERNATE_CHARACTERS -> settings.setAlternateCharactersEnabled(value)
        }
        notifySettingsChanged()
    }

    private fun renderKeyBehaviorControls() {
        currentCategoryId = KEY_BEHAVIOR_CONTROLS_ID
        renderedRows.clear()
        applyThemeToScreen()
        body.removeAllViews()
        addSectionHeader(
            "Key press behavior",
            "Long-press alternatives remain separate from the fast normal tap path.",
        )

        val currentLevel = settings.longPressDelayLevel().coerceIn(0, KeyboardSettings.LONG_PRESS_DELAY_LEVEL_COUNT - 1)
        val valueLabel = label(
            "${KeyboardSettings.longPressDelayForLevel(currentLevel)} ms",
            18f,
            activeTheme.textColor,
        ).apply {
            gravity = Gravity.CENTER
            contentDescription = "Long-press delay value"
        }
        body.addView(valueLabel, rowParams().apply { height = dp(44) })

        val seek = themedSeekBar().apply {
            max = KeyboardSettings.LONG_PRESS_DELAY_LEVEL_COUNT - 1
            progress = currentLevel
            contentDescription = "Long-press delay"
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                val safeLevel = value.coerceIn(0, KeyboardSettings.LONG_PRESS_DELAY_LEVEL_COUNT - 1)
                valueLabel.text = "${KeyboardSettings.longPressDelayForLevel(safeLevel)} ms"
                if (fromUser) {
                    settings.setLongPressDelayLevel(safeLevel)
                    notifySettingsChanged()
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        body.addView(seek, rowParams().apply {
            setMargins(dp(8), 0, dp(8), dp(8))
        })
        body.addView(
            label(
                "Fast 350 ms   •   Normal 500 ms   •   Slow 700 ms   •   Extra slow 900 ms",
                12f,
                mutedColor(),
            ).apply {
                setPadding(dp(8), 0, dp(8), dp(12))
                contentDescription = "Long-press presets: fast 350 milliseconds, normal 500 milliseconds, slow 700 milliseconds, extra slow 900 milliseconds"
            },
            rowParams(),
        )
        body.addView(
            label(
                "Alphabet keys show relevant alternate characters after the selected delay. A normal tap still commits immediately.",
                13f,
                mutedColor(),
            ).apply {
                setPadding(dp(8), dp(8), dp(8), dp(8))
            },
            rowParams(),
        )
        bodyScroll.scrollTo(0, 0)
    }

    private fun renderAppearanceControls() {
        currentCategoryId = APPEARANCE_CONTROLS_ID
        renderedRows.clear()
        applyThemeToScreen()
        body.removeAllViews()
        addSectionHeader(
            "Font & Emoji Size",
            "Choose a bounded level and see the approximate result immediately.",
        )
        addSizeControl(
            title = "Font size",
            description = "Keyboard labels and suggestion text",
            initialLevel = settings.fontSizeLevel(),
            preview = { level -> "Tt  Aa  123  " + (level + 1) + "/" + SIZE_LEVEL_COUNT },
            scale = { level -> KeyboardSettings.fontScaleForLevel(level) },
            onLevelChanged = { level ->
                settings.setFontSizeLevel(level)
                notifySettingsChanged()
            },
        )
        addSizeControl(
            title = "Emoji size",
            description = "Emoji shown in the in-keyboard Emoji panel",
            initialLevel = settings.emojiSizeLevel(),
            preview = { level -> "😀  😊  ❤️  🔥   " + (level + 1) + "/" + SIZE_LEVEL_COUNT },
            scale = { level -> KeyboardSettings.emojiScaleForLevel(level) },
            onLevelChanged = { level ->
                settings.setEmojiSizeLevel(level)
                notifySettingsChanged()
            },
        )
        bodyScroll.scrollTo(0, 0)
    }

    private fun addSizeControl(
        title: String,
        description: String,
        initialLevel: Int,
        preview: (Int) -> String,
        scale: (Int) -> Float,
        onLevelChanged: (Int) -> Unit,
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedSurface()
        }
        card.addView(label(title, 17f, activeTheme.textColor))
        card.addView(label(description, 12f, mutedColor()).apply {
            setPadding(0, dp(2), 0, dp(8))
        })
        val previewLabel = label(preview(initialLevel), 22f, activeTheme.textColor).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }
        previewLabel.textSize = if (title == "Emoji size") {
            25f * scale(initialLevel)
        } else {
            22f * scale(initialLevel)
        }
        card.addView(previewLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48),
        ))
        val seek = themedSeekBar().apply {
            max = SIZE_LEVEL_COUNT - 1
            progress = initialLevel.coerceIn(0, max)
            contentDescription = title
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                previewLabel.text = preview(value)
                previewLabel.textSize = if (title == "Emoji size") {
                    25f * scale(value)
                } else {
                    22f * scale(value)
                }
                if (fromUser) onLevelChanged(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        card.addView(seek, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(42),
        ))
        body.addView(card, rowParams())
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent(KeyboardSettings.ACTION_SETTINGS_CHANGED).setPackage(packageName))
    }

    private fun confirmClear() {
        themedAlertDialog()
            .setTitle(getString(R.string.clear_personal_data))
            .setMessage(getString(R.string.clear_personal_data_confirmation))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PersonalVocabularyStore.clearPersisted(this)
                Toast.makeText(this, "Personal data cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun confirmClipboardClear(scope: ClipboardClearScope) {
        val (title, message, toast) = when (scope) {
            ClipboardClearScope.RECENT -> Triple(
                "Clear Recent Clips",
                "Remove all unpinned Recent clips? Pinned clips will remain.",
                "Recent clips cleared",
            )
            ClipboardClearScope.PINNED -> Triple(
                "Clear Pinned Clips",
                "Remove all pinned clips? This cannot be undone.",
                "Pinned clips cleared",
            )
            ClipboardClearScope.ALL -> Triple(
                "Clear All Clipboard Data",
                "Remove Recent and Pinned clipboard history? Other keyboard data will remain.",
                "All clipboard data cleared",
            )
        }
        themedAlertDialog()
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Thread {
                    val store = ClipboardHistoryStore(applicationContext)
                    try {
                        when (scope) {
                            ClipboardClearScope.RECENT -> store.clearRecent()
                            ClipboardClearScope.PINNED -> store.clearPinned()
                            ClipboardClearScope.ALL -> store.clearAll()
                        }
                    } finally {
                        store.close()
                    }
                    runOnUiThread {
                        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
                        categories = buildCategories()
                        renderCurrentPage()
                    }
                }.start()
            }
            .show()
    }

    private fun applyThemeToScreen() {
        activeTheme = themeStore.activeTheme()
        screenRoot.background = themeBackground()
        headerTitle.setTextColor(activeTheme.textColor)
        headerBack.iconColor = activeTheme.textColor
        headerBack.circleColor = activeTheme.keyColor
        searchIcon.iconColor = activeTheme.textColor
        searchClear.setTextColor(activeTheme.textColor)
        search.setTextColor(activeTheme.textColor)
        search.setHintTextColor(mutedColor())
        searchShell.background = roundedSurface(
            cornerDp = 15,
            strokeColor = if (search.hasFocus()) activeTheme.accentColor else activeTheme.borderColor,
        )
        body.background = null
        updateSystemBars()
    }

    private fun updateSystemBars() {
        window.statusBarColor = activeTheme.backgroundColor
        window.navigationBarColor = activeTheme.backgroundColor
        var flags = window.decorView.systemUiVisibility
        flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        if (isLightColor(activeTheme.backgroundColor)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun isLightColor(color: Int): Boolean {
        val red = Color.red(color) / 255.0
        val green = Color.green(color) / 255.0
        val blue = Color.blue(color) / 255.0
        val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        return luminance > 0.55
    }

    private fun roundedSurface(
        cornerDp: Int = 15,
        fillColor: Int = activeTheme.keyColor,
        strokeColor: Int = activeTheme.borderColor,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(cornerDp).toFloat()
        setColor(fillColor)
        setStroke(dp(1), strokeColor)
    }

    private fun themeBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        if (activeTheme.isGradient) {
            setColors(
                intArrayOf(
                    activeTheme.gradientStart ?: activeTheme.backgroundColor,
                    activeTheme.gradientEnd ?: activeTheme.backgroundColor,
                ),
                GradientDrawable.Orientation.TOP_BOTTOM,
            )
        } else {
            setColor(activeTheme.backgroundColor)
        }
    }

    private fun themedSeekBar(): SeekBar = SeekBar(this).apply {
        progressTintList = ColorStateList.valueOf(activeTheme.accentColor)
        progressBackgroundTintList = ColorStateList.valueOf(activeTheme.borderColor)
        thumbTintList = ColorStateList.valueOf(activeTheme.accentColor)
    }

    private fun controlTint(
        enabledChecked: Int,
        enabledUnchecked: Int,
    ): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(mutedColor(), 120),
            enabledChecked,
            enabledUnchecked,
        ),
    )

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun themedAlertDialog(): AlertDialog.Builder = AlertDialog.Builder(
        this,
        if (isLightColor(activeTheme.backgroundColor)) {
            android.R.style.Theme_Material_Light_Dialog_Alert
        } else {
            android.R.style.Theme_Material_Dialog_Alert
        },
    )

    private fun label(value: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = true
    }

    private fun mutedColor(): Int = Color.argb(
        172,
        Color.red(activeTheme.textColor),
        Color.green(activeTheme.textColor),
        Color.blue(activeTheme.textColor),
    )

    private fun dividerColor(): Int = Color.argb(
        92,
        Color.red(activeTheme.textColor),
        Color.green(activeTheme.textColor),
        Color.blue(activeTheme.textColor),
    )

    private fun categoryIconKind(id: String): SettingsIconKind = when (id) {
        "languages" -> SettingsIconKind.LANGUAGES
        "typing" -> SettingsIconKind.PREFERENCES
        "suggestions" -> SettingsIconKind.CORRECTIONS
        "glide" -> SettingsIconKind.GLIDE
        "voice" -> SettingsIconKind.VOICE
        "clipboard" -> SettingsIconKind.CLIPBOARD
        "learning" -> SettingsIconKind.DICTIONARY
        "emoji" -> SettingsIconKind.EMOJI
        "themes" -> SettingsIconKind.THEMES
        "toolbar" -> SettingsIconKind.TOOLBAR
        "resize" -> SettingsIconKind.RESIZE
        "ai" -> SettingsIconKind.AI
        "privacy" -> SettingsIconKind.PRIVACY
        "accessibility" -> SettingsIconKind.ACCESSIBILITY
        "about" -> SettingsIconKind.ABOUT
        "help" -> SettingsIconKind.HELP
        else -> SettingsIconKind.INFO
    }

    private fun entryIconKind(id: String): SettingsIconKind = when {
        id.startsWith("languages") -> SettingsIconKind.LANGUAGES
        id.startsWith("typing") -> SettingsIconKind.PREFERENCES
        id.startsWith("suggestions") -> SettingsIconKind.CORRECTIONS
        id.startsWith("glide") -> SettingsIconKind.GLIDE
        id.startsWith("voice") -> SettingsIconKind.VOICE
        id.startsWith("clipboard") -> SettingsIconKind.CLIPBOARD
        id.startsWith("learning") -> SettingsIconKind.DICTIONARY
        id.startsWith("emoji") -> SettingsIconKind.EMOJI
        id.startsWith("themes") -> SettingsIconKind.THEMES
        id.startsWith("toolbar") -> SettingsIconKind.TOOLBAR
        id.startsWith("resize") -> SettingsIconKind.RESIZE
        id.startsWith("ai") -> SettingsIconKind.AI
        id.startsWith("privacy") -> SettingsIconKind.PRIVACY
        id.startsWith("accessibility") -> SettingsIconKind.ACCESSIBILITY
        id.startsWith("about") -> SettingsIconKind.ABOUT
        id.startsWith("help") -> SettingsIconKind.HELP
        else -> SettingsIconKind.INFO
    }

    private fun rowParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = 0
        bottomMargin = 0
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildCategories(): List<SettingsCategory> = listOf(
        SettingsCategory(
            id = "languages",
            icon = "文",
            title = "Languages",
            description = "English, Urdu, Roman Urdu, and mixed-language input",
            entries = listOf(
                info("languages.english", "English", "English QWERTY input is available.", "Language > English", "english qwerty"),
                info("languages.urdu", "Urdu", "Real Urdu Unicode input with RTL support.", "Language > Urdu", "urdu rtl unicode"),
                info("languages.roman", "Roman Urdu", "Latin layout with Roman Urdu prediction and variants.", "Language > Roman Urdu", "roman urdu prediction"),
                info("languages.switch", "Language switching", "Switch instantly from Tools > Language without closing the IME.", "Language > Switching", "switch active language"),
                toggle("languages.switch.key", "Show Language Switch Key", "Show a compact globe key beside 123 and Space in the bottom row.", "Languages > Bottom row", ToggleKey.SHOW_LANGUAGE_SWITCH, "globe language key bottom row space"),
            ),
        ),
        SettingsCategory(
            id = "typing",
            icon = "⌨",
            title = "Preferences",
            description = "General keyboard behavior, layout preferences, and appearance controls",
            entries = listOf(
                action("typing.appearance", "Font & Emoji Size", "Open the seven-level live controls for keyboard text and emoji.", "Preferences > Appearance", SettingAction.OPEN_APPEARANCE_CONTROLS, "font size emoji size appearance preview"),
                toggle("typing.number_row", "Number Row", "Show 1–0 above the alphabet rows without changing the bottom row.", "Preferences > Layout", ToggleKey.NUMBER_ROW, "number row digits 1234567890 top numbers"),
                toggle("typing.alternate_characters", "Long-press alternate characters", "Show symbol/alternate-character popups when an alphabet key is held.", "Preferences > Key press behavior", ToggleKey.ALTERNATE_CHARACTERS, "long press alternate characters popup symbols enable disable"),
                action("typing.long_press", "Long-press delay", "Choose Fast, Normal, Slow, or Extra Slow timing for alternate-character popups.", "Preferences > Key press behavior", SettingAction.OPEN_KEY_BEHAVIOR_CONTROLS, "long press delay alternate popup milliseconds fast normal slow"),
                info("typing.fast", "Typing responsiveness", "Keys respond immediately during normal typing.", "Preferences > Key press behavior", "key press behavior latency"),
                info("typing.shift", "Shift and Caps Lock", "One-shot Shift and Caps Lock are supported.", "Preferences > Shift", "shift caps lock"),
                info("typing.symbols", "Numbers and symbols", "The keyboard includes punctuation and symbol layouts.", "Preferences > Symbols", "numbers symbols punctuation"),
                info("typing.space", "Auto space", "Normal Space commits immediately; suggestion acceptance uses one safe space.", "Preferences > Space", "auto space"),
                info("typing.repeat", "Backspace repeat", "Hold Backspace for controlled repeated deletion.", "Preferences > Backspace", "key repeat backspace"),
                unavailable("typing.haptics", "Haptic feedback", "Haptic feedback is managed by the device and is not configurable here.", "Preferences > Feedback", "vibration sound key press"),
            ),
        ),
        SettingsCategory(
            id = "suggestions",
            icon = "✦",
            title = "Corrections & Suggestions",
            description = "Corrections, current-word suggestions, and next-word prediction",
            entries = listOf(
                info("suggestions.personal", "Personalized suggestions", "This setting is controlled in Dictionary & Personal Learning so there is one source of truth.", "Corrections & Suggestions > Personalization", "learning personal"),
                info("suggestions.next", "Next-word prediction", "After Space, the next-word context is requested asynchronously.", "Suggestions > Next word", "next word context"),
                info("suggestions.languages", "English, Urdu, and Roman Urdu prediction", "Language-aware local candidates remain available for mixed text.", "Suggestions > Languages", "english urdu roman"),
                info("suggestions.typed", "Keep the typed word", "Your original typed form remains available; suggestions do not silently replace it.", "Suggestions > User control", "typed word correction"),
                info("suggestions.size", "Suggestion text size", "Suggestions use readable sizing with bounded cells and ellipsis for long words.", "Suggestions > Appearance", "suggestion font text size"),
                info("suggestions.space", "Auto-space after accepted suggestion", "An accepted normal word receives exactly one safe following space.", "Suggestions > Space", "suggestion space"),
            ),
        ),
        SettingsCategory(
            id = "glide",
            icon = "⌁",
            title = "Glide Typing",
            description = "On-device gesture typing and path recognition",
            entries = listOf(
                toggle("glide.enabled", "Glide typing", "Enable or disable gesture recognition without changing tap input.", "Glide Typing > Main switch", ToggleKey.GLIDE_TYPING, "swipe gesture"),
                info("glide.trail", "Gesture trail", "The lightweight trail follows genuine gestures and respects accessibility behavior.", "Glide Typing > Feedback", "gesture trail"),
                info("glide.prediction", "Glide prediction", "Candidates use live key geometry, local dictionaries, and context.", "Glide Typing > Recognition", "glide prediction"),
                info("glide.space", "Space gesture cursor movement", "Horizontal Space movement is clamped to the editor text boundaries.", "Glide Typing > Space gesture", "space gesture cursor"),
            ),
        ),
        SettingsCategory(
            id = "voice",
            icon = "♩",
            title = "Voice Typing",
            description = "Speech, Urdu/English recognition, and translation",
            entries = listOf(
                info("voice.main", "Voice typing", "Open the fixed-height voice panel from the toolbar microphone.", "Voice Typing > Main", "voice microphone"),
                info("voice.language", "Speech languages", "English, Urdu, and Auto recognition are available in the voice panel.", "Voice Typing > Speech language", "english urdu auto speech"),
                info("voice.translation", "Translation target", "Translation is optional and keeps the original transcript available.", "Voice Typing > Translation", "translate english urdu"),
                info("voice.permission", "Microphone permission", "Permission is requested only when voice typing starts; normal typing is unaffected.", "Voice Typing > Privacy", "microphone permission"),
            ),
        ),
        SettingsCategory(
            id = "clipboard",
            icon = "▣",
            title = "Clipboard",
            description = "Local text history with separate Recent and Pinned collections",
            entries = listOf(
                toggle("clipboard.enabled", "Clipboard History", "Save eligible plain-text clips locally; existing pinned clips remain when this is off.", "Clipboard > History", ToggleKey.CLIPBOARD_HISTORY, "clipboard history on off enable disable"),
                info("clipboard.recent.limit", "Recent Clips Limit", "Recent keeps exactly 8 newest unpinned items. Pinned items do not count toward this limit.", "Clipboard > Recent", "recent clips limit eight 8"),
                info("clipboard.pinned", "Pinned Clips", "Pinned clips are separate and remain until explicitly unpinned or deleted; manage them from Tools > Clipboard.", "Clipboard > Pinned", "pinned unlimited manage"),
                action("clipboard.clear.recent", "Clear Recent Clips", "Remove all unpinned Recent items and keep Pinned items untouched.", "Clipboard > Clear Recent", SettingAction.CLEAR_CLIPBOARD_RECENT, "clear recent"),
                action("clipboard.clear.pinned", "Clear Pinned Clips", "Remove all intentionally pinned clips after confirmation.", "Clipboard > Clear Pinned", SettingAction.CLEAR_CLIPBOARD_PINNED, "clear pinned"),
                action("clipboard.clear.all", "Clear All Clipboard Data", "Remove both Recent and Pinned clipboard history only.", "Clipboard > Clear All", SettingAction.CLEAR_CLIPBOARD_ALL, "clear all clipboard"),
                info("clipboard.sensitive", "Sensitive Content Protection", "Always on: password, PIN, OTP, authentication, token-like, and Android-sensitive clips are not stored.", "Clipboard > Privacy", "sensitive password pin otp token privacy"),
            ),
        ),
        SettingsCategory(
            id = "learning",
            icon = "◇",
            title = "Dictionary & Personal Learning",
            description = "Private vocabulary, learned words, and sensitive-field protection",
            entries = listOf(
                toggle("learning.personal", "Personalized suggestions", "Learn only meaningful commits in non-sensitive fields.", "Dictionary & Personal Learning > Control", ToggleKey.PERSONALIZED_SUGGESTIONS, "learn words"),
                action("learning.clear", "Clear learned words", "Remove private vocabulary and ranking data from this device.", "Dictionary & Personal Learning > Privacy", SettingAction.CLEAR_PERSONAL_DATA, "clear personal typing data"),
                info("learning.roman", "Personal Roman Urdu preferences", "Repeated spelling variants can rank naturally without forcing one spelling.", "Dictionary & Personal Learning > Roman Urdu", "mjhy mujhe nhi nahi"),
                info("learning.protection", "Sensitive-field protection", "Password, PIN, authentication, and no-learning fields are excluded.", "Personal Learning > Privacy", "privacy password pin otp"),
            ),
        ),
        SettingsCategory(
            id = "emoji",
            icon = "☺",
            title = "Emojis",
            description = "Emoji panel, search, recent use, and Unicode-safe insertion",
            entries = listOf(
                info("emoji.panel", "Emoji panel", "Open Emoji from Tools without closing the keyboard.", "Emojis > Panel", "emoji"),
                info("emoji.recent", "Recent and frequent emoji", "Recent and frequency data are kept locally with bounded storage.", "Emojis > Usage", "recent frequent"),
                info("emoji.unicode", "Unicode-safe emoji editing", "ZWJ sequences, flags, and skin-tone sequences are preserved on insertion/deletion.", "Emojis > Unicode", "skin tone flags ZWJ"),
            ),
        ),
        SettingsCategory(
            id = "themes",
            icon = "◈",
            title = "Themes & Appearance",
            description = "Keyboard colors, borders, gradients, and custom themes",
            entries = listOf(
                action("themes.editor", "Themes and appearance", "Open the theme editor with Apply and Cancel support.", "Themes & Appearance > Theme editor", SettingAction.OPEN_THEMES, "theme colour gradient image"),
                info("themes.current", "Current theme", "The active theme is ${activeTheme.name}.", "Themes & Appearance > Current theme", "solid white black neon"),
                info("themes.keys", "Bordered or borderless keys", "Visual key style changes do not alter touch or glide geometry.", "Themes & Appearance > Key style", "border borderless"),
            ),
        ),
        SettingsCategory(
            id = "toolbar",
            icon = "☷",
            title = "Toolbar & Tools",
            description = "Toolbar order, fixed controls, and the in-keyboard tools panel",
            entries = listOf(
                info("toolbar.reorder", "Toolbar customization", "Long-press a toolbar item to reorder it, or long-press a Tools item and choose Add to Toolbar.", "Toolbar & Tools > Reorder and add", "reorder toolbar add to toolbar customize"),
                action("toolbar.reset", "Reset toolbar layout", "Restore the approved default toolbar order.", "Toolbar & Tools > Layout", SettingAction.RESET_TOOLBAR, "reset"),
                info("toolbar.tools", "Tools panel", "Tools opens inside the fixed IME height and can be closed with the left X control.", "Toolbar & Tools > Tools panel", "tools panel close"),
                info("toolbar.mic", "Microphone button", "The microphone remains fixed on the right and opens Voice inside the IME.", "Toolbar & Tools > Microphone", "mic"),
                info("toolbar.search", "Search settings shortcut", "The Tools search action opens this screen with Search focused.", "Toolbar & Tools > Search", "search settings"),
            ),
        ),
        SettingsCategory(
            id = "resize",
            icon = "↕",
            title = "Resize & Layout",
            description = "Safe keyboard dimensions, position, and protected bottom space",
            entries = listOf(
                info("resize.open", "Resize keyboard", "Use Tools > Resize to adjust the keyboard manually; panels never resize it automatically.", "Resize & Layout > Resize", "resize keyboard size"),
                info("resize.current", "Current size", "Saved keyboard height: ${settings.keyboardHeightDp()}dp.", "Resize & Layout > Current size", "height minimum maximum"),
                info("resize.safe", "Bottom safe space", "The reference bottom region and system navigation inset are accounted for once.", "Resize & Layout > Safe area", "safe space navigation"),
            ),
        ),
        SettingsCategory(
            id = "ai",
            icon = "✧",
            title = "AI Tools",
            description = "Reserved extension points for future writing tools",
            entries = listOf(
                unavailable("ai.tools", "AI writing tools", "AI writing tools are unavailable until an approved provider is connected.", "AI Tools > Writing", "ai grammar rewrite humanise tone"),
                unavailable("ai.provider", "AI provider and model", "Provider and model selection will appear when an approved provider is connected.", "AI Tools > Provider", "provider model ChatGPT connection"),
                unavailable("ai.privacy", "AI data behavior", "AI writing is not connected; normal typing remains on-device.", "AI Tools > Privacy", "network api privacy"),
            ),
        ),
        SettingsCategory(
            id = "privacy",
            icon = "□",
            title = "Privacy & Security",
            description = "Local processing, sensitive fields, and personal data controls",
            entries = listOf(
                info("privacy.sensitive", "Sensitive-field protection", "Password, PIN, OTP, authentication, and no-personalized-learning fields are protected.", "Privacy & Security > Sensitive fields", "password pin otp authentication"),
                info("privacy.learning", "Personalized learning", "Use Dictionary & Personal Learning for the single persistent learning control; sensitive fields remain protected.", "Privacy & Security > Learning", "learning personal data"),
                info("privacy.voice", "Voice processing", "Audio is processed only for the active voice session and is not stored by default.", "Privacy & Security > Voice", "recording audio"),
                info("privacy.network", "Network behavior", "Normal typing and local prediction do not require network access.", "Privacy & Security > Network", "offline network cloud"),
            ),
        ),
        SettingsCategory(
            id = "accessibility",
            icon = "◉",
            title = "Accessibility",
            description = "System accessibility support and readable keyboard behavior",
            entries = listOf(
                info("accessibility.talkback", "TalkBack support", "Keyboard controls expose descriptive actions where supported; TalkBack is managed by Android.", "Accessibility > TalkBack", "talkback screen reader content description"),
                unavailable("accessibility.motion", "Reduced motion", "Animation controls are not available separately.", "Accessibility > Motion", "reduced motion animation"),
                unavailable("accessibility.labels", "Larger labels", "Use Font & Emoji Size to adjust supported keyboard text.", "Accessibility > Text", "larger labels text size"),
                unavailable("accessibility.contrast", "High contrast", "Theme contrast is applied automatically.", "Accessibility > Contrast", "high contrast"),
            ),
        ),
        SettingsCategory(
            id = "about",
            icon = "ⓘ",
            title = "About",
            description = "Build information and keyboard architecture",
            entries = listOf(
                info("about.version", "AI Keyboard", "Version ${appVersion()}.", "About > Version", "version build"),
                info("about.build", "Build number", "Installed build ${appBuild()}.", "About > Build", "build number version code"),
                info("about.engine", "Keyboard technology", "Typing, languages, suggestions, and keyboard tools are available here.", "About > Engine", "ime input engine"),
            ),
        ),
        SettingsCategory(
            id = "help",
            icon = "?",
            title = "Help & Feedback",
            description = "Setup guidance and troubleshooting paths",
            entries = listOf(
                action("help.setup", "Keyboard setup help", "Open Android keyboard settings to enable or select AI Keyboard.", "Help & Feedback > Setup", SettingAction.OPEN_KEYBOARD_SETUP, "enable keyboard"),
                info("help.voice", "Voice troubleshooting", "Check microphone permission and the selected speech language when voice cannot start.", "Help & Feedback > Voice", "voice error"),
                info("help.feedback", "Report an issue", "Use the project repository to report reproducible keyboard issues.", "Help & Feedback > Feedback", "feedback report issue"),
            ),
        ),
    )

    private fun info(id: String, title: String, description: String, path: String, keywords: String) =
        SettingEntry(id, title, description, path, keywords, "i", EntryType.INFO, true)

    private fun unavailable(id: String, title: String, description: String, path: String, keywords: String) =
        SettingEntry(id, title, description, path, keywords, "•", EntryType.PLACEHOLDER, false)

    private fun action(id: String, title: String, description: String, path: String, action: SettingAction, keywords: String) =
        SettingEntry(id, title, description, path, keywords, "›", EntryType.ACTION, true, action = action)

    private fun toggle(id: String, title: String, description: String, path: String, toggle: ToggleKey, keywords: String) =
        SettingEntry(id, title, description, path, keywords, "●", EntryType.TOGGLE, true, toggle = toggle)

    private fun appVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "development"
    }.getOrDefault("development")

    private fun appBuild(): String = runCatching {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
    }.getOrDefault("development")

    companion object {
        const val EXTRA_FOCUS_SEARCH = "focus_settings_search"
        const val APPEARANCE_CONTROLS_ID = "appearance_controls"
        const val KEY_BEHAVIOR_CONTROLS_ID = "key_behavior_controls"
        const val SIZE_LEVEL_COUNT = 7
    }

    private data class SettingsCategory(
        val id: String,
        val icon: String,
        val title: String,
        val description: String,
        val entries: List<SettingEntry>,
    )

    private data class SettingEntry(
        val id: String,
        val title: String,
        val description: String,
        val path: String,
        val keywords: String,
        val icon: String,
        val type: EntryType,
        val available: Boolean,
        val action: SettingAction? = null,
        val toggle: ToggleKey? = null,
    )

    private enum class EntryType { INFO, ACTION, TOGGLE, PLACEHOLDER }
    private enum class ToggleKey { PERSONALIZED_SUGGESTIONS, GLIDE_TYPING, SHOW_LANGUAGE_SWITCH, CLIPBOARD_HISTORY, NUMBER_ROW, ALTERNATE_CHARACTERS }
    private enum class ClipboardClearScope { RECENT, PINNED, ALL }
    private enum class SettingAction {
        OPEN_THEMES,
        OPEN_APPEARANCE_CONTROLS,
        OPEN_KEY_BEHAVIOR_CONTROLS,
        RESET_TOOLBAR,
        CLEAR_PERSONAL_DATA,
        CLEAR_CLIPBOARD_RECENT,
        CLEAR_CLIPBOARD_PINNED,
        CLEAR_CLIPBOARD_ALL,
        OPEN_KEYBOARD_SETUP,
    }
}

private enum class SettingsIconKind {
    BACK,
    SEARCH,
    LANGUAGES,
    PREFERENCES,
    CORRECTIONS,
    GLIDE,
    VOICE,
    CLIPBOARD,
    DICTIONARY,
    EMOJI,
    THEMES,
    TOOLBAR,
    RESIZE,
    AI,
    PRIVACY,
    ACCESSIBILITY,
    ABOUT,
    HELP,
    INFO,
}

private class SettingsIconView(
    context: Context,
    private val kind: SettingsIconKind,
    private val showCircle: Boolean,
) : View(context) {
    var iconColor: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }
    var circleColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val scale = side / 40f
        val offsetX = (width - side) / 2f
        val offsetY = (height - side) / 2f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (showCircle) {
            paint.style = Paint.Style.FILL
            paint.color = circleColor
            canvas.drawCircle(20f, 20f, 19f, paint)
            paint.style = Paint.Style.STROKE
            paint.color = iconColor
        }

        when (kind) {
            SettingsIconKind.BACK -> {
                line(canvas, paint, 18f, 12f, 10f, 20f)
                line(canvas, paint, 10f, 20f, 18f, 28f)
                line(canvas, paint, 10f, 20f, 31f, 20f)
            }
            SettingsIconKind.SEARCH -> {
                canvas.drawCircle(17f, 17f, 7f, paint)
                line(canvas, paint, 22f, 22f, 29f, 29f)
            }
            SettingsIconKind.LANGUAGES -> {
                canvas.drawCircle(20f, 20f, 11f, paint)
                line(canvas, paint, 9f, 20f, 31f, 20f)
                canvas.drawOval(RectF(14f, 9f, 26f, 31f), paint)
                line(canvas, paint, 10f, 15f, 30f, 15f)
                line(canvas, paint, 10f, 25f, 30f, 25f)
            }
            SettingsIconKind.PREFERENCES -> {
                line(canvas, paint, 9f, 12f, 31f, 12f)
                line(canvas, paint, 9f, 20f, 31f, 20f)
                line(canvas, paint, 9f, 28f, 31f, 28f)
                dot(canvas, paint, 16f, 12f)
                dot(canvas, paint, 25f, 20f)
                dot(canvas, paint, 13f, 28f)
            }
            SettingsIconKind.CORRECTIONS -> {
                line(canvas, paint, 20f, 8f, 20f, 32f)
                line(canvas, paint, 8f, 20f, 32f, 20f)
                line(canvas, paint, 12f, 12f, 28f, 28f)
                line(canvas, paint, 28f, 12f, 12f, 28f)
                canvas.drawCircle(20f, 20f, 3f, paint)
            }
            SettingsIconKind.GLIDE -> {
                val path = Path().apply {
                    moveTo(9f, 27f)
                    cubicTo(14f, 23f, 16f, 13f, 23f, 12f)
                    cubicTo(27f, 11f, 29f, 14f, 31f, 10f)
                }
                canvas.drawPath(path, paint)
                dot(canvas, paint, 9f, 27f)
                dot(canvas, paint, 23f, 12f)
                dot(canvas, paint, 31f, 10f)
            }
            SettingsIconKind.VOICE -> {
                canvas.drawRoundRect(RectF(15f, 8f, 25f, 24f), 5f, 5f, paint)
                val path = Path().apply {
                    moveTo(10f, 20f)
                    cubicTo(10f, 30f, 30f, 30f, 30f, 20f)
                }
                canvas.drawPath(path, paint)
                line(canvas, paint, 20f, 30f, 20f, 34f)
                line(canvas, paint, 14f, 34f, 26f, 34f)
            }
            SettingsIconKind.CLIPBOARD -> {
                canvas.drawRoundRect(RectF(11f, 10f, 29f, 33f), 2f, 2f, paint)
                canvas.drawRoundRect(RectF(16f, 7f, 24f, 13f), 2f, 2f, paint)
                line(canvas, paint, 15f, 18f, 25f, 18f)
                line(canvas, paint, 15f, 23f, 25f, 23f)
                line(canvas, paint, 15f, 28f, 22f, 28f)
            }
            SettingsIconKind.DICTIONARY -> {
                val path = Path().apply {
                    moveTo(9f, 10f)
                    lineTo(19f, 12f)
                    lineTo(19f, 31f)
                    lineTo(9f, 29f)
                    close()
                    moveTo(31f, 10f)
                    lineTo(21f, 12f)
                    lineTo(21f, 31f)
                    lineTo(31f, 29f)
                    close()
                }
                canvas.drawPath(path, paint)
                line(canvas, paint, 12f, 16f, 17f, 17f)
                line(canvas, paint, 23f, 17f, 28f, 16f)
            }
            SettingsIconKind.EMOJI -> {
                canvas.drawCircle(20f, 20f, 11f, paint)
                dot(canvas, paint, 16f, 17f)
                dot(canvas, paint, 24f, 17f)
                val path = Path().apply {
                    moveTo(14f, 23f)
                    cubicTo(17f, 28f, 23f, 28f, 26f, 23f)
                }
                canvas.drawPath(path, paint)
            }
            SettingsIconKind.THEMES -> {
                val path = Path().apply {
                    moveTo(20f, 9f)
                    cubicTo(11f, 9f, 8f, 15f, 9f, 22f)
                    cubicTo(10f, 30f, 17f, 32f, 20f, 28f)
                    cubicTo(22f, 25f, 23f, 28f, 27f, 27f)
                    cubicTo(32f, 26f, 32f, 18f, 28f, 14f)
                    cubicTo(26f, 11f, 23f, 9f, 20f, 9f)
                    close()
                }
                canvas.drawPath(path, paint)
                dot(canvas, paint, 15f, 16f)
                dot(canvas, paint, 21f, 14f)
                dot(canvas, paint, 26f, 18f)
            }
            SettingsIconKind.TOOLBAR -> {
                canvas.drawRoundRect(RectF(9f, 11f, 31f, 29f), 3f, 3f, paint)
                canvas.drawRoundRect(RectF(13f, 15f, 18f, 20f), 1f, 1f, paint)
                canvas.drawRoundRect(RectF(22f, 15f, 27f, 20f), 1f, 1f, paint)
                line(canvas, paint, 13f, 24f, 27f, 24f)
            }
            SettingsIconKind.RESIZE -> {
                line(canvas, paint, 10f, 16f, 10f, 10f)
                line(canvas, paint, 10f, 10f, 16f, 10f)
                line(canvas, paint, 24f, 10f, 30f, 10f)
                line(canvas, paint, 30f, 10f, 30f, 16f)
                line(canvas, paint, 10f, 24f, 10f, 30f)
                line(canvas, paint, 10f, 30f, 16f, 30f)
                line(canvas, paint, 24f, 30f, 30f, 30f)
                line(canvas, paint, 30f, 30f, 30f, 24f)
            }
            SettingsIconKind.AI -> {
                line(canvas, paint, 20f, 8f, 20f, 32f)
                line(canvas, paint, 8f, 20f, 32f, 20f)
                line(canvas, paint, 12f, 12f, 28f, 28f)
                line(canvas, paint, 28f, 12f, 12f, 28f)
            }
            SettingsIconKind.PRIVACY -> {
                val path = Path().apply {
                    moveTo(20f, 8f)
                    lineTo(30f, 12f)
                    lineTo(28f, 24f)
                    cubicTo(27f, 29f, 23f, 32f, 20f, 34f)
                    cubicTo(17f, 32f, 13f, 29f, 12f, 24f)
                    lineTo(10f, 12f)
                    close()
                }
                canvas.drawPath(path, paint)
                line(canvas, paint, 16f, 21f, 19f, 24f)
                line(canvas, paint, 19f, 24f, 25f, 17f)
            }
            SettingsIconKind.ACCESSIBILITY -> {
                canvas.drawCircle(20f, 10f, 3f, paint)
                line(canvas, paint, 10f, 16f, 30f, 16f)
                line(canvas, paint, 20f, 16f, 20f, 29f)
                line(canvas, paint, 20f, 21f, 13f, 29f)
                line(canvas, paint, 20f, 21f, 27f, 29f)
            }
            SettingsIconKind.ABOUT, SettingsIconKind.INFO -> {
                canvas.drawCircle(20f, 20f, 11f, paint)
                dot(canvas, paint, 20f, 15f)
                line(canvas, paint, 20f, 19f, 20f, 27f)
            }
            SettingsIconKind.HELP -> {
                canvas.drawCircle(20f, 20f, 11f, paint)
                val path = Path().apply {
                    moveTo(16f, 17f)
                    cubicTo(16f, 12f, 25f, 12f, 25f, 17f)
                    cubicTo(25f, 20f, 21f, 20f, 21f, 24f)
                }
                canvas.drawPath(path, paint)
                dot(canvas, paint, 21f, 28f)
            }
        }
        canvas.restore()
    }

    private fun line(canvas: Canvas, paint: Paint, startX: Float, startY: Float, endX: Float, endY: Float) {
        canvas.drawLine(startX, startY, endX, endY, paint)
    }

    private fun dot(canvas: Canvas, paint: Paint, x: Float, y: Float) {
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, 1.8f, paint)
        paint.style = oldStyle
    }
}

private class SettingsChevronView(context: Context) : View(context) {
    var lineColor: Int = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val scale = side / 40f
        canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(16f, 11f, 24f, 20f, paint)
        canvas.drawLine(24f, 20f, 16f, 29f, paint)
        canvas.restore()
    }
}

