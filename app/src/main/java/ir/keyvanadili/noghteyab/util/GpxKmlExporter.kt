package ir.keyvanadili.noghteyab.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ir.keyvanadili.noghteyab.data.TrackEntity
import ir.keyvanadili.noghteyab.data.TrackPointEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GpxKmlExporter {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun buildGpx(track: TrackEntity, points: List<TrackPointEntity>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="NoghteYab" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(track.name)).append("</name>\n")
        sb.append("    <trkseg>\n")
        for (p in points) {
            sb.append("""      <trkpt lat="${p.latitude}" lon="${p.longitude}">""").append('\n')
            sb.append("        <time>").append(isoFormat.format(Date(p.timestamp))).append("</time>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun buildKml(track: TrackEntity, points: List<TrackPointEntity>): String {
        val coords = points.joinToString(separator = " ") { "${it.longitude},${it.latitude},0" }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append('\n')
        sb.append("  <Document>\n")
        sb.append("    <name>").append(escapeXml(track.name)).append("</name>\n")
        sb.append("    <Placemark>\n")
        sb.append("      <name>").append(escapeXml(track.name)).append("</name>\n")
        sb.append("      <LineString>\n")
        sb.append("        <tessellate>1</tessellate>\n")
        sb.append("        <coordinates>").append(coords).append("</coordinates>\n")
        sb.append("      </LineString>\n")
        sb.append("    </Placemark>\n")
        sb.append("  </Document>\n")
        sb.append("</kml>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** Writes the given content to a shareable file under the app's cache and returns a content:// Uri. */
    private fun writeShareableFile(context: Context, fileName: String, content: String): android.net.Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareGpx(context: Context, track: TrackEntity, points: List<TrackPointEntity>) {
        val content = buildGpx(track, points)
        val uri = writeShareableFile(context, exportFileName(track, "gpx"), content)
        shareFile(context, uri, "application/gpx+xml")
    }

    fun shareKml(context: Context, track: TrackEntity, points: List<TrackPointEntity>) {
        val content = buildKml(track, points)
        val uri = writeShareableFile(context, exportFileName(track, "kml"), content)
        shareFile(context, uri, "application/vnd.google-earth.kml+xml")
    }

    private fun shareFile(context: Context, uri: android.net.Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, null)
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /** ASCII-only file name, independent of the (Persian) display name shown inside the app. */
    private fun exportFileName(track: TrackEntity, extension: String): String {
        return "track_${fileNameFormat.format(Date(track.startTime))}.$extension"
    }
}
