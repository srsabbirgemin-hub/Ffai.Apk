package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.FloatingAIApp
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.remote.LiveCallState
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonPurpleDark
import com.example.ui.theme.NeonPurpleLight
import com.example.ui.theme.NeonTeal
import com.example.ui.theme.NeonTealLight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class PanelTab {
    CHAT,
    VOICE,
    HISTORY,
    SETTINGS
}

val GEMINI_VOICES = listOf(
    "Kore",
    "Leda",
    "Callirrhoe",
    "Autonoe",
    "Despina",
    "Erinome",
    "Laomedeia",
    "Achernar",
    "Gacrux",
    "Puck",
    "Charon",
    "Aoede",
    "Fenrir"
)

val ORB_COLORS = listOf("PURPLE", "TEAL", "CYAN", "ROSE", "AMBER", "EMERALD")

@Composable
fun FloatingPanelComposable(
    onMinimize: () -> Unit,
    onMovePanel: (dx: Float, dy: Float) -> Unit,
    onResizePanel: (dw: Float, dh: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = FloatingAIApp.instance
    val prefs = app.preferencesManager
    val repo = app.repository
    val voiceMgr = app.voiceCallManager
    val groqClient = app.groqClient
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(PanelTab.CHAT) }
    var panelOpacity by remember { mutableFloatStateOf(prefs.getOrbOpacity()) }
    var showOpacitySlider by remember { mutableStateOf(false) }

    val assistantName by prefs.assistantNameFlow.collectAsState()
    val orbColor by prefs.orbColorFlow.collectAsState()
    val orbSize by prefs.orbSizeFlow.collectAsState()
    val language by prefs.languageFlow.collectAsState()
    val geminiKey by prefs.geminiApiKeyFlow.collectAsState()
    val groqKeys by prefs.groqApiKeysFlow.collectAsState()
    val selectedVoice by prefs.selectedVoiceFlow.collectAsState()

    val isVoiceActive by voiceMgr.isCallActive.collectAsState()
    val callState by voiceMgr.geminiLiveClient.callState.collectAsState()
    val isMuted by voiceMgr.isMuted.collectAsState()
    val amplitude by voiceMgr.audioAmplitude.collectAsState()
    val liveTranscript by voiceMgr.currentTranscript.collectAsState()

    // Automatically switch to Voice tab if a call is initiated
    LaunchedEffect(isVoiceActive) {
        if (isVoiceActive) {
            selectedTab = PanelTab.VOICE
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Color(0xEE0A071E).copy(alpha = panelOpacity.coerceIn(0.35f, 1.0f))
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        NeonPurple.copy(alpha = 0.8f * panelOpacity),
                        NeonTeal.copy(alpha = 0.6f * panelOpacity),
                        Color(0x33A855F7)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            var isExpanded by remember { mutableStateOf(false) }
            PanelHeader(
                title = assistantName,
                isVoiceActive = isVoiceActive,
                onMove = onMovePanel,
                onToggleOpacity = { showOpacitySlider = !showOpacitySlider },
                onStartVoice = {
                    voiceMgr.startCall()
                    selectedTab = PanelTab.VOICE
                },
                onToggleSize = {
                    isExpanded = !isExpanded
                    if (isExpanded) {
                        onResizePanel(120f, 180f)
                    } else {
                        onResizePanel(-120f, -180f)
                    }
                },
                onMinimize = onMinimize
            )

            // Opacity slider dropdown
            AnimatedVisibility(visible = showOpacitySlider) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCard)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = null,
                            tint = NeonTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Opacity: ${(panelOpacity * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = panelOpacity,
                            onValueChange = {
                                panelOpacity = it
                                prefs.setOrbOpacity(it)
                            },
                            valueRange = 0.35f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonTeal,
                                activeTrackColor = NeonPurple
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Compact Tab Navigation Bar
            PanelTabsBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                isVoiceActive = isVoiceActive
            )

            // Tab Content Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    PanelTab.CHAT -> ChatTabContent(
                        assistantName = assistantName,
                        groqKeys = groqKeys,
                        language = language,
                        onSwitchToVoice = {
                            voiceMgr.startCall()
                            selectedTab = PanelTab.VOICE
                        }
                    )
                    PanelTab.VOICE -> VoiceTabContent(
                        assistantName = assistantName,
                        callState = callState,
                        isMuted = isMuted,
                        amplitude = amplitude,
                        liveTranscript = liveTranscript,
                        orbColor = orbColor,
                        onToggleMute = { voiceMgr.toggleMute() },
                        onEndCall = { voiceMgr.endCall() },
                        onMinimize = onMinimize
                    )
                    PanelTab.HISTORY -> HistoryTabContent(
                        onSelectSession = { session ->
                            selectedTab = PanelTab.CHAT
                        }
                    )
                    PanelTab.SETTINGS -> SettingsTabContent(
                        assistantName = assistantName,
                        orbColor = orbColor,
                        orbSize = orbSize,
                        orbOpacity = panelOpacity,
                        language = language,
                        selectedVoice = selectedVoice,
                        geminiKey = geminiKey,
                        groqKeys = groqKeys,
                        onPreviewVoice = { voice -> voiceMgr.previewVoice(voice) }
                    )
                }
            }
        }

        // Resizable bottom-right corner drag handle
        Box(
            modifier = Modifier
                .size(46.dp)
                .align(Alignment.BottomEnd)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResizePanel(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(4.dp)
                .clip(RoundedCornerShape(bottomEnd = 18.dp)),
            contentAlignment = Alignment.BottomEnd
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = NeonTeal,
                    start = androidx.compose.ui.geometry.Offset(w * 0.25f, h),
                    end = androidx.compose.ui.geometry.Offset(w, h * 0.25f),
                    strokeWidth = 3f
                )
                drawLine(
                    color = NeonPurple,
                    start = androidx.compose.ui.geometry.Offset(w * 0.55f, h),
                    end = androidx.compose.ui.geometry.Offset(w, h * 0.55f),
                    strokeWidth = 3f
                )
                drawLine(
                    color = NeonCyan,
                    start = androidx.compose.ui.geometry.Offset(w * 0.82f, h),
                    end = androidx.compose.ui.geometry.Offset(w, h * 0.82f),
                    strokeWidth = 3f
                )
            }
        }
    }
}

