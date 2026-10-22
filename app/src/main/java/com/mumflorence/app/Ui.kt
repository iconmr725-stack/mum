package com.mumflorence.app

import android.app.Activity
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.AdapterView

/** Tiny helper to build settings screens in code. Controls added with a key are saved by save(). */
class Form(val a: Activity, val store: Store) {
    private val pad = (14 * a.resources.displayMetrics.density).toInt()
    val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
    private val savers = ArrayList<() -> Unit>()

    fun title(t: String): TextView = TextView(a).apply {
        text = t; textSize = 20f; setPadding(0, pad, 0, pad / 3); root.addView(this)
    }

    fun text(t: String, size: Float = 15f): TextView = TextView(a).apply {
        text = t; textSize = size; setPadding(0, pad / 4, 0, pad / 4); root.addView(this)
    }

    fun button(label: String, onClick: () -> Unit): Button = Button(a).apply {
        text = label; setOnClickListener { onClick() }; root.addView(this)
    }

    /** A text box with no key (you read it yourself). */
    fun plainEdit(label: String, hint: String = "", value: String = ""): EditText {
        text(label)
        return EditText(a).apply { this.hint = hint; setText(value); root.addView(this) }
    }

    fun plainChoice(label: String, options: List<String>): Spinner {
        text(label)
        return Spinner(a).apply {
            adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, options)
            root.addView(this)
        }
    }

    fun edit(key: String, label: String, default: String = "", hint: String = "", secret: Boolean = false): EditText {
        text(label)
        val e = EditText(a).apply {
            this.hint = hint
            setText(store.get(key, default))
            if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            root.addView(this)
        }
        savers.add { store.put(key, e.text.toString().trim()) }
        return e
    }

    fun toggle(key: String, label: String, default: Boolean = true) {
        val s = Switch(a).apply { text = label; isChecked = store.on(key, default); setPadding(0, pad / 2, 0, pad / 2); root.addView(this) }
        savers.add { store.put(key, if (s.isChecked) "on" else "off") }
    }

    fun seek(key: String, label: String, min: Int, max: Int, default: Int) {
        val v = (store.get(key, default.toString()).toIntOrNull() ?: default).coerceIn(min, max)
        val tv = text("$label: $v")
        val sb = SeekBar(a).apply {
            this.max = max - min
            progress = v - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { tv.text = "$label: ${p + min}" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            root.addView(this)
        }
        savers.add { store.put(key, (sb.progress + min).toString()) }
    }

    fun choice(key: String, label: String, options: List<String>, default: String) {
        text(label)
        val sp = Spinner(a).apply {
            adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(options.indexOf(store.get(key, default)).coerceAtLeast(0))
            root.addView(this)
        }
        savers.add { store.put(key, sp.selectedItem.toString()) }
    }

    fun onSave(f: () -> Unit) { savers.add(f) }
    fun save() { savers.forEach { it() } }
    fun scroll(): View = ScrollView(a).apply { addView(root) }
}
