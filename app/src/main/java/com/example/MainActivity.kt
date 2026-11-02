package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.service.FloatingOverlayService
import com.example.ui.components.FloatingPanelComposable
import com.example.ui.components.GEMINI_VOICES
import com.example.ui.components.GlowingOrbComposable
import com.example.ui.components.ORB_COLORS
import com.example.ui.components.getOrbColors
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.FloatingAITheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleDark
import com.example.ui.theme.NeonPurpleLight
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.NeonTealLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloatingAITheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DeepSpace
                ) { innerPadding ->
                    MainScreen(paddingValues = innerPadding)
                }
            }
        }
    }
}

enum class MainAppTab {
    SETUP,
    IN_APP_ASSISTANT
}

@Composable
fun MainScreen(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTab by remember { mutableStateOf(MainAppTab.SETUP) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Auto-refresh permission state whenever activity resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // App Header & Navigation Tabs
        TabRow(
            selectedTabIndex = if (currentTab == MainAppTab.SETUP) 0 else 1,
            containerColor = DarkSurface,
            contentColor = NeonTeal,
            indicator = { tabPositions ->
                val idx = if (currentTab == MainAppTab.SETUP) 0 else 1
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
                    color = NeonPurple,
                    height = 3.dp
                )
            },
            divider = {}
        ) {
            Tab(
                selected = currentTab == MainAppTab.SETUP,
                onClick = { currentTab = MainAppTab.SETUP },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (currentTab == MainAppTab.SETUP) NeonPurple else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Overlay Setup & Keys",
                            fontWeight = if (currentTab == MainAppTab.SETUP) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            color = if (currentTab == MainAppTab.SETUP) TextPrimary else TextMuted
                        )
                    }
                }
            )

            Tab(
                selected = currentTab == MainAppTab.IN_APP_ASSISTANT,
                onClick = { currentTab = MainAppTab.IN_APP_ASSISTANT },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = if (currentTab == MainAppTab.IN_APP_ASSISTANT) NeonTeal else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Live AI Assistant",
                            fontWeight = if (currentTab == MainAppTab.IN_APP_ASSISTANT) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            color = if (currentTab == MainAppTab.IN_APP_ASSISTANT) TextPrimary else TextMuted
                        )
                    }
                }
            )
        }

        when (currentTab) {
            MainAppTab.SETUP -> SetupTabContent(
                hasOverlayPermission = hasOverlayPermission,
                onSwitchToAssistant = { currentTab = MainAppTab.IN_APP_ASSISTANT }
            )
            MainAppTab.IN_APP_ASSISTANT -> InAppAssistantTab()
        }
    }
}

