package me.hufman.androidautoidrive.carapp.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.androidautoidrive.*
import me.hufman.androidautoidrive.carapp.RHMIActionAbort
import me.hufman.androidautoidrive.carapp.RHMIUtils
import me.hufman.androidautoidrive.carapp.notifications.views.DetailsView
import me.hufman.androidautoidrive.carapp.notifications.views.NotificationListView
import me.hufman.androidautoidrive.carapp.notifications.views.PopupView
import me.hufman.androidautoidrive.notifications.*
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationIdempotent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationSynchronized
import me.hufman.idriveconnectionkit.android.CarAppResources
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.security.SecurityAccess
import me.hufman.idriveconnectionkit.rhmi.*
import java.lang.RuntimeException
import java.util.*

const val TAG = "PhoneNotifications"

class PhoneNotifications(val securityAccess: SecurityAccess, val carAppAssets: CarAppResources, val phoneAppResources: PhoneAppResources, val graphicsHelpers: GraphicsHelpers, val controller: CarNotificationController, val audioPlayer: AudioPlayer, val notificationSettings: NotificationSettings) {
	val notificationListener = PhoneNotificationListener(this)
	val notificationReceiver = NotificationUpdaterControllerIntent.Receiver(notificationListener)
	var notificationBroadcastReceiver: BroadcastReceiver? = null
	var readoutInteractions: ReadoutInteractions
	val carappListener = CarAppListener()
	val carConnection: BMWRemotingServer
	val carApp: RHMIApplicationSynchronized
	val amHandle: Int
	val focusEvent: RHMIEvent.FocusEvent
	val readHistory = PopupHistory()       // suppress any duplicate New Notification actions
	val viewPopup: PopupView                // notification about notification
	val viewList: NotificationListView      // show a list of active notifications
	val viewDetails: DetailsView            // view a notification with actions to do
	val stateInput: RHMIState.PlainState    // show a reply input form

	var passengerSeated = false             // whether a passenger is seated

	init {
		carConnection = IDriveConnection.getEtchConnection(IDriveConnectionListener.host ?: "127.0.0.1", IDriveConnectionListener.port ?: 8003, carappListener)
		val appCert = carAppAssets.getAppCertificate(IDriveConnectionListener.brand ?: "")?.readBytes() as ByteArray
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_login = securityAccess.signChallenge(challenge=sas_challenge)
		carConnection.sas_login(sas_login)
		carappListener.server = carConnection

		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("me.hufman.androidautoidrive", BMWRemoting.VersionInfo(0, 1, 0), "me.hufman.androidautoidrive", "me.hufman"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppAssets.getUiDescription())
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppAssets.getTextsDB(IDriveConnectionListener.brand ?: "common"))
		RHMIUtils.rhmi_setResourceCached(carConnection, rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppAssets.getImagesDB(IDriveConnectionListener.brand ?: "common"))
		carConnection.rhmi_initialize(rhmiHandle)

		readoutInteractions = ReadoutInteractions(notificationSettings)

		// set up the app in the car
		carApp = RHMIApplicationSynchronized(RHMIApplicationIdempotent(RHMIApplicationEtch(carConnection, rhmiHandle)), carConnection)
		carappListener.app = carApp
		carApp.loadFromXML(carAppAssets.getUiDescription()?.readBytes() as ByteArray)

		val unclaimedStates = LinkedList(carApp.states.values)

		// figure out which views to use
		viewPopup = PopupView(unclaimedStates.removeFirst { PopupView.fits(it) }, phoneAppResources)
		viewList = NotificationListView(unclaimedStates.removeFirst { NotificationListView.fits(it) }, phoneAppResources, graphicsHelpers, notificationSettings, readoutInteractions)
		viewDetails = DetailsView(unclaimedStates.removeFirst { DetailsView.fits(it) }, phoneAppResources, graphicsHelpers, controller, readoutInteractions)

