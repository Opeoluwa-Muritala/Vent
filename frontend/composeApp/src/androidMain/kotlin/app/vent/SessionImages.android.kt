package app.vent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
internal actual fun rememberSessionImagePicker(onRead: (String?) -> Unit): (() -> Unit)? {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    val image =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes = resolver.openInputStream(uri)?.use { it.readNBytes(8_000_001) }
                                if (bytes == null || bytes.size > 8_000_000) null else encodeHeader(bytes)
                            }.getOrNull()
                        }
                    onRead(image)
                }
            }
        }
    return { launcher.launch("image/*") }
}

private fun encodeHeader(bytes: ByteArray): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..16_384 || bounds.outHeight !in 1..16_384) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 960) sample *= 2
    val bitmap =
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
    return try {
        ByteArrayOutputStream().use { output ->
            for (quality in listOf(85, 65, 45)) {
                output.reset()
                if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output) && output.size() <= 135_000) {
                    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                }
            }
            null
        }
    } finally {
        bitmap.recycle()
    }
}

@Composable
internal actual fun SessionHeaderImage(
    image: String?,
    modifier: Modifier,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image) {
        value =
            withContext(Dispatchers.Default) {
                runCatching {
                    image?.takeIf { it.length <= 180_000 }?.let {
                        val bytes = Base64.decode(it, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }
    val header = bitmap
    if (header == null) {
        DefaultSessionHeader(modifier)
    } else {
        Image(header, null, modifier, contentScale = ContentScale.Crop)
    }
}
