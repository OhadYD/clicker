package com.holdclicker.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.holdclicker.app.R
import com.holdclicker.app.data.ConfigStore
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Lane
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.StopMode
import com.holdclicker.app.model.TargetAction
import com.holdclicker.app.service.AutoClickService

class SingleTargetActivity : ThemedActivity() {

    private lateinit var etInterval: EditText
    private lateinit var etHold: EditText
    private lateinit var etStopTime: EditText
    private lateinit var etStopCycles: EditText
    private lateinit var rbTap: RadioButton
    private lateinit var rbHold: RadioButton
    private lateinit var rbInfinite: RadioButton
    private lateinit var rbTime: RadioButton
    private lateinit var rbCycles: RadioButton
    private lateinit var holdRow: LinearLayout
    private lateinit var timeRow: LinearLayout
    private lateinit var cyclesRow: LinearLayout
    private lateinit var txtWarning: TextView

    /** Action kept from a loaded config so its coordinates survive editing. */
    private var loadedAction: TargetAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_target)

        etInterval = findViewById(R.id.etInterval)
        etHold = findViewById(R.id.etHold)
        etStopTime = findViewById(R.id.etStopTime)
        etStopCycles = findViewById(R.id.etStopCycles)
        rbTap = findViewById(R.id.rbTap)
        rbHold = findViewById(R.id.rbHold)
        rbInfinite = findViewById(R.id.rbInfinite)
        rbTime = findViewById(R.id.rbTime)
        rbCycles = findViewById(R.id.rbCycles)
        holdRow = findViewById(R.id.holdRow)
        timeRow = findViewById(R.id.timeRow)
        cyclesRow = findViewById(R.id.cyclesRow)
        txtWarning = findViewById(R.id.txtWarning)

        rbTap.setOnCheckedChangeListener { _, _ -> updateVisibility(); updateWarning() }
        rbHold.setOnCheckedChangeListener { _, _ -> updateVisibility(); updateWarning() }
        rbInfinite.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        rbTime.setOnCheckedChangeListener { _, _ -> updateVisibility() }
        rbCycles.setOnCheckedChangeListener { _, _ -> updateVisibility() }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updateWarning()
        }
        etInterval.addTextChangedListener(watcher)
        etHold.addTextChangedListener(watcher)

        findViewById<Button>(R.id.btnLaunch).setOnClickListener {
            buildConfig()?.let { launchOverlay(it) }
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            buildConfig()?.let { promptSave(it) }
        }

        intent.getStringExtra("configName")?.let { name ->
            ConfigStore.load(this, name)?.let { applyConfig(it) }
        }

        updateVisibility()
        updateWarning()
    }

    private fun applyConfig(cfg: ClickerConfig) {
        etInterval.setText(cfg.intervalMs.toString())
        val action = cfg.actions.firstOrNull()
        if (action != null) {
            loadedAction = action.copyOf()
            if (action.type == ActionType.HOLD) rbHold.isChecked = true else rbTap.isChecked = true
            etHold.setText(action.holdMs.toString())
        }
        when (cfg.stopMode) {
            StopMode.INFINITE -> rbInfinite.isChecked = true
            StopMode.TIME -> rbTime.isChecked = true
            StopMode.CYCLES -> rbCycles.isChecked = true
        }
        etStopTime.setText((cfg.stopTimeMs / 1000L).toString())
        etStopCycles.setText(cfg.stopCycles.toString())
    }

    private fun updateVisibility() {
        holdRow.visibility = if (rbHold.isChecked) View.VISIBLE else View.GONE
        timeRow.visibility = if (rbTime.isChecked) View.VISIBLE else View.GONE
        cyclesRow.visibility = if (rbCycles.isChecked) View.VISIBLE else View.GONE
    }

    private fun updateWarning() {
        val interval = etInterval.text.toString().toLongOrNull()
        val hold = etHold.text.toString().toLongOrNull()
        val messages = mutableListOf<String>()
        if (interval != null && interval < 40) {
            messages.add("⚠ Intervals below 40 ms can overload apps and your device.")
        }
        if (rbHold.isChecked && interval != null && hold != null && hold > interval) {
            messages.add("ℹ Hold is longer than the interval — the runner waits for each hold to finish before the next one.")
        }
        if (messages.isEmpty()) {
            txtWarning.visibility = View.GONE
        } else {
            txtWarning.visibility = View.VISIBLE
            txtWarning.text = messages.joinToString("\n\n")
        }
    }

    /** Returns a validated config, or null (with a toast) if input is invalid. */
    private fun buildConfig(): ClickerConfig? {
        val interval = etInterval.text.toString().toLongOrNull()
        if (interval == null || interval < 10) {
            Toast.makeText(this, "Interval must be at least 10 ms", Toast.LENGTH_SHORT).show()
            return null
        }
        val type = if (rbHold.isChecked) ActionType.HOLD else ActionType.TAP
        val hold = etHold.text.toString().toLongOrNull() ?: 800L
        if (type == ActionType.HOLD && hold < 1) {
            Toast.makeText(this, "Hold duration must be at least 1 ms", Toast.LENGTH_SHORT).show()
            return null
        }
        val stopMode = when {
            rbTime.isChecked -> StopMode.TIME
            rbCycles.isChecked -> StopMode.CYCLES
            else -> StopMode.INFINITE
        }
        val stopTimeSec = etStopTime.text.toString().toLongOrNull() ?: 60L
        val stopCycles = etStopCycles.text.toString().toLongOrNull() ?: 100L
        if (stopMode == StopMode.TIME && stopTimeSec <= 0) {
            Toast.makeText(this, "Stop time must be greater than 0", Toast.LENGTH_SHORT).show()
            return null
        }
        if (stopMode == StopMode.CYCLES && stopCycles <= 0) {
            Toast.makeText(this, "Cycle count must be greater than 0", Toast.LENGTH_SHORT).show()
            return null
        }

        val dm = resources.displayMetrics
        val action = (loadedAction?.copyOf() ?: TargetAction(
            x = dm.widthPixels / 2f,
            y = dm.heightPixels / 2f
        )).also {
            it.type = type
            it.holdMs = hold
        }

        return ClickerConfig(
            name = "Unsaved single",
            mode = Mode.SINGLE,
            intervalMs = interval,
            stopMode = stopMode,
            stopTimeMs = stopTimeSec * 1000L,
            stopCycles = stopCycles,
            lanes = mutableListOf(Lane(mutableListOf(action)))
        )
    }

    private fun launchOverlay(cfg: ClickerConfig) {
        val svc = AutoClickService.instance
        if (svc == null) {
            AlertDialog.Builder(this)
                .setTitle("Accessibility service required")
                .setMessage(
                    "HoldClicker performs taps and holds through Android's accessibility " +
                        "gesture system. Enable the HoldClicker accessibility service, then " +
                        "come back and press Show overlay again."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        svc.showOverlay(cfg)
        Toast.makeText(this, "Overlay shown — drag the circle, then press ▶", Toast.LENGTH_LONG).show()
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun promptSave(cfg: ClickerConfig) {
        val input = EditText(this).apply {
            hint = "Configuration name"
            setText(intent.getStringExtra("configName") ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Save configuration")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    cfg.name = name
                    ConfigStore.save(this, cfg)
                    Toast.makeText(this, "Saved \"$name\"", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
