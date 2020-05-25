package me.hufman.androidautoidrive.phoneui

import com.earnstone.perf.Registry
import java.util.concurrent.ConcurrentHashMap

object DebugStatus {
	val carCapabilities = ConcurrentHashMap<String, String>()
	val performanceCounters = Registry()
}