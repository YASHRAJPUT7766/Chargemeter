package com.yash.chargemeterpro.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.BuildConfig
import com.yash.chargemeterpro.ui.components.InstrumentCard
import com.yash.chargemeterpro.ui.navigation.ScreenBackTopBar
import com.yash.chargemeterpro.ui.theme.PanelGray

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenBackTopBar(title = "About", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ChargeMeter Pro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PanelGray
                        )
                        Text(
                            "ChargeMeter Pro reads the charging and battery information your Android device already makes available through the platform's official BatteryManager APIs, and presents it as clearly and accurately as possible.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "What this app can and can't measure",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Battery voltage, current, temperature, and percentage come directly from your device's own battery fuel gauge — this app doesn't add its own sensors or hardware access beyond what Android exposes.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Wattage figures are calculated from these values (Power = Voltage × Current) and represent power flowing into the battery, not the charger's wall-side output — no consumer Android device can measure that directly.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Some fields — cycle count, design capacity, and specific fast-charging protocol names — aren't available on every device. When they're not, this app says so plainly instead of guessing.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                InstrumentCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Developer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Yash", style = MaterialTheme.typography.bodyMedium)
                        Text("yash92726@gmail.com", style = MaterialTheme.typography.bodyMedium, color = PanelGray)
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenBackTopBar(title = "Privacy Policy", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PolicySection(
                    title = "What we collect",
                    body = "ChargeMeter Pro does not collect personal information. The app does not require an account, and no name, email, or contact information is gathered by the app itself."
                )
            }
            item {
                PolicySection(
                    title = "Where your data lives",
                    body = "Charging session history, battery readings, and app settings are stored only in a local database on your device (Android Room/SQLite and DataStore preferences). This data is never uploaded to any server by default."
                )
            }
            item {
                PolicySection(
                    title = "Cloud backup (optional, off by default)",
                    body = "If you explicitly enable Cloud Backup in Settings, your charging history may sync to a backend to make it available across devices. This feature is off unless you turn it on, and turning it back off stops future syncing."
                )
            }
            item {
                PolicySection(
                    title = "Analytics (optional, off by default)",
                    body = "If you explicitly enable Anonymous Usage Analytics in Settings, the app may report anonymous, aggregated usage patterns to help improve the app. This never includes your specific charging readings, history, or device identifiers tied to you personally, and is off unless you turn it on."
                )
            }
            item {
                PolicySection(
                    title = "Permissions",
                    body = "The app requests only the permissions needed for its features: posting notifications for charging alerts, running a foreground service for the optional Always-On Monitor, and restarting scheduled background checks after a reboot. It does not request location, contacts, or broad storage access."
                )
            }
            item {
                PolicySection(
                    title = "Exporting and sharing your data",
                    body = "When you export a CSV or PDF report or share a session, that file is created locally on your device and only leaves the device through the sharing action you explicitly choose (e.g. the system share sheet)."
                )
            }
            item {
                PolicySection(
                    title = "Contact",
                    body = "Questions about this policy can be directed to yash92726@gmail.com."
                )
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    InstrumentCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = PanelGray)
        }
    }
}
