# Otnor — Native Android App Specification

## ⚠️ READ THIS FIRST — FOR AI CODE GENERATOR

You are building a **native Android app called Otnor**. This document is the **complete, authoritative specification**. Follow it precisely. Do not simplify, skip sections, or make assumptions beyond what is written here.

**Key constraints:**
- Language: **Kotlin**
- UI Framework: **Jetpack Compose** (Material 3)
- Min SDK: **26** (Android 8.0)
- Target SDK: **34** (Android 14)
- The app's main UI is a **WebView** that loads content from a localhost server (Termux backend)
- The app embeds an **HTTP API server** called Andropi (via NanoHTTPD) that exposes native Android capabilities
- The app includes an **AccessibilityService** for screen text capture, fingerprint gestures, and overlay features
- This is a **personal-use app** — it will NOT be published on Google Play Store. Do NOT add security restrictions, rate limiting, or authentication to the Andropi API. All localhost connections are trusted.
- The app requests **every possible permission** upfront — graceful degradation is not needed; if a permission is denied, show a toast explaining why it's needed and re-prompt.

---

## 1. APP IDENTITY

| Property | Value |
|----------|-------|
| App Name | Otnor |
| Package Name | `com.brainer.otnor` |
| Version | 1.0.0 |
| Min SDK | 26 |
| Target SDK | 34 |
| Language | Kotlin |
| UI | Jetpack Compose + WebView |
| Architecture | Single Activity |

---

## 2. APP PURPOSE & CONTEXT

Otnor is the **native Android shell** for a larger system called **Brainer**. It does three things:

1. **WebView Host**: Displays HTML/CSS/JS UI loaded from a Python backend running in Termux on `http://127.0.0.1:8080`. When the backend is unavailable, it loads the last cached version of the UI.

2. **Andropi Engine**: Runs an embedded HTTP server on port `8081` that exposes 60+ native Android capabilities (storage, clipboard, camera, sensors, notifications, accessibility, etc.) as REST API endpoints. This allows the Termux backend and CLI scripts to control native Android features programmatically.

3. **Accessibility Features**: Provides an AccessibilityService that can capture text from other apps (ChatGPT, Gemini, etc.), detect fingerprint gestures to trigger quick actions, and freeze the screen as an overlay for word selection.

**The app is built ONCE and rarely updated.** All UI/UX development happens in HTML/CSS/JS served by the Termux backend. The app only needs rebuilding when new native commands are added.

---

## 3. ARCHITECTURE OVERVIEW

```
┌─────────────────────────────────────────────────────────┐
│                    OTNOR APP                             │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  MainActivity (Jetpack Compose)                     │ │
│  │  ├── WebView (loads http://127.0.0.1:8080)         │ │
│  │  ├── AndropiBridge (JS Interface on WebView)       │ │
│  │  ├── Status bar (connection indicator)             │ │
│  │  └── Permission request UI                          │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  Andropi HTTP Server (NanoHTTPD on :8081)           │ │
│  │  ├── /andropi/v1/device/*                          │ │
│  │  ├── /andropi/v1/storage/*                         │ │
│  │  ├── /andropi/v1/clipboard/*                       │ │
│  │  ├── /andropi/v1/notification/*                    │ │
│  │  ├── /andropi/v1/media/*                           │ │
│  │  ├── /andropi/v1/sensors/*                         │ │
│  │  ├── /andropi/v1/screen/*                          │ │
│  │  ├── /andropi/v1/apps/*                            │ │
│  │  ├── /andropi/v1/network/*                         │ │
│  │  ├── /andropi/v1/biometric/*                       │ │
│  │  ├── /andropi/v1/accessibility/*                   │ │
│  │  ├── /andropi/v1/overlay/*                         │ │
│  │  ├── /andropi/v1/dialog/*                          │ │
│  │  └── /andropi/v1/system/*                          │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  BrainerAccessibilityService                        │ │
│  │  ├── Screen text capture (node tree traversal)     │ │
│  │  ├── Fingerprint gesture detection                 │ │
│  │  └── Screen freeze overlay + word selection        │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  BrainerForegroundService                           │ │
│  │  └── Keeps Andropi server + accessibility alive    │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  UIVersionManager                                   │ │
│  │  └── Caches WebView HTML for offline fallback      │ │
│  └─────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 4. COMPLETE FILE STRUCTURE

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/brainer/otnor/
│   ├── OtnorApp.kt                         # Application class
│   ├── MainActivity.kt                     # Single activity, hosts Compose + WebView
│   │
│   ├── webview/
│   │   ├── WebViewScreen.kt                # Compose screen wrapping WebView
│   │   ├── WebViewManager.kt               # WebView lifecycle, loading, reload
│   │   ├── AndropiBridge.kt                # @JavascriptInterface bridge
│   │   └── UIVersionManager.kt             # HTML snapshot caching
│   │
│   ├── andropi/
│   │   ├── AndropiServer.kt                # NanoHTTPD server on :8081
│   │   ├── AndropiRouter.kt                # Route dispatcher
│   │   ├── CommandResult.kt                # Standard response model
│   │   └── commands/
│   │       ├── DeviceCommands.kt           # Device info, battery, memory
│   │       ├── StorageCommands.kt          # File read/write/list/delete
│   │       ├── ClipboardCommands.kt        # Get/set clipboard
│   │       ├── NotificationCommands.kt     # Show/cancel notifications
│   │       ├── MediaCommands.kt            # Camera, audio, TTS
│   │       ├── SensorCommands.kt           # Sensors, location
│   │       ├── ScreenCommands.kt           # Brightness, orientation, vibrate, toast
│   │       ├── AppCommands.kt              # List/launch apps, send intents
│   │       ├── NetworkCommands.kt          # WiFi, bluetooth info
│   │       ├── BiometricCommands.kt        # Fingerprint/face auth
│   │       ├── DialogCommands.kt           # Native alert/confirm/input/picker dialogs
│   │       ├── AccessibilityCommands.kt    # Screen text capture, node actions
│   │       ├── OverlayCommands.kt          # Screen freeze, popup overlay
│   │       └── SystemCommands.kt           # Permissions, health, command listing
│   │
│   ├── accessibility/
│   │   ├── BrainerAccessibilityService.kt  # The AccessibilityService impl
│   │   ├── ScreenTextCapture.kt            # Node tree traversal + text extraction
│   │   ├── FingerprintGestureHandler.kt    # Fingerprint swipe gesture detection
│   │   ├── ScreenFreezeOverlay.kt          # Screenshot overlay + word selection
│   │   └── CaptureBuffer.kt               # Thread-safe buffer for captured data
│   │
│   ├── service/
│   │   ├── BrainerForegroundService.kt     # Foreground service (keep-alive)
│   │   └── BootReceiver.kt                 # Auto-start on device boot
│   │
│   └── util/
│       ├── PermissionManager.kt            # Request all permissions at once
│       ├── JsonHelper.kt                   # JSON serialization helpers
│       └── Constants.kt                    # Ports, URLs, config constants
│
└── res/
    ├── xml/
    │   ├── accessibility_service_config.xml
    │   └── network_security_config.xml      # Allow cleartext to localhost
    ├── layout/                              # (empty — using Compose)
    ├── values/
    │   ├── strings.xml
    │   ├── themes.xml
    │   └── colors.xml
    └── drawable/
        └── ic_notification.xml              # Foreground service notification icon
```

