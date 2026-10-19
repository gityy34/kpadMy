package com.example.aikeyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.aikeyboard.R
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
            renderCurrentPage()
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
            setPadding(dp(16), dp(10), dp(16), 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = TextView(this).apply {
            text = "‹"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(activeTheme.textColor)
            setPadding(0, 0, dp(10), 0)
            setOnClickListener {
                if (currentCategoryId != null) renderHome() else finish()
            }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(42), dp(52)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(label("AI Keyboard Settings", 22f, activeTheme.textColor))
        heading.addView(label("Configure typing, privacy, tools, and appearance", 12f, mutedColor()))
        header.addView(heading, LinearLayout.LayoutParams(0, dp(52), 1f))
        screenRoot.addView(header)

        search = EditText(this).apply {
            hint = getString(R.string.search_settings)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(activeTheme.textColor)
            setHintTextColor(mutedColor())
            setPadding(dp(16), 0, dp(16), 0)
            background = roundedSurface()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty().trim()
                if (query.isBlank()) {
                    renderCurrentPage()
                } else {
                    renderSearchResults(query)
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        screenRoot.addView(search, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52),
        ).apply { topMargin = dp(10); bottomMargin = dp(10) })

        bodyScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(18))
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        bodyScroll.addView(body, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
        screenRoot.addView(bodyScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        setContentView(screenRoot)
        applyThemeToScreen()
    }

    private fun renderCurrentPage() {
        val categoryId = currentCategoryId
        if (categoryId == null) renderHome() else renderCategory(categoryId)
    }

    private fun renderHome() {
        currentCategoryId = null
        renderedRows.clear()
        applyThemeToScreen()
        body.removeAllViews()
        addSectionHeader("Keyboard settings", "Choose a section or search every setting above.")
        categories.forEach { category ->
            val card = categoryCard(category)
            body.addView(card, cardParams())
        }
        bodyScroll.scrollTo(0, 0)
    }

    private fun renderCategory(categoryId: String, focusEntryId: String? = null) {
        val category = categories.firstOrNull { it.id == categoryId } ?: return
        currentCategoryId = category.id
        renderedRows.clear()
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
        body.removeAllViews()
        addSectionHeader("Search results", "Matching titles, descriptions, categories, and language names.")
        val normalized = query.lowercase(Locale.ROOT)
        val matches = categories.flatMap { category ->
            category.entries.map { entry ->
                category.id to entry.copy(path = "${category.title}  ›  ${entry.path}")
            }
        }.filter { entry ->
            listOf(entry.second.title, entry.second.description, entry.second.path, entry.second.keywords)
                .joinToString(" ")
                .lowercase(Locale.ROOT)
                .contains(normalized)
        }.distinctBy { it.second.id }

        if (matches.isEmpty()) {
            body.addView(label("No matching settings", 16f, mutedColor()).apply {
                setPadding(dp(12), dp(22), dp(12), dp(22))
            })
        } else {
            matches.forEach { (categoryId, entry) ->
                val row = settingRow(entry, showPath = true).apply {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openSearchResult(categoryId, entry.id) }
                }
                body.addView(row, rowParams())
            }
        }
    }

    private fun openSearchResult(categoryId: String, entryId: String) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(search.windowToken, 0)
        currentCategoryId = null
        search.setText("")
        renderCategory(categoryId, entryId)
    }

    private fun addSectionHeader(title: String, description: String) {
        body.addView(label(title, 18f, activeTheme.textColor).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(3))
        })
        body.addView(label(description, 13f, mutedColor()).apply {
            setPadding(dp(4), 0, dp(4), dp(12))
        })
    }

    private fun categoryCard(category: SettingsCategory): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = roundedSurface()
            isClickable = true
            isFocusable = true
            setOnClickListener { renderCategory(category.id) }
        }
        card.addView(label(category.icon, 23f, activeTheme.accentColor).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(42), dp(46)))
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        text.addView(label(category.title, 16f, activeTheme.textColor))
        text.addView(label(category.description, 12f, mutedColor()).apply {
            maxLines = 2
        })
        card.addView(text, LinearLayout.LayoutParams(0, dp(46), 1f))
        card.addView(label("›", 28f, activeTheme.accentColor).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(28), dp(46)))
        return card
    }

    private fun settingRow(entry: SettingEntry, showPath: Boolean = false): View {
        val enabled = entry.available
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(10), dp(10))
            background = roundedSurface()
            isClickable = enabled && entry.action != null
            isFocusable = isClickable
            alpha = if (enabled) 1f else 0.58f
        }
        val icon = label(entry.icon, 20f, if (enabled) activeTheme.accentColor else mutedColor())
        icon.gravity = Gravity.CENTER
        row.addView(icon, LinearLayout.LayoutParams(dp(38), dp(52)))

        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        text.addView(label(entry.title, 15f, activeTheme.textColor))
        text.addView(label(entry.description, 12f, mutedColor()).apply { maxLines = 2 })
        if (showPath) {
            text.addView(label(entry.path, 11f, activeTheme.accentColor).apply { maxLines = 1 })
        }
        row.addView(text, LinearLayout.LayoutParams(0, dp(if (showPath) 66 else 52), 1f))

        val toggleKey = entry.toggle
        val action = entry.action
        when {
            toggleKey != null -> {
                val toggle = Switch(this).apply {
                    isChecked = readToggle(toggleKey)
                    isEnabled = enabled
                    setOnCheckedChangeListener { _, checked -> writeToggle(toggleKey, checked) }
                }
                row.addView(toggle, LinearLayout.LayoutParams(dp(54), dp(52)))
                row.setOnClickListener { toggle.performClick() }
            }
            action != null && enabled -> {
                row.addView(label("›", 26f, activeTheme.accentColor).apply {
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(28), dp(52)))
                row.setOnClickListener { performAction(action) }
            }
            !enabled -> row.addView(label("Soon", 11f, mutedColor()).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(48), dp(52)))
            else -> row.addView(label("i", 16f, mutedColor()).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(28), dp(52)))
        }
        return row
    }

    private fun performAction(action: SettingAction?) {
        when (action) {
            SettingAction.OPEN_THEMES -> startActivity(Intent(this, ThemeActivity::class.java))
            SettingAction.RESET_TOOLBAR -> {
                settings.setToolbarOrder(KeyboardSettings.DEFAULT_TOOLBAR_ORDER)
                Toast.makeText(this, "Toolbar layout reset", Toast.LENGTH_SHORT).show()
                renderCurrentPage()
            }
            SettingAction.CLEAR_PERSONAL_DATA -> confirmClear()
            SettingAction.OPEN_KEYBOARD_SETUP -> startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
            null -> Unit
        }
    }

    private fun readToggle(toggle: ToggleKey): Boolean = when (toggle) {
        ToggleKey.PERSONALIZED_SUGGESTIONS -> settings.personalizedSuggestionsEnabled()
        ToggleKey.GLIDE_TYPING -> settings.glideTypingEnabled()
    }

    private fun writeToggle(toggle: ToggleKey, value: Boolean) {
        when (toggle) {
            ToggleKey.PERSONALIZED_SUGGESTIONS -> settings.setPersonalizedSuggestionsEnabled(value)
            ToggleKey.GLIDE_TYPING -> settings.setGlideTypingEnabled(value)
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_personal_data))
            .setMessage(getString(R.string.clear_personal_data_confirmation))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                PersonalVocabularyStore.clearPersisted(this)
                Toast.makeText(this, "Personal data cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun applyThemeToScreen() {
        activeTheme = themeStore.activeTheme()
        screenRoot.setBackgroundColor(activeTheme.backgroundColor)
        if (::search.isInitialized) {
            search.setTextColor(activeTheme.textColor)
            search.setHintTextColor(mutedColor())
            search.background = roundedSurface()
        }
    }

    private fun roundedSurface(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(14).toFloat()
        setColor(activeTheme.keyColor)
        setStroke(dp(1), activeTheme.borderColor)
    }

    private fun label(value: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = true
    }

    private fun mutedColor(): Int = Color.argb(
        170,
        Color.red(activeTheme.textColor),
        Color.green(activeTheme.textColor),
        Color.blue(activeTheme.textColor),
    )

    private fun cardParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(5)
        bottomMargin = dp(5)
    }

    private fun rowParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(4)
        bottomMargin = dp(4)
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
            ),
        ),
        SettingsCategory(
            id = "typing",
            icon = "⌨",
            title = "Typing & Layout",
            description = "Fast input, key behavior, punctuation, and editing",
            entries = listOf(
                info("typing.fast", "Fast key input", "Normal key commits stay independent from background work.", "Typing & Layout > Key press behavior", "key press behavior latency"),
                info("typing.shift", "Shift and Caps Lock", "One-shot Shift and Caps Lock are supported.", "Typing & Layout > Shift", "shift caps lock"),
                info("typing.symbols", "Numbers and symbols", "The keyboard includes punctuation and symbol layouts.", "Typing & Layout > Symbols", "numbers symbols punctuation"),
                info("typing.space", "Auto space", "Normal Space commits immediately; suggestion acceptance uses one safe space.", "Typing & Layout > Space", "auto space"),
                info("typing.repeat", "Backspace repeat", "Hold Backspace for controlled repeated deletion.", "Typing & Layout > Backspace", "key repeat backspace"),
                unavailable("typing.haptics", "Haptic feedback", "Device haptics are not configurable in this build.", "Typing & Layout > Feedback", "vibration sound key press"),
            ),
        ),
        SettingsCategory(
            id = "suggestions",
            icon = "✦",
            title = "Suggestions & Prediction",
            description = "Local English, Urdu, and Roman Urdu suggestions",
            entries = listOf(
                toggle("suggestions.personal", "Personalized suggestions", "Use private learned ranking when enabled.", "Suggestions > Personalization", ToggleKey.PERSONALIZED_SUGGESTIONS, "learning personal"),
                info("suggestions.next", "Next-word prediction", "After Space, the next-word context is requested asynchronously.", "Suggestions > Next word", "next word context"),
                info("suggestions.languages", "English, Urdu, and Roman Urdu prediction", "Language-aware local candidates remain available for mixed text.", "Suggestions > Languages", "english urdu roman"),
                info("suggestions.typed", "Keep the typed word", "Your original typed form remains available; suggestions do not silently replace it.", "Suggestions > User control", "typed word correction"),
                info("suggestions.size", "Suggestion text size", "Suggestions use readable sizing with bounded cells and ellipsis for long words.", "Suggestions > Appearance", "suggestion font text size"),
                info("suggestions.space", "Auto-space after accepted suggestion", "An accepted normal word receives exactly one safe following space.", "Suggestions > Space", "suggestion space"),
            ),
        ),
        SettingsCategory(
            id = "learning",
            icon = "◇",
            title = "Personal Learning",
            description = "Private vocabulary and ranking controls",
            entries = listOf(
                toggle("learning.personal", "Personalized suggestions", "Learn only meaningful commits in non-sensitive fields.", "Personal Learning > Control", ToggleKey.PERSONALIZED_SUGGESTIONS, "learn words"),
                action("learning.clear", "Clear learned words", "Remove private vocabulary and ranking data from this device.", "Personal Learning > Privacy", SettingAction.CLEAR_PERSONAL_DATA, "clear personal typing data"),
                info("learning.roman", "Personal Roman Urdu preferences", "Repeated spelling variants can rank naturally without forcing one spelling.", "Personal Learning > Roman Urdu", "mjhy mujhe nhi nahi"),
                info("learning.protection", "Sensitive-field protection", "Password, PIN, authentication, and no-learning fields are excluded.", "Personal Learning > Privacy", "privacy password pin otp"),
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
            id = "ai",
            icon = "✧",
            title = "AI Tools",
            description = "Reserved extension points for future writing tools",
            entries = listOf(
                unavailable("ai.tools", "AI writing tools", "Grammar, rewrite, tone, and assistant tools are not connected in this build.", "AI Tools > Writing", "ai grammar rewrite humanise tone"),
                unavailable("ai.provider", "AI provider and model", "Provider and model selection will appear when an approved provider is connected.", "AI Tools > Provider", "provider model ChatGPT connection"),
                unavailable("ai.privacy", "AI data behavior", "No AI network request is made by the current keyboard build.", "AI Tools > Privacy", "network api privacy"),
            ),
        ),
        SettingsCategory(
            id = "toolbar",
            icon = "☷",
            title = "Toolbar & Tools",
            description = "Toolbar order, fixed controls, and the in-keyboard tools panel",
            entries = listOf(
                info("toolbar.reorder", "Toolbar customization", "Long-press a reorderable toolbar item to change its order.", "Toolbar & Tools > Reorder", "reorder toolbar"),
                action("toolbar.reset", "Reset toolbar layout", "Restore the approved default toolbar order.", "Toolbar & Tools > Layout", SettingAction.RESET_TOOLBAR, "reset"),
                info("toolbar.tools", "Tools panel", "Tools opens inside the fixed IME height and can be closed with the left X control.", "Toolbar & Tools > Tools panel", "tools panel close"),
                info("toolbar.mic", "Microphone button", "The microphone remains fixed on the right and opens Voice inside the IME.", "Toolbar & Tools > Microphone", "mic"),
                info("toolbar.search", "Search settings shortcut", "The Tools search action opens this screen with Search focused.", "Toolbar & Tools > Search", "search settings"),
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
            id = "resize",
            icon = "↕",
            title = "Resize & Keyboard Size",
            description = "Safe keyboard dimensions and protected bottom space",
            entries = listOf(
                info("resize.open", "Resize keyboard", "Use Tools > Resize to adjust the keyboard manually; panels never resize it automatically.", "Resize & Keyboard Size > Resize", "resize keyboard size"),
                info("resize.current", "Current size", "Saved keyboard height: ${settings.keyboardHeightDp()}dp.", "Resize & Keyboard Size > Current size", "height minimum maximum"),
                info("resize.safe", "Bottom safe space", "The reference bottom region and system navigation inset are accounted for once.", "Resize & Keyboard Size > Safe area", "safe space navigation"),
            ),
        ),
        SettingsCategory(
            id = "emoji",
            icon = "☺",
            title = "Emoji",
            description = "Emoji panel, search, recent use, and Unicode-safe insertion",
            entries = listOf(
                info("emoji.panel", "Emoji panel", "Open Emoji from Tools without closing the keyboard.", "Emoji > Panel", "emoji"),
                info("emoji.recent", "Recent and frequent emoji", "Recent and frequency data are kept locally with bounded storage.", "Emoji > Usage", "recent frequent"),
                info("emoji.unicode", "Unicode-safe emoji editing", "ZWJ sequences, flags, and skin-tone sequences are preserved on insertion/deletion.", "Emoji > Unicode", "skin tone flags ZWJ"),
            ),
        ),
        SettingsCategory(
            id = "privacy",
            icon = "□",
            title = "Privacy & Security",
            description = "Local processing, sensitive fields, and personal data controls",
            entries = listOf(
                info("privacy.sensitive", "Sensitive-field protection", "Password, PIN, OTP, authentication, and no-personalized-learning fields are protected.", "Privacy & Security > Sensitive fields", "password pin otp authentication"),
                toggle("privacy.learning", "Personalized suggestions", "Turn private vocabulary learning and personal ranking on or off.", "Privacy & Security > Learning", ToggleKey.PERSONALIZED_SUGGESTIONS, "learning"),
                info("privacy.voice", "Voice processing", "Audio is processed only for the active voice session and is not stored by default.", "Privacy & Security > Voice", "recording audio"),
                info("privacy.network", "Network behavior", "Normal typing and local prediction do not require network access.", "Privacy & Security > Network", "offline network cloud"),
            ),
        ),
        SettingsCategory(
            id = "about",
            icon = "ⓘ",
            title = "About",
            description = "Build information and keyboard architecture",
            entries = listOf(
                info("about.version", "AI Keyboard", "Version ${appVersion()}.", "About > Version", "version build"),
                info("about.engine", "Keyboard engine", "InputMethodService with modular typing, composition, prediction, and UI boundaries.", "About > Engine", "ime input engine"),
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

    companion object {
        const val EXTRA_FOCUS_SEARCH = "focus_settings_search"
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
    private enum class ToggleKey { PERSONALIZED_SUGGESTIONS, GLIDE_TYPING }
    private enum class SettingAction { OPEN_THEMES, RESET_TOOLBAR, CLEAR_PERSONAL_DATA, OPEN_KEYBOARD_SETUP }
}
