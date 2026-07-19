package com.daftar.app.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Inter
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Max receipt photos per transaction (v20). */
const val MAX_TX_PHOTOS = 10

// Decode a content:// image, optionally downsampled so thumbnails stay light.
private fun loadBitmap(context: Context, uri: String, maxDim: Int): ImageBitmap? = try {
    val parsed = Uri.parse(uri)
    if (maxDim <= 0) {
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it)?.asImageBitmap()
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest > 0 && largest / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
        }
    }
} catch (_: Exception) {
    null // Missing file / revoked permission — the caller shows a placeholder.
}

/** Loads a content-URI image off the main thread; null while loading or on failure. */
@Composable
fun rememberImageBitmap(uri: String, maxDim: Int = 0): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, maxDim) {
        value = withContext(Dispatchers.IO) { loadBitmap(context, uri, maxDim) }
    }
    return bitmap
}

/**
 * Receipt-photo section shown on You Received / You Gave and the saved detail:
 * a 5-per-row thumbnail grid (each removable when [editable]) up to
 * [MAX_TX_PHOTOS], with an add-more tile, or a single "Add photo" CTA when empty.
 */
@Composable
fun PhotoAttachmentSection(
    uris: List<String>,
    editable: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit = {},
    onRemove: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MonoLabel("Photos · انځورونه", fontSize = 9, letterSpacing = 0.15)
        Spacer(Modifier.height(8.dp))

        if (uris.isEmpty()) {
            if (editable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .dashedBorder(DaftarColors.LineDashed, 1.5.dp, 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAdd)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Image, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Add photo · انځور",
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = DaftarColors.InkSoft),
                    )
                }
            } else {
                Text(
                    "No photos attached",
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
                )
            }
            return
        }

        // Grid — 5 columns; pad short rows with empty cells so squares stay aligned.
        val showAddTile = editable && uris.size < MAX_TX_PHOTOS
        val cells: List<String?> = uris + (if (showAddTile) listOf(null) else emptyList())
        cells.chunked(5).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowCells.forEach { uri ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        if (uri == null) {
                            AddMoreTile(onAdd)
                        } else {
                            PhotoThumb(uri, editable, onOpen = { onOpen(uri) }, onRemove = { onRemove(uri) })
                        }
                    }
                }
                repeat(5 - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PhotoThumb(uri: String, editable: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val bitmap = rememberImageBitmap(uri, maxDim = 256)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(DaftarColors.PaperDeep)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Attached photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Rounded.Image, null, tint = DaftarColors.MutedLight, modifier = Modifier.size(18.dp))
        }
        if (editable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(DaftarColors.Ink)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, null, tint = DaftarColors.Paper, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun AddMoreTile(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dashedBorder(DaftarColors.LineDashed, 1.5.dp, 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = DaftarColors.InkSoft, modifier = Modifier.size(18.dp))
    }
}

/** Tap-a-thumbnail full-screen viewer. */
@Composable
fun FullScreenPhotoViewer(uri: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = rememberImageBitmap(uri, maxDim = 1600)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            } else {
                Icon(Icons.Rounded.Image, null, tint = DaftarColors.MutedLight, modifier = Modifier.size(48.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