---

## 5. ANDROID MANIFEST (COMPLETE)

The manifest MUST include ALL of the following. Do not omit any permission.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.brainer.otnor">

    <!-- ==================== PERMISSIONS ==================== -->

    <!-- Core / Internet -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />

    <!-- Foreground Service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

    <!-- Wake / Boot / Battery -->
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- Storage (FULL access — personal app, all files needed) -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION" />

    <!-- Camera & Microphone -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <!-- Contacts -->
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.WRITE_CONTACTS" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />

    <!-- Calendar -->
    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />

    <!-- SMS -->
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_MMS" />
    <uses-permission android:name="android.permission.RECEIVE_WAP_PUSH" />

    <!-- Phone -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.WRITE_CALL_LOG" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.ADD_VOICEMAIL" />

    <!-- Notifications -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Biometrics -->
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />

    <!-- Overlay (draw over other apps) -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- Bluetooth -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />

    <!-- WiFi -->
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

    <!-- Sensors -->
    <uses-permission android:name="android.permission.BODY_SENSORS" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

    <!-- Vibrate -->
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Media Projection (for screen freeze/capture feature) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

    <!-- Query All Packages (API 30+ to see and launch ALL installed apps) -->
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <!-- Special App Access Permissions -->
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.WRITE_SETTINGS"
        tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

    <!-- ==================== APPLICATION ==================== -->

    <application
        android:name=".OtnorApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Otnor"
        android:supportsRtl="true"
        android:theme="@style/Theme.Otnor"
        android:networkSecurityConfig="@xml/network_security_config"
        android:requestLegacyExternalStorage="true"
        android:usesCleartextTraffic="true"
        tools:targetApi="34">

        <!-- Main Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Accessibility Service -->
        <service
            android:name=".accessibility.BrainerAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <!-- Foreground Service -->
        <service
            android:name=".service.BrainerForegroundService"
            android:exported="false"
            android:foregroundServiceType="specialUse" />

        <!-- Boot Receiver -->
        <receiver
            android:name=".service.BootReceiver"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

        <!-- Broadcast Receiver for Termux communication -->
        <receiver
            android:name=".service.TermuxCommandReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.brainer.otnor.COMMAND" />
            </intent-filter>
        </receiver>

        <!-- Notification Listener Service (intercept & read incoming messages across all apps) -->
        <service
            android:name=".service.BrainerNotificationListenerService"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

---

## 6. ACCESSIBILITY SERVICE CONFIG

File: `res/xml/accessibility_service_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackAllMask"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagRequestFingerprintGestures|flagIncludeNotImportantViews|flagReportViewIds|flagRequestFilterKeyEvents|flagRequestTouchExplorationMode|flagSendMotionEvents|flagRequestAccessibilityButton"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canRequestFingerprintGestures="true"
    android:canTakeScreenshot="true"
    android:canRequestFilterKeyEvents="true"
    android:canRequestTouchExplorationMode="true"
    android:canControlMagnification="true"
    android:notificationTimeout="50"
    android:settingsActivity="com.brainer.otnor.MainActivity" />
```

**Critical flags & capabilities explained:**
- `canRetrieveWindowContent="true"` — REQUIRED to inspect the UI tree and read text from other apps
- `canPerformGestures="true"` — Allows programmatic gesture injection (`dispatchGesture` for clicks, taps, swipes anywhere on screen by X,Y coordinates)
- `canRequestFingerprintGestures="true"` — Enables fingerprint swipe detection
- `canTakeScreenshot="true"` — Enables `takeScreenshot()` API (API 30+)
- `canRequestFilterKeyEvents="true"` — Intercept hardware keys (e.g. Volume Up/Down shortcuts)
- `canRequestTouchExplorationMode="true"` — Enables touch exploration support
- `canControlMagnification="true"` — Enables programmatically controlling screen zoom/magnification
- `flagRetrieveInteractiveWindows` — Access all windows including system UI, dialogs, keyboards
- `flagRequestFingerprintGestures` — Receive fingerprint gesture callbacks
- `flagIncludeNotImportantViews` — Don't skip views marked as not important for accessibility
- `flagReportViewIds` — CRITICAL: Ensures `viewIdResourceName` (e.g. `com.whatsapp:id/send_button`) is populated so elements can be found and clicked by ID
- `flagRequestFilterKeyEvents` — Enables `onKeyEvent` interception for volume and media buttons
- `flagRequestAccessibilityButton` — Callback when accessibility button in nav bar is tapped
- `flagSendMotionEvents` — Receives motion events for high-precision pointer tracking

---

## 7. NETWORK SECURITY CONFIG

File: `res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

---

## 8. GRADLE DEPENDENCIES

In `app/build.gradle.kts`:

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // WebView (for embedding in Compose)
    implementation("com.google.accompanist:accompanist-webview:0.34.0")
    // If accompanist-webview is deprecated at build time, use AndroidView wrapper instead

    // NanoHTTPD (Andropi HTTP server)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // Location services
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // ML Kit Text Recognition (for screen freeze OCR fallback)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // CameraX (for camera commands)
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
}
```

---

