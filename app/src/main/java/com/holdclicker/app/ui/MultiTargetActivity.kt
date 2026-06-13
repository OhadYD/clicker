package com.holdclicker.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.holdclicker.app.R
import com.holdclicker.app.data.ConfigStore
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.StopMode
import com.holdclicker.app.model.TargetAction
import com.holdclicker.app.service.AutoClickService
import java.util.Collections

class MultiTargetActivity : AppCompatActivity() {

    private val actions = mutableListOf<TargetAction>()
    private val holders = mutableListOf<RowHolder>()

    private lateinit var container: LinearLayout
    private lateinit var etInterval: EditText
    private lateinit var etStopTime: EditText
    private lateinit var etStopCycles: EditText
    private lateinit var rbInfinite: RadioButton
    private lateinit var rbTime: RadioButton
    private lateinit var rbCycles: RadioButton
    private lateinit var timeRow: LinearLayout
    private lateinit var cyclesRow: LinearLayout

    private inner class RowHolder(val view: View) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val btnUp: TextView = view.findViewById(R.id.btnUp)
        val btnDown: TextView = view.findViewById(R.id.btnDown)
        val btnDel: TextView = view.findViewById(R.id.btnDel)
        val spType: Spinner = view.findViewById(R.id.spType)
        val holdRow: View = view.findViewById(R.id.holdRow)
        val etHold: EditText = view.findViewById(R.id.etHold)
        val swipeRow: View = view.findViewById(R.id.swipeRow)
        val etSwipe: EditText = view.findViewById(R.id.etSwipe)
        val etBefore: EditText = view.findViewById(R.id.etBefore)
        val etAfter: EditText = view.findViewById(R.id.etAfter)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_target)

        container = findViewById(R.id.container)
        etInterval = findViewById(R.id.etInterval)
        etStopTime = findViewById(R.id.etStopTime)
        etStopCycles = findViewById(R.id.etStopCycles)
        rbInfinite = findViewById(R.id.rbInfinite)
        rbTime = findViewById(R.id.rbTime)
        rbCycles = findViewById(R.id.rbCycles)
        timeRow = findViewById(R.id.timeRow)
        cyclesRow = findViewById(R.id.cyclesRow)

        rbInfinite.setOnCheckedChangeListener { _, _ -> updateStopVisibility() }
        rbTime.setOnCheckedChangeListener { _, _ -> updateStopVisibility() }
        rbCycles.setOnCheckedChangeListener { _, _ -> updateStopVisibility() }

        findViewById<Button>(R.id.btnAddAction).setOnClickListener {
            syncFromViews()
            actions.add(defaultAction())
            render()
        }
        findViewById<Button>(R.id.btnLaunch).setOnClickListener {
            buildConfig()?.let { launchOverlay(it) }
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            buildConfig()?.let { promptSave(it) }
        }

        val loadedName = intent.getStringExtra("configName")
        if (loadedName != null) {
            ConfigStore.load(this, loadedName)?.let { applyConfig(it) }
        }
        if (actions.isEmpty()) {
            actions.add(defaultAction())
            actions.add(defaultAction())
        }

        updateStopVisibility()
        render()
    }

    private fun applyConfig(cfg: ClickerConfig) {
        etInterval.setText(cfg.intervalMs.toString())
        when (cfg.stopMode) {
            StopMode.INFINITE -> rbInfinite.isChecked = true
            StopMode.TIME -> rbTime.isChecked = true
            StopMode.CYCLES -> rbCycles.isChecked = true
        }
        etStopTime.setText((cfg.stopTimeMs / 1000L).toString())
        etStopCycles.setText(cfg.stopCycles.toString())
        actions.clear()
        actions.addAll(cfg.actions.map { it.copyOf() })
    }

    private fun updateStopVisibility() {
        timeRow.visibility = if (rbTime.isChecked) View.VISIBLE else View.GONE
        cyclesRow.visibility = if (rbCycles.isChecked) View.VISIBLE else View.GONE
    }

    private fun defaultAction(): TargetAction {
        val dm = resources.displayMetrics
        val i = actions.size
        return TargetAction(
            x = dm.widthPixels * 0.3f + i * dm.widthPixels * 0.08f,
            y = dm.heightPixels * 0.4f + i * dm.heightPixels * 0.06f,
            endX = dm.widthPixels * 0.7f,
            endY = dm.heightPixels * 0.6f
        )
    }

    private fun render() {
        container.removeAllViews()
        holders.clear()
        val inflater = LayoutInflater.from(this)
        actions.forEachIndexed { index, a ->
            val row = inflater.inflate(R.layout.row_action, container, false)
            val h = RowHolder(row)
            h.title.text = "Target ${index + 1}"

            h.spType.adapter = ArrayAdapter(
                this, android.R.layout.simple_spinner_dropdown_item,
                listOf("Tap", "Hold", "Swipe")
            )
            h.spType.setSelection(a.type.ordinal)
            h.spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    applyTypeVisibility(h, ActionType.values()[pos])
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }

            h.etHold.setText(a.holdMs.toString())
            h.etSwipe.setText(a.swipeMs.toString())
            h.etBefore.setText(a.delayBeforeMs.toString())
            h.etAfter.setText(a.delayAfterMs.toString())
            applyTypeVisibility(h, a.type)

            h.btnUp.setOnClickListener {
                if (index > 0) {
                    syncFromViews()
                    Collections.swap(actions, index, index - 1)
                    render()
                }
            }
            h.btnDown.setOnClickListener {
                if (index < actions.size - 1) {
                    syncFromViews()
                    Collections.swap(actions, index, index + 1)
                    render()
                }
            }
            h.btnDel.setOnClickListener {
                syncFromViews()
                actions.removeAt(index)
                render()
            }

            holders.add(h)
            container.addView(row)
        }
    }

    private fun applyTypeVisibility(h: RowHolder, type: ActionType) {
        h.holdRow.visibility = if (type == ActionType.HOLD) View.VISIBLE else View.GONE
        h.swipeRow.visibility = if (type == ActionType.SWIPE) View.VISIBLE else View.GONE
    }

    /** Copies the values currently typed in each row back into [actions]. */
    private fun syncFromViews() {
        holders.forEachIndexed { i, h ->
            if (i >= actions.size) return@forEachIndexed
            val a = actions[i]
            a.type = ActionType.values()[h.spType.selectedItemPosition]
            a.holdMs = (h.etHold.text.toString().toLongOrNull() ?: 800L).coerceAtLeast(1L)
            a.swipeMs = (h.etSwipe.text.toString().toLongOrNull() ?: 300L).coerceAtLeast(1L)
            a.delayBeforeMs = (h.etBefore.text.toString().toLongOrNull() ?: 0L).coerceAtLeast(0L)
            a.delayAfterMs = (h.etAfter.text.toString().toLongOrNull() ?: 200L).coerceAtLeast(0L)
        }
    }

    private fun buildConfig(): ClickerConfig? {
        syncFromViews()
        val interval = etInterval.text.toString().toLongOrNull()
        if (interval == null || interval < 0) {
            Toast.makeText(this, "Cycle delay must be 0 or more", Toast.LENGTH_SHORT).show()
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
        return ClickerConfig(
            name = "Unsaved multi",
            mode = Mode.MULTI,
            intervalMs = interval,
            stopMode = stopMode,
            stopTimeMs = stopTimeSec * 1000L,
            stopCycles = stopCycles,
            actions = actions.map { it.copyOf() }.toMutableList()
        )
    }

    private fun launchOverlay(cfg: ClickerConfig) {
        val svc = AutoClickService.instance
        if (svc == null) {
            AlertDialog.Builder(this)
                .setTitle("Accessibility service required")
                .setMessage(
                    "HoldClicker performs taps, holds and swipes through Android's " +
                        "accessibility gesture system. Enable the HoldClicker accessibility " +
                        "service, then come back and press Show overlay again."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        svc.showOverlay(cfg)
        Toast.makeText(
            this, "Overlay shown — drag the circles into place, then press ▶",
            Toast.LENGTH_LONG
        ).show()
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