		stateInput = carApp.states.values.filterIsInstance<RHMIState.PlainState>().first{
			it.componentsList.filterIsInstance<RHMIComponent.Input>().isNotEmpty()
		}

		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = viewList.state.id
			it.getAction()?.asRAAction()?.rhmiActionCallback = RHMIActionButtonCallback {
				viewList.entryButtonTimestamp = System.currentTimeMillis()
			}
		}

		// set up the AM icon in the "Calendar"/Communications section
		amHandle = carConnection.am_create("0", "\u0000\u0000\u0000\u0000\u0000\u0002\u0000\u0000".toByteArray())
		carConnection.am_addAppEventHandler(amHandle, "me.hufman.androidautoidrive.notifications")
		focusEvent = carApp.events.values.filterIsInstance<RHMIEvent.FocusEvent>().first()
		createAmApp()

		// set up the list
		viewList.initWidgets(viewDetails)

		// set up the popup
		viewPopup.initWidgets()

		// set up the details view
		viewDetails.initWidgets(viewList, stateInput)

		// subscribe to CDS for passenger seat info
		val cdsHandle = carConnection.cds_create()
		val interestingProperties = mapOf("76" to "sensors.seatOccupiedPassenger",
				"37" to "driving.gear",
				"40" to "driving.parkingBrake")
		interestingProperties.entries.forEach {
			val ident = it.key
			val name = it.value
			carConnection.cds_addPropertyChangedEventHandler(cdsHandle, name, ident, 5000)
			carConnection.cds_getPropertyAsync(cdsHandle, ident, name)
		}

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "me.hufman.androidautoidrive.notifications", -1, -1)
	}

	fun createAmApp() {
		val name = L.NOTIFICATIONS_TITLE
		val carAppImages = Utils.loadZipfile(carAppAssets.getImagesDB(IDriveConnectionListener.brand ?: "common"))

		val amInfo = mutableMapOf<Int, Any>(
				0 to 145,   // basecore version
				1 to name,  // app name
				2 to (carAppImages["157.png"] ?: ""),
				3 to "Addressbook",   // section
				4 to true,
				5 to 800,   // weight
				8 to viewList.state.id  // mainstateId
		)
		// language translations, dunno which one is which
		for (languageCode in 101..123) {
			amInfo[languageCode] = name
		}

		synchronized(carConnection) {
			carConnection.am_registerApp(amHandle, "androidautoidrive.notifications", amInfo)
		}
	}

	inner class CarAppListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		var app: RHMIApplication? = null

		override fun am_onAppEvent(handle: Int?, ident: String?, appId: String?, event: BMWRemoting.AMEvent?) {
			try {
				focusEvent.triggerEvent(mapOf(0.toByte() to viewList.state.id))
			} catch (e: BMWRemoting.ServiceException) {
				Log.w(TAG, "Failed to trigger focus event for AM icon: $e")
			}
			createAmApp()
		}

		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			try {
				app?.actions?.get(actionId)?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			} catch (e: RHMIActionAbort) {
				// Action handler requested that we don't claim success
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, false)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler! $e")
				synchronized(server!!) {
					server?.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.w(TAG, msg)

			val state = app?.states?.get(componentId)
			state?.onHmiEvent(eventId, args)

			val component = app?.components?.get(componentId)
			component?.onHmiEvent(eventId, args)
		}

		override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
			val propertyData = loadJSON(propertyValue) ?: return

			if (propertyName == "sensors.seatOccupiedPassenger") {
				passengerSeated = propertyData.getInt("seatOccupiedPassenger") != 0
				readoutInteractions.passengerSeated = propertyData.getInt("seatOccupiedPassenger") != 0
			}
			if (propertyName == "driving.gear") {
				val GEAR_PARK = 3
				if (propertyData.getInt("gear") == GEAR_PARK) {
					viewDetails.lockSpeedLock()
				}
			}
			if (propertyName == "driving.parkingBrake") {
				val APPLIED_BRAKES = setOf(2, 8, 32)
				if (APPLIED_BRAKES.contains(propertyData.getInt("parkingBrake"))) {
					viewDetails.lockSpeedLock()
				}
			}
		}
	}

	fun onCreate(context: Context, handler: Handler) {
		Log.i(TAG, "Registering car thread listeners for notifications")
		val notificationBroadcastReceiver = this.notificationBroadcastReceiver ?: object: BroadcastReceiver() {
			override fun onReceive(p0: Context?, p1: Intent?) {
				p1 ?: return
				notificationReceiver.onReceive(p1)
			}
		}
		this.notificationBroadcastReceiver = notificationBroadcastReceiver
		notificationReceiver.register(context, notificationBroadcastReceiver, handler)

		viewList.onCreate(handler)
	}
	fun onDestroy(context: Context) {
		val notificationReceiver = this.notificationBroadcastReceiver
		if (notificationReceiver != null) {
			context.unregisterReceiver(notificationReceiver)
		}
		try {
			Log.i(TAG, "Trying to shut down etch connection")
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}

	/** All open, so that we can mock them in tests */
	open inner class PhoneNotificationListener(val phoneNotifications: PhoneNotifications): NotificationUpdaterController {
		override fun onNewNotification(key: String) {
			val sbn = NotificationsState.getNotificationByKey(key) ?: return
			onNotification(sbn)
		}

		fun onNotification(sbn: CarNotification) {
			Log.i(TAG, "Received a new notification to show in the car: $sbn")

			// only show if we haven't popped it before
			val alreadyShown = readHistory.contains(sbn)
			readHistory.add(sbn)
			if (!alreadyShown) {
				viewList.showStatusBarIcon()

				if (notificationSettings.shouldPopup(passengerSeated)) {
					if (!sbn.equalsKey(viewDetails.selectedNotification)) {
						viewPopup.showNotification(sbn)
					}
				}

				val played = if (notificationSettings.shouldPlaySound()) {
					audioPlayer.playRingtone(sbn.soundUri)
				} else false

				if (notificationSettings.shouldReadoutNotificationPopup(passengerSeated) && played) {
					Thread.sleep(3000)
				}
				readoutInteractions.triggerPopupReadout(sbn)
			}
		}

		override fun onUpdatedList() {
			Log.i(TAG, "Received a list of new notifications to show")
			viewList.gentlyUpdateNotificationList()

			viewDetails.redraw()

			// clear out any popped notifications that don't exist anymore
			val currentNotifications = NotificationsState.cloneNotifications()
			readHistory.retainAll(currentNotifications)

			// if the notification we popped up disappeared, clear the popup
			if (currentNotifications.find { it.key == viewPopup.currentNotification?.key } == null) {
				viewPopup.hideNotification()
			}

			// cancel the notification readout if it goes away
			if (currentNotifications.find { it.key == readoutInteractions.currentNotification?.key } == null) {
				readoutInteractions.cancel()
			}
		}
	}
}