## 9. COMPONENT SPECIFICATIONS

### 9.1 MainActivity.kt

**Responsibilities:**
- Set up Jetpack Compose UI with a full-screen WebView
- Start the BrainerForegroundService
- Start the Andropi HTTP server
- Request ALL permissions on first launch
- Show a connection status indicator (green = backend connected, red = offline/cached)
- Handle back button (WebView back navigation)

**Behavior on launch:**
1. Check and request all permissions (use `PermissionManager`)
2. Start `BrainerForegroundService`
3. Start `AndropiServer` on port 8081
4. Load WebView with `http://127.0.0.1:8080`
5. If backend unreachable after 3 seconds, load from `UIVersionManager` cache
6. Register `AndropiBridge` as JavaScript interface named `"Andropi"`
7. Prompt user to enable Accessibility Service if not already enabled (navigate to Settings)
8. Prompt user to grant "Display over other apps" permission if not already granted
9. Prompt user to grant "All files access" (MANAGE_EXTERNAL_STORAGE) if not already granted
10. Prompt user to disable battery optimization for this app

### 9.2 WebViewManager.kt

**Responsibilities:**
- Configure WebView settings
- Handle page loading, errors, and caching
- Inject the Andropi JavaScript bridge
- Auto-reload when backend comes back online

**WebView Settings (ALL must be set):**
```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    allowFileAccess = true
    allowContentAccess = true
    mediaPlaybackRequiresUserGesture = false
    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    cacheMode = WebSettings.LOAD_DEFAULT
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
    loadWithOverviewMode = true
    useWideViewPort = true
    javaScriptCanOpenWindowsAutomatically = true
    allowUniversalAccessFromFileURLs = true
    allowFileAccessFromFileURLs = true
    userAgentString = "Otnor/1.0 (Android; Brainer)"
}
```

**WebViewClient behavior:**
- `onPageFinished()`: Save HTML snapshot via `UIVersionManager`
- `onReceivedError()` / `onReceivedHttpError()`: Load cached version if backend is down
- Do NOT override `shouldOverrideUrlLoading()` for `127.0.0.1` URLs — let them load in WebView
- For external URLs (non-localhost), open in the device's default browser

**WebChromeClient behavior:**
- Handle `onGeolocationPermissionsShowPrompt()` → auto-grant (personal app)
- Handle `onPermissionRequest()` → auto-grant all (personal app)
- Handle `onConsoleMessage()` → log to Logcat for debugging
- Handle file input for camera/gallery uploads

### 9.3 UIVersionManager.kt

**Responsibilities:**
- Save complete HTML page source after each successful load
- Store snapshots in app's internal storage: `filesDir/ui_cache/`
- Maintain a version index (JSON file) mapping URL paths → snapshot files
- Keep last 10 versions per URL path
- Load latest cached snapshot when backend is unreachable

**How to capture HTML:**
After `onPageFinished()`, call:
```kotlin
webView.evaluateJavascript(
    "(function() { return document.documentElement.outerHTML; })();",
    { html -> saveSnapshot(url, html) }
)
```

### 9.4 AndropiBridge.kt — JavaScript Interface

This class is registered on the WebView as `window.Andropi`. All methods annotated with `@JavascriptInterface`.

**These are SYNCHRONOUS fast-path methods for use from WebView JavaScript:**

```kotlin
@JavascriptInterface fun showToast(message: String)                    // Show Android toast
@JavascriptInterface fun vibrate(milliseconds: Long)                   // Vibrate device
@JavascriptInterface fun getClipboard(): String                        // Get clipboard text
@JavascriptInterface fun setClipboard(text: String)                    // Set clipboard text
@JavascriptInterface fun getDeviceInfo(): String                       // Returns JSON with device model, manufacturer, Android version, SDK
@JavascriptInterface fun getBatteryLevel(): Int                        // Battery percentage
@JavascriptInterface fun getBatteryCharging(): Boolean                 // Is charging?
@JavascriptInterface fun getNetworkType(): String                      // "wifi", "mobile", "none"
@JavascriptInterface fun getWifiSSID(): String                         // Current WiFi name
@JavascriptInterface fun isAccessibilityEnabled(): Boolean             // Is our accessibility service active?
@JavascriptInterface fun isAndropiRunning(): Boolean                   // Is Andropi HTTP server running?
@JavascriptInterface fun getAndropiPort(): Int                         // Returns 8081
@JavascriptInterface fun openAccessibilitySettings()                   // Navigate to accessibility settings
@JavascriptInterface fun openAppSettings()                             // Navigate to app settings
@JavascriptInterface fun openUrl(url: String)                          // Open URL in default browser
@JavascriptInterface fun shareText(text: String)                       // Android share sheet
@JavascriptInterface fun log(tag: String, message: String)             // Log to Android Logcat
@JavascriptInterface fun getScreenBrightness(): Int                    // Current brightness 0-255
@JavascriptInterface fun setScreenBrightness(value: Int)               // Set brightness 0-255
@JavascriptInterface fun keepScreenOn(enabled: Boolean)                // Keep screen on toggle
@JavascriptInterface fun reloadWebView()                               // Force reload from backend
```

---

## 10. ANDROPI HTTP SERVER — COMPLETE API SPECIFICATION

The Andropi server is a NanoHTTPD instance running on `http://127.0.0.1:8081`. It accepts ONLY connections from localhost (127.0.0.1). All request/response bodies are JSON.

### Standard Response Format

Every endpoint returns JSON in this format:
```json
{
    "success": true,
    "data": { ... },
    "error": null
}
```
On error:
```json
{
    "success": false,
    "data": null,
    "error": "Human-readable error message"
}
```

### 10.1 Device Commands (`/andropi/v1/device/`)

#### GET `/andropi/v1/device/info`
Returns comprehensive device information.
```json
{
    "success": true,
    "data": {
        "model": "CPH2551",
        "manufacturer": "OPPO",
        "brand": "OPPO",
        "device": "OP5961L1",
        "product": "CPH2551",
        "android_version": "13",
        "sdk_level": 33,
        "build_id": "TP1A.220905.001",
        "board": "mt6833",
        "hardware": "mt6833",
        "display_id": "...",
        "fingerprint": "...",
        "serial": "unknown",
        "bootloader": "...",
        "host": "...",
        "uptime_ms": 123456789
    }
}
```

