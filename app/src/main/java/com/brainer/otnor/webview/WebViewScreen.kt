package com.brainer.otnor.webview

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.brainer.otnor.OtnorApp
import com.brainer.otnor.accessibility.ScreenFreezeOverlay
import com.brainer.otnor.service.BrainerForegroundService
import com.brainer.otnor.util.FullPermissionsStatus
import com.brainer.otnor.util.PermissionManager
import com.example.ui.theme.OtnorAmber
import com.example.ui.theme.OtnorBorder
import com.example.ui.theme.OtnorCoral
import com.example.ui.theme.OtnorCyan
import com.example.ui.theme.OtnorDarkBg
import com.example.ui.theme.OtnorEmerald
import com.example.ui.theme.OtnorSurface
import com.example.ui.theme.OtnorSurfaceElevated
import com.example.ui.theme.OtnorTextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    webViewManager: WebViewManager,
    showSetupWizardInitially: Boolean,
    onRequestRuntimePermissions: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onSetupCompleted: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by webViewManager.isLoading.collectAsState()
    val isUsingCachedFallback by webViewManager.isUsingCachedFallback.collectAsState()
    val backendReachable by BrainerForegroundService.backendReachable.collectAsState()

    var showPermissionsSheet by remember { mutableStateOf(showSetupWizardInitially) }
    var permissionStatus by remember { mutableStateOf(PermissionManager.getPermissionsStatus(context)) }
    var andropiAlive by remember { mutableStateOf(OtnorApp.andropiServer?.isAlive == true) }

    // Periodically refresh permission & server indicators + auto-reload when backend comes online
    LaunchedEffect(Unit) {
        var wasBackendUp = backendReachable
        while (true) {
            permissionStatus = PermissionManager.getPermissionsStatus(context)
            andropiAlive = OtnorApp.andropiServer?.isAlive == true
            val nowBackendUp = BrainerForegroundService.backendReachable.value
            if (!wasBackendUp && nowBackendUp && isUsingCachedFallback) {
                webViewManager.reloadBackend()
            }
            wasBackendUp = nowBackendUp
            delay(2500L)
        }
    }

    BackHandler(enabled = webViewManager.canGoBack()) {
        webViewManager.goBack()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = OtnorDarkBg,
        topBar = {
            OtnorStatusBar(
                backendOnline = backendReachable && !isUsingCachedFallback,
                andropiOnline = andropiAlive,
                accessibilityOnline = permissionStatus.specialPermissions.accessibilityService,
                cachedSnapshots = webViewManager.uiVersionManager.getSnapshotCount(),
                onReloadClick = { webViewManager.reloadBackend() },
                onOpenPermissionsClick = {
                    permissionStatus = PermissionManager.getPermissionsStatus(context)
                    showPermissionsSheet = true
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("otnor_webview"),
                factory = {
                    webViewManager.createAndConfigureWebView().apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )

            AnimatedVisibility(
                visible = isLoading,
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("webview_loading_indicator"),
                    color = OtnorCyan,
                    trackColor = OtnorSurfaceElevated
                )
            }
        }
    }

    if (showPermissionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showPermissionsSheet = false
                onSetupCompleted()
            },
            sheetState = sheetState,
            containerColor = OtnorSurface,
            scrimColor = Color.Black.copy(alpha = 0.7f)
        ) {
            PermissionSetupWizardContent(
                status = permissionStatus,
                onRequestRuntimePermissions = {
                    onRequestRuntimePermissions()
                    permissionStatus = PermissionManager.getPermissionsStatus(context)
                },
                onRequestBackgroundLocation = {
                    onRequestBackgroundLocation()
                    permissionStatus = PermissionManager.getPermissionsStatus(context)
                },
                onDismiss = {
                    showPermissionsSheet = false
                    onSetupCompleted()
                }
            )
        }
    }
}

