package ai.androidassistant.app.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AutomationAccessibilityService : AccessibilityService() {
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    // Intentionally empty: service is enabled/disabled via Settings.
  }

  override fun onInterrupt() {
    // No-op
  }
}
