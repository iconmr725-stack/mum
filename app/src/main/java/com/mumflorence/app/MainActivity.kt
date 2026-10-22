package com.mumflorence.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Home screen: status of everything, one-tap setup, and links to all settings. */
class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var status: TextView
    private lateinit var sleepBtn: Button
    private var wizard = false
    private val asked = HashSet<String>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        buildUi()
    }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun tv(t: String, size: Float) = TextView(this).apply { text = t; textSize = size; setPadding(0, pad / 2, 0, pad / 2) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        root.addView(tv(store.get("assistant_name", "Mum Florence"), 30f))
        status = tv("", 15f)
        root.addView(status)
        root.addView(btn("Set up everything") { wizard = true; asked.clear(); nextStep() })
        sleepBtn = btn("Sleep") {
            val s = FlorenceService.instance
            if (s == null) toast("She is not running yet. Tap Set up everything.")
            else { s.setSleeping(!s.isSleeping()); status.postDelayed({ refreshStatus() }, 500) }
        }
        root.addView(sleepBtn)
        root.addView(btn("Settings") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(btn("What she knows and our conversations") { startActivity(Intent(this, MemoryActivity::class.java)) })
        root.addView(btn("My own commands") { startActivity(Intent(this, CommandsActivity::class.java)) })
        root.addView(btn("Voices that can command her") { startActivity(Intent(this, VoicesActivity::class.java)) })
        root.addView(tv(
            "Say her name any time. Try: \"call John\", \"text Mary saying I am coming\", \"open WhatsApp\", " +
                "\"read my notifications\", \"take a picture\", \"search for football news\", \"scroll down\", " +
                "\"remember my name is ...\", \"go to sleep\", or \"take control and open YouTube and play music\". " +
                "Say \"stop\" any time to make her stop.\n\n" +
                "If a switch is greyed out (Android 13+): Settings > Apps > Mum Florence > three dots > Allow restricted settings.",
            14f
        ))
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    private fun runtimePerms(): List<String> {
        val l = mutableListOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l
    }

    private fun missingPerms() = runtimePerms().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    private fun micOk() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun overlayOk() = Settings.canDrawOverlays(this)
    private fun batteryOk() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    private fun listenerOk() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    private fun a11yOk() = Settings.Secure.getString(contentResolver, "enabled_accessibility_services")?.contains(packageName) == true

    private fun refreshStatus() {
        fun m(ok: Boolean) = if (ok) "✓ " else "✗ "
        val s = FlorenceService.instance
        val state = when {
            s == null -> "not running"
            s.isSleeping() -> "asleep (say: wake up)"
            else -> "listening"
        }
        sleepBtn.text = if (s != null && s.isSleeping()) "Wake up" else "Sleep"
        status.text = m(s != null) + "${store.get("assistant_name", "Mum Florence")} is $state\n" +
            m(missingPerms().isEmpty()) + "Microphone, phone, SMS, contacts, location\n" +
            m(overlayOk()) + "Open apps while the phone is asleep\n" +
            m(batteryOk()) + "Stay awake in the background\n" +
            m(listenerOk()) + "Read and reply to notifications\n" +
            m(a11yOk()) + "Control the screen (read, tap, scroll, type)\n" +
            "• API key: ${if (store.get("api_key").isBlank()) "none (offline mode)" else Brain.lastStatus}\n" +
            "• Voices saved: ${store.voices().size}" + (if (store.voices().isEmpty()) " (anybody can command her)" else "") + "\n" +
            "• Last heard: ${s?.lastHeard ?: "-"}\n" +
            "• Last reply: ${s?.lastReply?.take(80) ?: "-"}"
    }

    private fun step(name: String, ok: Boolean, message: String, launch: () -> Unit): Boolean {
        if (ok || !asked.add(name)) return false
        toast(message)
        launch()
        return true
    }

    private fun nextStep() {
        refreshStatus()
        val miss = missingPerms()
        if (miss.isNotEmpty() && asked.add("perms")) { requestPermissions(miss.toTypedArray(), 1); return }
        if (step("overlay", overlayOk(), "Switch ON for ${store.get("assistant_name", "Mum Florence")}, then press Back") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }) return
        if (step("battery", batteryOk(), "Tap Allow so I can keep listening") {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }) return
        if (step("listener", listenerOk(), "Switch ON Mum Florence, then press Back") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) return
        if (step("a11y", a11yOk(), "Open Mum Florence in the list and switch it ON, then press Back") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) return
        wizard = false
        startFlorence()
        status.postDelayed({ refreshStatus() }, 1500)
    }

    private fun startFlorence() {
        if (!micOk()) return
        try { startForegroundService(Intent(this, FlorenceService::class.java)) } catch (e: Exception) { toast("Could not start: ${e.message}") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (wizard) nextStep() else refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (wizard) nextStep() else if (micOk() && FlorenceService.instance == null) {
            startFlorence()
            status.postDelayed({ refreshStatus() }, 1500)
        }
    }
}
