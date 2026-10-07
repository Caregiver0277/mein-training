package de.beispiel.meintraining.ui.stats

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Der Ordner im Zwischenspeicher, aus dem geteilt wird – derselbe wie in res/xml/share_paths.xml. */
private const val SHARE_DIR = "geteilt"
private const val SHARE_FILE = "rueckblick.png"
private const val PNG_QUALITY = 100

/**
 * Legt das Bild des Rückblicks im Zwischenspeicher ab und öffnet das Android-Teilen-Menü.
 *
 * Immer dieselbe Datei: Ein geteiltes Bild braucht nur so lange zu leben, bis die andere App es
 * gelesen hat, und so sammeln sich keine alten an. Der Zwischenspeicher kommt auch nicht in die
 * Gerätesicherung. Freigegeben wird nur diese Datei, nur zum Lesen und nur für diesen Vorgang –
 * über den FileProvider, ohne eine Berechtigung und ohne Internet.
 */
suspend fun shareReviewImage(context: Context, bitmap: Bitmap, chooserTitle: String) {
    val uri = withContext(Dispatchers.IO) { writeShareImage(context, bitmap) }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        // Auch als ClipData: Erst damit gilt die Leseerlaubnis schon für die Vorschau im Menü.
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

private fun writeShareImage(context: Context, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
    val file = File(dir, SHARE_FILE)
    // Ein Bild aus dem Grafikspeicher lässt sich nicht überall auslesen; eine Kopie schon.
    val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    }
    file.outputStream().use { readable.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
