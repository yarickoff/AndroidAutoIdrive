package me.hufman.androidautoidrive.carapp.music

import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.MutableAppSettings
import me.hufman.androidautoidrive.PercentileCounter

/**
 * Logic to help decide when to use Audio Context
 *
 * The only allowed scenarios are:
 *     Bluetooth app connection
 *     USB app connection if the phone is running an OS earlier than Oreo
 *
 * To identify the USB mode, the AppProber is recording the latency of a short RPC call
 * and we decide USB vs Bluetooth based on that latency
 * AppProber has its own Etch connection, so it gets multiplexed by the BCL proxy and
 * shouldn't encounter any blocking-queue problems
 *
 * The risks of guessing wrong are:
 *     Latency is surprisingly low -> Incorrectly guessing USB -> Not requesting context
 *     Latency is surprisingly high -> Incorrectly guessing BT connection -> Requests a context which can't be heard
 * So, we want to be very sure that we are BT before ruling that we should request context
 */
class MusicAppMode(val appSettings: MutableAppSettings, val latency: PercentileCounter) {
	fun isUSBConnection(): Boolean {
		// USB is usually between 10-20 and can get up to 40 when first starting
		// Bluetooth idles around 35 but can spike up to 200+
		return latency.median() < 30
	}
	fun supportsUsbAudio(): Boolean {
		return appSettings[AppSettings.KEYS.AUDIO_SUPPORTS_USB].toBoolean()
	}
	fun shouldRequestAudioContext(): Boolean {
		val automaticContext = appSettings[AppSettings.KEYS.AUDIO_AUTOMATIC_CONTEXT].toBoolean()
		if (!automaticContext) {
			return appSettings[AppSettings.KEYS.AUDIO_ENABLE_CONTEXT].toBoolean()
		}
		if (latency.size() < 5) {
			// Wait for the connection to settle before deciding
			return false
		}
		val isUSBConnection = isUSBConnection()
		val useUSB = supportsUsbAudio() && isUSBConnection
		val useBT = !isUSBConnection
		return useUSB || useBT
	}
}