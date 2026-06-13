package com.holdclicker.app.data

import android.content.Context
import com.holdclicker.app.model.ActionType
import com.holdclicker.app.model.ClickerConfig
import com.holdclicker.app.model.Lane
import com.holdclicker.app.model.Mode
import com.holdclicker.app.model.StopMode
import com.holdclicker.app.model.TargetAction
import org.json.JSONObject

/** Saves/loads configurations as JSON strings in SharedPreferences. */
object ConfigStore {

    private const val FILE = "configs"
    private const val META = "configs_meta"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun listNames(ctx: Context): List<String> = prefs(ctx).all.keys.toList().sorted()

    fun exists(ctx: Context, name: String) = prefs(ctx).contains(name)

    fun save(ctx: Context, config: ClickerConfig) {
        prefs(ctx).edit().putString(config.name, config.toJson().toString()).apply()
    }

    fun load(ctx: Context, name: String): ClickerConfig? {
        val s = prefs(ctx).getString(name, null) ?: return null
        return try {
            ClickerConfig.fromJson(JSONObject(s))
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context, name: String) {
        prefs(ctx).edit().remove(name).apply()
    }

    fun rename(ctx: Context, oldName: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || exists(ctx, trimmed)) return false
        val cfg = load(ctx, oldName) ?: return false
        cfg.name = trimmed
        save(ctx, cfg)
        delete(ctx, oldName)
        return true
    }

    fun duplicate(ctx: Context, name: String) {
        val cfg = load(ctx, name) ?: return
        var copyName = "$name copy"
        var i = 2
        while (exists(ctx, copyName)) {
            copyName = "$name copy $i"
            i++
        }
        cfg.name = copyName
        save(ctx, cfg)
    }

    /** Seeds a few example configurations on first run. */
    fun ensureDefaults(ctx: Context) {
        val meta = ctx.getSharedPreferences(META, Context.MODE_PRIVATE)
        if (meta.getBoolean("seeded", false)) return

        save(ctx, ClickerConfig(
            name = "Config 0",
            mode = Mode.SINGLE,
            intervalMs = 300L,
            stopMode = StopMode.INFINITE,
            actions = mutableListOf(TargetAction(x = 400f, y = 800f, type = ActionType.TAP))
        ))

        save(ctx, ClickerConfig(
            name = "Config 1",
            mode = Mode.SINGLE,
            intervalMs = 1000L,
            stopMode = StopMode.CYCLES,
            stopCycles = 100L,
            actions = mutableListOf(TargetAction(x = 400f, y = 800f, type = ActionType.TAP))
        ))

        save(ctx, ClickerConfig(
            name = "Hold test",
            mode = Mode.SINGLE,
            intervalMs = 300L,
            stopMode = StopMode.INFINITE,
            actions = mutableListOf(
                TargetAction(x = 400f, y = 800f, type = ActionType.HOLD, holdMs = 800L)
            )
        ))

        save(ctx, ClickerConfig(
            name = "Multi target test",
            mode = Mode.MULTI,
            intervalMs = 500L,
            stopMode = StopMode.INFINITE,
            actions = mutableListOf(
                TargetAction(x = 300f, y = 700f, type = ActionType.TAP, delayAfterMs = 200L),
                TargetAction(x = 520f, y = 900f, type = ActionType.HOLD, holdMs = 800L, delayAfterMs = 200L),
                TargetAction(
                    x = 300f, y = 1100f, type = ActionType.SWIPE,
                    endX = 700f, endY = 1100f, swipeMs = 400L, delayAfterMs = 200L
                )
            )
        ))

        // Parallel branches demo: hold one spot while tapping another.
        save(ctx, ClickerConfig(
            name = "Parallel hold + tap",
            mode = Mode.MULTI,
            intervalMs = 0L,
            stopMode = StopMode.INFINITE,
            lanes = mutableListOf(
                Lane(mutableListOf(
                    TargetAction(x = 250f, y = 1000f, type = ActionType.HOLD, holdMs = 1500L)
                )),
                Lane(mutableListOf(
                    TargetAction(x = 850f, y = 900f, type = ActionType.TAP, delayAfterMs = 300L),
                    TargetAction(x = 850f, y = 900f, type = ActionType.TAP, delayAfterMs = 300L),
                    TargetAction(x = 850f, y = 900f, type = ActionType.TAP, delayAfterMs = 300L)
                ))
            )
        ))

        meta.edit().putBoolean("seeded", true).apply()
    }
}