@Composable
private fun OtnorStatusBar(
    backendOnline: Boolean,
    andropiOnline: Boolean,
    accessibilityOnline: Boolean,
    cachedSnapshots: Int,
    onReloadClick: () -> Unit,
    onOpenPermissionsClick: () -> Unit
) {
    Surface(
        color = OtnorSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Backend status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (backendOnline) OtnorEmerald else OtnorCoral)
                        .testTag("backend_status_dot")
                )
                Column {
                    Text(
                        text = if (backendOnline) "OTNOR • :8080 ONLINE" else "OTNOR • :8080 OFFLINE (CACHE)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (backendOnline) OtnorEmerald else OtnorAmber
                    )
                    Text(
                        text = buildString {
                            append(if (andropiOnline) "Andropi :8081 UP" else "Andropi :8081 DOWN")
                            append(" • ")
                            append(if (accessibilityOnline) "A11y ON" else "A11y OFF")
                            if (cachedSnapshots > 0) {
                                append(" • $cachedSnapshots cached")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = OtnorTextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onReloadClick,
                    modifier = Modifier.testTag("reload_webview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload WebView from Backend",
                        tint = OtnorCyan
                    )
                }
                IconButton(
                    onClick = onOpenPermissionsClick,
                    modifier = Modifier.testTag("open_permissions_wizard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Permissions & Andropi Setup Wizard",
                        tint = if (accessibilityOnline) OtnorCyan else OtnorAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionSetupWizardContent(
    status: FullPermissionsStatus,
    onRequestRuntimePermissions: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Otnor Native Permissions Wizard",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Grant full native access for Andropi (:8081) and Brainer Accessibility.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OtnorTextSecondary
                )
            }
        }

        // Phase 1: Batch Runtime Permissions Card
        Card(
            colors = CardDefaults.cardColors(containerColor = OtnorSurfaceElevated),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Phase 1 • Runtime Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        color = OtnorCyan
                    )
                    Text(
                        text = "${status.granted.size} Granted / ${status.denied.size} Pending",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status.denied.isEmpty()) OtnorEmerald else OtnorAmber
                    )
                }
                Text(
                    text = "Camera, Microphone, Location, Contacts, Calendar, SMS, Phone, Bluetooth, Sensors, Notifications, and Media.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OtnorTextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRequestRuntimePermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = OtnorCyan, contentColor = OtnorDarkBg),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_runtime_permissions_button")
                    ) {
                        Text("Grant All Runtime")
                    }
                    OutlinedButton(
                        onClick = onRequestBackgroundLocation,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("grant_bg_location_button")
                    ) {
                        Text("Background GPS")
                    }
                }
            }
        }

        // Phase 2: Special System Settings Permissions
        Text(
            text = "Phase 2 • Special System Access",
            style = MaterialTheme.typography.titleMedium,
            color = OtnorCyan
        )

        SpecialPermissionRow(
            title = "1. Brainer Accessibility Service",
            subtitle = "Required for screen text capture, node actions, fingerprint gestures & screen freeze",
            granted = status.specialPermissions.accessibilityService,
            testTag = "special_perm_accessibility",
            onClick = { PermissionManager.openAccessibilitySettings(context) }
        )

        SpecialPermissionRow(
            title = "2. Display Over Other Apps (Overlay)",
            subtitle = "Required for Screen Freeze OCR word selector & floating quick-actions popup",
            granted = status.specialPermissions.overlayPermission,
            testTag = "special_perm_overlay",
            onClick = { PermissionManager.openOverlaySettings(context) }
        )

        SpecialPermissionRow(
            title = "3. All Files Access (MANAGE_EXTERNAL_STORAGE)",
            subtitle = "Required for /andropi/v1/storage/* read/write across /storage/emulated/0/",
            granted = status.specialPermissions.manageExternalStorage,
            testTag = "special_perm_storage",
            onClick = { PermissionManager.openManageAllFilesSettings(context) }
        )

        SpecialPermissionRow(
            title = "4. Battery Optimization Exemption",
            subtitle = "Keeps Andropi server (:8081) and Accessibility alive 24/7 in background (ColorOS/Android)",
            granted = status.specialPermissions.batteryOptimizationExempt,
            testTag = "special_perm_battery",
            onClick = { PermissionManager.openBatteryOptimizationSettings(context) }
        )

        SpecialPermissionRow(
            title = "5. Notification Listener Service",
            subtitle = "Intercepts incoming WhatsApp, Telegram, SMS & Gmail messages with direct reply",
            granted = status.specialPermissions.notificationListener,
            testTag = "special_perm_notification_listener",
            onClick = { PermissionManager.openNotificationListenerSettings(context) }
        )

        SpecialPermissionRow(
            title = "6. Usage Access (PACKAGE_USAGE_STATS)",
            subtitle = "Allows monitoring active foreground apps and screen usage",
            granted = status.specialPermissions.usageStats,
            testTag = "special_perm_usage_stats",
            onClick = { PermissionManager.openUsageAccessSettings(context) }
        )

        SpecialPermissionRow(
            title = "7. Modify System Settings (WRITE_SETTINGS)",
            subtitle = "Allows Andropi to control system screen brightness and display timeout",
            granted = status.specialPermissions.writeSettings,
            testTag = "special_perm_write_settings",
            onClick = { PermissionManager.openWriteSettings(context) }
        )

        HorizontalDivider(color = OtnorBorder)

        // Alternative Floating Quick-Action Trigger (helpful on Oppo A79 5G side-mounted fingerprint sensor)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    ScreenFreezeOverlay.showQuickActionsPopup(context)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("launch_quick_popup_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Quick Overlay")
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = OtnorEmerald, contentColor = Color.White),
                modifier = Modifier
                    .weight(1f)
                    .testTag("finish_setup_wizard_button")
            ) {
                Text("Continue to Otnor")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SpecialPermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OtnorSurfaceElevated)
            .border(
                width = 1.dp,
                color = if (granted) OtnorEmerald.copy(alpha = 0.5f) else OtnorBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = OtnorTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (granted) "Granted" else "Tap to Grant",
            tint = if (granted) OtnorEmerald else OtnorAmber,
            modifier = Modifier.size(24.dp)
        )
    }
}
