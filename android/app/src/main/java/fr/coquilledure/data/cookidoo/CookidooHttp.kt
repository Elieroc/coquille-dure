package fr.coquilledure.data.cookidoo

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed class CookidooException(message: String) : Exception(message) {
    /** Token refusé ou expiré. */
    class Unauthorized : CookidooException("Session Cookidoo expirée ou refusée")
    /** Service Cookidoo en panne (5xx, page de maintenance HTML). */
    class Unavailable(val service: String) : CookidooException("Service Cookidoo indisponible : $service")
    /** Réponse dans un format inattendu : Cookidoo a probablement changé. */
    class Changed(val service: String) : CookidooException("Format Cookidoo inattendu : $service")
    class Network(cause: Throwable) : CookidooException("Pas de connexion à Cookidoo : ${cause.message}")
}

object CookidooHttp {
    /** Requête HTTP brute ; (code, corps). Lève [CookidooException.Network] sur erreur réseau. */
    fun request(
        url: String,
        method: String = "GET",
        form: Map<String, String>? = null,
        bearer: String? = null,
        accept: String = "application/json",
    ): Pair<Int, String> {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Accept", accept)
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
            if (form != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { o ->
                    o.write(form.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }.toByteArray())
                }
            }
            val code = c.responseCode
            val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } catch (e: IOException) {
            throw CookidooException.Network(e)
        }
    }
}
