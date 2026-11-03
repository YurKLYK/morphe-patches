package app.morphe.patches.youtube.music

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.shared.misc.settings.preference.TextPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.onCreateHook
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.patches.youtube.video.videoid.hookVideoId
import app.morphe.patches.youtube.video.videoid.videoIdPatch
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "Lapp/morphe/extension/youtube/music/SpeedLinkedMusicRuntime;"

private val speedLinkedMusicResourcePatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            listOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_LOCATION",
                "android.permission.POST_NOTIFICATIONS",
            ).forEach { permission ->
                val exists = document.getElementsByTagName("uses-permission").let { nodes ->
                    (0 until nodes.length).any { (nodes.item(it) as Element).getAttribute("android:name") == permission }
                }
                if (!exists) document.createElement("uses-permission").also {
                    it.setAttribute("android:name", permission)
                    manifest.insertBefore(it, manifest.firstChild)
                }
            }
            val services = document.getElementsByTagName("service")
            val serviceName = "app.morphe.extension.youtube.music.SpeedLocationForegroundService"
            val hasService = (0 until services.length).any { (services.item(it) as Element).getAttribute("android:name") == serviceName }
            if (!hasService) document.createElement("service").also {
                it.setAttribute("android:name", serviceName)
                it.setAttribute("android:exported", "false")
                it.setAttribute("android:foregroundServiceType", "location")
                manifest.appendChild(it)
            }
        }
    }
}

val speedLinkedMusicPatch = bytecodePatch(
    name = "Speed-linked music mode",
    description = "Adds an opt-in, device-local speed source and stable video-switching bridge for music mode.",
) {
    dependsOn(
        speedLinkedMusicResourcePatch,
        sharedExtensionPatch,
        videoInformationPatch,
        videoIdPatch,
        settingsPatch,
    )
    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        PreferenceScreen.MISC.addPreferences(
            PreferenceScreenPreference(
                key = "morphe_speed_linked_music_screen",
                sorting = PreferenceScreenPreference.Sorting.UNSORTED,
                preferences = setOf(
                    SwitchPreference("morphe_speed_linked_music_enabled"),
                    TextPreference(
                        key = "morphe_speed_linked_music_status",
                        tag = "app.morphe.extension.youtube.music.SpeedLinkedMusicStatusPreference",
                    ),
                    TextPreference("morphe_speed_linked_music_set"),
                    TextPreference("morphe_speed_linked_music_bands"),
                    TextPreference(
                        key = "morphe_speed_linked_music_register_current",
                        tag = "app.morphe.extension.youtube.music.SpeedLinkedMusicRegisterPreference",
                    ),
                    TextPreference("morphe_speed_linked_music_smoothing_ms"),
                    TextPreference("morphe_speed_linked_music_confirmation_ms"),
                    TextPreference("morphe_speed_linked_music_cooldown_ms"),
                    TextPreference("morphe_speed_linked_music_hysteresis_kmh"),
                    TextPreference("morphe_speed_linked_music_debug_speed_ms"),
                    TextPreference("morphe_speed_linked_music_unknown_behavior"),
                    TextPreference("morphe_speed_linked_music_normal_video_id"),
                )
            )
        )
        onCreateHook(EXTENSION_CLASS, "initialize")
        hookVideoId("$EXTENSION_CLASS->onVideoIdChanged(Ljava/lang/String;)V")
    }
}
