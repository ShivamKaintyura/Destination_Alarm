package com.destinationalarm.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Green = Color(0xFF3FB950)
private val SoftGreen = Color(0xFF7EE787)
private val Muted = Color(0xFF8B98A9)

/**
 * First-launch screen. Automatically walks the user through every permission the
 * alarm needs (notifications -> foreground location -> background location ->
 * battery exemption) so they never have to hunt through Settings themselves.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val perms = rememberPermState()

    var step by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var autoStarted by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = 2 }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { step = 3 }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = 5 }

    // Kick the whole chain off automatically on first launch.
    LaunchedEffect(Unit) {
        if (autoStarted) return@LaunchedEffect
        autoStarted = true
        delay(800)
        val p = readPerms(context)
        if (!p.fine || !p.notifications) {
            attempts++
            step = 1
        }
    }

    LaunchedEffect(step) {
        if (step !in 1..4) return@LaunchedEffect
        delay(350)
        val p = readPerms(context)
        when (step) {
            1 -> if (!p.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                step = 2
            }

            2 -> if (!p.fine) {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                step = 3
            }

            3 -> if (p.fine && !p.background) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                step = 5
            }

            else -> step = 5
        }
    }

    val primaryLabel: String
    val primaryAction: () -> Unit
    when {
        !perms.fine || !perms.notifications -> {
            if (attempts >= 2 && !perms.fine) {
                primaryLabel = "Open app settings"
                primaryAction = { openAppSettings(context) }
            } else {
                primaryLabel = "Allow location & notifications"
                primaryAction = {
                    attempts++
                    step = 1
                }
            }
        }

        !perms.background -> {
            primaryLabel = "Allow background location"
            primaryAction = {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        !perms.batteryOk -> {
            primaryLabel = "Keep tracking alive"
            primaryAction = { requestBatteryExemption(context) }
        }

        else -> {
            primaryLabel = "Start using Destination Alarm"
            primaryAction = onFinish
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1220), Color(0xFF0D1117), Color(0xFF07130F))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(340.dp)
                .offset(x = 130.dp, y = (-120).dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color(0x553FB950), Color(0x113FB950), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-120).dp, y = 520.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Color(0x334A9EFF), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            val transition = rememberInfiniteTransition(label = "glow")
            val pulse by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.09f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                            alpha = 0.35f
                        }
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0x773FB950), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Surface(
                    modifier = Modifier.size(104.dp),
                    shape = CircleShape,
                    color = Color(0xFF161B22),
                    shadowElevation = 14.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⏰", fontSize = 46.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Destination Alarm",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFF0F6FC),
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Snooze on the metro. We'll wake you right before your stop.",
                fontSize = 14.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(28.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF141A22),
                shadowElevation = 10.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    FeatureRow("🌍", "Search any place on Earth", "Stations, addresses, landmarks")
                    Spacer(Modifier.height(14.dp))
                    FeatureRow("🔔", "Rings on silent mode", "Loud alarm on the alarm channel")
                    Spacer(Modifier.height(14.dp))
                    FeatureRow("🛰️", "Screen locked? Still works", "Background tracking until you arrive")
                }
            }

            Spacer(Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF10161D)
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        "SETUP",
                        fontSize = 10.sp,
                        color = Muted,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    StatusRow("Location", perms.fine)
                    StatusRow("Notifications", perms.notifications)
                    StatusRow("Background location", perms.background)
                    StatusRow("Battery optimization off", perms.batteryOk)
                }
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = primaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green,
                    contentColor = Color(0xFF07130F)
                )
            ) {
                Text(primaryLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            TextButton(onClick = onFinish) {
                Text("Skip for now", color = Muted, fontSize = 13.sp)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "Free maps · © OpenStreetMap contributors",
                fontSize = 10.sp,
                color = Color(0xFF4C5866),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF1D2632), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 19.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE6EDF3)
            )
            Text(sub, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (ok) "✓" else "○",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) SoftGreen else Color(0xFF5D6B7D)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFFC9D4E0),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (ok) "Allowed" else "Needed",
            fontSize = 11.sp,
            color = if (ok) SoftGreen else Color(0xFF5D6B7D)
        )
    }
}

