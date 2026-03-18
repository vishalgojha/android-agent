package ai.androidassistant.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AgentForegroundService : Service() {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var notificationJob: Job? = null
  private var didStartForeground = false

  private val runtime: AgentRuntime
    get() = (application as NodeApp).agentRuntime

  override fun onCreate() {
    super.onCreate()
    ensureChannel()
    startForegroundWithTypes(buildNotification(statusText = "Starting…"))

    runtime.start()
    notificationJob =
      scope.launch {
        combine(
          runtime.statusText,
          runtime.isListening,
        ) { status, listening ->
          Pair(status, listening)
        }.collect { (status, listening) ->
          val listeningSuffix = if (listening) " · Listening" else ""
          startForegroundWithTypes(
            buildNotification(statusText = status + listeningSuffix),
          )
        }
      }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        runtime.stop()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_START -> runtime.start()
    }
    return START_STICKY
  }

  override fun onDestroy() {
    notificationJob?.cancel()
    runtime.shutdown()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?) = null

  private fun ensureChannel() {
    val mgr = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Agent Status",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Wake-word listener status"
        setShowBadge(false)
      }
    mgr.createNotificationChannel(channel)
  }

  private fun buildNotification(statusText: String): Notification {
    val stopIntent = Intent(this, AgentForegroundService::class.java).setAction(ACTION_STOP)
    val stopPending =
      PendingIntent.getService(
        this,
        1,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val needsMicPermission = !runtime.hasMicPermission()
    val enableMicPending =
      PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val builder =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Android Assistant")
        .setContentText(if (needsMicPermission) "Microphone permission required" else statusText)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(0, "Stop", stopPending)

    if (needsMicPermission) {
      builder.addAction(0, "Enable mic", enableMicPending)
    }

    return builder.build()
  }

  private fun updateNotification(notification: Notification) {
    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mgr.notify(NOTIFICATION_ID, notification)
  }

  private fun startForegroundWithTypes(notification: Notification) {
    if (didStartForeground) {
      updateNotification(notification)
      return
    }
    startForeground(
      NOTIFICATION_ID,
      notification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
    )
    didStartForeground = true
  }

  companion object {
    private const val CHANNEL_ID = "agent_status"
    private const val NOTIFICATION_ID = 101

    private const val ACTION_STOP = "ai.androidassistant.app.action.AGENT_STOP"
    private const val ACTION_START = "ai.androidassistant.app.action.AGENT_START"

    fun start(context: Context) {
      val intent = Intent(context, AgentForegroundService::class.java).setAction(ACTION_START)
      context.startForegroundService(intent)
    }
  }
}

