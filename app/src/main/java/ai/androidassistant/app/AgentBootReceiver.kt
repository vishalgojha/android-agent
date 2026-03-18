package ai.androidassistant.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AgentBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED,
      -> AgentForegroundService.start(context)
    }
  }
}

