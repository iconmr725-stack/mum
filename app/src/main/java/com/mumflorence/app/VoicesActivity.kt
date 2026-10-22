package com.mumflorence.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

class VoicesActivity : Activity() {
    private lateinit var store: Store

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this)
        render()
    }

    private fun withService(job: (FlorenceService) -> Unit) {
        val s = FlorenceService.instance
        if (s != null) { job(s); return }
        try { startForegroundService(Intent(this, FlorenceService::class.java)) } catch (e: Exception) {}
        Toast.makeText(this, "Starting Mum Florence...", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({
            FlorenceService.instance?.let(job) ?: Toast.makeText(this, "Please allow all permissions on the main screen first.", Toast.LENGTH_LONG).show()
        }, 6000)
    }

    private fun render() {
        val f = Form(this, store)
        f.title("Voices that can command me")
        val voices = store.voices()
        f.text(if (voices.isEmpty()) "No voices saved yet. Until you add one, anybody can command me." else voices.keys.joinToString("\n") { "• $it" })
        f.text("You can add as many people as you like. Record in a quiet place, speak normally, and say the three sentences she asks for.", 13f)

        val name = f.plainEdit("Name of the person", "for example: Claude")
        f.button("Record this voice") {
            if (name.text.isBlank()) Toast.makeText(this, "Type the name first", Toast.LENGTH_SHORT).show()
            else {
                val n = name.text.toString().trim()
                Toast.makeText(this, "Listen to her, then speak.", Toast.LENGTH_LONG).show()
                withService { it.voiceJob("enroll:$n") }
            }
        }
        f.button("Test a voice") { withService { it.voiceJob("test") } }
        val del = f.plainEdit("Remove a voice (type the name)")
        f.button("Remove") { if (del.text.isNotBlank()) { store.deleteVoice(del.text.toString().trim()); render() } }
        f.button("Refresh list") { render() }
        setContentView(f.scroll())
    }
}
