package br.com.gamemap.render

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Responds at the HTTP layer: an online resource transform cannot switch to the asset loader. */
class GameGlyphInterceptor(
    private val router: GameGlyphRouter,
    private val readAsset: (String) -> ByteArray,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val destination = router.transform(4, request.url.toString())
        if (destination.startsWith("asset://")) {
            val bytes = readAsset(destination.removePrefix("asset://"))
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "application/x-protobuf")
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(bytes.toResponseBody("application/x-protobuf".toMediaType())).build()
        }
        return chain.proceed(if (destination == request.url.toString()) request
            else request.newBuilder().url(destination).build())
    }
}
