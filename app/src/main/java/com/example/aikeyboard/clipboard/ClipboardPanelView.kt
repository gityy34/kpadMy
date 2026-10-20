package com.example.aikeyboard.clipboard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.aikeyboard.theme.KeyboardTheme
import java.util.concurrent.Executors

/**
 * Fixed-height, in-IME clipboard panel. It uses ordinary Android views so cards and actions
 * remain accessible, and it pages pinned rows without loading the whole collection at once.
 */
class ClipboardPanelView(
    context: Context,
    private val store: ClipboardHistoryStore,
    private val onPaste: (String) -> Unit,
    private val onBack: () -> Unit,
) : LinearLayout(context) {
    private enum class Tab { RECENT, PINNED }
    private enum class Action { PIN, UNPIN, DELETE }

    private val density = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val header = LinearLayout(context)
    private val title = TextView(context)
    private val recentTab = TextView(context)
    private val pinnedTab = TextView(context)
    private val grid = GridLayout(context)
    private val scroll = ScrollView(context)
    private val actionBar = LinearLayout(context)
    private var theme = KeyboardTheme.SOLID_WHITE
    private var tab = Tab.RECENT
    private var items = emptyList<ClipboardItem>()
    private var pinnedItems = mutableListOf<ClipboardItem>()
    private var pinnedOffset = 0
    private var canLoadMorePinned = false
    private var loading = false
    private var selectedItem: ClipboardItem? = null

    init {
        orientation = VERTICAL
        clipChildren = true
        clipToPadding = true
        grid.columnCount = 2
        grid.useDefaultMargins = false
        buildHeader()
        scroll.isFillViewport = true
        scroll.clipToPadding = false
        scroll.setPadding(dp(4), dp(3), dp(4), dp(3))
        scroll.addView(
            grid,
            ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            scroll,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        buildActionBar()
        applyTheme(theme)
        selectTab(Tab.RECENT)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (
                tab == Tab.PINNED &&
                canLoadMorePinned &&
                !loading &&
                scrollY + scroll.height >= scroll.getChildAt(0).height - dp(80)
            ) {
                loadPinnedPage()
            }
        }
    }

    fun applyTheme(value: KeyboardTheme) {
        theme = value
        setBackgroundColor(value.backgroundColor)
        title.setTextColor(value.textColor)
        recentTab.setTextColor(value.textColor)
        pinnedTab.setTextColor(value.textColor)
        updateTabBackgrounds()
        updateActionBarTheme()
        render(items)
    }

    fun refreshFromStore() {
        if (tab == Tab.RECENT) {
            loadRecent()
        } else {
            pinnedOffset = 0
            pinnedItems = mutableListOf()
            loadPinnedPage(reset = true)
        }
    }

    fun dispose() {
        worker.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun buildHeader() {
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(4), dp(3), dp(4), dp(3))

        val back = TextView(context).apply {
            text = "‹"
            textSize = 30f
            gravity = Gravity.CENTER
            contentDescription = "Back to keyboard"
            setOnClickListener { onBack() }
        }
        header.addView(back, LayoutParams(dp(40), dp(42)))

        title.apply {
            text = "Clipboard"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(title, LayoutParams(0, dp(42), 1f))

        recentTab.apply {
            text = "Recent"
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Recent clipboard items"
            setOnClickListener { selectTab(Tab.RECENT) }
        }
        header.addView(recentTab, LayoutParams(dp(68), dp(34)))

        pinnedTab.apply {
            text = "Pinned"
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Pinned clipboard items"
            setOnClickListener { selectTab(Tab.PINNED) }
        }
        header.addView(pinnedTab, LayoutParams(dp(68), dp(34)))

        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun buildActionBar() {
        actionBar.orientation = HORIZONTAL
        actionBar.gravity = Gravity.CENTER_VERTICAL
        actionBar.setPadding(dp(6), dp(3), dp(6), dp(5))
        val actionLabel = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
        }
        actionBar.addView(actionLabel, LayoutParams(0, dp(38), 1f))

        val primary = actionButton("Pin") {
            val action = if (selectedItem?.isPinned == true) Action.UNPIN else Action.PIN
            performSelected(action)
        }
        primary.tag = "primary"
        actionBar.addView(primary, LayoutParams(dp(70), dp(36)))

        val delete = actionButton("Delete") { performSelected(Action.DELETE) }
        delete.contentDescription = "Delete clipboard item"
        actionBar.addView(delete, LayoutParams(dp(78), dp(36)))

        val cancel = actionButton("Cancel") {
            selectedItem = null
            actionBar.visibility = View.GONE
        }
        cancel.contentDescription = "Cancel clipboard action"
        actionBar.addView(cancel, LayoutParams(dp(78), dp(36)))

        actionBar.tag = actionLabel
        actionBar.visibility = View.GONE
        addView(actionBar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
    }

    private fun selectTab(next: Tab) {
        loading = false
        tab = next
        selectedItem = null
        actionBar.visibility = View.GONE
        updateTabBackgrounds()
        if (next == Tab.RECENT) loadRecent() else {
            pinnedOffset = 0
            pinnedItems = mutableListOf()
            loadPinnedPage(reset = true)
        }
    }

    private fun loadRecent() {
        if (loading) return
        loading = true
        showLoading()
        worker.execute {
            val result = runCatching { store.recent() }.getOrDefault(emptyList())
            mainHandler.post {
                loading = false
                if (tab != Tab.RECENT) return@post
                items = result
                render(items)
            }
        }
    }

    private fun loadPinnedPage(reset: Boolean = false) {
        if (loading) return
        loading = true
        if (reset) showLoading()
        val offset = pinnedOffset
        worker.execute {
            val page = runCatching {
                store.pinned(offset = offset, limit = ClipboardHistoryStore.DEFAULT_PINNED_PAGE_SIZE)
            }.getOrDefault(emptyList())
            mainHandler.post {
                loading = false
                if (tab != Tab.PINNED) return@post
                if (reset) pinnedItems.clear()
                pinnedItems.addAll(page)
                pinnedOffset += page.size
                canLoadMorePinned = page.size == ClipboardHistoryStore.DEFAULT_PINNED_PAGE_SIZE
                items = pinnedItems.toList()
                render(items)
            }
        }
    }

    private fun performSelected(action: Action) {
        val item = selectedItem ?: return
        selectedItem = null
        actionBar.visibility = View.GONE
        worker.execute {
            when (action) {
                Action.PIN -> store.pin(item.id)
                Action.UNPIN -> store.unpin(item.id)
                Action.DELETE -> store.delete(item.id)
            }
            mainHandler.post { refreshFromStore() }
        }
    }

    private fun render(values: List<ClipboardItem>) {
        if (!isAttachedToWindow && childCount == 0) return
        grid.removeAllViews()
        if (values.isEmpty()) {
            val empty = TextView(context).apply {
                text = if (tab == Tab.RECENT) {
                    "No recent clips"
                } else {
                    "No pinned clips"
                }
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(theme.textColor)
                contentDescription = text
            }
            grid.addView(empty, GridLayout.LayoutParams().apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = dp(72)
                columnSpec = GridLayout.spec(0, 2)
                setMargins(dp(6), dp(12), dp(6), dp(12))
            })
            return
        }

        values.forEachIndexed { index, item ->
            val card = TextView(context).apply {
                text = item.preview()
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(theme.textColor)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                isLongClickable = true
                contentDescription = "Clipboard item: ${item.preview(220)}"
                background = rounded(theme.keyColor)
                setOnClickListener {
                    onPaste(item.fullText)
                    worker.execute { store.markUsed(item.id) }
                }
                setOnLongClickListener {
                    selectedItem = item
                    showActionBar(item)
                    true
                }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 2),
                GridLayout.spec(index % 2, 1f),
            ).apply {
                width = 0
                height = dp(66)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(card, params)
        }
    }

    private fun showActionBar(item: ClipboardItem) {
        val label = actionBar.tag as? TextView ?: return
        label.text = if (item.isPinned) "Pinned clip selected" else "Recent clip selected"
        val primary = actionBar.findViewWithTag<TextView>("primary")
        primary?.text = if (item.isPinned) "Unpin" else "Pin"
        primary?.contentDescription = if (item.isPinned) "Unpin clipboard item" else "Pin clipboard item"
        actionBar.visibility = View.VISIBLE
        actionBar.announceForAccessibility(label.text)
    }

    private fun showLoading() {
        grid.removeAllViews()
        val loadingLabel = TextView(context).apply {
            text = "Loading clipboard…"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(theme.textColor)
        }
        grid.addView(loadingLabel, GridLayout.LayoutParams().apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = dp(72)
            columnSpec = GridLayout.spec(0, 2)
        })
    }

    private fun updateTabBackgrounds() {
        recentTab.background = rounded(
            if (tab == Tab.RECENT) theme.pressedKeyColor else theme.keyColor,
        )
        pinnedTab.background = rounded(
            if (tab == Tab.PINNED) theme.pressedKeyColor else theme.keyColor,
        )
    }

    private fun updateActionBarTheme() {
        actionBar.setBackgroundColor(theme.backgroundColor)
        actionBar.findViewWithTag<TextView>("primary")?.background = rounded(theme.keyColor)
        actionBar.getChildAt(1)?.setBackgroundDrawable(rounded(theme.keyColor))
        actionBar.getChildAt(2)?.setBackgroundDrawable(rounded(theme.keyColor))
        actionBar.getChildAt(3)?.setBackgroundDrawable(rounded(theme.keyColor))
        for (index in 0 until actionBar.childCount) {
            (actionBar.getChildAt(index) as? TextView)?.setTextColor(theme.textColor)
        }
    }

    private fun actionButton(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rounded(theme.keyColor)
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(10).toFloat()
        setColor(color)
        setStroke(dp(1), theme.borderColor)
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