#### GET `/andropi/v1/device/battery`
```json
{
    "success": true,
    "data": {
        "level": 85,
        "is_charging": true,
        "charge_type": "usb",
        "temperature": 28.5,
        "voltage": 4200,
        "health": "good"
    }
}
```

#### GET `/andropi/v1/device/memory`
```json
{
    "success": true,
    "data": {
        "total_ram_mb": 8192,
        "available_ram_mb": 4096,
        "used_ram_mb": 4096,
        "low_memory": false
    }
}
```

#### GET `/andropi/v1/device/display`
```json
{
    "success": true,
    "data": {
        "width_px": 1080,
        "height_px": 2400,
        "density_dpi": 420,
        "density": 2.625,
        "orientation": "portrait",
        "refresh_rate": 90.0
    }
}
```

#### GET `/andropi/v1/device/storage-stats`
```json
{
    "success": true,
    "data": {
        "internal": {
            "total_gb": 128.0,
            "free_gb": 45.2,
            "used_gb": 82.8
        },
        "external": {
            "available": true,
            "total_gb": 256.0,
            "free_gb": 200.1,
            "used_gb": 55.9
        }
    }
}
```

### 10.2 Storage Commands (`/andropi/v1/storage/`)

#### POST `/andropi/v1/storage/read`
**Request:**
```json
{
    "path": "/storage/emulated/0/Documents/notes.md",
    "encoding": "utf-8"
}
```
**Response:**
```json
{
    "success": true,
    "data": {
        "content": "file contents here...",
        "size_bytes": 1234,
        "last_modified": "2025-01-15T10:30:00Z",
        "mime_type": "text/markdown"
    }
}
```
For binary files, set `"encoding": "base64"` and content will be base64-encoded.

#### POST `/andropi/v1/storage/write`
**Request:**
```json
{
    "path": "/storage/emulated/0/Documents/output.txt",
    "content": "Hello World",
    "encoding": "utf-8",
    "create_dirs": true
}
```

#### POST `/andropi/v1/storage/append`
**Request:**
```json
{
    "path": "/storage/emulated/0/Documents/log.txt",
    "content": "New line\n"
}
```

#### POST `/andropi/v1/storage/list`
**Request:**
```json
{
    "path": "/storage/emulated/0/Documents/",
    "recursive": false,
    "include_hidden": false
}
```
**Response:**
```json
{
    "success": true,
    "data": {
        "path": "/storage/emulated/0/Documents/",
        "entries": [
            {
                "name": "notes.md",
                "path": "/storage/emulated/0/Documents/notes.md",
                "is_directory": false,
                "size_bytes": 1234,
                "last_modified": "2025-01-15T10:30:00Z",
                "mime_type": "text/markdown"
            }
        ],
        "total_entries": 1
    }
}
```

#### POST `/andropi/v1/storage/delete`
Request: `{"path": "...", "recursive": false}`

#### POST `/andropi/v1/storage/move`
Request: `{"from": "...", "to": "..."}`

#### POST `/andropi/v1/storage/copy`
Request: `{"from": "...", "to": "..."}`

#### POST `/andropi/v1/storage/exists`
Request: `{"path": "..."}`
Response: `{"data": {"exists": true, "is_directory": false}}`

#### POST `/andropi/v1/storage/stat`
Request: `{"path": "..."}`
Response: Returns name, path, size, last_modified, is_directory, is_readable, is_writable, mime_type.

#### POST `/andropi/v1/storage/mkdir`
Request: `{"path": "...", "recursive": true}`

### 10.3 Clipboard Commands (`/andropi/v1/clipboard/`)

#### GET `/andropi/v1/clipboard/get`
Response: `{"data": {"text": "clipboard content", "has_content": true}}`

#### POST `/andropi/v1/clipboard/set`
Request: `{"text": "new clipboard content"}`

### 10.4 Notification Commands (`/andropi/v1/notification/`)

#### POST `/andropi/v1/notification/show`
**Request:**
```json
{
    "id": 1001,
    "title": "Brainer",
    "body": "Time to review 5 cards!",
    "channel_id": "brainer_reminders",
    "priority": "high",
    "ongoing": false,
    "auto_cancel": true,
    "vibrate": true,
    "sound": true
}
```

#### POST `/andropi/v1/notification/cancel`
Request: `{"id": 1001}`

#### POST `/andropi/v1/notification/cancel-all`
No request body needed.

#### GET `/andropi/v1/notification/active`
Returns list of currently active notifications posted by this app.

#### POST `/andropi/v1/notification/channel/create`
Request: `{"id": "brainer_reminders", "name": "Brainer Reminders", "importance": "high", "description": "Review reminders"}`

### 10.5 Media Commands (`/andropi/v1/media/`)

#### POST `/andropi/v1/media/tts/speak`
Request: `{"text": "Hello Vibhor", "language": "en", "pitch": 1.0, "speed": 1.0}`

#### POST `/andropi/v1/media/tts/stop`
No request body.

#### GET `/andropi/v1/media/tts/voices`
Returns available TTS voices.

#### POST `/andropi/v1/media/audio/record/start`
Request: `{"output_path": "/storage/emulated/0/Documents/recording.m4a", "max_duration_seconds": 60}`

#### POST `/andropi/v1/media/audio/record/stop`
Response: `{"data": {"path": "...", "duration_seconds": 15.3, "size_bytes": 24000}}`

#### POST `/andropi/v1/media/audio/play`
Request: `{"path": "/storage/emulated/0/Documents/recording.m4a"}`

#### POST `/andropi/v1/media/audio/stop`
No request body.

#### POST `/andropi/v1/media/camera/photo`
Request: `{"camera": "back", "output_path": "/storage/emulated/0/DCIM/brainer_photo.jpg"}`
Response: `{"data": {"path": "...", "width": 4000, "height": 3000, "size_bytes": 2500000}}`

### 10.6 Sensor Commands (`/andropi/v1/sensors/`)

#### GET `/andropi/v1/sensors/list`
Returns all available sensors with type, name, vendor, max range, resolution.

