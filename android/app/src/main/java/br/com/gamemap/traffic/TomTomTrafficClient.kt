package br.com.gamemap.traffic

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal object TomTomTrafficRequests {
    fun incidents(bounds: TrafficBounds, key: String): Request = Request.Builder()
        .url("https://api.tomtom.com/maps/orbis/traffic/incidents/details".toHttpUrl().newBuilder()
            .addQueryParameter("apiVersion", "2").addQueryParameter("bbox", bounds.query)
            .addQueryParameter("timeValidity", "present").build())
        .header("TomTom-Api-Key", key)
        .header("Accept-Language", "pt-BR")
        .header("Attributes", "incidents(type,geometry(type,coordinates),properties(id,iconCategory,magnitudeOfDelay,events(description),delayInSeconds,lastReportTime,startTime,endTime))")
        .build()
}

internal fun interface TrafficRequest { fun cancel() }

/** The controller owns scheduling/rendering; providers own requests and their tile endpoint. */
internal interface TrafficClient {
    fun rasterTemplate(dark: Boolean): String
    fun fetch(bounds: TrafficBounds, key: String, callback: (Result<List<TrafficIncident>>) -> Unit): TrafficRequest
}

/** Optional viewport request only. Keys never appear in URLs, errors, or logs. */
internal class TomTomTrafficClient(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
) : TrafficClient {
    override fun rasterTemplate(dark: Boolean) =
        "https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/{z}/{x}/{y}?apiVersion=2&style=${if (dark) "dark" else "light"}&tileSize=256"

    override fun fetch(bounds: TrafficBounds, key: String, callback: (Result<List<TrafficIncident>>) -> Unit): TrafficRequest {
        val call = client.newCall(TomTomTrafficRequests.incidents(bounds, key))
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) callback(Result.failure(TrafficException(TrafficFailure.OFFLINE)))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = response.use { reply ->
                    runCatching {
                        if (!reply.isSuccessful) throw TrafficException(when (reply.code) {
                            401, 403 -> TrafficFailure.AUTHENTICATION
                            429 -> TrafficFailure.RATE_LIMIT
                            else -> TrafficFailure.UNAVAILABLE
                        }, reply.header("Retry-After")?.toLongOrNull())
                        val body = reply.body ?: throw TrafficException(TrafficFailure.INVALID_RESPONSE)
                        val source = body.source()
                        source.request(TrafficIncidentParser.MAX_BODY_BYTES + 1L)
                        if (source.buffer.size > TrafficIncidentParser.MAX_BODY_BYTES) throw TrafficException(TrafficFailure.INVALID_RESPONSE)
                        TrafficIncidentParser.parse(source.readUtf8())
                    }
                }
                if (!call.isCanceled()) callback(result)
            }
        })
        return TrafficRequest { call.cancel() }
    }
}
