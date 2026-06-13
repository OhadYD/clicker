package com.holdclicker.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.holdclicker.app.R
import com.holdclicker.app.data.ConfigStore
import com.holdclicker.app.service.AutoClickService

class MainActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ConfigStore.ensureDefaults(this)

        findViewById<android.view.View>(R.id.cardSingle).setOnClickListener {
            startActivity(Intent(this, SingleTargetActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardMulti).setOnClickListener {
            startActivity(Intent(this, MultiTargetActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardRecord).setOnClickListener {
            startRecording()
        }
        findViewById<android.view.View>(R.id.cardConfigs).setOnClickListener {
            startActivity(Intent(this, ConfigsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val enabled = AutoClickService.instance != null
        val status = findViewById<TextView>(R.id.txtServiceStatus)
        val button = findViewById<Button>(R.id.btnAccessibility)
        if (enabled) {
            status.text = "Accessibility service: enabled ✓"
            status.setTextColor(ContextCompat.getColor(this, R.color.teal))
            button.visibility = android.view.View.GONE
        } else {
            status.text = "Accessibility service: not enabled"
            status.setTextColor(ContextCompat.getColor(this, R.color.pink))
            button.visibility = android.view.View.VISIBLE
        }
    }

    private fun startRecording() {
        val svc = AutoClickService.instance
        if (svc == null) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Accessibility service required")
                .setMessage(
                    "Recording uses the HoldClicker accessibility service to show a capture " +
                        "layer and play the result back. Enable it first, then try again."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        svc.startRecording()
        android.widget.Toast.makeText(
            this, "Recording starts after a 3-second countdown", android.widget.Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }
}
