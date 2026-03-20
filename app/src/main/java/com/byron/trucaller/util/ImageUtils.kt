package com.byron.trucaller.util

import android.content.Context
import android.net.Uri
import java.io.File

fun copyImageToInternal(context: Context, uri: Uri, name: String): File {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(dir, "$name.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    return file
}
