package br.com.gamemap.navigation.provider

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class ProviderErrorKind {
    CONFIGURATION, AUTHENTICATION, RATE_LIMIT, OFFLINE, NO_RESULTS, NO_ROUTE,
    UNAVAILABLE, INVALID_RESPONSE,
}

class TypedProviderException(
    val kind: ProviderErrorKind,
    val retryAfterSeconds: Long? = null,
) : Exception(when (kind) {
    ProviderErrorKind.CONFIGURATION -> "Configure sua chave Geoapify no celular."
    ProviderErrorKind.AUTHENTICATION -> "Chave Geoapify inválida ou sem acesso. Confira a configuração."
    ProviderErrorKind.RATE_LIMIT -> "Limite de consultas atingido. Aguarde antes de tentar novamente."
    ProviderErrorKind.OFFLINE -> "Sem conexão com o serviço. Verifique a internet."
    ProviderErrorKind.NO_RESULTS -> "Nenhum local encontrado. Tente outro nome ou endereço."
    ProviderErrorKind.NO_ROUTE -> "Não foi possível encontrar uma rota para este destino."
    ProviderErrorKind.UNAVAILABLE -> "Serviço temporariamente indisponível. Tente novamente."
    ProviderErrorKind.INVALID_RESPONSE -> "O serviço retornou dados de navegação inválidos. Tente novamente."
})

data class HttpResponse(
    val code: Int,
    val body: String,
    val headers: Map<String, List<String>> = emptyMap(),
)

fun interface HttpTransport {
    fun get(url: String): HttpResponse
}

object HttpsTransport : HttpTransport {
    private const val MAX_BODY_BYTES = 4 * 1024 * 1024

    override fun get(url: String): HttpResponse {
        val target = try { URL(url) } catch (_: Exception) {
            throw TypedProviderException(ProviderErrorKind.CONFIGURATION)
        }
        if (target.protocol != "https" || target.host != "api.geoapify.com" ||
            target.userInfo != null || target.port !in listOf(-1, 443)) {
            throw TypedProviderException(ProviderErrorKind.CONFIGURATION)
        }
        val connection = target.openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "NavSkin-Android/0.5")
            val code = connection.responseCode
            if (connection.contentLengthLong > MAX_BODY_BYTES) {
                throw TypedProviderException(ProviderErrorKind.INVALID_RESPONSE)
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw IOException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BODY_BYTES) {
                        throw TypedProviderException(ProviderErrorKind.INVALID_RESPONSE)
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()
            val headers = connection.headerFields.entries.mapNotNull { (name, values) ->
                if (name != null && values != null) name to values else null
            }.toMap()
            return HttpResponse(code, body, headers)
        } finally {
            connection.disconnect()
        }
    }
}
