package me.hufman.androidautoidrive

import com.earnstone.perf.AvgCounter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun <T> AvgCounter.timeIt(callable: () -> T): T {
	val startTime = System.nanoTime()
	val ret = callable()
	val endTime = System.nanoTime()
	val duration = (endTime - startTime) / 1000000
	this.addValue(duration)
	return ret
}

class PercentileCounter: AvgCounter() {
	fun size(): Int {
		val samplesArray = this.items
		return Array(samplesArray.length()) { index -> samplesArray[index]}.count { value -> value != Long.MIN_VALUE }
	}

	fun median(): Long {
		val samplesArray = this.items
		val sortedArray = Array(samplesArray.length()) { index -> samplesArray[index] }
		sortedArray.sort()

		val startIndex = sortedArray.indexOfFirst { value -> value != Long.MIN_VALUE }
		// no samples are recorded
		if (startIndex < 0) {
			return 0
		}
		// startIndex is the first index of data
		val finalIndex = sortedArray.size - 1
		val medianIndex = (finalIndex - startIndex) / 2 + startIndex
		return sortedArray[medianIndex]
	}

	/**
	 * Percentile should be between 0.0 and 1.0
	 */
	fun percentile(percentile: Double): Long {
		val samplesArray = this.items
		val sortedArray = Array(samplesArray.length()) { index -> samplesArray[index] }
		sortedArray.sort()

		val startIndex = sortedArray.indexOfFirst { value -> value != Long.MIN_VALUE }
		// no samples are recorded
		if (startIndex < 0) {
			return 0
		}
		// startIndex is the first index of data
		val finalIndex = sortedArray.size - 1
		var medianIndex = ((finalIndex - startIndex) * percentile).roundToInt() + startIndex
		medianIndex = max(0, medianIndex)
		medianIndex = min(medianIndex, finalIndex)
		return sortedArray[medianIndex]
	}

	override fun getValue(): Double {
		return median().toDouble()
	}
}