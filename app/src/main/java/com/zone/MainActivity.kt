package com.zone

import android.Manifest
import android.content.ComponentName
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.zone.ui.theme.ZoneTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var engine: MusicEngine

    // Connecting a controller is what starts PlaybackService
    private var controllerFuture: ListenableFuture<MediaController>? = null

    // Must be registered before the activity is STARTED, so it's a property
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // The engine now lives in the Application, so it outlives this activity
        engine = (application as ZoneApp).engine

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ZoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showPlayer by rememberSaveable { mutableStateOf(false) }
                    BackHandler(enabled = showPlayer) { showPlayer = false }

                    Box(Modifier.fillMaxSize()) {
                        // bottom layer: always composed, never destroyed
                        HomeScreen(
                            engine = engine,
                            onPillClick = { showPlayer = true }
                        )

                        // top layer: drawn over the home screen
                        AnimatedVisibility(
                            visible = showPlayer,
                            enter = slideInVertically(tween(400)) { it },
                            exit = slideOutVertically(tween(300)) { it }
                        ) {
                            DismissibleSheet(onDismiss = { showPlayer = false }) {
                                PlayerScreen(engine = engine)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    // No onDestroy: the engine is owned by ZoneApp and must keep playing in the background
}

@Composable
fun DismissibleSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableFloatStateOf(1f) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
    ) {
        // Layer 1: the player, moves with the finger
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
        ) {
            content()
        }

        // Layer 2: invisible drag zone, stays fixed at the top
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        scope.launch {
                            if (offsetY.value > heightPx * 0.25f || velocity > 1500f) {
                                offsetY.animateTo(heightPx, tween(200))
                                onDismiss()
                            } else {
                                offsetY.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                )
                            }
                        }
                    }
                )
        )
    }
}