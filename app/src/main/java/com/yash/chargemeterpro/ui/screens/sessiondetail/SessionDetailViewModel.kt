package com.yash.chargemeterpro.ui.screens.sessiondetail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.local.entity.ChargingSampleEntity
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import com.yash.chargemeterpro.export.CsvExportBuilder
import com.yash.chargemeterpro.export.PdfExportBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SessionDetailUiState(
    val session: ChargingSessionEntity? = null,
    val samples: List<ChargingSampleEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionRepository: ChargingSessionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    private val _shareEvents = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<Uri> = _shareEvents

    private var loadedSessionId: Long? = null

    /** Safe to call every recomposition — only actually (re)subscribes if [sessionId] changed. */
    fun load(sessionId: Long) {
        if (loadedSessionId == sessionId) return
        loadedSessionId = sessionId
        viewModelScope.launch {
            combine(
                sessionRepository.observeAllSessions(),
                sessionRepository.observeSamplesForSession(sessionId)
            ) { allSessions, samples ->
                val session = allSessions.firstOrNull { it.id == sessionId }
                SessionDetailUiState(session = session, samples = samples, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }

    /** Feature: "Share charging session report" — builds a PDF for this single session and emits its shareable Uri. */
    fun shareAsPdf() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val file = PdfExportBuilder.buildSingleSessionReport(appContext, session)
                PdfExportBuilder.shareUriFor(appContext, file)
            }
            _shareEvents.emit(uri)
        }
    }

    /** CSV export of this session's raw sample time-series. */
    fun exportSamplesAsCsv() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val file = CsvExportBuilder.buildSessionSamplesCsv(appContext, session.id, _uiState.value.samples)
                CsvExportBuilder.shareUriFor(appContext, file)
            }
            _shareEvents.emit(uri)
        }
    }
}
