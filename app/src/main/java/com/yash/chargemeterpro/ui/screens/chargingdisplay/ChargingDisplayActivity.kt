package com.yash.chargemeterpro.ui.screens.chargingdisplay

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.chargemeterpro.data.repository.BatteryRepository
import com.yash.chargemeterpro.domain.model.BatterySnapshot
import com.yash.chargemeterpro.domain.model.ChargingStatus
import com.yash.chargemeterpro.domain.usecase.PowerCalculator
import com.yash.chargemeterpro.ui.theme.ChargeMeterProTheme
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.VoltageBlue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.math.min

/**
 * The "Charging Display" screen — shows full-screen over the lock screen
 * the moment charging starts (if the user enabled it in Settings ->
 * Charging Display), similar to the OEM charging-animation screens on
 * MIUI/OnePlus/ColorOS. Dismisses itself automatically the moment
 * charging stops, or immediately if the user taps anywhere on screen.
 *
 * HONESTY NOTE ABOUT THE DECIMAL ANIMATION: Android's battery API only
 * ever reports whole-number percent (45%, 46%, ...) -- no device exposes
 * true sub-percent precision. The smoothly-climbing "45.01% -> 45.02%..."
 * display here is a deliberately-labeled SIMULATION: it interpolates
 * between the two most recent real integer readings using the actual
 * measured charge rate (percent-per-minute -- the same honest approach
 * ChargeTimeEstimator uses elsewhere in this app), so the pace you see
 * genuinely reflects how fast the phone is charging right now. It never
 * outruns the next real reading -- every time a new real integer %
 * arrives, the animation re-anchors to it rather than drifting further
 * from reality.
 */
@AndroidEntryPoint
class ChargingDisplayActivity : ComponentActivity() {

    @Inject lateinit var batteryRepository: BatteryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        keyguardManager?.requestDismissKeyguard(this, null)

        enableEdgeToEdge()
        setContent {
            ChargeMeterProTheme {
                ChargingDisplayScreen(
                    batteryRepository = batteryRepository,
                    onDismiss = { finish() }
                )
            }
        }
    }

    companion object {
        fun launchIntent(context: Context): Intent =
            Intent(context, ChargingDisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
    }
}

@Composable
private fun ChargingDisplayScreen(
    batteryRepository: BatteryRepository,
    onDismiss: () -> Unit
) {
    var snapshot by remember { mutableStateOf(batteryRepository.readSnapshotNow()) }
    var previousSnapshot by remember { mutableStateOf<BatterySnapshot?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val fresh = batteryRepository.readSnapshotNow()
            if (!fresh.isCharging) {
                onDismiss()
                return@LaunchedEffect
            }
            if (fresh.batteryPercent != snapshot.batteryPercent) {
                previousSnapshot = snapshot
            }
            snapshot = fresh
            delay(3000)
        }
    }

    val watts = PowerCalculator.batteryInputPowerWatts(snapshot)
    val ratePercentPerMinute = remember(snapshot, previousSnapshot) {
        val prev = previousSnapshot ?: return@remember null
        val minutes = (snapshot.timestampMillis - prev.timestampMillis) / 60_000.0
        val delta = snapshot.batteryPercent - prev.batteryPercent
        if (minutes <= 0.0 || delta <= 0) null else delta / minutes
    }

    val animatedPercent = remember { Animatable(snapshot.batteryPercent.toFloat()) }
    LaunchedEffect(snapshot.batteryPercent, ratePercentPerMinute) {
        val basePercent = snapshot.batteryPercent.toFloat()
        val ratePerSecond = (ratePercentPerMinute ?: 0.2) / 60.0
        val cap = basePercent + 0.97f
        animatedPercent.snapTo(basePercent)
        var elapsedSeconds = 0.0
        while (isActive) {
            delay(100)
            elapsedSeconds += 0.1
            val next = min(cap, basePercent + (ratePerSecond * elapsedSeconds).toFloat())
            animatedPercent.snapTo(next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF060A0E), Color(0xFF0A1420))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ChargingRing(displayPercent = animatedPercent.value, isCharging = snapshot.isCharging)

            VSpace(24.dp)

            Text(
                text = "%.2f%%".format(animatedPercent.value),
                style = MaterialTheme.typography.displayMedium,
                color = PhosphorGreen,
                fontWeight = FontWeight.Bold
            )

            VSpace(8.dp)

            Text(
                text = statusLine(snapshot),
                style = MaterialTheme.typography.bodyLarge,
                color = PanelGray
            )

            VSpace(16.dp)

            Text(
                text = watts?.let { "%.1f W".format(it) } ?: "-- W",
                style = MaterialTheme.typography.titleLarge,
                color = VoltageBlue,
                fontWeight = FontWeight.SemiBold
            )

            VSpace(32.dp)

            Text(
                text = "Simulated fine-grained readout, based on measured charge rate -- tap anywhere to dismiss",
                style = MaterialTheme.typography.labelSmall,
                color = PanelGray.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ChargingRing(displayPercent: Float, isCharging: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "charging_pulse")
    val pulseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val ringSize = 220.dp
    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = Color(0xFF1B2530),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val sweep = (displayPercent / 100f).coerceIn(0f, 1f) * 360f
            drawArc(
                brush = Brush.sweepGradient(listOf(PhosphorGreen, VoltageBlue, PhosphorGreen)),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (isCharging) {
                rotate(pulseRotation) {
                    drawCircle(
                        color = PhosphorGreen.copy(alpha = 0.6f),
                        radius = 5.dp.toPx(),
                        center = Offset(size.width / 2, strokeWidth / 2)
                    )
                }
            }
        }

        Text(
            text = "${displayPercent.toInt()}%",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

private fun statusLine(snapshot: BatterySnapshot): String = when (snapshot.chargingStatus) {
    ChargingStatus.CHARGING -> "Charging"
    ChargingStatus.FULL -> "Fully charged"
    else -> "Connected"
}

@Composable
private fun VSpace(height: Dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(height))
}
