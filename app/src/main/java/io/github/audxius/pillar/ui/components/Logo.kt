package io.github.audxius.pillar.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.sqrt

/** Crops away fully transparent margins so every logo is measured by its visible artwork. */
class TrimTransparent : Transformation() {
    override val cacheKey: String = "trim-transparent-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val px = IntArray(w * h)
        input.getPixels(px, 0, w, 0, 0, w, h)
        var top = h; var bottom = -1; var left = w; var right = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > 24) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        if (bottom < 0) return input
        val cw = right - left + 1
        val ch = bottom - top + 1
        if (cw == w && ch == h) return input
        return Bitmap.createBitmap(input, left, top, cw, ch)
    }
}

/**
 * A game logo sized by visual weight: every logo gets roughly the same area, so a long wordmark
 * and a tall emblem look equally prominent, within [maxWidth] x [maxHeight].
 */
@Composable
fun GameLogo(
    url: String,
    contentDescription: String,
    maxWidth: Dp,
    maxHeight: Dp,
    area: Float = 13000f,
    alignment: Alignment = Alignment.CenterStart,
    modifier: Modifier = Modifier,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    var aspect by remember(url) { mutableStateOf<Float?>(null) }
    val request = remember(url) {
        // Capped decode: logos never show larger than this, and trimming a huge bitmap is slow.
        ImageRequest.Builder(context).data(url).size(900, 900).allowHardware(false).transformations(TrimTransparent()).build()
    }
    // Width x height = area, width / height = aspect, then shrink to fit the box if needed.
    val sized = aspect?.let { a ->
        var h = sqrt(area / a)
        var w = h * a
        val scale = minOf(1f, maxWidth.value / w, maxHeight.value / h)
        w *= scale; h *= scale
        Modifier.size(w.dp, h.dp)
    } ?: Modifier.size(min(maxWidth, 200.dp), 1.dp)
    AsyncImage(
        request,
        contentDescription,
        contentScale = ContentScale.Fit,
        alignment = alignment,
        onSuccess = { s ->
            val img = s.result.image
            if (img.height > 0) aspect = img.width.toFloat() / img.height
        },
        onError = { onFailed() },
        modifier = modifier.then(sized),
    )
}
