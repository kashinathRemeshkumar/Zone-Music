package com.zone

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zone.ui.theme.ZoneTheme

class MainActivity : ComponentActivity() {

    private lateinit var engine: MusicEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT)
        )

        engine = MusicEngine(this)

        setContent {
            ZoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home",
                        enterTransition = { slideInVertically(initialOffsetY = { it },animationSpec = tween(durationMillis = 400)) },
                        exitTransition = { ExitTransition.None},
                        popExitTransition={slideOutVertically(targetOffsetY = { it },animationSpec = tween(durationMillis = 400))},
                        popEnterTransition = { EnterTransition.None })
                    {
                        composable("home") { HomeScreen(engine = engine, onPillClick = { navController.navigate("player")}) }

                        composable("player") { PlayerScreen(engine = engine)}
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}