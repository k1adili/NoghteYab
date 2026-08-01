package ir.keyvanadili.noghteyab.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.keyvanadili.noghteyab.data.GeoPoint

object MapsUtil {

    /** Opens the point location directly in Google Maps (or any maps app). */
    fun openInMaps(context: Context, point: GeoPoint) {
        val label = Uri.encode(point.name.ifBlank { "نقطه" })
        val uri = Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}($label)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback: no Google Maps installed, use generic geo intent
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    /** Shares raw coordinates as text (e.g. via any app, Telegram, SMS...). */
    fun shareCoordinates(context: Context, point: GeoPoint) {
        val text = "${point.name}\nhttps://maps.google.com/?q=${point.latitude},${point.longitude}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
