package com.destinationalarm.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.destinationalarm.app.alarm.Sounds
import com.destinationalarm.app.data.Prefs
import com.destinationalarm.app.model.Fix
import com.destinationalarm.app.model.SavedPlace
import com.destinationalarm.app.net.Net
import com.destinationalarm.app.ui.Banner
import com.destinationalarm.app.ui.BrandAmber
import com.destinationalarm.app.ui.BrandBlue
import com.destinationalarm.app.ui.BrandGreen
import com.destinationalarm.app.ui.CardRaised
import com.destinationalarm.app.ui.DestAlarmTheme
import com.destinationalarm.app.ui.MainViewModel
import com.destinationalarm.app.ui.MapPane
import com.destinationalarm.app.ui.OnboardingScreen
import com.destinationalarm.app.ui.UiState
import com.destinationalarm.app.ui.openAppSettings
import com.destinationalarm.app.ui.rememberPermState
import com.destinationalarm.app.ui.requestBatteryExemption
import com.destinationalarm.app.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DestAlarmTheme {
                val context = applicationContext
                var onboarded by remember { mutableStateOf(Prefs.isOnboarded(context)) }

                if (!onboarded) {
                    OnboardingScreen(
                        onFinish = {
                            Prefs.setOnboarded(context)
                            onboarded = true
                        }
                    )
                } else {
                    RootScreen(vm)
                }
            }
        }
    }
}

/** A location the user is positioning with the Uber-style centre pin. */
private data class PendingPick(
    val lat: Double,
    val lon: Double,
    val name: String?
)

