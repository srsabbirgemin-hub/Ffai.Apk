package com.example.service

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.FloatingAIApp
import com.example.MainActivity
import com.example.R
import com.example.ui.components.CloseZoneComposable
import com.example.ui.components.FloatingPanelComposable
import com.example.ui.components.GlowingOrbComposable
import com.example.ui.theme.FloatingAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.hypot

class FloatingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val NOTIFICATION_ID = 9021
        const val ACTION_START_OVERLAY = "com.example.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.example.action.STOP_OVERLAY"
        const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"
        const val ACTION_TOGGLE_CALL = "com.example.action.TOGGLE_CALL"
        const val ACTION_OPEN_VOICE = "com.example.action.OPEN_VOICE"

        fun start(context: Context) {
            try {
                val intent = Intent(context, FloatingOverlayService::class.java).apply {
                    action = ACTION_START_OVERLAY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("FloatingOverlayService", "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingOverlayService::class.java).apply {
                    action = ACTION_STOP_OVERLAY
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("FloatingOverlayService", "Failed to stop service", e)
            }
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var orbView: ComposeView? = null
    private var orbParams: WindowManager.LayoutParams? = null

    private var closeZoneView: ComposeView? = null
    private var closeZoneParams: WindowManager.LayoutParams? = null

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var isPanelVisible = false
    private var isCloseZoneVisible = false
    private var isHoveringClose = false

    private var screenWidth = 1080
    private var screenHeight = 2400

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()

        // 1. Check overlay permission first
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Display over other apps' permission first", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        FloatingAIApp.instance.preferencesManager.setOverlayRunning(true)
        startForegroundWithNotification()

        createOrbView()
        prepareCloseZoneView()
        preparePanelView()

        // Observe voice state to update notification dynamically
        serviceScope.launch {
            FloatingAIApp.instance.voiceCallManager.geminiLiveClient.callState.collect {
                updateNotification()
            }
        }
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels.coerceAtLeast(480)
        screenHeight = metrics.heightPixels.coerceAtLeast(800)
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasMicPermission) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasMicPermission) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        } catch (e: Exception) {
            Log.w("FloatingOverlayService", "Falling back to basic startForeground", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                Log.e("FloatingOverlayService", "startForeground failed completely", ex)
            }
        }
    }

    private fun buildNotification(): Notification {
        val voiceMgr = FloatingAIApp.instance.voiceCallManager
        val isVoiceActive = voiceMgr.isCallActive.value
        val isMuted = voiceMgr.isMuted.value
        val name = FloatingAIApp.instance.preferencesManager.getAssistantName()

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPending = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePending = PendingIntent.getService(
            this, 1, muteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_TOGGLE_CALL
        }
        val callPending = PendingIntent.getService(
            this, 2, callIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPending = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isVoiceActive) "$name: Live Voice Call" else "$name: Floating Assistant"
        val content = if (isVoiceActive) {
            if (isMuted) "Microphone is Muted" else "Streaming audio via Gemini Live"
        } else {
            "Tap orb to chat or long-press for live voice call"
        }

        val builder = NotificationCompat.Builder(this, FloatingAIApp.OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(mainPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isVoiceActive) {
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                if (isMuted) "Unmute" else "Mute",
                mutePending
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                "End Call",
                callPending
            )
        } else {
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                "Voice Call",
                callPending
            )
        }

        builder.addAction(
            R.drawable.ic_launcher_foreground,
            "Close",
            stopPending
        )

        return builder.build()
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Ignore if notification cannot be updated
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOrbView() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val prefs = FloatingAIApp.instance.preferencesManager
        val orbDp = prefs.getOrbSizeDp().toFloat()
        val orbPx = (orbDp * resources.displayMetrics.density).toInt()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        orbParams = WindowManager.LayoutParams(
            orbPx,
            orbPx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = screenHeight / 3
        }

        orbView = ComposeView(themedContext).apply {
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            setContent {
                FloatingAITheme {
                    val colorName by prefs.orbColorFlow.collectAsState()
                    val sizeDp by prefs.orbSizeFlow.collectAsState()
                    val opacity by prefs.orbOpacityFlow.collectAsState()
                    val callState by FloatingAIApp.instance.voiceCallManager.geminiLiveClient.callState.collectAsState()
                    val amplitude by FloatingAIApp.instance.voiceCallManager.audioAmplitude.collectAsState()

                    GlowingOrbComposable(
                        size = sizeDp.dp,
                        colorName = colorName,
                        callState = callState,
                        amplitude = amplitude,
                        opacity = opacity
                    )
                }
            }
        }

        attachOrbTouchListener(orbView!!, orbParams!!)
        try {
            windowManager.addView(orbView, orbParams)
        } catch (e: Exception) {
            Log.e("FloatingOverlayService", "Failed to add orbView to WindowManager", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOrbTouchListener(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var hasLongPressed = false

        val longPressRunnable = Runnable {
            hasLongPressed = true
            vibrate(50)
            FloatingAIApp.instance.voiceCallManager.startCall()
            showPanel(true)
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    updateScreenDimensions()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    hasLongPressed = false
                    handler.postDelayed(longPressRunnable, 480)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        if (!isDragging) {
                            isDragging = true
                            handler.removeCallbacks(longPressRunnable)
                            showCloseZone(true)
                        }

                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            if (view.isAttachedToWindow) {
                                windowManager.updateViewLayout(view, params)
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }

                        // Check hover proximity to Close Zone (bottom center)
                        val closeZoneCenterX = screenWidth / 2
                        val closeZoneCenterY = screenHeight - 200
                        val distanceToClose = hypot(
                            (event.rawX - closeZoneCenterX).toDouble(),
                            (event.rawY - closeZoneCenterY).toDouble()
                        )

                        val wasHovering = isHoveringClose
                        isHoveringClose = distanceToClose < 220
                        if (!wasHovering && isHoveringClose) {
                            vibrate(40)
                            updateCloseZoneHover(true)
                        } else if (wasHovering && !isHoveringClose) {
                            updateCloseZoneHover(false)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    showCloseZone(false)

                    if (isHoveringClose) {
                        vibrate(80)
                        stopSelf()
                        return@setOnTouchListener true
                    }

                    if (!isDragging && !hasLongPressed) {
                        // Single Tap: Toggle Floating Panel
                        vibrate(20)
                        togglePanel()
                    } else if (isDragging) {
                        // Snap smoothly to left or right screen edge
                        val viewWidth = view.width.coerceAtLeast(100)
                        val targetX = if (event.rawX < screenWidth / 2) {
                            16
                        } else {
                            (screenWidth - viewWidth - 16).coerceAtLeast(16)
                        }
                        animateOrbSnap(params.x, targetX)
                    }
                    isDragging = false
                    isHoveringClose = false
                    true
                }

                else -> false
            }
        }
    }

    private fun animateOrbSnap(startX: Int, targetX: Int) {
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                orbParams?.let { params ->
                    params.x = anim.animatedValue as Int
                    try {
                        orbView?.let {
                            if (it.isAttachedToWindow) {
                                windowManager.updateViewLayout(it, params)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
        animator.start()
    }

    private fun prepareCloseZoneView() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        closeZoneParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        closeZoneView = ComposeView(themedContext).apply {
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            setContent {
                FloatingAITheme {
                    CloseZoneComposable(isHovered = isHoveringClose)
                }
            }
        }
    }

    private fun showCloseZone(show: Boolean) {
        if (show == isCloseZoneVisible) return
        isCloseZoneVisible = show
        val czView = closeZoneView ?: return
        val czParams = closeZoneParams ?: return

        try {
            if (show) {
                if (!czView.isAttachedToWindow) {
                    windowManager.addView(czView, czParams)
                }
            } else {
                if (czView.isAttachedToWindow) {
                    windowManager.removeView(czView)
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingOverlayService", "Error toggling closeZoneView", e)
        }
    }

    private fun updateCloseZoneHover(hovered: Boolean) {
        closeZoneView?.setContent {
            FloatingAITheme {
                CloseZoneComposable(isHovered = hovered)
            }
        }
    }

    private fun preparePanelView() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val defaultWidth = (screenWidth * 0.90f).toInt().coerceAtMost(900)
        val defaultHeight = (screenHeight * 0.62f).toInt().coerceAtMost(1400)

        panelParams = WindowManager.LayoutParams(
            defaultWidth,
            defaultHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        panelView = ComposeView(themedContext).apply {
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            setContent {
                FloatingAITheme {
                    FloatingPanelComposable(
                        onMinimize = { showPanel(false) },
                        onMovePanel = { dx, dy -> movePanel(dx, dy) },
                        onResizePanel = { dw, dh -> resizePanel(dw, dh) }
                    )
                }
            }
        }
    }

    private fun togglePanel() {
        showPanel(!isPanelVisible)
    }

    private fun showPanel(show: Boolean) {
        if (show == isPanelVisible) return
        isPanelVisible = show
        val pView = panelView ?: return
        val pParams = panelParams ?: return

        try {
            if (show) {
                if (!pView.isAttachedToWindow) {
                    windowManager.addView(pView, pParams)
                }
                orbView?.alpha = 0.4f
            } else {
                if (pView.isAttachedToWindow) {
                    windowManager.removeView(pView)
                }
                orbView?.alpha = 1.0f
            }
        } catch (e: Exception) {
            Log.e("FloatingOverlayService", "Error showing/hiding panelView", e)
        }
    }

    private fun movePanel(dx: Float, dy: Float) {
        panelParams?.let { params ->
            params.x += dx.toInt()
            params.y += dy.toInt()
            try {
                panelView?.let {
                    if (it.isAttachedToWindow) {
                        windowManager.updateViewLayout(it, params)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun resizePanel(dw: Float, dh: Float) {
        panelParams?.let { params ->
            val minWidth = (screenWidth * 0.65f).toInt().coerceAtLeast(500)
            val maxWidth = (screenWidth * 0.98f).toInt()
            val minHeight = (screenHeight * 0.35f).toInt().coerceAtLeast(600)
            val maxHeight = (screenHeight * 0.85f).toInt()

            params.width = (params.width + dw.toInt()).coerceIn(minWidth, maxWidth)
            params.height = (params.height + dh.toInt()).coerceIn(minHeight, maxHeight)
            try {
                panelView?.let {
                    if (it.isAttachedToWindow) {
                        windowManager.updateViewLayout(it, params)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopSelf()
            }
            ACTION_TOGGLE_MUTE -> {
                FloatingAIApp.instance.voiceCallManager.toggleMute()
                updateNotification()
            }
            ACTION_TOGGLE_CALL -> {
                val voiceMgr = FloatingAIApp.instance.voiceCallManager
                if (voiceMgr.isCallActive.value) {
                    voiceMgr.endCall()
                } else {
                    voiceMgr.startCall()
                    showPanel(true)
                }
                updateNotification()
            }
            ACTION_OPEN_VOICE -> {
                FloatingAIApp.instance.voiceCallManager.startCall()
                showPanel(true)
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        FloatingAIApp.instance.preferencesManager.setOverlayRunning(false)
        FloatingAIApp.instance.voiceCallManager.endCall()

        try {
            orbView?.let {
                if (it.isAttachedToWindow) windowManager.removeView(it)
            }
            closeZoneView?.let {
                if (it.isAttachedToWindow) windowManager.removeView(it)
            }
            panelView?.let {
                if (it.isAttachedToWindow) windowManager.removeView(it)
            }
        } catch (e: Exception) {
            Log.e("FloatingOverlayService", "Error cleaning up views", e)
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
