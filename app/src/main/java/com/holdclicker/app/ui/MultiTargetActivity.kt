package com.holdclicker.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.holdclicker.app.R
import com.holdclicker.app.data.ConfigStore
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Lane
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.StopMode
import com.holdclicker.app.model.TargetAction
import com.holdclicker.app.overlay.LaneTreeView
import com.holdclicker.app.service.AutoClickService
import java.util.Collections

class MultiTargetActivity : ThemedActivity() {

    private val lanes = mutableListOf<Lane>()
    private val laneHolders = mutableListOf<LaneHolder>()

    private lateinit var lanesContainer: LinearLayout
    private lateinit var treeView: LaneTreeView
    private lateinit var etInterval: EditText
    private lateinit var etStopTime: EditText
    private lateinit var etStopCycles: EditText
    private lateinit var rbInfinite: RadioButton
    private lateinit var rbTime: RadioButton
    private lateinit var rbCycles: RadioButton
    private lateinit var timeRow: LinearLayout
    private lateinit var cyclesRow: LinearLayout

    private inner class RowHolder(val view: View, val action: TargetAction) {
        val title: TextView = view.findViewById(R.id.rowTitle)
        val btnUp: TextView = view.findViewById(R.id.btnUp)
        val btnDown: TextView = view.findViewById(R.id.btnDown)
        val btnDel: TextView = view.findViewById(R.id.btnDel)
        val spType: Spinner = view.findViewById(R.id.spType)
        val holdRow: View = view.findViewById(R.id.holdRow)
        val etHold: EditText = view.findViewById(R.id.etHold)
        val cbPermanentHold: CheckBox = view.findViewById(R.id.cbPermanentHold)
        val swipeRow: View = view.findViewById(R.id.swipeRow)
        val etSwipe: EditText = view.findViewById(R.id.etSwipe)
        val etBefore: EditText = view.findViewById(R.id.etBefore)
        val etAfter: EditText = view.findViewById(R.id.etAfter)
    }

    private inner class LaneHolder(val view: View, val lane: Lane) {
        val title: TextView = view.findViewById(R.id.laneTitle)
        val btnDelLane: TextView = view.findViewById(R.id.btnDelLane)
        val actionsContainer: LinearLayout = view.findViewById(R.id.actionsContainer)
        val btnAddAction: Button = view.findViewById(R.id.btnAddAction)
        val rows = mutableListOf<RowHolder>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_target)

        lanesContainer = findViewById(R.id.lanesContainer)
        treeView = findViewById(R.id.treeView)
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

