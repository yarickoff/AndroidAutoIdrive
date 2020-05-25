package me.hufman.androidautoidrive

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import me.hufman.androidautoidrive.carapp.music.MusicAppMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestMusicAppMode {
	@Test
	fun testMusicAppManual() {
		val settings = mock<MutableAppSettings>() {
			on { get(AppSettings.KEYS.AUDIO_AUTOMATIC_CONTEXT) } doReturn "false"
			on { get(AppSettings.KEYS.AUDIO_ENABLE_CONTEXT) } doReturn "false"
		}
		val latency = mock<PercentileCounter>()
		val mode = MusicAppMode(settings, latency)
		assertFalse(mode.shouldRequestAudioContext())

		whenever(settings[AppSettings.KEYS.AUDIO_ENABLE_CONTEXT]) doReturn "true"
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testLatencySettling() {
		val settings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.AUDIO_AUTOMATIC_CONTEXT) } doReturn "true"
			on { get(AppSettings.KEYS.AUDIO_SUPPORTS_USB) } doReturn "false"
		}
		val latency = mock<PercentileCounter> {
			on { median() } doReturn 50
			on { size() } doReturn 0
		}
		val mode = MusicAppMode(settings, latency)
		assertFalse(mode.shouldRequestAudioContext())

		whenever(latency.size()) doReturn 40
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testLatencyBluetooth() {
		val settings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.AUDIO_AUTOMATIC_CONTEXT) } doReturn "true"
			on { get(AppSettings.KEYS.AUDIO_SUPPORTS_USB) } doReturn "false"
		}
		val latency = mock<PercentileCounter> {
			on { median() } doReturn 10
			on { size() } doReturn 10
		}
		val mode = MusicAppMode(settings, latency)
		assertFalse(mode.shouldRequestAudioContext())

		whenever(latency.median()) doReturn 40
		assertTrue(mode.shouldRequestAudioContext())
	}

	@Test
	fun testUSBSupport() {
		val settings = mock<MutableAppSettings> {
			on { get(AppSettings.KEYS.AUDIO_AUTOMATIC_CONTEXT) } doReturn "true"
			on { get(AppSettings.KEYS.AUDIO_SUPPORTS_USB) } doReturn "false"
		}
		val latency = mock<PercentileCounter> {
			on { median() } doReturn 10
			on { size() } doReturn 10
		}
		val mode = MusicAppMode(settings, latency)
		assertFalse(mode.shouldRequestAudioContext())

		whenever(settings[AppSettings.KEYS.AUDIO_SUPPORTS_USB]) doReturn "true"
		assertTrue(mode.shouldRequestAudioContext())
	}
}