package com.comp.traap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.comp.traap.service.AudioService
import com.comp.traap.service.LocationService
import com.comp.traap.ui.theme.TraapTheme
import com.comp.traap.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                val fineLocationGranted =
                        permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted =
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

                if (fineLocationGranted && coarseLocationGranted) {
                    // Check background location permission for Android 10+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        requestBackgroundLocationPermission()
                    } else {
                        checkNotificationPermission()
                    }
                } else {
                    Toast.makeText(this, "Permission", Toast.LENGTH_LONG).show()
                }
            }

    private val backgroundLocationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    checkNotificationPermission()
                } else {
                    // Background location optional, continue anyway
                    checkNotificationPermission()
                }
            }

    private val notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    startLocationTracking()
                    requestMicrophonePermission()
                } else {
                    Toast.makeText(this, "Permission notifikasi diperlukan", Toast.LENGTH_LONG)
                            .show()
                }
            }

    private val microphonePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startAudioService()
                } else {
                    Toast.makeText(
                                    this,
                                    "Permission microphone diperlukan untuk audio service",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if service is still running and restore state
        if (LocationService.isServiceRunning(this)) {
            viewModel.restoreServiceState(isRunning = true)
        }

        setContent {
            TraapTheme {
                PowerButtonScreen(viewModel = viewModel, onPowerButtonClick = ::onPowerButtonClick)
            }
        }
    }

    private fun onPowerButtonClick() {
        val uiState = viewModel.uiState.value

        if (uiState.isServiceRunning) {
            // Stop service
            LocationService.stopService(this)
            viewModel.stopLocationService()
        } else {
            // Request permissions and start service
            requestLocationPermissions()
        }
    }

    private fun requestLocationPermissions() {
        val permissionsToRequest =
                mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                )

        locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationPermissionLauncher.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            } else {
                checkNotificationPermission()
            }
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startLocationTracking()
                requestMicrophonePermission() // Request mic permission immediately
            }
        } else {
            startLocationTracking()
            requestMicrophonePermission() // Request mic permission immediately
        }
    }

    private fun startLocationTracking() {
        LocationService.startService(this)
        viewModel.startLocationService()
    }

    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startAudioService()
        }
    }

    private fun startAudioService() {
        AudioService.startService(this)
        android.util.Log.d("MainActivity", "AudioService started")
    }
}

@Composable
fun PowerButtonScreen(
        viewModel: MainViewModel,
        onPowerButtonClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
            modifier =
                    Modifier.fillMaxSize().clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                            ) { viewModel.onScreenTap() },
            color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Status message at bottom
            if (uiState.statusMessage.isNotEmpty()) {
                Text(
                        text = uiState.statusMessage,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color =
                                if (uiState.statusMessage.contains("berhasil") ||
                                                uiState.statusMessage.contains("dimulai")
                                )
                                        Color(0xFF4CAF50)
                                else Color(0xFF2196F3)
                )
            }

            AnimatedVisibility(
                    visible = uiState.isButtonVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
            ) {
                Surface(
                        modifier =
                                Modifier.size(100.dp)
                                        .shadow(
                                                elevation = 12.dp,
                                                shape = CircleShape,
                                                spotColor = Color.Red.copy(alpha = 0.3f)
                                        ),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(3.dp, Color.Red)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        IconButton(
                                onClick = {
                                    viewModel.hideButton()
                                    onPowerButtonClick()
                                },
                                modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Power Button",
                                    tint =
                                            if (uiState.isServiceRunning)
                                                    Color(0xFF4CAF50) // Green when running
                                            else Color.Red, // Red when stopped
                                    modifier = Modifier.size(60.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
