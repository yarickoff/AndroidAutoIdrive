package me.hufman.androidautoidrive.phoneui

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlinx.android.synthetic.main.activity_navintent.*
import me.hufman.androidautoidrive.R
import me.hufman.androidautoidrive.carapp.navigation.AndroidGeocoderSearcher
import me.hufman.androidautoidrive.carapp.navigation.NavigationParser
import me.hufman.androidautoidrive.carapp.navigation.NavigationTriggerSender

class NavIntentActivity: Activity() {
	companion object {
		val TAG = "NavActivity"
		val INTENT_NAV_SUCCESS = "me.hufman.androidautoidrive.NavIntentActivity.SUCCESS"

		val TIMEOUT = 8000L
		val SUCCESS = 1000L
	}

	class ParserTask(val parent: NavIntentActivity): AsyncTask<String, Unit, String?>() {
		val parser = NavigationParser(AndroidGeocoderSearcher(parent))

		override fun doInBackground(vararg p0: String?): String? {
			val url = p0.getOrNull(0) ?: return null
			try {
				val rhmi = parser.parseUrl(url)
				Log.i(TAG, "Parsed $url into car nav $rhmi")
				return rhmi
			} catch (e: Exception) {
				return null
			}
		}

		override fun onPostExecute(result: String?) {
			if (result == null) {
				parent.onParseFailure()
			} else {
				NavigationTriggerSender(parent).triggerNavigation(result)
				parent.onBegin()
			}
		}
	}

	var parsingTask: ParserTask? = null
	var listening = false
	val successListener = object: BroadcastReceiver() {
		override fun onReceive(p0: Context?, p1: Intent?) {
			if (p1?.action == INTENT_NAV_SUCCESS) {
				onSuccess()
			}
		}
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		val params = window.decorView.layoutParams as WindowManager.LayoutParams
		params.alpha = 1f
		params.dimAmount = 0.2f
		window.attributes = params

		val display = windowManager.defaultDisplay
		val size = Point()
		display.getSize(size)

		window.setLayout((0.7 * size.x).toInt(), (0.5 * size.y).toInt())
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContentView(R.layout.activity_navintent)
	}

	override fun onResume() {
		super.onResume()
		val url = intent?.data
		if (url != null) {
			txtNavLabel.text = getText(R.string.lbl_navigation_listener_searching)
			txtNavError.text = ""
			prgNavSpinner.visible = true
			prgNavSpinner.isIndeterminate = true
			parsingTask = ParserTask(this)
			parsingTask?.execute(url.toString())
		}
	}

	fun onParseFailure() {
		txtNavLabel.text = getString(R.string.lbl_navigation_listener_parsefailure)
		txtNavError.text = intent?.data?.toString() ?: ""
		prgNavSpinner.visible = false
		prgNavSpinner.isIndeterminate = false
		prgNavSpinner.progress = 0
	}

	fun onBegin() {
		registerReceiver(successListener, IntentFilter(INTENT_NAV_SUCCESS))
		listening = true

		txtNavLabel.text = getText(R.string.lbl_navigation_listener_pending)
		txtNavError.text = ""

		Handler().postDelayed({
			finish()
		}, TIMEOUT)
	}

	fun onSuccess() {
		txtNavLabel.text = getText(R.string.lbl_navigation_listener_success)
		prgNavSpinner.isIndeterminate = false
		prgNavSpinner.progress = 0

		val animation = ObjectAnimator.ofInt(prgNavSpinner, "progress", prgNavSpinner.progress, prgNavSpinner.max)
		animation.duration = 500
		animation.interpolator = DecelerateInterpolator()
		animation.start()

		Handler().postDelayed({
			finish()
		}, SUCCESS)
	}

	override fun onPause() {
		super.onPause()
		parsingTask?.cancel(false)
		if (listening) {
			unregisterReceiver(successListener)
			listening = false
		}
	}
}