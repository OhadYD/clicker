package com.holdclicker.app.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.holdclicker.app.R
import com.holdclicker.app.data.Prefs
import com.holdclicker.app.service.AutoClickService

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val txtTargetSize = findViewById<TextView>(R.id.txtTargetSize)
        val seekTarget = findViewById<SeekBar>(R.id.seekTarget)
        val txtBarScale = findViewById<TextView>(R.id.txtBarScale)
        val seekBarScale = findViewById<SeekBar>(R.id.seekBarScale)
        val switchVibration = findViewById<SwitchMaterial>(R.id.switchVibration)
        val switchCountdown = findViewById<SwitchMaterial>(R.id.switchCountdown)

        // Target size: progress 0..96 maps to 24..120 dp.
        seekTarget.progress = (Prefs.targetSizeDp(this) - 24).coerceIn(0, 96)
        txtTargetSize.text = "Target size: ${Prefs.targetSizeDp(this)} dp"
        seekTarget.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val dp = 24 + progress
                Prefs.setTargetSizeDp(this@SettingsActivity, dp)
                txtTargetSize.text = "Target size: $dp dp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                AutoClickService.instance?.overlay?.rebuildTargets()
            }
        })

        // Bar scale: progress 0..150 maps to 50..200 %.
        seekBarScale.progress = (Prefs.barScalePercent(this) - 50).coerceIn(0, 150)
        txtBarScale.text =
            "Control bar size: ${Prefs.barScalePercent(this)}% (applies when overlay is reopened)"
        seekBarScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = 50 + progress
                Prefs.setBarScalePercent(this@SettingsActivity, percent)
                txtBarScale.text =
                    "Control bar size: $percent% (applies when overlay is reopened)"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        switchVibration.isChecked = Prefs.vibrationEnabled(this)
        switchVibration.setOnCheckedChangeListener { _, checked ->
            Prefs.setVibrationEnabled(this, checked)
        }

        switchCountdown.isChecked = Prefs.countdownEnabled(this)
        switchCountdown.setOnCheckedChangeListener { _, checked ->
            Prefs.setCountdownEnabled(this, checked)
        }
    }
}