#### POST `/andropi/v1/sensors/read`
Request: `{"type": "accelerometer"}` (or "gyroscope", "light", "proximity", "pressure", "magnetic_field", "gravity", "step_counter")
Response: `{"data": {"type": "accelerometer", "values": [0.1, 9.8, 0.3], "accuracy": 3, "timestamp": "..."}}`

#### GET `/andropi/v1/sensors/location`
Response: `{"data": {"latitude": 28.6139, "longitude": 77.2090, "altitude": 216.0, "accuracy": 15.0, "speed": 0.0, "provider": "gps"}}`

### 10.7 Screen & UI Commands (`/andropi/v1/screen/`)

#### POST `/andropi/v1/screen/brightness`
Request: `{"value": 128}` (0-255)

#### POST `/andropi/v1/screen/orientation`
Request: `{"orientation": "portrait"}` (portrait, landscape, auto)

#### POST `/andropi/v1/screen/keep-on`
Request: `{"enabled": true}`

#### POST `/andropi/v1/screen/toast`
Request: `{"message": "Hello!", "duration": "short"}` (short or long)

#### POST `/andropi/v1/screen/vibrate`
Request: `{"pattern": [0, 200, 100, 200]}` (wait, vibrate, wait, vibrate in ms)
Or simple: `{"duration_ms": 200}`

### 10.8 Dialog Commands (`/andropi/v1/dialog/`)

**IMPORTANT**: Dialog commands are BLOCKING — the HTTP response is sent only after the user interacts with the dialog. The Andropi server must handle this asynchronously (use a callback/continuation) so it doesn't block other API calls.

#### POST `/andropi/v1/dialog/alert`
Request: `{"title": "Warning", "message": "Are you sure?", "button_text": "OK"}`
Response: `{"data": {"dismissed": true}}`

#### POST `/andropi/v1/dialog/confirm`
Request: `{"title": "Confirm", "message": "Delete this entry?", "positive": "Yes", "negative": "No"}`
Response: `{"data": {"confirmed": true}}`

#### POST `/andropi/v1/dialog/input`
Request: `{"title": "Quick Capture", "message": "Enter your thought:", "hint": "Type here...", "default_value": "", "input_type": "text"}`
Response: `{"data": {"text": "user's input", "submitted": true}}`

#### POST `/andropi/v1/dialog/picker/date`
Request: `{"title": "Select Date"}`
Response: `{"data": {"year": 2025, "month": 6, "day": 15, "date_string": "2025-06-15"}}`

#### POST `/andropi/v1/dialog/picker/time`
Request: `{"title": "Select Time", "is_24_hour": true}`
Response: `{"data": {"hour": 14, "minute": 30, "time_string": "14:30"}}`

### 10.9 App Management Commands (`/andropi/v1/apps/`)

#### GET `/andropi/v1/apps/list`
Response: List of all installed apps with package name, app name, version, install date, is system app.

#### POST `/andropi/v1/apps/launch`
Request: `{"package": "com.openai.chatgpt"}`

#### POST `/andropi/v1/apps/intent`
Request:
```json
{
    "action": "android.intent.action.VIEW",
    "data": "https://example.com",
    "type": null,
    "package": null,
    "extras": {}
}
```

#### GET `/andropi/v1/apps/running`
Response: List of currently running app processes.

### 10.10 Network Commands (`/andropi/v1/network/`)

#### GET `/andropi/v1/network/wifi/info`
Response: SSID, BSSID, IP address, link speed, frequency, signal strength.

#### GET `/andropi/v1/network/wifi/scan`
Response: List of nearby WiFi networks with SSID, BSSID, signal level, frequency, capabilities.

#### GET `/andropi/v1/network/bluetooth/scan`
Starts a scan and returns discovered devices (name, address, type, bond state).

#### POST `/andropi/v1/network/bluetooth/connect`
Request: `{"address": "AA:BB:CC:DD:EE:FF"}`

### 10.11 Biometric Commands (`/andropi/v1/biometric/`)

#### POST `/andropi/v1/biometric/authenticate`
Request: `{"title": "Confirm Identity", "subtitle": "Verify to continue", "negative_text": "Cancel"}`
Response: `{"data": {"authenticated": true, "auth_type": "fingerprint"}}`
This is BLOCKING — waits for user to authenticate.

#### GET `/andropi/v1/biometric/available`
Response: `{"data": {"available": true, "types": ["fingerprint"], "can_authenticate": true}}`

### 10.12 Accessibility Commands (`/andropi/v1/accessibility/`)

**These commands communicate with BrainerAccessibilityService via a shared singleton (CaptureBuffer).**

#### GET `/andropi/v1/accessibility/status`
Response:
```json
{
    "data": {
        "service_enabled": true,
        "is_connected": true,
        "fingerprint_gestures_available": true,
        "can_retrieve_window_content": true,
        "can_take_screenshot": true,
        "can_perform_gestures": true
    }
}
```

#### POST `/andropi/v1/accessibility/capture-text`
Captures all visible text from the current foreground app's screen.
Request: `{}` (no body needed)
Response:
```json
{
    "data": {
        "package_name": "com.openai.chatgpt",
        "activity_name": "...",
        "texts": [
            {"text": "Hello, how can I help you?", "view_id": "response_text", "class": "android.widget.TextView", "bounds": {"left": 0, "top": 100, "right": 1080, "bottom": 200}},
            {"text": "Type a message", "view_id": "input_hint", "class": "android.widget.EditText", "bounds": {...}}
        ],
        "capture_time": "2025-01-15T10:30:00Z"
    }
}
```

#### POST `/andropi/v1/accessibility/capture-app`
Captures text only from a specific app. Waits until that app is in the foreground (max 10 seconds timeout).
Request: `{"package": "com.openai.chatgpt", "timeout_seconds": 10}`
Response: Same as capture-text.

#### POST `/andropi/v1/accessibility/find-text`
Finds UI nodes containing specific text on the current screen.
Request: `{"query": "Send", "exact_match": false}`
Response:
```json
{
    "data": {
        "matches": [
            {"text": "Send message", "class": "android.widget.Button", "clickable": true, "bounds": {...}}
        ]
    }
}
```

#### POST `/andropi/v1/accessibility/click-node`
Clicks on a specific UI element found by text or resource ID.
Request: `{"text": "Send"}` or `{"resource_id": "com.openai.chatgpt:id/send_button"}`
Response: `{"data": {"clicked": true}}`