@Composable
private fun PanelHeader(
    title: String,
    isVoiceActive: Boolean,
    onMove: (Float, Float) -> Unit,
    onToggleOpacity: () -> Unit,
    onStartVoice: () -> Unit,
    onToggleSize: () -> Unit,
    onMinimize: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface.copy(alpha = 0.95f))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        // Drag Grip Indicator Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x66A855F7))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Grip / Orb Icon
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (isVoiceActive) NeonTeal else NeonPurple)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title.ifBlank { "Nova" },
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Live Voice Call quick button
            if (!isVoiceActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonPurple.copy(alpha = 0.18f))
                        .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onStartVoice() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start Voice Call",
                            tint = NeonPurple,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Call",
                            color = NeonPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Quick 1-Tap Resize Toggle Button
            IconButton(
                onClick = onToggleSize,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Toggle Size",
                    tint = NeonTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Opacity toggle
            IconButton(
                onClick = onToggleOpacity,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Opacity,
                    contentDescription = "Adjust Opacity",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Minimize back to orb
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(28.dp).testTag("minimize_panel_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Minimize to Orb",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PanelTabsBar(
    selectedTab: PanelTab,
    onSelectTab: (PanelTab) -> Unit,
    isVoiceActive: Boolean
) {
    val tabs = listOf(
        Triple(PanelTab.CHAT, "Chat", Icons.Outlined.ChatBubbleOutline),
        Triple(PanelTab.VOICE, if (isVoiceActive) "Live Call" else "Voice", Icons.Default.Mic),
        Triple(PanelTab.HISTORY, "History", Icons.Default.History),
        Triple(PanelTab.SETTINGS, "Settings", Icons.Default.Settings)
    )

    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab },
        containerColor = Color(0xFF0F0B24),
        contentColor = NeonTeal,
        indicator = { tabPositions ->
            val idx = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
                color = if (selectedTab == PanelTab.VOICE) NeonTeal else NeonPurple,
                height = 2.5.dp
            )
        },
        divider = {}
    ) {
        tabs.forEach { (tab, label, icon) ->
            val isSelected = selectedTab == tab
            Tab(
                selected = isSelected,
                onClick = { onSelectTab(tab) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) {
                                if (tab == PanelTab.VOICE && isVoiceActive) NeonTeal else NeonPurple
                            } else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) TextPrimary else TextMuted
                        )
                    }
                }
            )
        }
    }
}

