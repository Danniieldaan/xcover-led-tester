package com.xcover.ledtester

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var ledManager: XCoverLedManager
    private var countdownTick by mutableIntStateOf(-1)
    private var countdownFiredTick = 0
    private var uiTickVersion by mutableIntStateOf(0)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            uiTickVersion += 1
        }

    private val countdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LedTriggerService.ACTION_COUNTDOWN_TICK -> {
                    countdownTick = intent.getIntExtra(LedTriggerService.EXTRA_SECONDS_LEFT, -1)
                }
                LedTriggerService.ACTION_COUNTDOWN_FIRED -> {
                    countdownTick = -1
                    countdownFiredTick += 1
                    uiTickVersion += 1
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ledManager = XCoverLedManager(applicationContext)
        ledManager.registerAllChannels()
        requestPostNotificationsIfNeeded()

        val filter = IntentFilter().apply {
            addAction(LedTriggerService.ACTION_COUNTDOWN_TICK)
            addAction(LedTriggerService.ACTION_COUNTDOWN_FIRED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(countdownReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(countdownReceiver, filter)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Dashboard(
                        countdownSecondsLeft = countdownTick,
                        firedCount = countdownFiredTick,
                        refreshToken = uiTickVersion,
                        onTrigger = ::startDelayedTrigger,
                        onClear = { ledManager.clearAll() },
                        onOpenNotificationSettings = ::openNotificationSettings,
                        onOpenDndSettings = ::openDndSettings,
                        onRecreateChannels = {
                            for (color in XCoverLedManager.SUPPORTED_COLORS) {
                                ledManager.recreateChannelForColor(color)
                            }
                            uiTickVersion += 1
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        uiTickVersion += 1
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(countdownReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startDelayedTrigger(color: XCoverLedManager.LedColor) {
        countdownTick = 5
        val intent = Intent(this, LedTriggerService::class.java).apply {
            putExtra(LedTriggerService.EXTRA_COLOR_KEY, color.key)
            putExtra(LedTriggerService.EXTRA_DELAY_MS, 5000L)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun openDndSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }
}

@Composable
private fun Dashboard(
    countdownSecondsLeft: Int,
    firedCount: Int,
    refreshToken: Int,
    onTrigger: (XCoverLedManager.LedColor) -> Unit,
    onClear: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDndSettings: () -> Unit,
    onRecreateChannels: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { XCoverLedManager(context.applicationContext) }
    var selectedKey by remember { mutableStateOf(XCoverLedManager.SUPPORTED_COLORS[0].key) }
    val selected = manager.colorForKey(selectedKey)
    val countingDown = countdownSecondsLeft >= 0

    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val dndGranted = remember(refreshToken) { notificationManager.isNotificationPolicyAccessGranted }
    val postGranted = remember(refreshToken) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "XCover Pro LED Tester",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Top hardware LED • screen-off testing",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A00)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "HARDWARE READINESS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFFFFC107)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Unplug charger (charging overrides LED). 2. Ensure Settings > Notifications > Advanced settings > LED indicator is ON.",
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "The LED only lights with the screen OFF. Trigger with a delay, then lock the screen.",
                    fontSize = 13.sp,
                    color = Color(0xFFFFE082)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenNotificationSettings) {
                        Text("Open Notification Settings", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (!dndGranted) {
                    Button(
                        onClick = onOpenDndSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                    ) {
                        Text("Grant Do Not Disturb Bypass", color = Color.Black, fontSize = 13.sp)
                    }
                } else {
                    Text("DND bypass granted.", color = Color(0xFF81C784), fontSize = 13.sp)
                }
                if (!postGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        "POST_NOTIFICATIONS not granted — allow notifications when prompted.",
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Pick LED color", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(340.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(XCoverLedManager.SUPPORTED_COLORS) { color ->
                        val isSelected = color.key == selectedKey
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedKey = color.key },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1B3A4B)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(color.argb))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    color.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Each color uses its own channel (${manager.channelIdForColor(selectedKey)}).",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = onRecreateChannels) {
                    Text("Recreate all channels", fontSize = 12.sp)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (countingDown) Color(0xFFB71C1C) else Color(0xFF1B2A1B)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (countingDown) {
                    Text(
                        "LOCK YOUR SCREEN NOW!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "$countdownSecondsLeft",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "LED (${selected.label}) fires in $countdownSecondsLeft s. Press power, unplug charger.",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                } else {
                    Text("Selected: ${selected.label}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onTrigger(selected) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Trigger LED in 5 Seconds", fontSize = 16.sp)
                    }
                    if (firedCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Fired $firedCount time(s). Lock screen was required to see it.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        Button(
            onClick = onClear,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        ) {
            Text("Clear Active LED / Cancel Notification", fontSize = 15.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
