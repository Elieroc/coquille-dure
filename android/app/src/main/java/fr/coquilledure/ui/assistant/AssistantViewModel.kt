package fr.coquilledure.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.coquilledure.data.assistant.AssistantContext
import fr.coquilledure.data.assistant.AssistantResponder
import fr.coquilledure.data.db.entities.RecipeEntity
import fr.coquilledure.data.repository.RecipeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val recipes: List<RecipeEntity> = emptyList(),
    val outro: String? = null,
    val suggestions: List<String> = emptyList(),
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val responder: AssistantResponder,
    private val repo: RecipeRepository,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    val starters: List<String> = responder.starters(LocalDate.now().toEpochDay())

    private var context: AssistantContext? = null
    private var nextId = 1L

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _thinking.value) return
        _messages.update { it + ChatMessage(nextId++, fromUser = true, text = text) }
        _thinking.value = true
        viewModelScope.launch {
            val reply = runCatching { responder.reply(text, context) }.getOrNull()
            delay(350) // laisse le temps de voir la question avant la réponse
            _thinking.value = false
            if (reply == null) {
                _messages.update { it + ChatMessage(nextId++, false, "Je n'ai pas réussi à chercher dans les recettes. Réessayez dans un instant.") }
                return@launch
            }
            context = reply.context
            _messages.update {
                it + ChatMessage(nextId++, false, reply.text, reply.recipes, reply.outro, reply.suggestions)
            }
        }
    }

    fun newConversation() {
        context = null
        _thinking.value = false
        _messages.value = emptyList()
    }

    fun toggleFavorite(recipe: RecipeEntity) {
        viewModelScope.launch {
            repo.toggleFavorite(recipe.id, recipe.isFavorite)
            _messages.update { list ->
                list.map { m ->
                    if (m.recipes.none { it.id == recipe.id }) m
                    else m.copy(recipes = m.recipes.map { if (it.id == recipe.id) it.copy(isFavorite = !recipe.isFavorite) else it })
                }
            }
        }
    }
}
