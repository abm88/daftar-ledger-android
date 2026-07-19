package com.daftar.app.data.remote

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Converts Android-only content URIs into portable data URLs accepted by the API. */
@Singleton
class PhotoPayloadEncoder @Inject constructor(
    private val contentResolver: ContentResolver,
) {
    fun encode(values: List<String>): List<String> = values.take(MAX_PHOTOS).map { value ->
        if (value.startsWith("data:") || value.startsWith("https://") || value.startsWith("http://")) {
            value
        } else {
            val uri = Uri.parse(value)
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Unable to read photo attachment")
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
    }

    private companion object {
        const val MAX_PHOTOS = 10
    }
}
