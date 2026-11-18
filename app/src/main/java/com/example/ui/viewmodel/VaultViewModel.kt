package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ChatVaultApplication
import com.example.data.local.CapturedMessageEntity
import com.example.data.local.ChatSummary
import com.example.data.local.RecoveredMediaEntity
import com.example.data.local.RecoveryEventEntity
import com.example.data.prefs.VaultPreferences
import com.example.data.prefs.VaultPreferencesRepository
import com.example.data.repository.AiRepository
import com.example.data.repository.MediaSyncResult
import com.example.data.repository.MediaVaultRepository
import com.example.data.repository.MessageVaultRepository
import com.example.service.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HtmlClarifyUiState {
    data object Idle : HtmlClarifyUiState
    data object Loading : HtmlClarifyUiState
    data class Success(val summary: String) : HtmlClarifyUiState
    data class Error(val message: String) : HtmlClarifyUiState
}

data class NumberLookupUiResult(
    val rawQuery: String = "",
    val normalizedDigits: String = "",
    val matchingThreads: List<ChatSummary> = emptyList(),
    val matchingMessages: List<CapturedMessageEntity> = emptyList(),
    val linkableThreads: List<ChatSummary> = emptyList(),
    val linkedNumberThreads: List<ChatSummary> = emptyList()
) {
    val totalCount: Int get() = matchingMessages.size
    val deletedCount: Int get() = matchingMessages.count { it.isDeleted }
}

data class VaultDashboardState(
    val isListenerEnabled: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val preferences: VaultPreferences = VaultPreferences(),
    val totalCapturedMessages: Int = 0,
    val deletedMessagesCaught: Int = 0,
    val recoveredDeletedMediaCount: Int = 0,
    val isSyncingMedia: Boolean = false,
    val statusBannerMessage: String? = null
)

