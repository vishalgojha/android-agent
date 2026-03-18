package ai.androidassistant.app.automation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Schedules and triggers time-based automation rules
 */
class TimeScheduler {

  companion object {
    private const val TAG = "TimeScheduler"
    private const val CHECK_INTERVAL = 60_000L
  }

  private val rules = ConcurrentHashMap<String, AutomationRule>()
  private val handler = Handler(Looper.getMainLooper())
  private var isRunning = false

  private val checkRunnable =
    object : Runnable {
      override fun run() {
        if (isRunning) {
          checkTimeRules()
          handler.postDelayed(this, CHECK_INTERVAL)
        }
      }
    }

  fun start(_context: Context) {
    if (isRunning) {
      return
    }
    isRunning = true
    handler.post(checkRunnable)
    Log.d(TAG, "Time scheduler started")
  }

  fun stop() {
    isRunning = false
    handler.removeCallbacks(checkRunnable)
    Log.d(TAG, "Time scheduler stopped")
  }

  fun replaceRules(newRules: List<AutomationRule>) {
    rules.clear()
    newRules
      .filter { it.triggerType == TriggerType.TIME }
      .forEach { rule -> rules[rule.id] = rule }
  }

  private fun checkTimeRules() {
    val calendar = Calendar.getInstance()
    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(Calendar.MINUTE)
    val currentDay = calendar.get(Calendar.DAY_OF_WEEK)

    rules.values
      .filter { it.isEnabled && it.triggerType == TriggerType.TIME }
      .forEach { rule ->
        val config = rule.triggerConfig as? TriggerConfig.TimeConfig ?: return@forEach
        if (config.hour != currentHour || config.minute != currentMinute) {
          return@forEach
        }
        if (config.daysOfWeek.isNotEmpty() && currentDay !in config.daysOfWeek) {
          return@forEach
        }

        AutomationEngine.onTriggerDetected(
          rule = rule,
          triggerData =
            mapOf(
              "time" to "$currentHour:$currentMinute",
              "day" to currentDay.toString(),
            ),
        )
        Log.d(TAG, "Time rule triggered: ${rule.name}")
      }
  }
}