#### POST `/andropi/v1/accessibility/perform-action`
Perform any accessibility action on a node.
Request: `{"text": "Message input", "action": "ACTION_SET_TEXT", "arguments": {"ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE": "Hello!"}}`

#### POST `/andropi/v1/accessibility/global-action`
Perform global accessibility actions.
Request: `{"action": "GLOBAL_ACTION_BACK"}` (or HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, POWER_DIALOG, LOCK_SCREEN, TAKE_SCREENSHOT, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_CENTER)

#### POST `/andropi/v1/accessibility/dispatch-gesture`
Dispatches arbitrary programmatic touch gestures anywhere on the screen using Android's `dispatchGesture()` API. This allows clicking, swiping, or dragging even on canvas, games, or views without text/IDs.
**Tap at coordinates:**
```json
{
    "type": "click",
    "x": 540,
    "y": 1200
}
```
**Swipe/Drag:**
```json
{
    "type": "swipe",
    "start_x": 540,
    "start_y": 1800,
    "end_x": 540,
    "end_y": 600,
    "duration_ms": 300
}
```
**Long press:**
```json
{
    "type": "long_press",
    "x": 540,
    "y": 1200,
    "duration_ms": 800
}
```

#### POST `/andropi/v1/accessibility/scroll`
Performs programmatic scroll actions on the active window or a specific node.
Request: `{"direction": "forward"}` (forward, backward, scroll_to_top, scroll_to_bottom)

#### POST `/andropi/v1/accessibility/key-event/register`
Register hardware key triggers (e.g. Volume Up, Volume Down pressed) to trigger actions like quick screen capture.
Request: `{"key": "VOLUME_DOWN", "action": "quick_capture"}`

#### GET `/andropi/v1/notification/listener/messages`
Returns all notifications/messages intercepted by `BrainerNotificationListenerService` across WhatsApp, Telegram, SMS, Gmail, etc.
Response:
```json
{
    "data": {
        "messages": [
            {
                "id": "101",
                "package_name": "com.whatsapp",
                "sender": "John Doe",
                "text": "Hey, are you free today?",
                "timestamp": "2025-01-15T10:35:00Z",
                "can_reply": true
            }
        ]
    }
}
```

#### POST `/andropi/v1/notification/listener/reply`
Directly sends a reply to an intercepted notification.
Request: `{"notification_id": "101", "reply_text": "Yes, I am free!"}`

#### POST `/andropi/v1/accessibility/gesture/register`
Register a fingerprint gesture to trigger an action.
Request:
```json
{
    "gesture": "swipe_up",
    "action": "open_capture_popup"
}
```
Supported gestures: `swipe_up`, `swipe_down`, `swipe_left`, `swipe_right`
Supported actions: `open_capture_popup`, `open_app`, `capture_screen_text`, `custom_url` (calls a URL)

#### POST `/andropi/v1/accessibility/gesture/unregister`
Request: `{"gesture": "swipe_up"}`

#### GET `/andropi/v1/accessibility/gesture/list`
Returns all registered gesture → action mappings.

### 10.13 Overlay Commands (`/andropi/v1/overlay/`)

#### POST `/andropi/v1/overlay/freeze`
Freezes the screen by taking a screenshot and displaying it as a full-screen overlay. User can tap on words in the frozen view.

**Implementation approach:**
1. Use `AccessibilityService.takeScreenshot()` (API 30+) to capture the screen
2. Before capturing, snapshot the accessibility node tree with all text nodes + their bounding rectangles
3. Display the screenshot as a TYPE_APPLICATION_OVERLAY
4. When user taps on the overlay, map tap coordinates to the nearest text node using the saved bounding rectangles
5. Highlight the tapped word/node
6. If node mapping fails (no text at coordinates), use ML Kit text recognition as fallback OCR on the screenshot bitmap in that region

Request: `{}` (no body needed)
Response: `{"data": {"frozen": true, "node_count": 47}}`

#### POST `/andropi/v1/overlay/unfreeze`
Removes the freeze overlay and returns selected words.
Response:
```json
{
    "data": {
        "unfrozen": true,
        "selected_words": ["spaced", "repetition", "algorithm"],
        "selected_nodes": [
            {"text": "spaced repetition", "source_package": "com.android.chrome", "bounds": {...}}
        ]
    }
}
```

#### POST `/andropi/v1/overlay/popup`
Shows a small floating popup overlay with custom HTML content or a quick-action menu.
Request:
```json
{
    "type": "quick_actions",
    "position": "bottom_right",
    "width_dp": 200,
    "height_dp": 300,
    "actions": [
        {"label": "📝 Quick Capture", "id": "capture"},
        {"label": "📚 Review Cards", "id": "review"},
        {"label": "🔍 Search Brain", "id": "search"},
        {"label": "❌ Close", "id": "close"}
    ]
}
```
When user taps an action, it's stored in CaptureBuffer for retrieval.

Or for custom content:
```json
{
    "type": "webview",
    "url": "http://127.0.0.1:8080/popup/capture",
    "position": "center",
    "width_dp": 350,
    "height_dp": 500
}
```

#### POST `/andropi/v1/overlay/dismiss`
Dismiss any active popup overlay.

#### GET `/andropi/v1/overlay/status`
Response: `{"data": {"freeze_active": false, "popup_active": true, "popup_type": "quick_actions"}}`

### 10.14 System Commands (`/andropi/v1/system/`)

#### GET `/andropi/v1/system/status`
Health check for the Andropi server.
```json
{
    "data": {
        "server_running": true,
        "port": 8081,
        "uptime_seconds": 3600,
        "version": "1.0.0",
        "accessibility_service_enabled": true,
        "foreground_service_running": true,
        "backend_reachable": true,
        "backend_url": "http://127.0.0.1:8080"
    }
}
```

#### GET `/andropi/v1/system/permissions`
Returns the status of ALL permissions.
```json
{
    "data": {
        "granted": ["android.permission.CAMERA", "android.permission.READ_CONTACTS", ...],
        "denied": ["android.permission.ACCESS_BACKGROUND_LOCATION"],
        "special_permissions": {
            "accessibility_service": true,
            "overlay_permission": true,
            "manage_external_storage": true,
            "battery_optimization_exempt": false
        }
    }
}
```

