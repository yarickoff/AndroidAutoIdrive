package me.hufman.androidautoidrive.carapp.music

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import me.hufman.androidautoidrive.GraphicsHelpers

class KJUMultimedia(val context: Context, val graphicsHelpers: GraphicsHelpers, val packageName: String) {
	fun setMultimediaInfo(artist: String, album: String, title: String) {
		val action = "$packageName#setMultimediaInfo(java.lang.String arg0, java.lang.String arg1, java.lang.String arg2)"
		val intent = Intent(action)
		intent.putExtra("arg0", artist)
		intent.putExtra("arg1", album)
		intent.putExtra("arg2", title)
		context.sendBroadcast(intent)
	}
	fun setMultimediaInfoCover(bitmap: Bitmap) {
		val action = "$packageName#setMultimediaInfoCover(byte[] arg0)"
		val intent = Intent(action)
		intent.putExtra("arg0", graphicsHelpers.compress(bitmap, 320, 320))
		context.sendBroadcast(intent)
	}
	fun setMultimediaInfoProgress(percentage: Int) {
		val action = "$packageName#setMultimediaInfoProgress(int arg0)"
		val intent = Intent(action)
		intent.putExtra("arg0", percentage)
		context.sendBroadcast(intent)
	}
}