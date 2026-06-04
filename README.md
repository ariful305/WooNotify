# WooNotify 📦📱

WooNotify is a modern, offline-first Android application developed in **Kotlin** and **Jetpack Compose** designed specifically for e-commerce merchants. It provides a real-time bridge between incoming payment transaction SMS notifications and pending WooCommerce store orders, allowing seamless payment verification and automatic order status fulfillment.

---

## 🚀 Key Features

* **WooCommerce Live Order Tracker**: Real-time integration to poll, list, and search for pending or unpaid orders from your WooCommerce store.
* **On-Device QR Setup Scanner**: Instantly set up connection parameters by pointing your live camera at a WooCommerce connection QR code (powered on-device by **Google ML Kit**), or copy the payload directly to the clipboard for automated autofill.
* **Intelligent Order Matching Engine**: Compare raw SMS payment notifications (such as bKash, SSLCommerz, Nagad, Stripe alerts, or bank transaction SMS) against pending order totals, customer names, or emails to quickly identify the matching order.
* **Automatic Status Updates**: Auto-transition WooCommerce orders to a "Processing" status and record a secure system transaction audit log right when the payment is confirmed.
* **Secure Local Storage**: Uses a local **Room SQLite** database to keep credentials safe and locally back up confirmation audits.
* **Remote Admin Verification Logs**: Synchronize verified order logs seamlessly with your private external audit server via a custom Synchronization URL API.

---

## 🛠️ Tech Stack & Design Architecture

WooNotify is built with modern Android development standards:

* **Jetpack Compose**: 100% declarative UI leveraging **Material Design 3 (M3)** with a dark aesthetic, edge-to-edge layout, and adaptive density.
* **CameraX & Google ML Kit Barcode Scanning**: Full integrated live-viewfinder camera scanner to detect and parse connection QR codes with modern permissions checking (`Accompanist Permissions`).
* **Kotlin Coroutines & Flow**: Reactive data processing with clean, lightweight concurrency.
* **Room Database**: Structured persistence layer for application configurations, received transaction logs, and local synchronization states.
* **Retrofit & OkHttp**: Fully functional WooCommerce REST API clients supporting basic authentication hashes.

---

## 📂 Project Structure

```text
/app/src/main/java/com/example/
├── data/
│   ├── database/     # AppDatabase, ConfigDao, LogDao definitions
│   ├── model/        # Order Entities, ApiConfigEntity, LogEntity
│   └── network/      # Retrofit api clients for WooCommerce API Integration
├── service/          # OrderPollingService running in-app background routines
├── ui/
│   ├── theme/        # Central Color scheme, Shape definitions, Typography
│   └── screens/      # Jetpack Compose visual interfaces:
│       ├── DashboardScreen.kt   # Order stats, polling state, connection health
│       ├── SmsScreen.kt         # SMS transaction histories and received notifications
│       ├── MatchEngineScreen.kt # Payment ID matcher and success animations
│       ├── LogsScreen.kt        # Local verification audit log synchronization
│       └── SettingsScreen.kt    # Manual settings, QR camera viewfinder, demo loader
└── MainActivity.kt        # Edge-to-edge composition entrypoint & scaffold tabs
```

---

## 📱 WooCommerce Connections & Setup

Setting up connection parameters is incredibly versatile! Merchant configurations can be set up using any of these three methods under **Settings**:

### 1. Instant QR Setup Scanner
Point the live camera scanner at a QR code containing your WooCommerce endpoint, keys, and sync server data. The QR code must use one of the two formats below:

#### Option A: JSON Configuration Payload
```json
{
  "url": "https://yourstore.com",
  "consumer_key": "ck_f4c398e09f5bc3a218d6e9871ab112f4...",
  "consumer_secret": "cs_78c2e1047fa918b320d7d96a12b43ef8...",
  "sync_server_url": "https://yourserver.com/api/audit"
}
```

#### Option B: Deep Link Protocol
```text
woonotify://connect?url=https://yourstore.com&key=ck_...&secret=cs_...&sync_url=https://yourserver.com/api/audit
```

### 2. Clipboard Auto-Detector
If you have copied either the JSON configuration or the deep-link connection pattern above, WooNotify will showcase an automated **autofill banner** in the connection dialog. Simply tap **"Auto-Connect from Clipboard"** to automatically populate your parameters.

### 3. Quick Demo Sandbox Mode
Want to test-drive features immediately? Tap **"Populate Demo Credentials"** in the Settings menu to load sample data and interface instantly with simulated WooCommerce orders.

---

## 🛠️ How to Build and Run the App

### Requirements
* **Android Studio Ladybug (or newer)**
* **Java SDK 17**
* **Gradle 8+**

### Local Build Commands
To compile and assemble the installer package (`.apk`):

```bash
# Compile and build Kotlin sources
gradle :app:compileDebugKotlin

# Build debug APK installer
gradle :app:assembleDebug
```

After building is complete, your generated debug APK will be situated inside the output folder:
`/app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Permissions & Security Notice
* **Camera Access (`android.permission.CAMERA`)**: Only requested when configuring settings using the live QR Code Reader. Camera handles all frame analysis fully on-device.
* **SMS Listeners (`android.permission.RECEIVE_SMS` / `android.permission.READ_SMS`)**: Utilized strictly to detect inbound gateway confirmation codes for auto-resolving invoices inside the core billing matching engine. No visual contact messages are shared or uploaded to the cloud without permission.

---

## 📄 License
This project is licensed under the **MIT License** - see the [LICENSE](./LICENSE) file for additional details.