@Composable
private fun RootScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val perms = rememberPermState()
    val keyboard = LocalSoftwareKeyboardController.current

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.3"
    }

    var recenterTick by remember { mutableIntStateOf(0) }
    var soundLabel by remember { mutableStateOf(Sounds.label(context)) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    var pending by remember { mutableStateOf<PendingPick?>(null) }
    var cameraTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var centerLat by remember { mutableDoubleStateOf(0.0) }
    var centerLon by remember { mutableDoubleStateOf(0.0) }
    var address by remember { mutableStateOf("") }
    var addressLoading by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) {
            pickedRingtoneUri(result.data)
        } else {
            null
        }
        if (uri != null) {
            Prefs.setAlarmSound(context, uri.toString())
            soundLabel = Sounds.label(context)
        }
    }

    fun openSoundPicker() {
        val current = Prefs.alarmSound(context)
        val existing = if (current.startsWith("content:") || current.startsWith("file:")) {
            Uri.parse(current)
        } else {
            null
        }

        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }

        runCatching { ringtoneLauncher.launch(intent) }.onFailure {
            Toast.makeText(context, "No sound picker on this device", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessage()
        }
    }

    fun openPicker(lat: Double, lon: Double, name: String?) {
        keyboard?.hide()
        pending = PendingPick(lat, lon, name)
        centerLat = lat
        centerLon = lon
        address = name ?: ""
        cameraTarget = lat to lon
        cameraTick++
    }

    // Live reverse geocode of whatever sits under the centre pin.
    LaunchedEffect(pending, centerLat, centerLon) {
        if (pending == null) return@LaunchedEffect
        address = ""
        addressLoading = true
        delay(450)
        val resolved = withContext(Dispatchers.IO) {
            runCatching { Net.reverseName(centerLat, centerLon) }.getOrNull()
        }
        if (resolved != null && resolved.isNotBlank()) address = resolved
        addressLoading = false
    }

    BackHandler(enabled = pending != null) { pending = null }

    val banner: Pair<String, String>? = when {
        !perms.fine -> "Location permission is needed to track your trip." to "Allow"
        !perms.notifications -> "Notifications are off — the alarm screen may not appear." to "Allow"
        !perms.background -> "Background location is off — tracking may pause when the phone locks." to "Allow"
        !perms.batteryOk -> "Battery optimization is on — the alarm may be delayed." to "Fix"
        else -> null
    }

    val bannerAction: () -> Unit = {
        when {
            !perms.fine -> locationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

            !perms.notifications -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppSettings(context)
                }
            }

            !perms.background -> backgroundLauncher.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )

            else -> requestBatteryExemption(context)
        }
    }

    fun startStop() {
        when {
            state.tracking -> vm.stop()

            !perms.fine -> {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                Toast.makeText(context, "Allow location, then tap Start again", Toast.LENGTH_SHORT)
                    .show()
            }

            else -> {
                vm.start()
                if (!perms.notifications) {
                    Toast.makeText(
                        context,
                        "Allow notifications so the alarm can wake the screen",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Destination Alarm feedback")
            putExtra(
                Intent.EXTRA_TEXT,
                "App version: $version\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Android: ${Build.VERSION.RELEASE}\n\n"
            )
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Send feedback"))
        }.onFailure {
            Toast.makeText(context, "No app available to send feedback", Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapPane(
            modifier = Modifier.fillMaxSize(),
            dest = if (pending == null) state.trip else null,
            fix = state.fix,
            route = if (pending == null) state.routeLine else emptyList(),
            recenterTick = recenterTick,
            pickerActive = pending != null,
            cameraTarget = cameraTarget,
            cameraTick = cameraTick,
            onMapClick = { lat, lon ->
                if (pending == null) openPicker(lat, lon, null)
            },
            onCenterChanged = { lat, lon ->
                centerLat = lat
                centerLon = lon
            }
        )

        if (pending == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchBar(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        searching = state.searching,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    SettingsButton(onClick = { showSettings = true })
                }

                AnimatedVisibility(
                    visible = state.results.isNotEmpty(),
                    enter = fadeIn() + slideInVertically { -it / 3 },
                    exit = fadeOut() + slideOutVertically { -it / 3 }
                ) {
                    ResultsCard(
                        results = state.results,
                        fix = state.fix,
                        onPick = { place -> openPicker(place.lat, place.lon, place.name) }
                    )
                }

                AnimatedVisibility(visible = state.tracking) {
                    Box(Modifier.padding(top = 8.dp)) {
                        ArmedPill(etaSec = state.etaSec)
                    }
                }

                banner?.let { (text, action) -> Banner(text, action, bannerAction) }
            }

            SmallFloatingActionButton(
                onClick = { recenterTick++ },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 84.dp, end = 14.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My location")
            }

            BottomSheet(
                state = state,
                soundLabel = soundLabel,
                onStartStop = ::startStop,
                onTest = vm::testAlarm,
                onClear = vm::clearDestination,
                onSavePlace = { showSaveDialog = true },
                onUseSaved = vm::useSaved,
                onDeleteSaved = { id ->
                    vm.removeSaved(id)
                    Toast.makeText(context, "Saved place removed", Toast.LENGTH_SHORT).show()
                },
                onSound = { showSoundDialog = true },
                onRadius = vm::setRadius,
                onMins = vm::setMinsBefore,
                onMode = vm::setMode
            )
        } else {
            PickerPin()
            PickerBar(onBack = { pending = null })

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📌", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (address.isBlank()) "Locating address…" else address,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (addressLoading) "Looking up address…"
                                else "Alarm will ring near this point",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (addressLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { pending = null },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                val name = address.ifBlank {
                                    pending?.name ?: "Selected location"
                                }
                                vm.confirmDestination(centerLat, centerLon, name)
                                pending = null
                            },
                            modifier = Modifier
                                .weight(1.6f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Confirm location", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showSoundDialog) {
        SoundDialog(
            current = Prefs.alarmSound(context),
            onSelect = { value ->
                Prefs.setAlarmSound(context, value)
                soundLabel = Sounds.label(context)
                showSoundDialog = false
            },
            onCustom = {
                showSoundDialog = false
                openSoundPicker()
            },
            onDismiss = { showSoundDialog = false }
        )
    }

    if (showSettings) {
        SettingsSheet(
            soundLabel = soundLabel,
            vibrate = Prefs.vibrate(context),
            version = version,
            onVibrate = { Prefs.setVibrate(context, it) },
            onSound = {
                showSettings = false
                showSoundDialog = true
            },
            onPrivacy = {
                showSettings = false
                showPrivacy = true
            },
            onDelete = {
                showSettings = false
                showDelete = true
            },
            onFeedback = {
                showSettings = false
                sendFeedback()
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showPrivacy) PrivacyDialog(onDismiss = { showPrivacy = false })

    if (showDelete) {
        ConfirmDialog(
            title = "Delete all data?",
            body = "This removes your destination, saved places, alarm settings and sound choice from this phone. " +
                "The app itself stays installed. This cannot be undone.",
            confirm = "Delete",
            onConfirm = {
                vm.deleteAllData()
                Prefs.setAlarmSound(context, Prefs.SOUND_SYSTEM_ALARM)
                Prefs.setVibrate(context, true)
                soundLabel = Sounds.label(context)
                showDelete = false
                Toast.makeText(context, "All data deleted", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showDelete = false }
        )
    }

    if (showSaveDialog) {
        SavePlaceDialog(
            suggestion = state.trip?.name?.take(18) ?: "",
            onSave = { label ->
                if (vm.saveCurrentAs(label)) {
                    Toast.makeText(context, "\"$label\" saved", Toast.LENGTH_SHORT).show()
                }
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

/* ---------------- top bar ---------------- */

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text("⚙️", fontSize = 19.sp)
        }
    }
}

@Composable
private fun ArmedPill(etaSec: Int?) {
    val transition = rememberInfiniteTransition(label = "armed")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot"
    )

    Surface(
        color = Color(0xFF0F2418),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color(0xFF1F4D2C))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(BrandGreen.copy(alpha = alpha), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Alarm armed",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB6F0C4)
            )
            if (etaSec != null) {
                Text(
                    " · ${Geo.fmtDuration(etaSec)} away",
                    fontSize = 12.sp,
                    color = Color(0xFF7BCB92)
                )
            }
        }
    }
}

/* ---------------- bottom sheet ---------------- */

@Composable
private fun BoxScope.BottomSheet(
    state: UiState,
    soundLabel: String,
    onStartStop: () -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit,
    onSavePlace: () -> Unit,
    onUseSaved: (SavedPlace) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onSound: () -> Unit,
    onRadius: (Int) -> Unit,
    onMins: (Int) -> Unit,
    onMode: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 14.dp)
        ) {
            SheetHandle()

            DestinationCard(
                trip = state.trip,
                onClear = onClear,
                onSave = onSavePlace
            )

            if (state.saved.isNotEmpty() || state.trip != null) {
                Spacer(Modifier.height(12.dp))
                SavedChips(
                    saved = state.saved,
                    canSave = state.trip != null,
                    onUse = onUseSaved,
                    onDelete = onDeleteSaved,
                    onSave = onSavePlace
                )
            }

            if (state.trip != null) {
                Spacer(Modifier.height(16.dp))

                val distance = state.fix?.let {
                    Geo.safeDistance(it.lat, it.lon, state.trip.lat, state.trip.lon)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        icon = "🛣️",
                        label = "DISTANCE",
                        value = if (distance != null && distance >= 0) Geo.fmtDistance(distance) else "—",
                        tint = BrandBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = "⏱️",
                        label = "ETA",
                        value = Geo.fmtDuration(state.etaSec),
                        tint = BrandGreen,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        icon = "🔔",
                        label = "RING AT",
                        value = "${state.trip.radiusM} m",
                        tint = BrandAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = state.fix?.let { "GPS ±${it.acc.roundToInt()} m" }
                        ?: "Waiting for GPS…",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(14.dp))

                ModeSelector(mode = state.trip.mode, onMode = onMode)

                Spacer(Modifier.height(14.dp))

                val radius = state.trip.radiusM.toFloat()
                LabeledSlider(
                    label = "Alarm radius",
                    valueText = "${radius.toInt()} m",
                    value = radius,
                    range = 200f..2000f,
                    steps = 17,
                    onValueChange = { onRadius(it.roundToInt()) }
                )

                Spacer(Modifier.height(6.dp))

                val mins = state.trip.minsBefore.toFloat()
                LabeledSlider(
                    label = "Ring before arrival",
                    valueText = if (mins <= 0f) "Off" else "${mins.toInt()} min",
                    value = mins,
                    range = 0f..10f,
                    steps = 9,
                    onValueChange = { onMins(it.roundToInt()) }
                )

                Spacer(Modifier.height(12.dp))
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tip: tap anywhere on the map to drop a pin, or search a station above.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SoundRow(label = soundLabel, onClick = onSound)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(
                    text = if (state.tracking) "Stop alarm" else "Start alarm",
                    enabled = state.trip != null || state.tracking,
                    danger = state.tracking,
                    modifier = Modifier.weight(1f),
                    onClick = onStartStop
                )

                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = CardRaised,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onTest),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔔", fontSize = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Free maps · © OpenStreetMap",
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF5D6B7D),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
        )
    }
}

@Composable
private fun DestinationCard(
    trip: com.destinationalarm.app.model.Trip?,
    onClear: () -> Unit,
    onSave: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(CardRaised, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (trip == null) "🧭" else "📍", fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            if (trip == null) {
                Text(
                    "Where are you headed?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Search or tap the map to set your stop",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    trip.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${trip.radiusM} m radius · ${trip.mode.lowercase().replaceFirstChar { it.uppercase() }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trip != null) {
            IconButton(onClick = onSave) {
                Text("⭐", fontSize = 16.sp)
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear destination",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedChips(
    saved: List<SavedPlace>,
    canSave: Boolean,
    onUse: (SavedPlace) -> Unit,
    onDelete: (String) -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        saved.forEach { place ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardRaised,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.combinedClickable(
                    onClick = { onUse(place) },
                    onLongClick = { onDelete(place.id) }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(place.emoji, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        place.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        if (canSave) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable(onClick = onSave)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("＋", fontSize = 13.sp, color = BrandGreen)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Save",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: String,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(CardRaised, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModeSelector(mode: String, onMode: (String) -> Unit) {
    val modes = listOf("🚗 Drive" to "DRIVE", "🚇 Transit" to "TRANSIT", "🚶 Walk" to "WALK")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardRaised, RoundedCornerShape(15.dp))
            .padding(4.dp)
    ) {
        modes.forEach { (label, value) ->
            val selected = mode == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .clickable { onMode(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    enabled: Boolean,
    danger: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val brush = when {
        !enabled -> Brush.horizontalGradient(listOf(Color(0xFF1B2430), Color(0xFF151D27)))
        danger -> Brush.horizontalGradient(listOf(Color(0xFFF85149), Color(0xFFB6231D)))
        else -> Brush.horizontalGradient(listOf(Color(0xFF46E07A), Color(0xFF2BBF8A)))
    }

    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = when {
                !enabled -> Color(0xFF5D6B7D)
                danger -> Color.White
                else -> Color(0xFF04120A)
            }
        )
    }
}

/* ---------------- picker ---------------- */

@Composable
private fun BoxScope.PickerBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                    "Choose exact location",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Drag the map to adjust the pin",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PickerPin() {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = (-28).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📍", fontSize = 46.sp)
    }
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(8.dp)
            .background(Color.White, CircleShape)
    )
}

/* ---------------- search ---------------- */

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    searching: Boolean,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(52.dp),
        placeholder = { Text("Search any place on Earth…", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )

                value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun ResultsCard(results: List<Net.Place>, fix: Fix?, onPick: (Net.Place) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 290.dp)
                .verticalScroll(rememberScrollState())
        ) {
            results.forEachIndexed { index, place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(place) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(CardRaised, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(place.icon, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            place.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (place.sub.isNotBlank()) {
                            Text(
                                place.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val d = fix?.let { Geo.safeDistance(it.lat, it.lon, place.lat, place.lon) }
                    if (d != null && d >= 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            Geo.fmtShortDistance(d),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index != results.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/* ---------------- sound ---------------- */

@Composable
private fun SoundRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔊", fontSize = 17.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Alarm sound",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "Change",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SoundDialog(
    current: String,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Prefs.SOUND_SYSTEM_ALARM to "System alarm sound",
        Prefs.SOUND_RINGTONE to "System ringtone",
        Prefs.SOUND_NOTIFICATION to "Notification sound",
        Prefs.SOUND_APP_TONE to "App tone (offline)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alarm sound", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == value,
                            onClick = { onSelect(value) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onCustom)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎵", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Choose from device…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Text(
                    "Plays on the alarm stream, so silent mode does not mute it. " +
                        "In Do Not Disturb, keep \"Alarms\" allowed.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/* ---------------- settings ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    soundLabel: String,
    vibrate: Boolean,
    version: String,
    onVibrate: (Boolean) -> Unit,
    onSound: () -> Unit,
    onPrivacy: () -> Unit,
    onDelete: () -> Unit,
    onFeedback: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Everything stays on this phone.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            SettingRow("🔊", "Alarm sound", soundLabel, onSound)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📳", fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Vibration", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Buzz while the alarm rings",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = vibrate, onCheckedChange = onVibrate)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow("🔒", "Privacy", "No account, no tracking", onPrivacy)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow("✉️", "Send feedback", "Tell us what to fix", onFeedback)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SettingRow(
                "🗑️",
                "Delete all data",
                "Trips, saved places, settings",
                onDelete,
                danger = true
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "Destination Alarm v$version · Free maps © OpenStreetMap",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 10.sp,
                color = Color(0xFF5D6B7D),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingRow(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    val body = """
        Destination Alarm is designed to work without an account.

        WHAT WE STORE ON YOUR PHONE
        • Your destination, alarm radius and mode
        • Saved places (Home / Work / custom)
        • Alarm sound choice and vibration setting
        • An onboarding flag so setup does not repeat

        WHAT WE NEVER COLLECT
        • No name, email or phone number
        • No location history
        • No advertising ID
        • No analytics or third-party trackers

        NETWORK REQUESTS
        When you search or set a destination, your query and map view are sent
        to free OpenStreetMap-based services (Photon, Nominatim, OSRM) to return
        results and routes. Map tiles come from OpenFreeMap. These requests are
        not linked to any identity.

        YOUR CONTROL
        "Delete all data" in Settings removes every stored value from this
        device instantly. Uninstalling the app does the same.

        The alarm itself is computed on your phone — nothing is uploaded.
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(body, fontSize = 12.sp, lineHeight = 18.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, fontSize = 13.sp, lineHeight = 19.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SavePlaceDialog(
    suggestion: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this place", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Give it a short name, then start the alarm with one tap.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 18) label = it },
                    singleLine = true,
                    placeholder = { Text("Home, Work, Gym…", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work", "Gym").forEach { quick ->
                        AssistChip(
                            onClick = { label = quick },
                            label = { Text(quick, fontSize = 12.sp) }
                        )
                    }
                }
                if (suggestion.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Destination: $suggestion",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label.trim().ifBlank { suggestion.ifBlank { "Place" } }) },
                enabled = label.isNotBlank()
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Suppress("DEPRECATION")
private fun pickedRingtoneUri(data: Intent?): Uri? {
    if (data == null) return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    } else {
        data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
    }
}
