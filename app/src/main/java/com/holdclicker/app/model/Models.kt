package com.holdclicker.app.model

import org.json.JSONArray
import org.json.JSONObject

enum class Mode { SINGLE, MULTI }

enum class ActionType { TAP, HOLD, SWIPE }

enum class StopMode { INFINITE, TIME, CYCLES }

/**
 * One target/action. Coordinates are absolute screen pixels of the
 * point the gesture is dispatched at (the center of the target circle).
 */
class TargetAction(
    var x: Float = 400f,
    var y: Float = 800f,
    var type: ActionType = ActionType.TAP,
    var holdMs: Long = 800L,
    var delayBeforeMs: Long = 0L,
    var delayAfterMs: Long = 200L,
    var endX: Float = 650f,
    var endY: Float = 1050f,
    var swipeMs: Long = 300L,
    /** When true, a HOLD action keeps pressing until the user presses Stop. */
    var holdIndefinite: Boolean = false,
    /** When true, this action fires at the same time as the next one in the list. */
    var simultaneousWithNext: Boolean = false
) {
    fun copyOf() = TargetAction(
        x, y, type, holdMs, delayBeforeMs, delayAfterMs, endX, endY, swipeMs, holdIndefinite,
        simultaneousWithNext
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("type", type.name)
        put("holdMs", holdMs)
        put("delayBeforeMs", delayBeforeMs)
        put("delayAfterMs", delayAfterMs)
        put("endX", endX.toDouble())
        put("endY", endY.toDouble())
        put("swipeMs", swipeMs)
        put("holdIndefinite", holdIndefinite)
        put("simultaneousWithNext", simultaneousWithNext)
    }

    companion object {
        fun fromJson(o: JSONObject) = TargetAction(
            x = o.optDouble("x", 400.0).toFloat(),
            y = o.optDouble("y", 800.0).toFloat(),
            type = runCatching { ActionType.valueOf(o.optString("type", "TAP")) }
                .getOrDefault(ActionType.TAP),
            holdMs = o.optLong("holdMs", 800L),
            delayBeforeMs = o.optLong("delayBeforeMs", 0L),
            delayAfterMs = o.optLong("delayAfterMs", 200L),
            endX = o.optDouble("endX", 650.0).toFloat(),
            endY = o.optDouble("endY", 1050.0).toFloat(),
            swipeMs = o.optLong("swipeMs", 300L),
            holdIndefinite = o.optBoolean("holdIndefinite", false),
            simultaneousWithNext = o.optBoolean("simultaneousWithNext", false)
        )
    }
}

/** An independent parallel branch: an ordered list of actions on its own timeline. */
class Lane(
    var actions: MutableList<TargetAction> = mutableListOf()
) {
    fun copyOf() = Lane(actions.map { it.copyOf() }.toMutableList())

    fun toJson(): JSONArray = JSONArray().apply { actions.forEach { put(it.toJson()) } }

    companion object {
        fun fromJson(arr: JSONArray): Lane {
            val lane = Lane()
            for (i in 0 until arr.length()) {
                lane.actions.add(TargetAction.fromJson(arr.getJSONObject(i)))
            }
            return lane
        }
    }
}

class ClickerConfig(
    var name: String = "Unnamed",
    var mode: Mode = Mode.SINGLE,
    /** Single mode: time between repeats. Multi mode: delay between full cycles. */
    var intervalMs: Long = 300L,
    var stopMode: StopMode = StopMode.INFINITE,
    var stopTimeMs: Long = 60_000L,
    var stopCycles: Long = 100L,
    /** One or more parallel branches. Every lane runs concurrently each cycle. */
    var lanes: MutableList<Lane> = mutableListOf(Lane())
) {
    /** Backward-compatible view of the first lane's actions (used by single mode). */
    val actions: MutableList<TargetAction>
        get() {
            if (lanes.isEmpty()) lanes.add(Lane())
            return lanes[0].actions
        }


    fun deepCopy() = ClickerConfig(
        name, mode, intervalMs, stopMode, stopTimeMs, stopCycles,
        lanes.map { it.copyOf() }.toMutableList()
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("mode", mode.name)
        put("intervalMs", intervalMs)
        put("stopMode", stopMode.name)
        put("stopTimeMs", stopTimeMs)
        put("stopCycles", stopCycles)
        put("lanes", JSONArray().apply { lanes.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): ClickerConfig {
            val cfg = ClickerConfig(
                name = o.optString("name", "Unnamed"),
                mode = runCatching { Mode.valueOf(o.optString("mode", "SINGLE")) }
                    .getOrDefault(Mode.SINGLE),
                intervalMs = o.optLong("intervalMs", 300L),
                stopMode = runCatching { StopMode.valueOf(o.optString("stopMode", "INFINITE")) }
                    .getOrDefault(StopMode.INFINITE),
                stopTimeMs = o.optLong("stopTimeMs", 60_000L),
                stopCycles = o.optLong("stopCycles", 100L),
                lanes = mutableListOf()
            )
            val lanesArr = o.optJSONArray("lanes")
            if (lanesArr != null) {
                for (i in 0 until lanesArr.length()) {
                    cfg.lanes.add(Lane.fromJson(lanesArr.getJSONArray(i)))
                }
            } else {
                // Legacy single-list format.
                val arr = o.optJSONArray("actions")
                val lane = Lane()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        lane.actions.add(TargetAction.fromJson(arr.getJSONObject(i)))
                    }
                }
                cfg.lanes.add(lane)
            }
            if (cfg.lanes.isEmpty()) cfg.lanes.add(Lane())
            return cfg
        }
    }
}
