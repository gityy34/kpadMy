package com.example.aikeyboard.theme

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class ThemeActivity : Activity() {
    private lateinit var store: ThemeStore
    private lateinit var preview: ThemePreviewView
    private lateinit var customName: EditText
    private var original = KeyboardTheme.SOLID_WHITE
    private var selected = KeyboardTheme.SOLID_WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ThemeStore(this)
        original = store.activeTheme()
        selected = original
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(20, 20, 20, 20)
        }
        root.addView(TextView(this).apply { text = "Themes"; textSize = 24f })
        preview = ThemePreviewView(this)
        preview.setTheme(selected)
        root.addView(preview, LinearLayout.LayoutParams(-1, 140))
        root.addView(TextView(this).apply { text = "Default"; textSize = 18f; setPadding(0, 18, 0, 6) })
        KeyboardTheme.BUILT_INS.forEach { theme ->
            root.addView(Button(this).apply {
                text = theme.name
                setOnClickListener { select(theme) }
            })
        }
        root.addView(TextView(this).apply { text = "Key Style"; textSize = 18f; setPadding(0, 18, 0, 4) })
        root.addView(Switch(this).apply {
            text = "Borderless keys"
            isChecked = selected.keyStyle == KeyStyle.BORDERLESS
            setOnCheckedChangeListener { _, borderless ->
                selected = selected.copy(keyStyle = if (borderless) KeyStyle.BORDERLESS else KeyStyle.BORDERED)
                preview.setTheme(selected)
            }
        })
        root.addView(TextView(this).apply { text = "Colours and Gradients"; textSize = 18f; setPadding(0, 18, 0, 4) })
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Light" to KeyboardTheme.SOLID_WHITE, "Dark" to KeyboardTheme.SOLID_BLACK, "Neon" to KeyboardTheme.NEON).forEach { (label, theme) ->
            presets.addView(Button(this).apply {
                text = label
                setOnClickListener { select(theme) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(presets)
        root.addView(Button(this).apply {
            text = "Choose background image"
            setOnClickListener { chooseImage() }
        })
        root.addView(TextView(this).apply { text = "My Themes"; textSize = 18f; setPadding(0, 18, 0, 4) })
        customName = EditText(this).apply { hint = "Custom theme name" }
        root.addView(customName)
        root.addView(Button(this).apply {
            text = "Save Custom Theme"
            setOnClickListener { saveCustom() }
        })
        store.customThemes().forEach { custom ->
            root.addView(Button(this).apply {
                text = "Delete ${custom.name}"
                setOnClickListener { confirmDelete(custom) }
            })
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Cancel"
            setOnClickListener { selected = original; finish() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply {
            text = "Apply"
            setOnClickListener { store.apply(selected); finish() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun select(theme: KeyboardTheme) {
        selected = theme.copy(keyStyle = selected.keyStyle, backgroundImageUri = selected.backgroundImageUri)
        preview.setTheme(selected)
    }

    private fun saveCustom() {
        val name = customName.text?.toString()?.trim().orEmpty().ifBlank { "My Theme" }
        selected = store.saveCustom(selected.copy(id = "custom_${name.lowercase().replace(" ", "_")}", name = name))
        preview.setTheme(selected)
        Toast.makeText(this, "Custom theme saved", Toast.LENGTH_SHORT).show()
    }

    private fun chooseImage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selected = selected.copy(backgroundImageUri = uri.toString())
            preview.setTheme(selected)
        }
    }

    private fun confirmDelete(theme: KeyboardTheme) {
        AlertDialog.Builder(this).setTitle("Delete ${theme.name}?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> store.deleteCustom(theme.id); recreate() }
            .show()
    }

    companion object { private const val IMAGE_REQUEST = 401 }
}
