package com.gbw.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gbw.android.project.ProjectRepository
import com.gbw.android.ui.GbwApp

internal class ProcessSessionGate {
    private var initialized = false

    @Synchronized
    fun beginSession(): Boolean {
        if (initialized) return false
        initialized = true
        return true
    }
}

internal object SessionLaunchPolicy {
    fun shouldStartClean(
        firstActivityInProcess: Boolean,
        hasSavedInstanceState: Boolean,
        launcherIntent: Boolean,
    ): Boolean =
        firstActivityInProcess || (!hasSavedInstanceState && launcherIntent)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // "Projeto aberto" is session state. A new app process starts clean,
        // while rotations/activity recreation inside the same process preserve
        // the current project.
        val firstActivityInProcess = processSessionGate.beginSession()
        val launcherIntent =
            intent?.action == android.content.Intent.ACTION_MAIN &&
                intent?.hasCategory(android.content.Intent.CATEGORY_LAUNCHER) == true
        if (
            SessionLaunchPolicy.shouldStartClean(
                firstActivityInProcess = firstActivityInProcess,
                hasSavedInstanceState = savedInstanceState != null,
                launcherIntent = launcherIntent,
            )
        ) {
            ProjectRepository(applicationContext).closeActive()
        }

        applyImmersiveMode()
        setContent {
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            GbwApp()
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private companion object {
        val processSessionGate = ProcessSessionGate()
    }
}