        findViewById<Button>(R.id.btnAddLane).setOnClickListener {
            syncFromViews()
            lanes.add(Lane(mutableListOf(defaultAction())))
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
        if (lanes.isEmpty()) {
            lanes.add(Lane(mutableListOf(defaultAction(), defaultAction())))
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
        lanes.clear()
        cfg.lanes.forEach { lanes.add(it.copyOf()) }
        if (lanes.isEmpty()) lanes.add(Lane(mutableListOf(defaultAction())))
    }

    private fun updateStopVisibility() {
        timeRow.visibility = if (rbTime.isChecked) View.VISIBLE else View.GONE
        cyclesRow.visibility = if (rbCycles.isChecked) View.VISIBLE else View.GONE
    }

    private fun defaultAction(): TargetAction {
        val dm = resources.displayMetrics
        val n = lanes.sumOf { it.actions.size }
        return TargetAction(
            x = dm.widthPixels * 0.3f + (n % 4) * dm.widthPixels * 0.08f,
            y = dm.heightPixels * 0.35f + (n % 5) * dm.heightPixels * 0.06f,
            endX = dm.widthPixels * 0.7f,
            endY = dm.heightPixels * 0.55f
        )
    }

    private fun render() {
        lanesContainer.removeAllViews()
        laneHolders.clear()
        val inflater = LayoutInflater.from(this)

        lanes.forEachIndexed { laneIndex, lane ->
            val laneView = inflater.inflate(R.layout.row_lane, lanesContainer, false)
            val lh = LaneHolder(laneView, lane)
            lh.title.text = "Branch ${('A' + laneIndex)}"
            lh.btnDelLane.visibility = if (lanes.size > 1) View.VISIBLE else View.GONE
            lh.btnDelLane.setOnClickListener {
                syncFromViews()
                lanes.removeAt(laneIndex)
                render()
            }
            lh.btnAddAction.setOnClickListener {
                syncFromViews()
                lane.actions.add(defaultAction())
                render()
            }

            lane.actions.forEachIndexed { ai, action ->
                val row = buildRow(inflater, lh.actionsContainer, lane, action, laneIndex, ai)
                lh.rows.add(row)
                lh.actionsContainer.addView(row.view)
            }

            laneHolders.add(lh)
            lanesContainer.addView(laneView)
        }
        refreshTree()
    }

    private fun buildRow(
        inflater: LayoutInflater,
        parent: ViewGroup,
        lane: Lane,
        action: TargetAction,
        laneIndex: Int,
        actionIndex: Int
    ): RowHolder {
        val view = inflater.inflate(R.layout.row_action, parent, false)
        val h = RowHolder(view, action)
        h.title.text = "Target ${actionIndex + 1}"

        h.spType.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Tap", "Hold", "Swipe")
        )
        h.spType.setSelection(action.type.ordinal)
        h.spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                applyTypeVisibility(h, ActionType.values()[pos])
                refreshTree()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        h.etHold.setText(action.holdMs.toString())
        h.cbPermanentHold.isChecked = action.holdIndefinite
        h.cbPermanentHold.setOnCheckedChangeListener { _, _ -> applyTypeVisibility(h, ActionType.values()[h.spType.selectedItemPosition]); refreshTree() }
        h.etSwipe.setText(action.swipeMs.toString())
        h.etBefore.setText(action.delayBeforeMs.toString())
        h.etAfter.setText(action.delayAfterMs.toString())
        applyTypeVisibility(h, action.type)

        h.btnUp.setOnClickListener {
            if (actionIndex > 0) {
                syncFromViews()
                Collections.swap(lane.actions, actionIndex, actionIndex - 1)
                render()
            }
        }
        h.btnDown.setOnClickListener {
            if (actionIndex < lane.actions.size - 1) {
                syncFromViews()
                Collections.swap(lane.actions, actionIndex, actionIndex + 1)
                render()
            }
        }
        h.btnDel.setOnClickListener {
            syncFromViews()
            lane.actions.removeAt(actionIndex)
            render()
        }
        return h
    }

    private fun applyTypeVisibility(h: RowHolder, type: ActionType) {
        h.holdRow.visibility = if (type == ActionType.HOLD) View.VISIBLE else View.GONE
        h.etHold.isEnabled = !(type == ActionType.HOLD && h.cbPermanentHold.isChecked)
        h.etHold.alpha = if (h.etHold.isEnabled) 1f else 0.45f
        h.swipeRow.visibility = if (type == ActionType.SWIPE) View.VISIBLE else View.GONE
    }

    /** Copies typed values back into the lane actions. */
    private fun syncFromViews() {
        laneHolders.forEach { lh ->
            lh.rows.forEach { h ->
                val a = h.action
                a.type = ActionType.values()[h.spType.selectedItemPosition]
                a.holdMs = (h.etHold.text.toString().toLongOrNull() ?: 800L).coerceAtLeast(1L)
                a.holdIndefinite = h.cbPermanentHold.isChecked
                a.swipeMs = (h.etSwipe.text.toString().toLongOrNull() ?: 300L).coerceAtLeast(1L)
                a.delayBeforeMs = (h.etBefore.text.toString().toLongOrNull() ?: 0L).coerceAtLeast(0L)
                a.delayAfterMs = (h.etAfter.text.toString().toLongOrNull() ?: 200L).coerceAtLeast(0L)
            }
        }
    }

    private fun refreshTree() {
        syncFromViews()
        treeView.config = ClickerConfig(
            name = "preview",
            mode = Mode.MULTI,
            lanes = lanes.map { it.copyOf() }.toMutableList()
        )
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

        val totalActions = lanes.sumOf { it.actions.size }
        if (totalActions > 10) {
            Toast.makeText(
                this,
                "A cycle can run at most 10 actions across all branches at once. " +
                    "Reduce actions or split into more cycles.",
                Toast.LENGTH_LONG
            ).show()
        }

        val copied = lanes.map { it.copyOf() }.filter { it.actions.isNotEmpty() }.toMutableList()
        if (copied.isEmpty()) copied.add(Lane())

        return ClickerConfig(
            name = "Unsaved multi",
            mode = Mode.MULTI,
            intervalMs = interval,
            stopMode = stopMode,
            stopTimeMs = stopTimeSec * 1000L,
            stopCycles = stopCycles,
            lanes = copied
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
