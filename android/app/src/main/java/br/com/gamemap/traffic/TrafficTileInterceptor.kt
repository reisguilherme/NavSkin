package br.com.gamemap.traffic

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Installed on MapLibre's existing HTTP client; unrelated map/glyph requests are untouched. */
class TrafficTileInterceptor(private val apiKey: () -> String, private val enabled: () -> Boolean) : Interceptor {
    internal fun authorizedRequest(request: Request): Request? {
        if (!isTrafficTile(request)) return request.newBuilder().removeHeader("TomTom-Api-Key").build()
        val key = if (enabled()) runCatching { apiKey().trim() }.getOrDefault("") else ""
        if (key.isEmpty() || key.length > 512 || key.any { it.code !in 33..126 }) return null
        return request.newBuilder().header("TomTom-Api-Key", key).build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = authorizedRequest(original) ?: return Response.Builder()
            .request(original).protocol(Protocol.HTTP_1_1).code(204).message("Traffic disabled")
            .body(ByteArray(0).toResponseBody()).build()
        return chain.proceed(request)
    }

}

/** A network interceptor must proceed exactly once; re-check every redirect before adding auth. */
class TrafficTileAuthenticationInterceptor(apiKey: () -> String, enabled: () -> Boolean) : Interceptor {
    private val authentication = TrafficTileInterceptor(apiKey, enabled)
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = authentication.authorizedRequest(original)
            ?: original.newBuilder().removeHeader("TomTom-Api-Key").build()
        return chain.proceed(request)
    }
}

private fun isTrafficTile(request: Request) = request.url.scheme == "https" && request.url.host == "api.tomtom.com" &&
    request.url.encodedPath.startsWith("/maps/orbis/traffic/flow/raster/tile/")
