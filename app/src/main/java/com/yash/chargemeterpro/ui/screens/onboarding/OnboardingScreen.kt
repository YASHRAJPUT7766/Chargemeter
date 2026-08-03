package com.yash.chargemeterpro.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yash.chargemeterpro.ui.theme.Hairline
import com.yash.chargemeterpro.ui.theme.InstrumentBg
import com.yash.chargemeterpro.ui.theme.PanelGray
import com.yash.chargemeterpro.ui.theme.PhosphorGreen
import com.yash.chargemeterpro.ui.theme.PhosphorGreenDim
import kotlinx.coroutines.delay

/**
 * First-launch experience: a branded splash beat, then a feature-highlight
 * screen with a typewriter reveal and a "Get Started" CTA. Both are gated
 * behind SettingsDataStore.onboardingComplete (see ChargeMeterNavHost) so
 * this entire flow is shown exactly once — every subsequent cold start
 * goes straight to Home behind the plain system splash in MainActivity.
 */
private enum class OnboardingStage { SPLASH, FEATURES }

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    var stage by remember { mutableStateOf(OnboardingStage.SPLASH) }

    when (stage) {
        OnboardingStage.SPLASH -> OnboardingSplashStage(onFinished = { stage = OnboardingStage.FEATURES })
        OnboardingStage.FEATURES -> FeaturesStage(onGetStarted = onGetStarted)
    }
}

/**
 * Standalone splash content, reused as-is for the "cold start after
 * onboarding is already complete" path too (see SplashGate in
 * ChargeMeterNavHost) — same visual, but there it's a fixed-duration
 * beat rather than the first stage of onboarding.
 */
@Composable
fun OnboardingSplashStage(onFinished: () -> Unit, minDurationMillis: Long = 1400L) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = minDurationMillis.toInt(), easing = LinearEasing))
        delay(150)
        onFinished()
    }

    val sweepTransition = rememberInfiniteTransitionSafe()
    val sweepOffset by sweepTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweepOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InstrumentBg),
        contentAlignment = Alignment.Center
    ) {
        // Diagonal phosphor light sweep, subtle, behind everything
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val bandWidth = w * 0.5f
            val cx = w * sweepOffset
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, PhosphorGreen.copy(alpha = 0.10f), Color.Transparent),
                    startX = cx - bandWidth / 2f,
                    endX = cx + bandWidth / 2f
                ),
                topLeft = Offset(0f, 0f),
                size = size
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(size = 88.dp)
            Spacer(modifier = Modifier.height(20.dp))
            BrandWordmark(fontSize = 30.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Smart. Fast. Powerful.",
                style = MaterialTheme.typography.bodyMedium,
                color = PanelGray
            )

            Spacer(modifier = Modifier.height(56.dp))

            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(PhosphorGreenDim, PhosphorGreen)))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Loading...", style = MaterialTheme.typography.labelMedium, color = PanelGray)
        }
    }
}

@Composable
private fun rememberInfiniteTransitionSafe() =
    androidx.compose.animation.core.rememberInfiniteTransition(label = "splashSweep")

// ---------------------------------------------------------------------
// Stage 2 — feature highlights with typewriter reveal + Get Started CTA
// ---------------------------------------------------------------------

private val features = listOf(
    "Live Charging Monitor",
    "Advanced Statistics",
    "Charging History",
    "Battery Health",
    "Beautiful & Modern UI"
)

@Composable
private fun FeaturesStage(onGetStarted: () -> Unit) {
    var revealedCount by remember { mutableIntStateOf(0) }
    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        for (i in features.indices) {
            revealedCount = i + 1
            delay(260)
        }
        delay(200)
        showButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InstrumentBg)
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            BrandMark(size = 72.dp)

            Spacer(modifier = Modifier.height(18.dp))
            BrandWordmark(fontSize = 28.sp)

            Text(
                "Battery Intelligence\nIn Your Hands",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                features.forEachIndexed { index, feature ->
                    AnimatedVisibility(
                        visible = index < revealedCount,
                        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }
                    ) {
                        TypewriterFeatureRow(text = feature, play = index < revealedCount)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Fixed-position button — a plain, clearly-visible green
            // pill with black "Get Started" text, always laid out right
            // after the feature list rather than pinned via a weighted
            // spacer, so it can never end up pushed off-screen.
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PhosphorGreen, contentColor = Color.Black)
                ) {
                    Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TypewriterFeatureRow(text: String, play: Boolean) {
    var visibleChars by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(play) {
        if (play) {
            visibleChars = 0
            for (i in 1..text.length) {
                visibleChars = i
                delay(18L)
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(PhosphorGreen.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = PhosphorGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text.take(visibleChars),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
    }
}

// ---------------------------------------------------------------------
// Shared brand elements
// ---------------------------------------------------------------------

@Composable
private fun BrandMark(size: Dp) {
    // Uses the plain PNG toolbar-logo asset (already used by
    // ChargeFlowTopBar) rather than R.mipmap.ic_launcher — the launcher
    // icon is an <adaptive-icon> XML, and painterResource() only supports
    // VectorDrawables and rasterized assets (PNG/JPG/WEBP), so loading
    // the adaptive mipmap directly crashes with IllegalArgumentException
    // on first launch. This PNG is the same artwork without that wrapper.
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.yash.chargemeterpro.R.drawable.ic_toolbar_logo),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
    )
}

@Composable
private fun BrandWordmark(fontSize: androidx.compose.ui.unit.TextUnit) {
    Row {
        Text("Charge", fontSize = fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("Flow", fontSize = fontSize, fontWeight = FontWeight.Bold, color = PhosphorGreen)
    }
}