// ==========================================
// 1. CHAT TAB CONTENT
// ==========================================
@Composable
private fun ChatTabContent(
    assistantName: String,
    groqKeys: String,
    language: String,
    onSwitchToVoice: () -> Unit
) {
    val context = LocalContext.current
    val app = FloatingAIApp.instance
    val repo = app.repository
    val groqClient = app.groqClient
    val ttsManager = app.ttsManager
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var currentAiReply by remember { mutableStateOf("") }
    val currentSessionId = remember { mutableStateOf(UUID.randomUUID().toString().take(8)) }

    val messages = remember { mutableStateListOf<ChatMessageEntity>() }
    val listState = rememberLazyListState()

    var selectedModel by remember { mutableStateOf(app.preferencesManager.getSelectedModel()) }
    var showModelPicker by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spoken = spokenList?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            inputText = if (inputText.isBlank()) spoken else "$inputText $spoken"
        }
    }

    fun readClipboardText(): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // Auto-scroll when messages or streaming text changes
    LaunchedEffect(messages.size, currentAiReply) {
        if (messages.isNotEmpty() || currentAiReply.isNotEmpty()) {
            listState.animateScrollToItem((messages.size).coerceAtLeast(0))
        }
    }

    val quickPrompts = listOf(
        "Summarize screen content",
        "Explain simply",
        "Brainstorm ideas",
        "Translate to Bengali"
    )

    fun sendMessage(text: String) {
        val query = text.trim()
        if (query.isEmpty() || isStreaming) return
        inputText = ""

        val userMsg = ChatMessageEntity(
            sessionId = currentSessionId.value,
            sender = "USER",
            content = query
        )
        messages.add(userMsg)

        scope.launch {
            repo.saveMessage(
                sessionId = currentSessionId.value,
                sender = "USER",
                content = query,
                sessionTitle = query.take(30)
            )

            isStreaming = true
            currentAiReply = ""

            val historyPayload = messages.map { Pair(it.sender.lowercase(), it.content) }
            val systemInstruction = "You are $assistantName, a high-speed floating AI assistant. Respond concisely and clearly in $language."
            val geminiKey = FloatingAIApp.instance.preferencesManager.getGeminiApiKey()

            groqClient.streamChat(
                rawKeys = groqKeys,
                messages = historyPayload,
                systemInstruction = systemInstruction,
                geminiKey = geminiKey,
                language = language,
                model = selectedModel
            ).collectLatest { delta ->
                currentAiReply += delta
            }

            val finalReply = currentAiReply
            if (finalReply.isNotEmpty()) {
                val aiMsg = ChatMessageEntity(
                    sessionId = currentSessionId.value,
                    sender = "AI",
                    content = finalReply
                )
                messages.add(aiMsg)
                repo.saveMessage(
                    sessionId = currentSessionId.value,
                    sender = "AI",
                    content = finalReply,
                    sessionTitle = query.take(30)
                )
            }
            currentAiReply = ""
            isStreaming = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Model Selector & Quick Clipboard Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model Selector Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { showModelPicker = !showModelPicker }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Model",
                        tint = NeonPurpleLight,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = when (selectedModel) {
                            "llama-3.1-8b-instant" -> "⚡ LLaMA 8B (Fast)"
                            "mixtral-8x7b-32768" -> "🧠 Mixtral 8x7B"
                            else -> "✨ LLaMA 70B (Pro)"
                        },
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Clipboard Action Chip
            val clip = remember { readClipboardText() }
            if (clip.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonTeal.copy(alpha = 0.15f))
                        .border(1.dp, NeonTeal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable {
                            val clipText = readClipboardText()
                            if (clipText.isNotBlank()) {
                                sendMessage("Summarize/explain this copied text:\n$clipText")
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Clipboard",
                            tint = NeonTeal,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Analyze Copied Text",
                            color = NeonTealLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Model Picker Dropdown Row
        AnimatedVisibility(visible = showModelPicker) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair("llama-3.3-70b-versatile", "LLaMA 70B"),
                    Pair("llama-3.1-8b-instant", "8B Instant"),
                    Pair("mixtral-8x7b-32768", "Mixtral")
                ).forEach { (mKey, mLabel) ->
                    val isSel = selectedModel == mKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) NeonPurple.copy(alpha = 0.35f) else DarkCard)
                            .border(1.dp, if (isSel) NeonPurple else DarkCardBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedModel = mKey
                                app.preferencesManager.setSelectedModel(mKey)
                                showModelPicker = false
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = mLabel,
                            color = if (isSel) Color.White else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty() && currentAiReply.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GlowingOrbComposable(size = 48.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "How can $assistantName help you?",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Type below, speak via mic, or paste clipboard",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            items(messages) { msg ->
                ChatBubble(
                    sender = msg.sender,
                    content = msg.content,
                    messageId = msg.id,
                    assistantName = assistantName,
                    language = language
                )
            }

            if (isStreaming && currentAiReply.isNotEmpty()) {
                item {
                    ChatBubble(
                        sender = "AI",
                        content = currentAiReply,
                        messageId = 0L,
                        assistantName = assistantName,
                        language = language,
                        isStreaming = true
                    )
                }
            }
        }

        // Quick prompts row
        if (messages.isEmpty() && !isStreaming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickPrompts.take(3).forEach { prompt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                            .clickable { sendMessage(prompt) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = prompt,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Input Field, Mic Dictation, & Send Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                placeholder = {
                    Text(
                        text = "Ask $assistantName anything...",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage(inputText) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonPurple,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard
                ),
                shape = RoundedCornerShape(14.dp)
            )

            // Voice Dictation (Speech-to-Text) Button
            IconButton(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "Bengali") "bn-BD" else "en-US")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to $assistantName...")
                    }
                    try {
                        speechLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Voice dictation not available", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
                    .border(1.dp, DarkCardBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Dictation",
                    tint = NeonTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Send Button
            IconButton(
                onClick = { sendMessage(inputText) },
                enabled = inputText.isNotBlank() && !isStreaming,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isStreaming) NeonPurple else DarkCard
                    )
                    .testTag("chat_send_button")
            ) {
                if (isStreaming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = NeonTeal,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Color.White else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    sender: String,
    content: String,
    messageId: Long = 0L,
    assistantName: String,
    language: String = "English",
    isStreaming: Boolean = false
) {
    val isUser = sender.equals("USER", ignoreCase = true)
    val context = LocalContext.current
    val tts = FloatingAIApp.instance.ttsManager
    val isSpeaking by tts.isSpeaking.collectAsState()
    val speakingId by tts.currentlySpokenId.collectAsState()
    val isThisSpeaking = isSpeaking && speakingId == messageId

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(NeonPurpleDark, NeonPurple))
                    } else {
                        Brush.linearGradient(listOf(DarkCard, Color(0xFF1F1845)))
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) NeonPurpleLight.copy(alpha = 0.5f) else DarkCardBorder,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else assistantName,
                        color = if (isUser) NeonTealLight else NeonPurpleLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Quick Actions for AI Message (Read Aloud TTS & Copy)
                    if (!isUser && !isStreaming) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Read Aloud / Stop button
                            IconButton(
                                onClick = {
                                    if (isThisSpeaking) {
                                        tts.stop()
                                    } else {
                                        tts.speak(content, messageId, language)
                                    }
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = if (isThisSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = "Read Aloud",
                                    tint = if (isThisSpeaking) Color(0xFFEF4444) else NeonTeal,
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            // Copy message
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("AI Message", content))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = content,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                if (isStreaming) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "●",
                        color = NeonTeal,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 2. VOICE TAB CONTENT (Live Voice Call)
// ==========================================
@Composable
private fun VoiceTabContent(
    assistantName: String,
    callState: LiveCallState,
    isMuted: Boolean,
    amplitude: Float,
    liveTranscript: String,
    orbColor: String,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    onMinimize: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Starry animated background for Voice Mode
        StarrySkyComposable()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x66000000))
                    .border(
                        1.dp,
                        when (callState) {
                            LiveCallState.LISTENING -> NeonCyan
                            LiveCallState.SPEAKING -> NeonTeal
                            LiveCallState.THINKING -> NeonPurple
                            LiveCallState.MUTED -> Color(0xFFEF4444)
                            else -> DarkCardBorder
                        },
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val stateText = when (callState) {
                    LiveCallState.CONNECTING -> "Connecting to Gemini Live..."
                    LiveCallState.LISTENING -> "Listening to your voice..."
                    LiveCallState.THINKING -> "Thinking..."
                    LiveCallState.SPEAKING -> "$assistantName is speaking..."
                    LiveCallState.MUTED -> "Microphone Muted"
                    LiveCallState.ERROR -> "Voice Error"
                    LiveCallState.IDLE -> "Call Ready"
                }
                Text(
                    text = stateText,
                    color = when (callState) {
                        LiveCallState.LISTENING -> NeonCyan
                        LiveCallState.SPEAKING -> NeonTeal
                        LiveCallState.MUTED -> Color(0xFFFCA5A5)
                        else -> TextPrimary
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Center Animated Glowing Voice Orb
            Box(
                modifier = Modifier
                    .size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                GlowingOrbComposable(
                    size = 130.dp,
                    colorName = orbColor,
                    callState = callState,
                    amplitude = amplitude
                )
            }

            // Live Transcript Captions Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x88120E28))
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = liveTranscript.ifBlank { "Speak naturally. Gemini Live is streaming..." },
                    color = if (liveTranscript.isNotBlank()) TextPrimary else TextMuted,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Voice Controls (Mute, End Call, Minimize)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute / Unmute
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFEF4444) else DarkCard)
                            .border(1.dp, if (isMuted) Color.White else DarkCardBorder, CircleShape)
                            .testTag("voice_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isMuted) "Unmute" else "Mute",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                // End Call (Hang Up)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626))
                            .testTag("voice_end_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "End",
                        color = Color(0xFFFCA5A5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Minimize to Orb (keep listening)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                            .border(1.dp, DarkCardBorder, CircleShape)
                            .testTag("voice_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Opacity,
                            contentDescription = "Minimize to Orb",
                            tint = NeonTeal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Minimize",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. HISTORY TAB CONTENT
// ==========================================
@Composable
private fun HistoryTabContent(
    onSelectSession: (ChatSessionEntity) -> Unit
) {
    val app = FloatingAIApp.instance
    val repo = app.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    val sessions by repo.allSessions.collectAsState(initial = emptyList())

    val filteredSessions = if (searchQuery.isBlank()) {
        sessions
    } else {
        sessions.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.lastMessage.contains(searchQuery, ignoreCase = true)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search chats & transcripts...", color = TextMuted, fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonPurple,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = DarkCard,
                unfocusedContainerColor = DarkCard
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "No conversation history yet" else "No matching conversations",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSessions) { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectSession(session) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (session.isVoiceSession) Icons.Default.Mic else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (session.isVoiceSession) NeonTeal else NeonPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = session.lastMessage,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = dateFormatter.format(Date(session.timestamp)),
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                        // Share session transcript
                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, session.title)
                                    putExtra(Intent.EXTRA_TEXT, "${session.title}\n\n${session.lastMessage}\n\n- Shared from Floating AI")
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Chat")
                                shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = NeonTeal,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { scope.launch { repo.deleteSession(session.sessionId) } },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Clear All Button
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Clear All History",
                    color = Color(0xFFFCA5A5),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { scope.launch { repo.clearAll() } }
                        .padding(4.dp)
                )
            }
        }
    }
}

// ==========================================
// 4. SETTINGS TAB CONTENT
// ==========================================
@Composable
private fun SettingsTabContent(
    assistantName: String,
    orbColor: String,
    orbSize: Int,
    orbOpacity: Float,
    language: String,
    selectedVoice: String,
    geminiKey: String,
    groqKeys: String,
    onPreviewVoice: (String) -> Unit
) {
    val app = FloatingAIApp.instance
    val prefs = app.preferencesManager
    val groqClient = app.groqClient
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nameInput by remember { mutableStateOf(assistantName) }
    var geminiKeyInput by remember { mutableStateOf(geminiKey) }
    var groqKeysInput by remember { mutableStateOf(groqKeys) }

    var groqStatus by remember { mutableStateOf(if (groqKeys.isNotBlank()) "Configured" else "Missing") }
    var isValidatingGroq by remember { mutableStateOf(false) }

    var geminiStatus by remember { mutableStateOf(if (geminiKey.isNotBlank()) "Configured" else "Missing") }
    var isValidatingGemini by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Assistant Name
        item {
            Text(
                text = if (language == "Bengali") "সহকারীর নাম (Assistant Name)" else "Assistant Name",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
        }

        // Voice Picker with Preview Audio
        item {
            Text(
                text = if (language == "Bengali") "জেমিনি লাইভ ভয়েস (Gemini Voice)" else "Gemini Live Voice",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    items(GEMINI_VOICES) { voice ->
                        val isSelected = voice.equals(selectedVoice, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NeonPurple.copy(alpha = 0.25f) else DarkCard)
                                .border(1.dp, if (isSelected) NeonPurple else DarkCardBorder, RoundedCornerShape(8.dp))
                                .clickable { prefs.setSelectedVoice(voice) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = voice,
                                color = if (isSelected) NeonTealLight else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            IconButton(
                                onClick = { onPreviewVoice(voice) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Preview $voice voice",
                                    tint = NeonTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Orb Color & Appearance
        item {
            Text(
                text = if (language == "Bengali") "অর্বের রঙের থিম (Orb Color)" else "Orb Color Theme",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ORB_COLORS.forEach { colorStr ->
                    val (c1, _) = getOrbColors(colorStr)
                    val isSelected = colorStr.equals(orbColor, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c1)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { prefs.setOrbColor(colorStr) }
                    )
                }
            }
        }

        // Language Picker
        item {
            Text(
                text = if (language == "Bengali") "ভাষা (Language)" else "Language",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("English", "Bengali").forEach { lang ->
                    val isSelected = lang.equals(language, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) NeonTeal.copy(alpha = 0.2f) else DarkCard)
                            .border(1.dp, if (isSelected) NeonTeal else DarkCardBorder, RoundedCornerShape(10.dp))
                            .clickable { prefs.setLanguage(lang) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (lang == "Bengali") "Bengali (বাংলা)" else "English",
                            color = if (isSelected) NeonTeal else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Groq Keys Input with Rotation Support & Validation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == "Bengali") "Groq এআই কী (দ্রুত চ্যাট)" else "Groq API Key(s)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (groqStatus == "Ready") Color(0x3310B981) else Color(0x33F59E0B))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = groqStatus,
                        color = if (groqStatus == "Ready") Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 10.sp
                    )
                }
            }
            Text("Separate multiple keys with commas for automatic 429 rotation", color = TextMuted, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = groqKeysInput,
                onValueChange = {
                    groqKeysInput = it
                    prefs.setGroqApiKeys(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("gsk_key1, gsk_key2...", color = TextMuted, fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonPurple,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard
                ),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Get free Groq keys ↗",
                    color = NeonTeal,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonPurple.copy(alpha = 0.2f))
                        .clickable {
                            val keys = groqClient.parseKeys(groqKeysInput)
                            if (keys.isNotEmpty()) {
                                isValidatingGroq = true
                                scope.launch {
                                    val res = groqClient.validateKey(keys.first())
                                    groqStatus = if (res.isSuccess) "Ready" else "Error"
                                    isValidatingGroq = false
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isValidatingGroq) "Checking..." else "Test Key",
                        color = NeonPurpleLight,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Gemini Key Input
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language == "Bengali") "Gemini এআই কী (লাইভ ভয়েস)" else "Gemini API Key (Live Voice)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (geminiStatus == "Ready") Color(0x3310B981) else Color(0x33F59E0B))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = geminiStatus,
                        color = if (geminiStatus == "Ready") Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontSize = 10.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = geminiKeyInput,
                onValueChange = {
                    geminiKeyInput = it
                    prefs.setGeminiApiKey(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("AIzaSy...", color = TextMuted, fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonPurple,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Get free Gemini key ↗",
                    color = NeonTeal,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonPurple.copy(alpha = 0.2f))
                        .clickable {
                            val key = geminiKeyInput.trim()
                            if (key.isNotEmpty()) {
                                isValidatingGemini = true
                                scope.launch {
                                    val res = groqClient.validateGeminiKey(key)
                                    geminiStatus = if (res.isSuccess) "Ready" else "Error"
                                    isValidatingGemini = false
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isValidatingGemini) "Checking..." else "Test Key",
                        color = NeonPurpleLight,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Save All Settings Button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    prefs.setAssistantName(nameInput.trim())
                    prefs.setGroqApiKeys(groqKeysInput.trim())
                    prefs.setGeminiApiKey(geminiKeyInput.trim())
                    val msg = if (language == "Bengali") "সব সেটিংস সেভ করা হয়েছে!" else "Settings saved successfully!"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("panel_save_all_settings_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DeepSpace,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (language == "Bengali") "সব সেটিংস সেভ করুন" else "Save All Settings",
                        color = DeepSpace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
