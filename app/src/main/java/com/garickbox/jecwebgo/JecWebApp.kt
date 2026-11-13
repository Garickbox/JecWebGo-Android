package com.garickbox.jecwebgo

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult

class JecWebApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(filesDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .eventListener(object : ImageLoader.EventListener {
                override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                    Log.d("JecWeb:poster", "OK ${request.data}")
                }
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    Log.e("JecWeb:poster", "ERR ${request.data} → ${result.throwable.message}")
                }
            })
            .build()
    }
}
