package me.hufman.androidautoidrive

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


class CarProberAudio {
	val TAG = "CarProberAudio"

	val probeResults = ArrayBlockingQueue<Pair<Int, Boolean>>(20)
	val activeProbes = ConcurrentHashMap<Int, Int>()  // map of avHandle to InstanceId probed
	val successProbes = HashSet<Int>()

	fun guessPorts(suggestions: Array<Int>): Iterable<Int> {
		val ports = LinkedHashSet<Int>()
		ports.addAll(suggestions)
		suggestions.forEach {
			ports.add(it - 1)
			ports.add(it + 1)
			ports.add(it - 2)
			ports.add(it + 2)
		}
		ports.addAll(12..13)
		ports.addAll(11..14)
		ports.addAll(5..25)
		return ports
	}

	fun probe(carConnection: BMWRemotingServer, suggestions: Array<Int> = arrayOf()): Int? {
		try {
			for (port in guessPorts(suggestions)) {
				synchronized(this) {
					val avHandle = carConnection.av_create(port, "me.hufman.androidautoidrive.CarProberAudio$port")
					activeProbes[avHandle] = port
					Log.d(TAG, "Starting audio probe for instanceId $port avHandle $avHandle")
					carConnection.av_requestConnection(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_INTERRUPT)
				}

				// pull off any probe results that have come back
				val result = probeResults.poll(100, TimeUnit.MILLISECONDS)
				handleResult(carConnection, result)
				var extraResult = probeResults.poll()
				while (extraResult != null) {
					handleResult(carConnection, extraResult)
					extraResult = probeResults.poll()
				}

				// if we found a connection, stop looking
				if (successProbes.isNotEmpty()) {
					break
				}
			}
		} catch (e: Exception) {
			Log.w(TAG, "Exception while probing for audio InstanceID", e)
		}
		Log.i(TAG, "Probed to find instanceId: ${successProbes.firstOrNull()} $successProbes")
		return successProbes.firstOrNull()
	}

	private fun handleResult(carConnection: BMWRemotingServer, result: Pair<Int, Boolean>?) {
		if (result == null) {
			// the car didn't reply in time, weird
			Log.i(TAG, "Car didn't reply with audio probe result within 100ms, moving on")
		} else {
			val avHandle = result.first
			val instanceId = activeProbes[avHandle]
			val success = result.second
			Log.i(TAG, "Car replied with audio probe result for (avHandle $avHandle) instanceId $instanceId: $success")
			synchronized(this) {
				if (instanceId != null && success) {
					successProbes.add(instanceId)
				}

				activeProbes.remove(avHandle)
			}
			if (success) {
				Log.d(TAG, "Closing avHandle $avHandle")
				carConnection.av_closeConnection(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_INTERRUPT)
			}
			Log.d(TAG, "Disposing avHandle $avHandle")
			carConnection.av_dispose(avHandle)
		}
	}

	internal fun reportResult(avHandle: Int, result: Boolean) {
		Log.d(TAG, "Car replied with audio probe result for avHandle $avHandle: $result")
		probeResults.put(avHandle to result)
	}
}

class CarProberAudioCallback(val prober: CarProberAudio): BaseBMWRemotingClient() {
	override fun av_connectionGranted(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		Log.d(TAG, "av_connectionGranted($handle, $connectionType)")
		if (handle != null) {
			prober.reportResult(handle, true)
		}
	}

	override fun av_connectionDenied(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		Log.d(TAG, "av_connectionDenied($handle, $connectionType)")
		if (handle != null) {
			prober.reportResult(handle, false)
		}
	}

	override fun av_connectionDeactivated(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
		Log.d(TAG, "av_connectionDeactivated($handle, $connectionType)")
	}

	override fun av_requestPlayerState(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
		Log.d(TAG, "av_requestPlayerState($handle, $connectionType, $playerState)")
	}
}