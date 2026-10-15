package io.github.audxius.pillar

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.audxius.pillar.data.AppGraph

class PillarApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // RomM covers can sit behind the same auth as the API, so attach it for that host only.
        val client = graph.http.newBuilder().addInterceptor { chain ->
            val req = chain.request()
            val rommHost = graph.romm.host()
            val auth = graph.romm.authHeader()
            if (rommHost != null && auth != null && req.url.host == rommHost) {
                chain.proceed(req.newBuilder().header("Authorization", auth).build())
            } else {
                chain.proceed(req)
            }
        }.build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache {
                DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(512L * 1024 * 1024).build()
            }
            .crossfade(true)
            .build()
    }
}
