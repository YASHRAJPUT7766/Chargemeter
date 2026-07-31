package com.yash.chargemeterpro.ui.screens.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.local.entity.ChargingSessionEntity
import com.yash.chargemeterpro.data.repository.ChargingSessionRepository
import com.yash.chargemeterpro.export.CsvExportBuilder
import com.yash.chargemeterpro.export.PdfExportBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: ChargingSessionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val sessions: StateFlow<List<ChargingSessionEntity>> = sessionRepository.observeAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * One-shot "here's a file ready to share" event. Modeled as a
     * SharedFlow (not StateFlow) deliberately — a StateFlow would
     * re-deliver the same share-sheet launch on every configuration
     * change/recomposition that re-collects it, which would incorrectly
     * re-open the share sheet e.g. after a screen rotation.
     */
    private val _shareEvents = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<Uri> = _shareEvents

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { sessionRepository.deleteSession(sessionId) }
    }

    fun deleteAllHistory() {
        viewModelScope.launch { sessionRepository.deleteAllHistory() }
    }

    /** Feature: "Export charging history as CSV". File I/O runs on Dispatchers.IO since writing to cache is blocking work. */
    fun exportHistoryCsv() {
        viewModelScope.launch {
            val currentSessions = sessions.value
            val uri = withContext(Dispatchers.IO) {
                val file = CsvExportBuilder.buildSessionsSummaryCsv(appContext, currentSessions)
                CsvExportBuilder.shareUriFor(appContext, file)
            }
            _shareEvents.emit(uri)
        }
    }

    /** Feature: "Export charging report as PDF". */
    fun exportHistoryPdf() {
        viewModelScope.launch {
            val currentSessions = sessions.value
            val uri = withContext(Dispatchers.IO) {
                val file = PdfExportBuilder.buildHistoryReport(appContext, currentSessions)
                PdfExportBuilder.shareUriFor(appContext, file)
            }
            _shareEvents.emit(uri)
        }
    }
}
