package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ChatVaultApplication
import com.example.data.local.AiChatMessageEntity
import com.example.data.repository.AiRepository
import com.example.data.repository.GeminiChatProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiChatStreamState {
    data object Idle : AiChatStreamState
    data object Loading : AiChatStreamState
    data class Streaming(val partialText: String) : AiChatStreamState
    data class Error(val message: String) : AiChatStreamState
}

class AiChatViewModel(
    application: Application,
    private val aiRepository: AiRepository
) : AndroidViewModel(application) {

    private val _selectedProfile = MutableStateFlow(GeminiChatProfile.GENERAL)
    val selectedProfile: StateFlow<GeminiChatProfile> = _selectedProfile.asStateFlow()

    private val _streamState = MutableStateFlow<AiChatStreamState>(AiChatStreamState.Idle)
    val streamState: StateFlow<AiChatStreamState> = _streamState.asStateFlow()

    val messages: StateFlow<List<AiChatMessageEntity>> = aiRepository
        .observeChatHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun selectProfile(profile: GeminiChatProfile) {
        _selectedProfile.value = profile
    }

    fun clearError() {
        if (_streamState.value is AiChatStreamState.Error) {
            _streamState.value = AiChatStreamState.Idle
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            aiRepository.clearChatHistory()
            _streamState.value = AiChatStreamState.Idle
        }
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return
        if (_streamState.value is AiChatStreamState.Loading ||
            _streamState.value is AiChatStreamState.Streaming
        ) return

        val profile = _selectedProfile.value
        val historySnapshot = messages.value

        viewModelScope.launch {
            _streamState.value = AiChatStreamState.Loading
            try {
                aiRepository.persistMessage(
                    role = "user",
                    text = prompt,
                    modelId = profile.modelId
                )

                val fullReply = aiRepository.streamChatReply(
                    profile = profile,
                    history = historySnapshot,
                    userMessage = prompt,
                    onChunk = { partial ->
                        _streamState.value = AiChatStreamState.Streaming(partial)
                    }
                )

                if (fullReply.isNotBlank()) {
                    aiRepository.persistMessage(
                        role = "model",
                        text = fullReply,
                        modelId = profile.modelId
                    )
                    _streamState.value = AiChatStreamState.Idle
                } else {
                    _streamState.value = AiChatStreamState.Error("Gemini returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini chat timed out", e)
                _streamState.value = AiChatStreamState.Error(
                    "Response timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat failed", e)
                _streamState.value = AiChatStreamState.Error(
                    e.localizedMessage ?: "Failed to connect to Gemini AI."
                )
            }
        }
    }

    fun analyzeRecoveredTranscript(transcript: String) {
        _selectedProfile.value = GeminiChatProfile.GENERAL
        sendMessage(
            "Analyze this recovered WhatsApp chat transcript. Summarize the key points, highlight any deleted messages and their context, and suggest next steps:\n\n$transcript"
        )
    }

    companion object {
        private const val TAG = "AiChatViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChatVaultApplication
                AiChatViewModel(
                    application = app,
                    aiRepository = app.container.aiRepository
                )
            }
        }
    }
}
