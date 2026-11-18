package fr.coquilledure.ui.importer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PROTOTYPE : valide le login OAuth2 (code + PKCE) de Cookidoo dans une WebView.
 * Le mot de passe est saisi sur la page officielle ; on intercepte seulement la redirection
 * com.vorwerk.cookidoo://code-grant, on échange le code, puis on lit le profil. Token gardé en mémoire.
 */
private const val TAG = "CookidooImport"
private const val OIDC = "https://ciam.prod.cookidoo.vorwerk-digital.com/.well-known/openid-configuration"
private const val CLIENT_ID = "mobile-android"
private const val REDIRECT = "com.vorwerk.cookidoo://code-grant"
private const val SCOPE = "openid profile email offline offline_access"

private class Pkce {
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    val verifier = b64(ByteArray(48).also { SecureRandom().nextBytes(it) })
    val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
    val state = b64(ByteArray(12).also { SecureRandom().nextBytes(it) })
}

private fun http(url: String, method: String = "GET", form: Map<String, String>? = null, bearer: String? = null): Pair<Int, String> {
    val c = URL(url).openConnection() as HttpURLConnection
    c.requestMethod = method
    c.connectTimeout = 15000; c.readTimeout = 30000
    c.setRequestProperty("Accept", "application/json")
    if (bearer != null) c.setRequestProperty("Authorization", "Bearer $bearer")
    if (form != null) {
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(form.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }.toByteArray()) }
    }
    val code = c.responseCode
    val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
    return code to body
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookidooLoginPrototype(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf(listOf<String>()) }
    var authUrl by remember { mutableStateOf<String?>(null) }
    var pkce by remember { mutableStateOf<Pkce?>(null) }
    var tokenEndpoint by remember { mutableStateOf("") }
    fun say(s: String) { Log.i(TAG, s); log = log + s }

    BackHandler(authUrl != null) { authUrl = null }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Import Cookidoo (prototype)") },
            navigationIcon = { IconButton(onClick = { if (authUrl != null) authUrl = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { padding ->
        val url = authUrl
        if (url == null) {
            Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    log = emptyList()
                    scope.launch {
                        try {
                            val (st, body) = withContext(Dispatchers.IO) { http(OIDC) }
                            say("OIDC discovery: HTTP $st")
                            val j = JsonParser.parseString(body).asJsonObject
                            tokenEndpoint = j["token_endpoint"].asString
                            val p = Pkce(); pkce = p
                            val q = mapOf(
                                "response_type" to "code", "client_id" to CLIENT_ID, "redirect_uri" to REDIRECT,
                                "market" to "fr", "scope" to SCOPE, "state" to p.state,
                                "code_challenge" to p.challenge, "code_challenge_method" to "S256", "ui_locales" to "fr-FR",
                            ).entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
                            CookieManager.getInstance().removeAllCookies(null)
                            authUrl = j["authorization_endpoint"].asString + "?" + q
                        } catch (e: Exception) { say("Erreur discovery: $e") }
                    }
                }) { Text("Se connecter à Cookidoo") }
                log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        } else {
            AndroidView(modifier = Modifier.padding(padding).fillMaxSize(), factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { Log.i(TAG, "page: ${u.substringBefore('?')}") }
                        override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: android.webkit.WebResourceResponse) {
                            if (r.isForMainFrame) Log.w(TAG, "HTTP ${e.statusCode} ${r.url.host}${r.url.path}")
                        }
                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                            val u = r.url
                            if (u.toString().startsWith(REDIRECT)) {
                                val p = pkce
                                val code = u.getQueryParameter("code")
                                if (p == null || u.getQueryParameter("state") != p.state || code == null) { say("Redirection invalide (state/code)"); authUrl = null; return true }
                                authUrl = null
                                say("Code reçu, échange du token…")
                                scope.launch {
                                    try {
                                        val token = withContext(Dispatchers.IO) {
                                            val (st, body) = http(tokenEndpoint, "POST", mapOf(
                                                "grant_type" to "authorization_code", "code" to code, "redirect_uri" to REDIRECT,
                                                "code_verifier" to p.verifier, "client_id" to CLIENT_ID))
                                            if (st != 200) { say("Token: HTTP $st"); null } else JsonParser.parseString(body).asJsonObject["access_token"].asString
                                        }
                                        CookieManager.getInstance().removeAllCookies(null)
                                        if (token != null) {
                                            say("Token OK (${token.length} car., en mémoire)")
                                            val (st, body) = withContext(Dispatchers.IO) { http("https://cookidoo.fr/community/profile/fr-FR", bearer = token) }
                                            say("GET community/profile: HTTP $st")
                                            if (st == 200) say("Profil: " + body.take(160).replace(Regex("\"(email|id)\"\\s*:\\s*\"[^\"]*\""), "\"$1\":\"…\""))
                                        }
                                    } catch (e: Exception) { say("Erreur: $e") }
                                }
                                return true
                            }
                            return false
                        }
                    }
                    loadUrl(url)
                }
            })
        }
    }
}
