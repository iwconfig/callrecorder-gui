package com.android.bcrgui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.bcrgui.ui.DashboardScreen
import com.android.bcrgui.ui.MainViewModel
import com.android.bcrgui.ui.OnboardingScreen
import com.android.bcrgui.ui.PlayerSheet
import com.android.bcrgui.ui.SettingsDialog
import com.android.bcrgui.ui.theme.BCRGUITheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.android.bcrgui.ui.RecycleBinDialog

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Custom exit animation: elegant slide up with anticipate curves
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashScreenView = splashScreenViewProvider.view
            val slideUp = android.animation.ObjectAnimator.ofFloat(
                splashScreenView,
                android.view.View.TRANSLATION_Y,
                0f,
                -splashScreenView.height.toFloat()
            )
            slideUp.interpolator = android.view.animation.AnticipateInterpolator()
            slideUp.duration = 500L

            slideUp.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    splashScreenViewProvider.remove()
                }
            })
            slideUp.start()
        }

        enableEdgeToEdge()

        // Request notification permission for API >= 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        setContent {
            val accentColor by viewModel.accentColor.collectAsState()
            val amoledMode by viewModel.amoledMode.collectAsState()

            BCRGUITheme(
                accentColor = accentColor,
                amoledMode = amoledMode
            ) {
                val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
                val folderUri by viewModel.folderUri.collectAsState()
                val filenameTemplate by viewModel.filenameTemplate.collectAsState()
                val fileExtension by viewModel.fileExtension.collectAsState()

                var showSettings by remember { mutableStateOf(false) }
                var showRecycleBin by remember { mutableStateOf(false) }

                if (!isOnboardingCompleted) {
                    OnboardingScreen(
                        onComplete = { folder, template, extension ->
                            viewModel.completeOnboarding(folder, template, extension)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        DashboardScreen(
                            viewModel = viewModel,
                            onOpenSettings = { showSettings = true },
                            onOpenRecycleBin = { showRecycleBin = true }
                        )

                        // Floating player at the bottom of the screen
                        PlayerSheet(
                            viewModel = viewModel,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        if (showSettings) {
                            SettingsDialog(
                                viewModel = viewModel,
                                currentFolderUri = folderUri,
                                currentTemplate = filenameTemplate,
                                currentExtension = fileExtension,
                                currentAccentColor = accentColor,
                                currentAmoledMode = amoledMode,
                                onDismiss = { showSettings = false },
                                onSave = { folder, template, extension, accent, amoled ->
                                    viewModel.saveSettings(folder, template, extension, accent, amoled)
                                },
                                onSaveAi = { serverUrl, model, autoTranscribe, llmProvider, diarize, language, additionalLanguages ->
                                    viewModel.saveAiSettings(serverUrl, model, autoTranscribe, llmProvider, diarize, language, additionalLanguages)
                                },
                                onResetOnboarding = {
                                    viewModel.resetOnboarding()
                                }
                            )
                        }

                        if (showRecycleBin) {
                            RecycleBinDialog(
                                viewModel = viewModel,
                                onDismiss = { showRecycleBin = false }
                            )
                        }
                    }
                }
            }
        }
    }
}