#### POST `/andropi/v1/system/permission/request`
Request: `{"permission": "android.permission.CAMERA"}`
This opens the system permission dialog. Response is sent after user acts.

#### GET `/andropi/v1/system/commands`
Returns a list of ALL available Andropi commands with their HTTP method, path, and description.

#### GET `/andropi/v1/system/version`
Response: `{"data": {"app_version": "1.0.0", "andropi_version": "1.0.0", "android_version": "13", "sdk_level": 33}}`

---

## 11. ACCESSIBILITY SERVICE — DETAILED IMPLEMENTATION

### 11.1 BrainerAccessibilityService.kt

**Core responsibilities:**
1. Listen to all accessibility events from all apps
2. On demand (triggered via CaptureBuffer signal), capture all text from the current screen's node tree
3. Listen for fingerprint gestures (swipe up/down/left/right) and trigger registered actions
4. Support screen freeze and word selection via overlay

**Critical implementation details:**

```kotlin
class BrainerAccessibilityService : AccessibilityService() {

    private var fingerprintGestureCallback: FingerprintGestureController.FingerprintGestureCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Register with singleton so Andropi commands can communicate
        ServiceHolder.accessibilityService = this

        // Set up fingerprint gesture listener
        setupFingerprintGestures()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Check if a capture has been requested via CaptureBuffer
        if (CaptureBuffer.captureRequested) {
            val targetPackage = CaptureBuffer.targetPackage
            val currentPackage = event.packageName?.toString()

            if (targetPackage == null || targetPackage == currentPackage) {
                performCapture(currentPackage)
                CaptureBuffer.captureRequested = false
            }
        }
    }

    private fun performCapture(packageName: String?) {
        val root = rootInActiveWindow ?: return
        val results = mutableListOf<CapturedNode>()
        traverseNodeTree(root, results)
        CaptureBuffer.storeResults(packageName, results)
        root.recycle()
    }

    private fun traverseNodeTree(node: AccessibilityNodeInfo, results: MutableList<CapturedNode>) {
        if (!node.text.isNullOrEmpty()) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            results.add(CapturedNode(
                text = node.text.toString(),
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                bounds = rect,
                clickable = node.isClickable,
                scrollable = node.isScrollable
            ))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNodeTree(child, results)
            child.recycle()
        }
    }

    private fun setupFingerprintGestures() {
        val controller = fingerprintGestureController ?: return
        if (!controller.isGestureDetectionAvailable) return

        fingerprintGestureCallback = object : FingerprintGestureController.FingerprintGestureCallback() {
            override fun onGestureDetected(gesture: Int) {
                val gestureType = when (gesture) {
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP -> "swipe_up"
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN -> "swipe_down"
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT -> "swipe_left"
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT -> "swipe_right"
                    else -> return
                }
                GestureActionRegistry.executeAction(gestureType, this@BrainerAccessibilityService)
            }
        }

        controller.registerFingerprintGestureCallback(fingerprintGestureCallback!!, null)
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceHolder.accessibilityService = null
        fingerprintGestureCallback?.let {
            fingerprintGestureController?.unregisterFingerprintGestureCallback(it)
        }
    }
}
```

### 11.2 CaptureBuffer.kt — Shared Singleton

Thread-safe singleton for communication between Andropi HTTP commands and the AccessibilityService:

```kotlin
object CaptureBuffer {
    @Volatile var captureRequested = false
    @Volatile var targetPackage: String? = null
    private val results = mutableListOf<CapturedNode>()
    private val lock = Object()
    private var resultReady = java.util.concurrent.CountDownLatch(1)

    fun requestCapture(targetPkg: String? = null): List<CapturedNode> {
        synchronized(lock) {
            results.clear()
            targetPackage = targetPkg
            resultReady = java.util.concurrent.CountDownLatch(1)
            captureRequested = true
        }
        // Wait for the accessibility service to fill results (timeout 10s)
        resultReady.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return synchronized(lock) { results.toList() }
    }

    fun storeResults(pkg: String?, capturedNodes: List<CapturedNode>) {
        synchronized(lock) {
            results.clear()
            results.addAll(capturedNodes)
            resultReady.countDown()
        }
    }
}
```

### 11.3 ServiceHolder.kt — Static Reference

```kotlin
object ServiceHolder {
    @Volatile var accessibilityService: BrainerAccessibilityService? = null
}
```

### 11.4 GestureActionRegistry.kt

Stores the mapping of fingerprint gestures → actions. Persists to SharedPreferences.

```kotlin
object GestureActionRegistry {
    private val actions = mutableMapOf<String, GestureAction>()

    fun register(gesture: String, action: GestureAction) {
        actions[gesture] = action
    }

    fun unregister(gesture: String) {
        actions.remove(gesture)
    }

    fun executeAction(gesture: String, service: BrainerAccessibilityService) {
        val action = actions[gesture] ?: return
        when (action.type) {
            "open_capture_popup" -> OverlayManager.showQuickActionsPopup(service)
            "open_app" -> launchApp(service, action.data)
            "capture_screen_text" -> CaptureBuffer.requestCapture()
            "custom_url" -> callUrl(action.data)
        }
    }
}
```

### 11.5 ScreenFreezeOverlay.kt

Uses WindowManager with TYPE_APPLICATION_OVERLAY to display a screenshot overlay. Maps tap coordinates to accessibility node bounding rectangles for word selection.

**Key implementation notes:**
- Use `AccessibilityService.takeScreenshot()` (API 30+) to capture the screen image
- Before freezing, call `rootInActiveWindow` to get the full node tree with bounding rectangles
- Store the node tree as a spatial index (list sorted by vertical position)
- On tap event, find the nearest text node whose bounding rect contains the tap point
- Highlight selected words with a semi-transparent colored rectangle drawn on the overlay
- If no node matches the tap point, crop the screenshot bitmap around the tap point and run ML Kit text recognition as fallback
- The overlay must have `FLAG_NOT_FOCUSABLE` set so the user can still interact

---

## 12. FOREGROUND SERVICE

### BrainerForegroundService.kt

- Creates a persistent notification ("Brainer is running")
- Keeps the Andropi HTTP server alive
- Monitors backend connectivity (ping `http://127.0.0.1:8080` every 30 seconds)
- If the accessibility service dies, show a notification prompting the user to re-enable it
- Uses `START_STICKY` to auto-restart if killed