@Composable
fun SetupTabContent(
    hasOverlayPermission: Boolean,
    onSwitchToAssistant: () -> Unit
) {
    val context = LocalContext.current
    val app = FloatingAIApp.instance
    val prefs = app.preferencesManager
    val groqClient = app.groqClient
    val voiceMgr = app.voiceCallManager
    val scope = rememberCoroutineScope()

    val assistantName by prefs.assistantNameFlow.collectAsState()
    val orbColor by prefs.orbColorFlow.collectAsState()
    val orbSize by prefs.orbSizeFlow.collectAsState()
    val orbOpacity by prefs.orbOpacityFlow.collectAsState()
    val language by prefs.languageFlow.collectAsState()
    val selectedVoice by prefs.selectedVoiceFlow.collectAsState()
    val geminiKey by prefs.geminiApiKeyFlow.collectAsState()
    val groqKeys by prefs.groqApiKeysFlow.collectAsState()

    var nameInput by remember(assistantName) { mutableStateOf(assistantName) }
    var geminiKeyInput by remember(geminiKey) { mutableStateOf(geminiKey) }
    var groqKeysInput by remember(groqKeys) { mutableStateOf(groqKeys) }

    var isOverlayRunning by remember { mutableStateOf(prefs.isOverlayRunning()) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var isBatteryOptimizationIgnored by remember {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    var groqStatus by remember { mutableStateOf(if (groqKeys.isNotBlank()) "Configured" else "Missing key") }
    var isValidatingGroq by remember { mutableStateOf(false) }

    var geminiStatus by remember { mutableStateOf(if (geminiKey.isNotBlank()) "Configured" else "Missing key") }
    var isValidatingGemini by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text("Overlay Permission Needed", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "To draw the floating orb on top of other apps, Android requires the 'Display over other apps' toggle. Tap 'Open Settings' to enable it, or use the 'Live AI Assistant' tab to test directly.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    onSwitchToAssistant()
                }) {
                    Text("Test In-App Instead", color = NeonTeal)
                }
            },
            containerColor = DarkCard
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Section: Glowing Orb & Overlay Launcher
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                NeonPurple.copy(alpha = 0.25f),
                                DarkCard,
                                DeepSpace
                            )
                        )
                    )
                    .border(1.5.dp, DarkCardBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GlowingOrbComposable(
                        size = 80.dp,
                        colorName = orbColor,
                        amplitude = 0.15f
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Floating AI: $assistantName",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == "Bengali") "যেকোনো অ্যাপের উপরে ভাসমান এআই সহকারী" else "Real-time floating assistant on top of any app",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Start/Stop Floating Button
                    Button(
                        onClick = {
                            if (!hasOverlayPermission) {
                                showPermissionDialog = true
                            } else {
                                if (isOverlayRunning) {
                                    FloatingOverlayService.stop(context)
                                    isOverlayRunning = false
                                    Toast.makeText(context, "Floating overlay stopped", Toast.LENGTH_SHORT).show()
                                } else {
                                    FloatingOverlayService.start(context)
                                    isOverlayRunning = true
                                    Toast.makeText(context, "Floating orb launched! Look for the glowing orb on screen.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOverlayRunning) Color(0xFFDC2626) else NeonPurple
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(48.dp)
                            .testTag("launch_overlay_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isOverlayRunning) Icons.Default.PowerSettingsNew else Icons.Default.Layers,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(
                                text = if (!hasOverlayPermission) {
                                    "Enable Overlay Permission"
                                } else if (isOverlayRunning) {
                                    "Stop Floating Overlay"
                                } else {
                                    "Launch Floating Orb"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Direct Test Button
                    TextButton(
                        onClick = onSwitchToAssistant,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "Or Open Live AI Assistant Tab Directly ➔",
                            color = NeonTealLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Section 1: Permissions Onboarding
        item {
            Text(
                text = "Permissions & System Setup",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enable permissions to allow the orb to float and hear voice commands",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Overlay
                PermissionCard(
                    title = "Display Over Other Apps",
                    description = "Required to render the floating orb and interactive panel anywhere",
                    icon = Icons.Default.Layers,
                    isGranted = hasOverlayPermission,
                    buttonLabel = "Grant Overlay",
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                )

                // 2. Microphone
                PermissionCard(
                    title = "Microphone Access",
                    description = "Enables real-time bidirectional voice conversations via Gemini Live",
                    icon = Icons.Default.Mic,
                    isGranted = hasMicPermission,
                    buttonLabel = "Grant Microphone",
                    onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )

                // 3. Notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionCard(
                        title = "Foreground Notification",
                        description = "Displays Mute and End controls in the notification drawer",
                        icon = Icons.Default.Notifications,
                        isGranted = hasNotificationPermission,
                        buttonLabel = "Grant Notifications",
                        onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }

                // 4. Battery Optimization
                PermissionCard(
                    title = "Unrestricted Battery Mode",
                    description = "Prevents Android from killing the floating service during long calls",
                    icon = Icons.Default.BatteryAlert,
                    isGranted = isBatteryOptimizationIgnored,
                    buttonLabel = "Ignore Restrictions",
                    onClick = {
                        @SuppressLint("BatteryLife")
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        // Section 2: AI API Keys Configuration
        item {
            Text(
                text = "AI API Keys (Stored Encrypted On-Device)",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Groq Keys
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Groq Keys (Fast Typed Chat)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (groqStatus == "Ready") Color(0x3310B981) else Color(0x33F59E0B))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = groqStatus,
                                    color = if (groqStatus == "Ready") Color(0xFF34D399) else Color(0xFFFBBF24),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Comma-separated keys rotate automatically on HTTP 429 rate limit",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = groqKeysInput,
                            onValueChange = {
                                groqKeysInput = it
                                prefs.setGroqApiKeys(it)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("groq_keys_input"),
                            placeholder = { Text("gsk_key1, gsk_key2...", color = TextMuted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Get free keys at Groq Console ↗",
                                color = NeonTeal,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                                    context.startActivity(intent)
                                }
                            )

                            Button(
                                onClick = {
                                    val keys = groqClient.parseKeys(groqKeysInput)
                                    if (keys.isNotEmpty()) {
                                        isValidatingGroq = true
                                        scope.launch {
                                            val res = groqClient.validateKey(keys.first())
                                            groqStatus = if (res.isSuccess) "Ready" else "Error"
                                            isValidatingGroq = false
                                        }
                                    } else {
                                        Toast.makeText(context, "Please enter at least one Groq API key", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp).testTag("validate_groq_button")
                            ) {
                                if (isValidatingGroq) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White)
                                } else {
                                    Text("Test Key", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Gemini Key
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == "Bengali") "Gemini এআই কী (লাইভ ভয়েস)" else "Gemini Key (Live Voice Call)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (geminiStatus == "Ready") Color(0x3310B981) else Color(0x33F59E0B))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = geminiStatus,
                                    color = if (geminiStatus == "Ready") Color(0xFF34D399) else Color(0xFFFBBF24),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (language == "Bengali") "রিয়েল-টাইম অডিও লাইভ কলের জন্য ব্যবহৃত হয়" else "Used for real-time audio WebSocket streaming to Gemini Live",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = {
                                geminiKeyInput = it
                                prefs.setGeminiApiKey(it)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("gemini_key_input"),
                            placeholder = { Text("AIzaSy...", color = TextMuted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Get free key at Google AI Studio ↗",
                                color = NeonTeal,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                    context.startActivity(intent)
                                }
                            )

                            Button(
                                onClick = {
                                    val key = geminiKeyInput.trim()
                                    if (key.isNotEmpty()) {
                                        isValidatingGemini = true
                                        scope.launch {
                                            val res = groqClient.validateGeminiKey(key)
                                            geminiStatus = if (res.isSuccess) "Ready" else "Error"
                                            isValidatingGemini = false
                                        }
                                    } else {
                                        Toast.makeText(context, "Please enter a Gemini API key", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp).testTag("validate_gemini_button")
                            ) {
                                if (isValidatingGemini) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White)
                                } else {
                                    Text(if (language == "Bengali") "কী টেস্ট" else "Test Key", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Voice Picker & Customization
        item {
            Text(
                text = "Assistant Persona & Voices",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Assistant Name
                    Column {
                        Text("Assistant Name", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = {
                                nameInput = it
                                prefs.setAssistantName(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple,
                                unfocusedBorderColor = DarkCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }

                    // Voice Picker with Preview Audio
                    Column {
                        Text("Gemini Live Voice Picker", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tap play to preview voice acoustic profile", color = TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(GEMINI_VOICES) { voice ->
                                val isSelected = voice.equals(selectedVoice, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) NeonPurple.copy(alpha = 0.3f) else DarkSurface)
                                        .border(1.5.dp, if (isSelected) NeonPurple else DarkCardBorder, RoundedCornerShape(12.dp))
                                        .clickable { prefs.setSelectedVoice(voice) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = voice,
                                            color = if (isSelected) NeonTealLight else TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        IconButton(
                                            onClick = { voiceMgr.previewVoice(voice) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Preview voice",
                                                tint = NeonTeal,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Orb Color Picker
                    Column {
                        Text("Orb Color Theme", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ORB_COLORS.forEach { colorName ->
                                val (c1, _) = getOrbColors(colorName)
                                val isSelected = colorName.equals(orbColor, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(c1)
                                        .border(
                                            width = if (isSelected) 3.5.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { prefs.setOrbColor(colorName) }
                                )
                            }
                        }
                    }

                    // Orb Size & Opacity
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Orb Size: ${orbSize}dp", color = TextSecondary, fontSize = 12.sp)
                            Text("Opacity: ${(orbOpacity * 100).toInt()}%", color = TextSecondary, fontSize = 12.sp)
                        }
                        Slider(
                            value = orbSize.toFloat(),
                            onValueChange = { prefs.setOrbSizeDp(it.toInt()) },
                            valueRange = 48f..96f,
                            colors = SliderDefaults.colors(thumbColor = NeonPurple, activeTrackColor = NeonTeal)
                        )
                    }

                    // Language Selector
                    Column {
                        Text("Language (ভাষা)", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("English", "Bengali").forEach { lang ->
                                val isSelected = lang.equals(language, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) NeonTeal.copy(alpha = 0.25f) else DarkSurface)
                                        .border(1.dp, if (isSelected) NeonTeal else DarkCardBorder, RoundedCornerShape(10.dp))
                                        .clickable { prefs.setLanguage(lang) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (lang == "Bengali") "Bengali (বাংলা)" else "English",
                                        color = if (isSelected) NeonTeal else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Save All Settings Button
        item {
            Button(
                onClick = {
                    prefs.setAssistantName(nameInput.trim())
                    prefs.setGroqApiKeys(groqKeysInput.trim())
                    prefs.setGeminiApiKey(geminiKeyInput.trim())
                    val msg = if (language == "Bengali") "সব সেটিংস সফলভাবে সংরক্ষিত হয়েছে!" else "All settings saved successfully!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_all_settings_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DeepSpace)
                    Text(
                        text = if (language == "Bengali") "সব সেটিংস সেভ করুন (Save All Settings)" else "Save All Settings",
                        color = DeepSpace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun InAppAssistantTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(8.dp)
    ) {
        FloatingPanelComposable(
            onMinimize = {
                // In-app mode: no-op or small feedback
            },
            onMovePanel = { _, _ -> },
            onResizePanel = { _, _ -> }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(
                1.dp,
                if (isGranted) Color(0x3310B981) else DarkCardBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) Color(0x2210B981) else Color(0x22A855F7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF34D399) else NeonPurple,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isGranted) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(text = buttonLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