class VaultViewModel(
    application: Application,
    private val prefsRepo: VaultPreferencesRepository,
    private val messageRepo: MessageVaultRepository,
    private val mediaRepo: MediaVaultRepository,
    private val aiRepository: AiRepository
) : AndroidViewModel(application) {

    private val _htmlClarifyState = MutableStateFlow<HtmlClarifyUiState>(HtmlClarifyUiState.Idle)
    val htmlClarifyState: StateFlow<HtmlClarifyUiState> = _htmlClarifyState.asStateFlow()

    val deletedMessagesFeed: StateFlow<List<CapturedMessageEntity>> = messageRepo.deletedMessagesFeed
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val recentEvents: StateFlow<List<RecoveryEventEntity>> =
        (messageRepo.observeRecentEvents(30) ?: flowOf(emptyList()))
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _isListenerEnabled = MutableStateFlow(NotificationHelper.isListenerEnabled(application))
    private val _hasNotificationPermission = MutableStateFlow(NotificationHelper.hasPostNotificationsPermission(application))
    private val _isSyncingMedia = MutableStateFlow(false)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)

    private val _chatSearchQuery = MutableStateFlow("")
    val chatSearchQuery: StateFlow<String> = _chatSearchQuery.asStateFlow()

    private val _showDeletedChatsOnly = MutableStateFlow(false)
    val showDeletedChatsOnly: StateFlow<Boolean> = _showDeletedChatsOnly.asStateFlow()

    private val _showDeletedMediaOnly = MutableStateFlow(false)
    val showDeletedMediaOnly: StateFlow<Boolean> = _showDeletedMediaOnly.asStateFlow()

    private val _lookupPhoneInput = MutableStateFlow("")
    val lookupPhoneInput: StateFlow<String> = _lookupPhoneInput.asStateFlow()

    val numberLookupResult: StateFlow<NumberLookupUiResult> = combine(
        _lookupPhoneInput,
        messageRepo.chatSummaries,
        messageRepo.allMessages
    ) { rawInput, summaries, allMessages ->
        val queryDigits = MessageVaultRepository.normalizePhoneDigits(rawInput)
        val linkedThreads = summaries.filter { !it.linkedPhoneNumber.isNullOrBlank() }

        if (queryDigits.length < 4) {
            NumberLookupUiResult(
                rawQuery = rawInput,
                normalizedDigits = queryDigits,
                linkableThreads = summaries,
                linkedNumberThreads = linkedThreads
            )
        } else {
            val matchedThreads = summaries.filter { summary ->
                MessageVaultRepository.phoneDigitsMatch(queryDigits, summary.linkedPhoneNumber) ||
                    MessageVaultRepository.phoneDigitsMatch(queryDigits, summary.chatTitle) ||
                    MessageVaultRepository.phoneDigitsMatch(queryDigits, summary.chatKey)
            }
            val matchedThreadKeys = matchedThreads.map { it.chatKey }.toSet()
            val matchedMessages = allMessages.filter { msg ->
                msg.chatKey in matchedThreadKeys ||
                    MessageVaultRepository.phoneDigitsMatch(queryDigits, msg.chatTitle) ||
                    MessageVaultRepository.phoneDigitsMatch(queryDigits, msg.senderName) ||
                    MessageVaultRepository.phoneDigitsMatch(queryDigits, msg.messageText)
            }
            val linkable = summaries.filter { it.chatKey !in matchedThreadKeys }
            NumberLookupUiResult(
                rawQuery = rawInput,
                normalizedDigits = queryDigits,
                matchingThreads = matchedThreads,
                matchingMessages = matchedMessages,
                linkableThreads = linkable,
                linkedNumberThreads = linkedThreads
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NumberLookupUiResult()
    )

    val dashboardState: StateFlow<VaultDashboardState> = combine(
        _isListenerEnabled,
        _hasNotificationPermission,
        prefsRepo.preferencesFlow,
        messageRepo.totalCount,
        messageRepo.deletedCount,
        mediaRepo.recoveredDeletedCount,
        _isSyncingMedia,
        _statusBannerMessage
    ) { values ->
        VaultDashboardState(
            isListenerEnabled = values[0] as Boolean,
            hasNotificationPermission = values[1] as Boolean,
            preferences = values[2] as VaultPreferences,
            totalCapturedMessages = values[3] as Int,
            deletedMessagesCaught = values[4] as Int,
            recoveredDeletedMediaCount = values[5] as Int,
            isSyncingMedia = values[6] as Boolean,
            statusBannerMessage = values[7] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultDashboardState(
            isListenerEnabled = _isListenerEnabled.value,
            hasNotificationPermission = _hasNotificationPermission.value
        )
    )

    val filteredChats: StateFlow<List<ChatSummary>> = combine(
        messageRepo.chatSummaries,
        _chatSearchQuery,
        _showDeletedChatsOnly
    ) { chats, query, deletedOnly ->
        val q = query.trim().lowercase()
        chats.filter { chat ->
            val matchesDeleted = !deletedOnly || chat.deletedCount > 0
            val matchesQuery = q.isEmpty() ||
                chat.chatTitle.lowercase().contains(q) ||
                chat.lastMessage.lowercase().contains(q) ||
                (chat.linkedPhoneNumber?.lowercase()?.contains(q) == true)
            matchesDeleted && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val filteredMedia: StateFlow<List<RecoveredMediaEntity>> = combine(
        mediaRepo.allMedia,
        _showDeletedMediaOnly
    ) { items, deletedOnly ->
        if (deletedOnly) items.filter { it.isDeletedFromSource } else items
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun refreshPermissionsStatus() {
        val ctx = getApplication<Application>()
        _isListenerEnabled.value = NotificationHelper.isListenerEnabled(ctx)
        _hasNotificationPermission.value = NotificationHelper.hasPostNotificationsPermission(ctx)
        NotificationHelper.requestListenerRebindIfEnabled(ctx)
    }

    fun updateSearchQuery(query: String) {
        _chatSearchQuery.value = query
    }

    fun setShowDeletedChatsOnly(enabled: Boolean) {
        _showDeletedChatsOnly.value = enabled
    }

    fun setShowDeletedMediaOnly(enabled: Boolean) {
        _showDeletedMediaOnly.value = enabled
    }

    fun observeMessagesForChat(chatKey: String): Flow<List<CapturedMessageEntity>> =
        messageRepo.observeMessages(chatKey)

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun onSafFolderLinked(treeUri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(treeUri, flags)
            }.onFailure {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            val folderLabel = treeUri.lastPathSegment?.substringAfterLast(':')?.ifBlank { "WhatsApp Media" }
                ?: "WhatsApp Media"
            prefsRepo.setLinkedFolder(treeUri.toString(), folderLabel)
            syncLinkedMediaFolder()
        }
    }

    fun syncLinkedMediaFolder() {
        viewModelScope.launch {
            val uri = prefsRepo.getSnapshot().linkedFolderUri
            if (uri.isNullOrBlank()) {
                _statusBannerMessage.value = "Link a WhatsApp Media folder first to sync."
                return@launch
            }
            _isSyncingMedia.value = true
            try {
                val result: MediaSyncResult = mediaRepo.syncLinkedSafFolder(uri)
                _statusBannerMessage.value = buildString {
                    append("Synced ${result.scannedTotalCount} items")
                    if (result.newlyCachedCount > 0) append(" • ${result.newlyCachedCount} backed up")
                    if (result.newlyDeletedDetectedCount > 0) {
                        append(" • ${result.newlyDeletedDetectedCount} deleted & recovered!")
                    }
                }
            } catch (e: Exception) {
                _statusBannerMessage.value = "Media sync failed: ${e.localizedMessage ?: "Permission denied"}"
            } finally {
                _isSyncingMedia.value = false
            }
        }
    }

    fun importPickedPhoto(uri: Uri) {
        viewModelScope.launch {
            val imported = mediaRepo.importPickedPhoto(uri)
            _statusBannerMessage.value = if (imported != null) {
                "Imported ${imported.fileName} into local vault."
            } else {
                "Unable to import selected photo."
            }
        }
    }

    fun saveMediaToGallery(item: RecoveredMediaEntity) {
        viewModelScope.launch {
            val ok = mediaRepo.saveToDeviceGallery(item.localVaultPath, item.fileName, item.mimeType)
            _statusBannerMessage.value = if (ok) {
                "Saved ${item.fileName} to Pictures/ChatVault."
            } else {
                "Use Share to save on this Android version."
            }
        }
    }

    fun createMediaShareIntent(item: RecoveredMediaEntity): Intent? =
        mediaRepo.createShareIntent(item.localVaultPath, item.mimeType)

    fun deleteMediaFromVault(item: RecoveredMediaEntity) {
        viewModelScope.launch {
            mediaRepo.deleteFromVault(item)
            _statusBannerMessage.value = "Removed ${item.fileName} from vault."
        }
    }

    suspend fun buildChatExportIntent(chatKey: String, chatTitle: String): Intent =
        messageRepo.shareChatTranscript(chatKey, chatTitle)

    suspend fun buildChatTranscriptText(chatKey: String, chatTitle: String): String =
        messageRepo.buildExportTranscript(chatKey, chatTitle)

    suspend fun buildHtmlShareIntent(htmlContent: String, fileNameSlug: String): Intent =
        messageRepo.shareHtmlReport(htmlContent, fileNameSlug)

    fun clarifyMessagesForHtml(messages: List<CapturedMessageEntity>) {
        if (_htmlClarifyState.value is HtmlClarifyUiState.Loading) return
        viewModelScope.launch {
            _htmlClarifyState.value = HtmlClarifyUiState.Loading
            try {
                val clarified = aiRepository.clarifyDeletedChats(messages)
                _htmlClarifyState.value = HtmlClarifyUiState.Success(clarified)
            } catch (e: TimeoutCancellationException) {
                _htmlClarifyState.value = HtmlClarifyUiState.Error(
                    "AI clarification timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _htmlClarifyState.value = HtmlClarifyUiState.Error(
                    e.localizedMessage ?: "Failed to clarify deleted messages."
                )
            }
        }
    }

    fun clearHtmlClarifyState() {
        _htmlClarifyState.value = HtmlClarifyUiState.Idle
    }

    fun updateLookupPhoneInput(input: String) {
        _lookupPhoneInput.value = input
    }

    fun linkPhoneNumberToThread(chatKey: String, rawPhoneNumber: String) {
        val cleaned = rawPhoneNumber.trim()
        if (MessageVaultRepository.normalizePhoneDigits(cleaned).length < 4) {
            _statusBannerMessage.value = "Enter at least 4 digits for a WhatsApp number."
            return
        }
        viewModelScope.launch {
            messageRepo.setThreadPhoneNumber(chatKey, cleaned)
            _lookupPhoneInput.value = cleaned
            _statusBannerMessage.value = "Linked WhatsApp number $cleaned to chat."
        }
    }

    fun unlinkPhoneNumberFromThread(chatKey: String) {
        viewModelScope.launch {
            messageRepo.setThreadPhoneNumber(chatKey, null)
            _statusBannerMessage.value = "Removed linked WhatsApp number."
        }
    }

    fun buildNumberTranscript(label: String, messages: List<CapturedMessageEntity>): String =
        messageRepo.buildTranscriptForMessages(label, messages)

    fun buildOpenWhatsAppIntent(rawPhone: String): Intent? =
        messageRepo.buildOpenWhatsAppIntent(rawPhone)

    fun toggleThreadPinned(chatKey: String, pinned: Boolean) {
        viewModelScope.launch {
            messageRepo.setThreadPinned(chatKey, pinned)
        }
    }

    fun deleteChatThread(chatKey: String) {
        viewModelScope.launch {
            messageRepo.deleteThread(chatKey)
        }
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            messageRepo.clearAll()
            _statusBannerMessage.value = "Cleared all archived messages."
        }
    }

    fun unlinkSafFolder() {
        viewModelScope.launch {
            prefsRepo.setLinkedFolder(null, null)
            _statusBannerMessage.value = "Unlinked media folder."
        }
    }

    fun sendDiagnosticTestNotification(isDeleteSignal: Boolean) {
        val ctx = getApplication<Application>()
        if (!NotificationHelper.isListenerEnabled(ctx)) {
            _statusBannerMessage.value = "Enable Notification Access first to run live capture test."
            return
        }
        if (!NotificationHelper.hasPostNotificationsPermission(ctx)) {
            _statusBannerMessage.value = "Grant notification permission first to post diagnostic alert."
            return
        }
        NotificationHelper.postDiagnosticTestNotification(ctx, isDeleteSignal)
        _statusBannerMessage.value = if (isDeleteSignal) {
            "Posted deletion signal — check Diagnostic Test Chat!"
        } else {
            "Posted live diagnostic notification to capture pipeline."
        }
    }

    fun setMonitorWhatsApp(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setMonitorWhatsApp(enabled) }
    }

    fun setMonitorWhatsAppBusiness(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setMonitorWhatsAppBusiness(enabled) }
    }

    fun setMonitorDiagnostic(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setMonitorDiagnostic(enabled) }
    }

    fun setNotifyOnDeleted(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNotifyOnDeleted(enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChatVaultApplication
                val container = app.container
                VaultViewModel(
                    application = app,
                    prefsRepo = container.preferencesRepository,
                    messageRepo = container.messageVaultRepository,
                    mediaRepo = container.mediaVaultRepository,
                    aiRepository = container.aiRepository
                )
            }
        }
    }
}
