package com.example

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.admin.TvDeviceAdminReceiver
import com.example.domain.AppScreen
import com.example.domain.VerificationReport
import com.example.ui.MainViewModel
import com.example.ui.screens.ConfirmationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExecutionConsoleScreen
import com.example.ui.screens.PinSetupScreen
import com.example.ui.screens.PinVerifyGateScreen
import com.example.ui.screens.SettingsAndHistoryScreen
import com.example.ui.screens.VerificationReportScreen
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TvWipeGuardTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TvWipeGuardTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var pendingExportReport by remember { mutableStateOf<VerificationReport?>(null) }

                val deviceAdminLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    viewModel.refreshTelemetry()
                }

                val safTreeLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        viewModel.addAuthorizedFolder(uri)
                    }
                }

                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain"),
                ) { uri ->
                    val report = pendingExportReport
                    pendingExportReport = null
                    if (uri != null && report != null) {
                        runCatching {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(viewModel.buildExportableReportText(report).toByteArray(Charsets.UTF_8))
                            }
                        }.onSuccess {
                            viewModel.showBanner("Registro exportado correctamente: ${report.reportId}.txt")
                        }.onFailure { err ->
                            viewModel.showBanner("Error al exportar registro: ${err.message}")
                        }
                    }
                }

                LaunchedEffect(uiState.bannerMessage) {
                    val msg = uiState.bannerMessage
                    if (!msg.isNullOrBlank()) {
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearBanner()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ObsidianBg,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = ObsidianSurfaceElevated,
                                contentColor = TextPrimary,
                                actionColor = CyanTelemetry,
                            )
                        }
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        AnimatedContent(
                            targetState = uiState.currentScreen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screenTransition",
                        ) { screen ->
                            when (screen) {
                                AppScreen.DASHBOARD -> DashboardScreen(
                                    uiState = uiState,
                                    onStartDeepWipe = { viewModel.startDeepWipeFlow() },
                                    onOpenPinSetup = { viewModel.openPinSetup() },
                                    onOpenSettingsHistory = { viewModel.navigateTo(AppScreen.SETTINGS_AND_HISTORY) },
                                    onRequestDeviceAdmin = {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(
                                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                                TvDeviceAdminReceiver.componentName(this@MainActivity),
                                            )
                                            putExtra(
                                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                getString(R.string.device_admin_explanation),
                                            )
                                        }
                                        deviceAdminLauncher.launch(intent)
                                    },
                                    onPickSafFolder = { safTreeLauncher.launch(null) },
                                    onRemoveSafFolder = { viewModel.removeAuthorizedFolder(it) },
                                    onSelectPassMode = { viewModel.setPassMode(it) },
                                    onRefreshTelemetry = { viewModel.refreshTelemetry() },
                                )

                                AppScreen.PIN_SETUP -> PinSetupScreen(
                                    uiState = uiState,
                                    onDigit = { viewModel.appendPinDigit(it) },
                                    onBackspace = { viewModel.backspacePin() },
                                    onSubmitStep = { viewModel.submitPinSetupStep() },
                                    onCancel = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                )

                                AppScreen.CONFIRMATION -> ConfirmationScreen(
                                    uiState = uiState,
                                    onProceedToPin = { viewModel.proceedToPinVerification() },
                                    onCancel = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                )

                                AppScreen.PIN_VERIFY -> PinVerifyGateScreen(
                                    uiState = uiState,
                                    onDigit = { viewModel.appendPinDigit(it) },
                                    onBackspace = { viewModel.backspacePin() },
                                    onVerifyPin = { viewModel.submitPinForWipeAuthorization() },
                                    onExecuteArmedWipe = { viewModel.executeArmedWipe() },
                                    onCancel = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                )

                                AppScreen.EXECUTION_CONSOLE -> ExecutionConsoleScreen(
                                    uiState = uiState,
                                )

                                AppScreen.VERIFICATION_REPORT -> {
                                    val report = uiState.activeReport
                                    if (report != null) {
                                        VerificationReportScreen(
                                            report = report,
                                            packageName = packageName,
                                            onExportReport = { target ->
                                                pendingExportReport = target
                                                createDocumentLauncher.launch("${target.reportId}_verificacion.txt")
                                            },
                                            onOpenSystemResetSettings = {
                                                startActivity(viewModel.buildSystemResetSettingsIntent())
                                            },
                                            onCopiedNotice = { viewModel.showBanner(it) },
                                            onBackToDashboard = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                        )
                                    } else {
                                        DashboardScreen(
                                            uiState = uiState,
                                            onStartDeepWipe = { viewModel.startDeepWipeFlow() },
                                            onOpenPinSetup = { viewModel.openPinSetup() },
                                            onOpenSettingsHistory = { viewModel.navigateTo(AppScreen.SETTINGS_AND_HISTORY) },
                                            onRequestDeviceAdmin = {},
                                            onPickSafFolder = { safTreeLauncher.launch(null) },
                                            onRemoveSafFolder = { viewModel.removeAuthorizedFolder(it) },
                                            onSelectPassMode = { viewModel.setPassMode(it) },
                                            onRefreshTelemetry = { viewModel.refreshTelemetry() },
                                        )
                                    }
                                }

                                AppScreen.SETTINGS_AND_HISTORY -> SettingsAndHistoryScreen(
                                    uiState = uiState,
                                    packageName = packageName,
                                    onToggleSafeMode = { viewModel.setSafeVerificationMode(it) },
                                    onOpenReport = { viewModel.openHistoryReport(it) },
                                    onBack = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTelemetry()
    }
}