### BootReceiver.kt

- On `BOOT_COMPLETED`, start `BrainerForegroundService`
- This ensures Andropi is available immediately after device restart

---

## 13. PERMISSION REQUEST FLOW

On first launch, the app must request ALL permissions. Implement in `PermissionManager.kt`:

**Phase 1 — Standard runtime permissions (single batch request):**
All dangerous permissions (camera, location, contacts, SMS, phone, calendar, microphone, storage, bluetooth, sensors, activity recognition, notifications)

**Phase 2 — Special permissions (each requires Settings navigation):**
1. `MANAGE_EXTERNAL_STORAGE` → `Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`
2. `SYSTEM_ALERT_WINDOW` → `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
3. Battery optimization exemption → `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
4. Accessibility Service → `Settings.ACTION_ACCESSIBILITY_SETTINGS`
5. Notification Listener Access (`BIND_NOTIFICATION_LISTENER_SERVICE`) → `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` (to read messages from WhatsApp/Telegram/SMS)
6. Usage Access (`PACKAGE_USAGE_STATS`) → `Settings.ACTION_USAGE_ACCESS_SETTINGS` (to monitor foreground apps and screen time)
7. Modify System Settings (`WRITE_SETTINGS`) → `Settings.ACTION_MANAGE_WRITE_SETTINGS` (to modify brightness, timeout, etc.)

**For each special permission:**
- Check if already granted
- If not, show an explanation dialog explaining WHY it's needed
- Navigate user to the correct Settings page
- On return, check if granted — if not, show toast "This feature requires [permission]. Please enable it."

**The app should show a setup wizard on first launch** that walks through all permission groups with clear explanations. After setup, show a summary of granted/denied permissions.

---

## 14. IMPORTANT DEVICE-SPECIFIC NOTES

### Oppo A79 5G Specifics:
- **Fingerprint sensor**: Side-mounted (power button). Fingerprint gesture support via `FingerprintGestureController` depends on hardware — **it may NOT support swipe gestures** on a side-mounted sensor. The app must gracefully handle `isGestureDetectionAvailable() == false` by: (a) disabling gesture features, (b) showing a message to the user, (c) offering an alternative (e.g., notification action or floating button instead).
- **Android 13 (API 33)**: Use `READ_MEDIA_IMAGES/VIDEO/AUDIO` instead of `READ_EXTERNAL_STORAGE` on API 33+.
- **ColorOS**: May have aggressive battery optimization. The app should guide the user to add Otnor to the "battery optimization whitelist" in ColorOS settings.
- **`takeScreenshot()` availability**: Available on API 30+. Since min SDK is 26, add a version check and fall back to MediaProjection on older APIs (though this device is API 33).

### General Android Compatibility Notes:
- `READ_EXTERNAL_STORAGE` is only needed for API < 33. Use `maxSdkVersion="32"`.
- `WRITE_EXTERNAL_STORAGE` is only needed for API < 30. Use `maxSdkVersion="29"`.
- Legacy Bluetooth permissions (`BLUETOOTH`, `BLUETOOTH_ADMIN`) only needed for API < 31. Use `maxSdkVersion="30"`.
- `ACCESS_BACKGROUND_LOCATION` must be requested SEPARATELY from foreground location (Android 11+ requirement).

---

## 15. CRITICAL IMPLEMENTATION RULES

1. **The Andropi HTTP server MUST only bind to 127.0.0.1**, never to 0.0.0.0. Use `new NanoHTTPD("127.0.0.1", 8081)`.

2. **CORS headers**: The Andropi server MUST add these headers to ALL responses:
   ```
   Access-Control-Allow-Origin: *
   Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
   Access-Control-Allow-Headers: Content-Type
   ```
   Also handle OPTIONS preflight requests by returning 200 with these headers.

3. **JSON everywhere**: All request bodies are `Content-Type: application/json`. All responses are JSON. Use Gson for serialization.

4. **Thread safety**: The NanoHTTPD server handles requests on worker threads. All Android UI operations (toasts, dialogs, overlays) MUST be posted to the main thread using `Handler(Looper.getMainLooper())`.

5. **Dialog commands are blocking HTTP requests**: When a dialog command is called, the HTTP response must wait until the user interacts with the dialog. Use `CountDownLatch` or `CompletableFuture` to block the NanoHTTPD worker thread until the dialog result is available.

6. **The WebView and Andropi server are independent**: The WebView loads from port 8080 (backend). Andropi runs on port 8081. They can both be active simultaneously. The WebView's JavaScript can call BOTH `window.Andropi.*` (JS bridge) AND `fetch('http://127.0.0.1:8081/andropi/v1/...')` (HTTP API).

7. **Error handling**: Never crash. Wrap every command handler in try-catch. Return error JSON on any exception.

8. **Logging**: Use `android.util.Log` with tag `"Otnor"` for general logs and `"Andropi"` for server logs. Log all incoming API requests (method + path) at DEBUG level.

---

## 16. TESTING CHECKLIST

After building, verify these work:

- [ ] App launches and shows WebView loading indicator
- [ ] If backend (port 8080) is not running, app shows "Backend offline" message or cached page
- [ ] `curl http://127.0.0.1:8081/andropi/v1/system/status` returns JSON from Termux
- [ ] `curl http://127.0.0.1:8081/andropi/v1/device/info` returns device information
- [ ] `curl http://127.0.0.1:8081/andropi/v1/clipboard/get` returns clipboard content
- [ ] `curl -X POST http://127.0.0.1:8081/andropi/v1/screen/toast -d '{"message":"Hello!","duration":"short"}'` shows a toast
- [ ] `curl -X POST http://127.0.0.1:8081/andropi/v1/screen/vibrate -d '{"duration_ms":200}'` vibrates the phone
- [ ] Accessibility service appears in Settings → Accessibility
- [ ] After enabling accessibility, `curl http://127.0.0.1:8081/andropi/v1/accessibility/status` shows `service_enabled: true`
- [ ] Text capture works when another app is in foreground
- [ ] All permissions are requested on first launch
- [ ] App survives device rotation without crashing
- [ ] Foreground notification persists ("Brainer is running")
- [ ] App auto-starts after device reboot
