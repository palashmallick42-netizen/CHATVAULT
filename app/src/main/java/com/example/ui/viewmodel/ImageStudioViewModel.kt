package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ChatVaultApplication
import com.example.data.repository.AiRepository
import com.example.data.repository.MediaVaultRepository
import com.example.data.repository.StudioAspectRatio
import com.example.data.repository.StudioImageSize
import com.google.firebase.ai.type.QuotaExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImageStudioUiState {
    data object Idle : ImageStudioUiState
    data object Loading : ImageStudioUiState
    data class Success(
        val bitmap: Bitmap,
        val sizeLabel: String,
        val ratioLabel: String,
        val prompt: String
    ) : ImageStudioUiState
    data class Error(val message: String) : ImageStudioUiState
}

class ImageStudioViewModel(
    application: Application,
    private val aiRepository: AiRepository,
    private val mediaRepo: MediaVaultRepository
) : AndroidViewModel(application) {

    private val _selectedSize = MutableStateFlow(StudioImageSize.SIZE_1K)
    val selectedSize: StateFlow<StudioImageSize> = _selectedSize.asStateFlow()

    private val _selectedRatio = MutableStateFlow(StudioAspectRatio.SQUARE)
    val selectedRatio: StateFlow<StudioAspectRatio> = _selectedRatio.asStateFlow()

    private val _referenceBitmap = MutableStateFlow<Bitmap?>(null)
    val referenceBitmap: StateFlow<Bitmap?> = _referenceBitmap.asStateFlow()

    private val _uiState = MutableStateFlow<ImageStudioUiState>(ImageStudioUiState.Idle)
    val uiState: StateFlow<ImageStudioUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun selectSize(size: StudioImageSize) {
        _selectedSize.value = size
    }

    fun selectRatio(ratio: StudioAspectRatio) {
        _selectedRatio.value = ratio
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearReferenceImage() {
        _referenceBitmap.value = null
    }

    fun loadReferenceFromUri(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
            if (bmp != null) {
                _referenceBitmap.value = scaleDownIfNeeded(bmp)
            } else {
                _actionMessage.value = "Could not load selected reference photo."
            }
        }
    }

    fun loadReferenceFromVaultPath(path: String) {
        viewModelScope.launch {
            val bmp = mediaRepo.loadBitmapFromVaultPath(path)
            if (bmp != null) {
                _referenceBitmap.value = scaleDownIfNeeded(bmp)
            } else {
                _actionMessage.value = "Could not load vault photo."
            }
        }
    }

    fun generateImage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return
        if (_uiState.value is ImageStudioUiState.Loading) return

        val size = _selectedSize.value
        val ratio = _selectedRatio.value
        val reference = _referenceBitmap.value

        viewModelScope.launch {
            _uiState.value = ImageStudioUiState.Loading
            try {
                val bitmap = aiRepository.generateStudioImage(
                    prompt = prompt,
                    imageSize = size,
                    aspectRatio = ratio,
                    referenceBitmap = reference
                )
                if (bitmap != null) {
                    _uiState.value = ImageStudioUiState.Success(
                        bitmap = bitmap,
                        sizeLabel = size.label,
                        ratioLabel = ratio.label,
                        prompt = prompt
                    )
                } else {
                    _uiState.value = ImageStudioUiState.Error(
                        "No image was returned by ${AiRepository.IMAGE_MODEL_ID}."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.value = ImageStudioUiState.Error(
                    "Image generation timed out after 60 seconds. Try again or use 1K resolution."
                )
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Quota exceeded", e)
                _uiState.value = ImageStudioUiState.Error(
                    "Quota or rate limit exceeded on your Firebase project. Please verify Blaze billing and quota."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageStudioUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    fun saveCurrentResultToVaultAndGallery() {
        val state = _uiState.value as? ImageStudioUiState.Success ?: return
        viewModelScope.launch {
            val fileName = "studio_${state.sizeLabel}_${System.currentTimeMillis()}.png"
            val savedEntity = mediaRepo.saveBitmapToVault(state.bitmap, fileName)
            val savedGallery = mediaRepo.saveToDeviceGallery(
                savedEntity.localVaultPath,
                savedEntity.fileName,
                savedEntity.mimeType
            )
            _actionMessage.value = if (savedGallery) {
                "Saved to ChatVault & Pictures/ChatVault!"
            } else {
                "Saved to ChatVault local media vault!"
            }
        }
    }

    suspend fun createShareIntentForCurrentResult(): Intent? {
        val state = _uiState.value as? ImageStudioUiState.Success ?: return null
        val fileName = "studio_share_${System.currentTimeMillis()}.png"
        val savedEntity = mediaRepo.saveBitmapToVault(state.bitmap, fileName)
        return mediaRepo.createShareIntent(savedEntity.localVaultPath, savedEntity.mimeType)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int = 1280): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        private const val TAG = "ImageStudioViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ChatVaultApplication
                val container = app.container
                ImageStudioViewModel(
                    application = app,
                    aiRepository = container.aiRepository,
                    mediaRepo = container.mediaVaultRepository
                )
            }
        }
    }
}
