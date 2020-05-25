package me.hufman.androidautoidrive

import org.junit.Assert.*
import org.junit.Test
import java.lang.Math.abs

class TestPerfCounters {
	@Test
	fun testTimeIt() {
		val counter = PercentileCounter()
		counter.timeIt { Thread.sleep(1000) }
		assertTrue(abs(counter.average()-1000) < 50)
	}

	@Test
	fun testPercentile() {
		// empty
		val counter = PercentileCounter()
		assertEquals("empty", 0, counter.median())
		assertEquals("empty", 0, counter.percentile(0.0))
		assertEquals("empty", 0, counter.percentile(1.0))

		// one item
		counter.addValue(5)
		assertEquals("single", 5, counter.median())
		assertEquals("single", 5, counter.percentile(0.0))
		assertEquals("single", 5, counter.percentile(1.0))

		// three items
		counter.addValue(2)
		counter.addValue(7)
		assertEquals("three", 5, counter.median())
		assertEquals("three", 2, counter.percentile(0.0))
		assertEquals("three", 7, counter.percentile(1.0))

		// five items
		counter.addValue(8)
		counter.addValue(3)
		assertEquals("five", 5, counter.median())
		assertEquals("five 0%", 2, counter.percentile(0.0))
		assertEquals("five 25%", 3, counter.percentile(0.25))
		assertEquals("five 50%", 5, counter.percentile(0.5))
		assertEquals("five 75%", 7, counter.percentile(0.75))
		assertEquals("five 100%", 8, counter.percentile(1.0))
	}
}