package space.wavedot.ui.app

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageResult
import coil3.request.crossfade
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import space.wavedot.core.model.ArtworkReference

private const val IMAGE_CACHE_BYTES = 256L * 1024 * 1024

fun interface ArtworkUrlResolver {
    suspend fun resolve(url: String): String?
}

class ArtworkReferenceInterceptor(private val resolver: ArtworkUrlResolver) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val reference = chain.request.data as? String
        if (reference == null || !ArtworkReference.matches(reference)) return chain.proceed()
        val resolved = resolver.resolve(reference) ?: return chain.proceed()
        val request = chain.request.newBuilder()
            .data(resolved)
            .memoryCacheKey(reference)
            .diskCacheKey(reference)
            .build()
        return chain.withRequest(request).proceed()
    }
}

fun createImageLoader(
    context: PlatformContext,
    httpClient: HttpClient,
    cacheDirectory: String?,
    artworkResolver: ArtworkUrlResolver,
): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(ArtworkReferenceInterceptor(artworkResolver))
            add(KtorNetworkFetcherFactory(httpClient = { httpClient }))
        }
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
fun InstallImageLoader(httpClient: HttpClient, cacheDirectory: String?, artworkResolver: ArtworkUrlResolver) {
    setSingletonImageLoaderFactory { context -> createImageLoader(context, httpClient, cacheDirectory, artworkResolver) }
}
