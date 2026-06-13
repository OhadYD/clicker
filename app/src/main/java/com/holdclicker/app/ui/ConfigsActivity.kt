package com.holdclicker.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.holdclicker.app.R
import com.holdclicker.app.data.ConfigStore
import com.holdclicker.app.model.Mode

class ConfigsActivity : ThemedActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configs)
        container = findViewById(R.id.container)
        ConfigStore.ensureDefaults(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        container.removeAllViews()
        val names = ConfigStore.listNames(this)
        if (names.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No configurations yet. Save one from Single or Multi target mode."
                setTextColor(ContextCompat.getColor(this@ConfigsActivity, R.color.textDim))
                textSize = 14f
            }
            container.addView(empty)
            return
        }
        val inflater = LayoutInflater.from(this)
        names.forEach { name ->
            val row = inflater.inflate(R.layout.row_config, container, false)
            row.findViewById<TextView>(R.id.txtName).text = name
            row.findViewById<TextView>(R.id.btnLoad).setOnClickListener { load(name) }
            row.findViewById<TextView>(R.id.btnRename).setOnClickListener { rename(name) }
            row.findViewById<TextView>(R.id.btnDup).setOnClickListener {
                ConfigStore.duplicate(this, name)
                render()
            }
            row.findViewById<TextView>(R.id.btnDel).setOnClickListener { confirmDelete(name) }
            container.addView(row)
        }
    }

    private fun load(name: String) {
        val cfg = ConfigStore.load(this, name)
        if (cfg == null) {
            Toast.makeText(this, "Could not load \"$name\"", Toast.LENGTH_SHORT).show()
            return
        }
        val target = if (cfg.mode == Mode.MULTI) {
            MultiTargetActivity::class.java
        } else {
            SingleTargetActivity::class.java
        }
        startActivity(Intent(this, target).putExtra("configName", name))
    }

    private fun rename(name: String) {
        val input = EditText(this).apply {
            hint = "New name"
            setText(name)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename \"$name\"")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (!ConfigStore.rename(this, name, newName)) {
                    Toast.makeText(
                        this, "Rename failed (empty name or name already in use)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"$name\"?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                ConfigStore.delete(this, name)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
