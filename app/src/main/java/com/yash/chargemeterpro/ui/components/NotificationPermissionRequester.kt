package com.yash.chargemeterpro.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Requests POST_NOTIFICATIONS (required on Android 13/API 33+ for any app
 * that wants to post notifications — everything below API 33 doesn't
 * need this permission at all, notifications just work). Without this,
 * every Smart Charging Alert in Settings would silently do nothing on a
 * Tiramisu+ device, since ChargeMeterNotificationManager already checks
 * NotificationManagerCompat.areNotificationsEnabled() before posting and
 * simply no-ops if denied — but the user needs an actual chance to grant
 * it first.
 *
 * Placed once near app entry (MainActivity's Compose tree) rather than
 * per-screen, so it's requested a single time up front instead of
 * surprising the user mid-task on whichever screen happens to trigger a
 * notification first.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NotificationPermissionRequester() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return // permission doesn't exist pre-API 33

    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    LaunchedEffect(permissionState) {
        if (!permissionState.status.isGranted && !permissionState.status.shouldShowRationale) {
            // First-ever ask: request directly. If previously denied once,
            // shouldShowRationale becomes true and we deliberately do NOT
            // auto-request again here — repeatedly prompting after a
            // decline is poor practice; the user can still enable alerts
            // later via system Settings, and the app functions fully
            // without notifications regardless.
            permissionState.launchPermissionRequest()
        }
    }
}
