package ai.androidassistant.app.node

import ai.androidassistant.app.protocol.AndroidAssistantCalendarCommand
import ai.androidassistant.app.protocol.AndroidAssistantCanvasA2UICommand
import ai.androidassistant.app.protocol.AndroidAssistantCanvasCommand
import ai.androidassistant.app.protocol.AndroidAssistantCameraCommand
import ai.androidassistant.app.protocol.AndroidAssistantCapability
import ai.androidassistant.app.protocol.AndroidAssistantContactsCommand
import ai.androidassistant.app.protocol.AndroidAssistantDeviceCommand
import ai.androidassistant.app.protocol.AndroidAssistantLocationCommand
import ai.androidassistant.app.protocol.AndroidAssistantMotionCommand
import ai.androidassistant.app.protocol.AndroidAssistantNotificationsCommand
import ai.androidassistant.app.protocol.AndroidAssistantPhotosCommand
import ai.androidassistant.app.protocol.AndroidAssistantSmsCommand
import ai.androidassistant.app.protocol.AndroidAssistantSystemCommand

data class NodeRuntimeFlags(
  val cameraEnabled: Boolean,
  val locationEnabled: Boolean,
  val smsAvailable: Boolean,
  val voiceWakeEnabled: Boolean,
  val motionActivityAvailable: Boolean,
  val motionPedometerAvailable: Boolean,
  val debugBuild: Boolean,
)

enum class InvokeCommandAvailability {
  Always,
  CameraEnabled,
  LocationEnabled,
  SmsAvailable,
  MotionActivityAvailable,
  MotionPedometerAvailable,
  DebugBuild,
}

enum class NodeCapabilityAvailability {
  Always,
  CameraEnabled,
  LocationEnabled,
  SmsAvailable,
  VoiceWakeEnabled,
  MotionAvailable,
}

data class NodeCapabilitySpec(
  val name: String,
  val availability: NodeCapabilityAvailability = NodeCapabilityAvailability.Always,
)

data class InvokeCommandSpec(
  val name: String,
  val requiresForeground: Boolean = false,
  val availability: InvokeCommandAvailability = InvokeCommandAvailability.Always,
)

object InvokeCommandRegistry {
  val capabilityManifest: List<NodeCapabilitySpec> =
    listOf(
      NodeCapabilitySpec(name = AndroidAssistantCapability.Canvas.rawValue),
      NodeCapabilitySpec(name = AndroidAssistantCapability.Device.rawValue),
      NodeCapabilitySpec(name = AndroidAssistantCapability.Notifications.rawValue),
      NodeCapabilitySpec(name = AndroidAssistantCapability.System.rawValue),
      NodeCapabilitySpec(
        name = AndroidAssistantCapability.Camera.rawValue,
        availability = NodeCapabilityAvailability.CameraEnabled,
      ),
      NodeCapabilitySpec(
        name = AndroidAssistantCapability.Sms.rawValue,
        availability = NodeCapabilityAvailability.SmsAvailable,
      ),
      NodeCapabilitySpec(
        name = AndroidAssistantCapability.VoiceWake.rawValue,
        availability = NodeCapabilityAvailability.VoiceWakeEnabled,
      ),
      NodeCapabilitySpec(
        name = AndroidAssistantCapability.Location.rawValue,
        availability = NodeCapabilityAvailability.LocationEnabled,
      ),
      NodeCapabilitySpec(name = AndroidAssistantCapability.Photos.rawValue),
      NodeCapabilitySpec(name = AndroidAssistantCapability.Contacts.rawValue),
      NodeCapabilitySpec(name = AndroidAssistantCapability.Calendar.rawValue),
      NodeCapabilitySpec(
        name = AndroidAssistantCapability.Motion.rawValue,
        availability = NodeCapabilityAvailability.MotionAvailable,
      ),
    )

  val all: List<InvokeCommandSpec> =
    listOf(
      InvokeCommandSpec(
        name = AndroidAssistantCanvasCommand.Present.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCanvasCommand.Hide.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCanvasCommand.Navigate.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCanvasCommand.Eval.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCanvasCommand.Snapshot.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCanvasA2UICommand.Push.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCanvasA2UICommand.PushJSONL.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCanvasA2UICommand.Reset.rawValue,
        requiresForeground = true,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantSystemCommand.Notify.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCameraCommand.List.rawValue,
        requiresForeground = true,
        availability = InvokeCommandAvailability.CameraEnabled,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCameraCommand.Snap.rawValue,
        requiresForeground = true,
        availability = InvokeCommandAvailability.CameraEnabled,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCameraCommand.Clip.rawValue,
        requiresForeground = true,
        availability = InvokeCommandAvailability.CameraEnabled,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantLocationCommand.Get.rawValue,
        availability = InvokeCommandAvailability.LocationEnabled,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantDeviceCommand.Status.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantDeviceCommand.Info.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantDeviceCommand.Permissions.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantDeviceCommand.Health.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantNotificationsCommand.List.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantNotificationsCommand.Actions.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantPhotosCommand.Latest.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantContactsCommand.Search.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantContactsCommand.Add.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCalendarCommand.Events.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantCalendarCommand.Add.rawValue,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantMotionCommand.Activity.rawValue,
        availability = InvokeCommandAvailability.MotionActivityAvailable,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantMotionCommand.Pedometer.rawValue,
        availability = InvokeCommandAvailability.MotionPedometerAvailable,
      ),
      InvokeCommandSpec(
        name = AndroidAssistantSmsCommand.Send.rawValue,
        availability = InvokeCommandAvailability.SmsAvailable,
      ),
      InvokeCommandSpec(
        name = "debug.logs",
        availability = InvokeCommandAvailability.DebugBuild,
      ),
      InvokeCommandSpec(
        name = "debug.ed25519",
        availability = InvokeCommandAvailability.DebugBuild,
      ),
    )

  private val byNameInternal: Map<String, InvokeCommandSpec> = all.associateBy { it.name }

  fun find(command: String): InvokeCommandSpec? = byNameInternal[command]

  fun advertisedCapabilities(flags: NodeRuntimeFlags): List<String> {
    return capabilityManifest
      .filter { spec ->
        when (spec.availability) {
          NodeCapabilityAvailability.Always -> true
          NodeCapabilityAvailability.CameraEnabled -> flags.cameraEnabled
          NodeCapabilityAvailability.LocationEnabled -> flags.locationEnabled
          NodeCapabilityAvailability.SmsAvailable -> flags.smsAvailable
          NodeCapabilityAvailability.VoiceWakeEnabled -> flags.voiceWakeEnabled
          NodeCapabilityAvailability.MotionAvailable -> flags.motionActivityAvailable || flags.motionPedometerAvailable
        }
      }
      .map { it.name }
  }

  fun advertisedCommands(flags: NodeRuntimeFlags): List<String> {
    return all
      .filter { spec ->
        when (spec.availability) {
          InvokeCommandAvailability.Always -> true
          InvokeCommandAvailability.CameraEnabled -> flags.cameraEnabled
          InvokeCommandAvailability.LocationEnabled -> flags.locationEnabled
          InvokeCommandAvailability.SmsAvailable -> flags.smsAvailable
          InvokeCommandAvailability.MotionActivityAvailable -> flags.motionActivityAvailable
          InvokeCommandAvailability.MotionPedometerAvailable -> flags.motionPedometerAvailable
          InvokeCommandAvailability.DebugBuild -> flags.debugBuild
        }
      }
      .map { it.name }
  }
}

