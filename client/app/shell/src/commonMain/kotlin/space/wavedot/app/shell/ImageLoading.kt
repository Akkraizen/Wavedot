package space.wavedot.app.shell

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import org.koin.compose.koinInject

private const val IMAGE_CACHE_BYTES = 256L * 1024 * 1024

fun createImageLoader(context: PlatformContext, httpClient: HttpClient, cacheDirectory: String?): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient })) }
        .apply {
            if (cacheDirectory != null) {
                diskCache {
                    DiskCache.Builder()
                        .directory(cacheDirectory.replace('\\', '/').toPath() / "images")
                        .maxSizeBytes(IMAGE_CACHE_BYTES)
                        .build()
                }
            }
        }
        .crossfade(true)
        .build()

@Composable
fun InstallImageLoader() {
    val httpClient = koinInject<HttpClient>()
    val environment = koinInject<PlatformEnvironment>()
    setSingletonImageLoaderFactory { context -> createImageLoader(context, httpClient, environment.cacheDirectory) }
}
