package com.yash.chargemeterpro.ui

import androidx.lifecycle.ViewModel
import com.yash.chargemeterpro.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsDataStore: SettingsDataStore
) : ViewModel() {
    val themeMode = settingsDataStore.themeMode
    val useDynamicColor = settingsDataStore.useDynamicColor
}
