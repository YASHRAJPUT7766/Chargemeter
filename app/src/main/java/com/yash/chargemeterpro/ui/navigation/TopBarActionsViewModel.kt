package com.yash.chargemeterpro.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.export.PdfExportBuilder
import com.yash.chargemeterpro.export.SvgExportBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the top bar's Share action (see ChargeFlowTopBar): builds a
 * report of the CURRENT live battery/charging snapshot — as opposed to
 * SessionDetailViewModel's share, which reports on one already-completed,
 * saved session. Offers both PDF and SVG, per spec, and reuses the same
 * FileProvider-backed share-Uri pattern as every other export in the
 * app rather than introducing a new sharing mechanism.
 */
@HiltViewModel
class TopBarActionsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _shareEvents = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<Uri> = _shareEvents

    fun shareCurrentStatusAsPdf(snapshot: BatterySnapshot) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val file = PdfExportBuilder.buildLiveStatusReport(appContext, snapshot)
                PdfExportBuilder.shareUriFor(appContext, file)
            }
            _shareEvents.emit(uri)
        }
    }

    fun shareCurrentStatusAsSvg(snapshot: BatterySnapshot) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val file = SvgExportBuilder.buildLiveStatusSvg(appContext, snapshot)
                SvgExportBuilder.shareUriFor(appContext, file)
            }
            _shareEvents.emit(uri)
        }
    }
}
