package fr.coquilledure.ui.importer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.coquilledure.data.cookidoo.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ImportStep {
    data object Intro : ImportStep
    data class Login(val request: AuthRequest) : ImportStep
    data class Working(val message: String, val progress: Float? = null) : ImportStep
    data class Review(val snapshot: CookidooSnapshot) : ImportStep
    data class Done(val report: ImportReport) : ImportStep
    data class Error(val message: String, val retry: Boolean = true) : ImportStep
}

@HiltViewModel
class CookidooImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: CookidooImporter,
) : ViewModel() {
    private val _step = MutableStateFlow<ImportStep>(ImportStep.Intro)
    val step: StateFlow<ImportStep> = _step

    val selected = MutableStateFlow(ImportCategory.entries.toSet() - ImportCategory.RATINGS)
    val mode = MutableStateFlow(ImportMode.MERGE)

    /** Token en mémoire seulement ; effacé à la fin de l'import et à la destruction du ViewModel. */
    private var token: String? = null
    private var job: Job? = null

    fun toggle(c: ImportCategory) { selected.value = selected.value.let { if (c in it) it - c else it + c } }

    fun start() {
        job?.cancel()
        _step.value = ImportStep.Working("Ouverture de la connexion…")
        job = viewModelScope.launch { guard { _step.value = ImportStep.Login(CookidooAuth.begin()) } }
    }

    fun onRedirect(req: AuthRequest, code: String?) {
        if (code == null) { _step.value = ImportStep.Error("La connexion a échoué. Réessayez."); return }
        job?.cancel()
        _step.value = ImportStep.Working("Connexion en cours…")
        job = viewModelScope.launch {
            guard {
                token = CookidooAuth.exchange(code, req)
                val collector = CookidooCollector(CookidooApi(token!!), File(context.filesDir, "images"))
                val snap = collector.analyze { _step.value = ImportStep.Working("Lecture de votre compte : $it…") }
                _step.value = ImportStep.Review(snap)
            }
        }
    }

    fun runImport(snap: CookidooSnapshot) {
        val t = token ?: return start()
        val sel = selected.value.filter { it !in snap.unavailable && snap.count(it) > 0 }.toSet()
        job?.cancel()
        _step.value = ImportStep.Working("Import en cours…")
        job = viewModelScope.launch {
            guard {
                var ratings = emptyMap<String, Int>()
                if (ImportCategory.RATINGS in sel) {
                    ratings = CookidooCollector(CookidooApi(t), File(context.filesDir, "images")).ratings(snap.ratingCandidates()) { i, n ->
                        _step.value = ImportStep.Working("Lecture de vos évaluations ($i/$n)…", i.toFloat() / n)
                    }
                }
                _step.value = ImportStep.Working("Écriture dans Coquille dure…")
                val report = importer.import(snap, sel, mode.value, ratings)
                wipe()
                _step.value = ImportStep.Done(report)
            }
        }
    }

    fun reset() { job?.cancel(); wipe(); _step.value = ImportStep.Intro }

    private fun wipe() {
        token = null
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
    }

    private suspend fun guard(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: CookidooException) {
            wipe()
            _step.value = ImportStep.Error(
                when (e) {
                    is CookidooException.Unauthorized -> "Session Cookidoo expirée ou refusée. Reconnectez-vous."
                    is CookidooException.Network -> "Pas de connexion à Internet, ou Cookidoo ne répond pas."
                    is CookidooException.Unavailable -> "Cookidoo est momentanément indisponible. Réessayez plus tard."
                    is CookidooException.Changed -> "Cookidoo a changé et l'import ne sait plus le lire (${e.service})."
                },
            )
        } catch (e: Exception) {
            wipe()
            _step.value = ImportStep.Error("L'import a échoué : ${e.message ?: e::class.simpleName}")
        }
    }

    override fun onCleared() { wipe() }
}
