package com.destinationalarm.app.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.destinationalarm.app.ui.DestAlarmTheme

class AlarmActivity : ComponentActivity() {

    private var alarmTitle by mutableStateOf("You're here!")
    private var alarmSub by mutableStateOf("Destination reached")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        readExtras(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
        render()
    }

    private fun readExtras(intent: Intent?) {
        alarmTitle = intent?.getStringExtra(AlarmService.EXTRA_TITLE) ?: alarmTitle
        alarmSub = intent?.getStringExtra(AlarmService.EXTRA_SUB) ?: alarmSub
    }

    private fun render() {
        setContent {
            DestAlarmTheme {
                AlarmScreen(
                    title = alarmTitle,
                    subtitle = alarmSub,
                    onDismiss = { act(AlarmService.ACTION_DISMISS) },
                    onSnooze = { act(AlarmService.ACTION_SNOOZE) }
                )
            }
        }
    }

    private fun act(action: String) {
        startService(Intent(this, AlarmService::class.java).setAction(action))
        finishAndRemoveTask()
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "scale"
    )

    val bg = Brush.radialGradient(
        colors = listOf(Color(0xFF3A0D10), Color(0xFF0D0405)),
        radius = 1200f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⏰",
                fontSize = 76.sp,
                modifier = Modifier.scale(pulse)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = subtitle,
                color = Color(0xFFD9A7A7),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(44.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF2F5F8),
                    contentColor = Color(0xFF111111)
                )
            ) {
                Text("Dismiss", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x1AFFFFFF),
                    contentColor = Color.White
                )
            ) {
                Text("Snooze 5 min", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
