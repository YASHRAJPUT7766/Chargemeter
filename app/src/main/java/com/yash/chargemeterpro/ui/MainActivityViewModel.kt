package com.yash.chargemeterpro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.chargemeterpro.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    val themeMode = settingsDataStore.themeMode
    val useDynamicColor = settingsDataStore.useDynamicColor

    /**
     * The top bar's quick theme toggle (see ChargeFlowTopBar) always
     * lands on an explicit "dark" or "light" — it never sets "system".
     * If the user is currently on "system", the toggle starts from
     * whatever that resolves to right now (passed in as
     * [currentlyResolvedDark] since only the composable knows the
     * actual on-screen resolution of "system" at this instant) and
     * flips away from it. The full three-way choice, including
     * "Follow system", remains available in Settings.
     */
    fun toggleTheme(currentlyResolvedDark: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(if (currentlyResolvedDark) "light" else "dark")
        }
    }
}
