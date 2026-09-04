package com.example.beaqua

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ContainerImageLoader {
    private val executor = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(imageView: ImageView, source: String?) {
        imageView.tag = null
        imageView.setImageResource(R.drawable.ic_placeholder)
        if (source.isNullOrBlank()) return
        imageView.tag = source

        DefaultContainerImages.find(source)?.let { defaultImage ->
            imageView.setImageResource(defaultImage.drawableRes)
            return
        }

        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            imageView.setImageURI(Uri.parse(source))
            return
        }

        cache.get(source)?.let {
            imageView.setImageBitmap(it)
            return
        }

        executor.execute {
            val bitmap = try {
                val connection = URL(source).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.inputStream.use(BitmapFactory::decodeStream).also {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) {
                cache.put(source, bitmap)
                imageView.post {
                    if (imageView.tag == source) imageView.setImageBitmap(bitmap)
                }
            }
        }
    }
}
