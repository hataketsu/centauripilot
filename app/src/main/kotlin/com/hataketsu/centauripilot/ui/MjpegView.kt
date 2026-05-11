package com.hataketsu.centauripilot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import android.graphics.BitmapFactory

/**
 * Lightweight MJPEG viewer for SDCP camera at http://<host>:3031/video
 * Parses multipart/x-mixed-replace stream frame-by-frame.
 */
@Composable
fun MjpegView(host: String, modifier: Modifier = Modifier, urlOverride: String? = null) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(host, urlOverride) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build()
            try {
                val req = Request.Builder().url(urlOverride ?: "http://$host:3031/video").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) { err = "HTTP ${resp.code}"; return@withContext }
                    val body = resp.body ?: return@withContext
                    val stream = body.byteStream()
                    readMjpeg(stream) { jpegBytes ->
                        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        if (bmp != null) bitmap = bmp.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                err = e.message
            }
        }
    }

    Box(modifier.background(Color(0xFF000000)), contentAlignment = androidx.compose.ui.Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = "camera", modifier = Modifier.fillMaxSize())
        } else {
            Text(err ?: "Connecting camera…", color = Color(0xFFAAAAAA))
        }
    }
}

private fun readMjpeg(input: InputStream, onFrame: (ByteArray) -> Unit) {
    val buf = java.io.ByteArrayOutputStream()
    val recent = ByteArray(2)
    val SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) // start of image
    val EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte()) // end of image
    var inFrame = false
    val byte = ByteArray(1)
    while (true) {
        val read = input.read(byte)
        if (read < 0) break
        val b = byte[0]
        // shift recent
        recent[0] = recent[1]; recent[1] = b
        if (!inFrame) {
            if (recent[0] == SOI[0] && recent[1] == SOI[1]) {
                inFrame = true
                buf.reset()
                buf.write(SOI[0].toInt()); buf.write(SOI[1].toInt())
            }
        } else {
            buf.write(b.toInt())
            if (recent[0] == EOI[0] && recent[1] == EOI[1]) {
                inFrame = false
                onFrame(buf.toByteArray())
                buf.reset()
            }
        }
    }
}
