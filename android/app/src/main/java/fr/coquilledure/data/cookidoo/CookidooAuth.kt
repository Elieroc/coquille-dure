package fr.coquilledure.data.cookidoo

import android.util.Base64
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** Demande d'autorisation OAuth2 (code + PKCE) à afficher dans une WebView. */
class AuthRequest(val url: String, val state: String, val verifier: String, val tokenEndpoint: String)

/**
 * Connexion Cookidoo : la page de connexion officielle s'affiche dans une WebView, l'app n'a jamais
 * accès au mot de passe ; elle intercepte la redirection [REDIRECT] et échange le code contre un token.
 */
object CookidooAuth {
    const val REDIRECT = "com.vorwerk.cookidoo://code-grant"
    private const val OIDC = "https://ciam.prod.cookidoo.vorwerk-digital.com/.well-known/openid-configuration"
    private const val CLIENT_ID = "mobile-android"
    private const val SCOPE = "openid profile email offline offline_access"

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun random(n: Int) = b64(ByteArray(n).also { SecureRandom().nextBytes(it) })
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun begin(): AuthRequest = withContext(Dispatchers.IO) {
        val (status, body) = CookidooHttp.request(OIDC)
        if (status != 200) throw CookidooException.Unavailable("connexion")
        val doc = try { JsonParser.parseString(body).asJsonObject } catch (e: Exception) { throw CookidooException.Changed("connexion") }
        val authz = doc["authorization_endpoint"]?.asString ?: throw CookidooException.Changed("connexion")
        val token = doc["token_endpoint"]?.asString ?: throw CookidooException.Changed("connexion")
        val verifier = random(48)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = random(12)
        val query = linkedMapOf(
            "response_type" to "code", "client_id" to CLIENT_ID, "redirect_uri" to REDIRECT, "market" to "fr",
            "scope" to SCOPE, "state" to state, "code_challenge" to challenge, "code_challenge_method" to "S256",
            "ui_locales" to "fr-FR",
        ).entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        AuthRequest("$authz?$query", state, verifier, token)
    }

    /** Vérifie la redirection et renvoie le code d'autorisation, ou null si elle est invalide. */
    fun codeFrom(redirect: android.net.Uri, req: AuthRequest): String? =
        redirect.getQueryParameter("code")?.takeIf { redirect.getQueryParameter("state") == req.state }

    suspend fun exchange(code: String, req: AuthRequest): String = withContext(Dispatchers.IO) {
        val (status, body) = CookidooHttp.request(
            req.tokenEndpoint, "POST",
            form = mapOf(
                "grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT,
                "code_verifier" to req.verifier, "client_id" to CLIENT_ID,
            ),
        )
        if (status == 400 || status == 401 || status == 403) throw CookidooException.Unauthorized()
        if (status != 200) throw CookidooException.Unavailable("connexion")
        try { JsonParser.parseString(body).asJsonObject["access_token"].asString } catch (e: Exception) { throw CookidooException.Changed("connexion") }
    }
}
