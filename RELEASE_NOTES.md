# Release Notes - WooNotify v1.0.0 (Initial Release) 🚀

## Release Title: **WooNotify v1.0.0 - Real-Time SMS Bridge & Intelligent Match Engine**

We are thrilled to announce the official initial release of **WooNotify**! Designed as a robust, offline-first client-side companion for WooCommerce merchant stores, WooNotify bridges the gap between payment notification SMS and pending order fulfillment.

This initial release introduces deep-level on-device automation, dynamic Material Design 3 interfaces, and seamless camera integrations.

---

## 🌟 What's New in v1.0.0

### 📷 Live Camera QR Config Scanner
* **On-Device QR Parsing**: Integrated Google ML Kit's fully local Barcode Scanning API via CameraX.
* **Auto-Matching Formats**: Scan directly from standardized JSON credentials or deep-link URI schemas (`woonotify://connect`).
* **Intelligent Autofill**: Auto-detects configuration strings copied to the clipboard with an instant "Click to Connect" dialog banner.

### 🧠 Intelligent Matching & Verification Engine
* **Order Payment Matcher**: Scan through incoming payment confirmation messages (SMS) to compare transaction IDs, customer names, billing amounts, or mobile numbers directly with active WooCommerce pending orders.
* **Auto-Fulfillment Transition**: Auto-updates matched WooCommerce store orders to the "Processing" phase securely using WooCommerce HTTPS REST protocols.
* **Detailed Audit Histograms**: Tracks unmatched transaction references and allows manual resolution matching right within the app interface.

### 📊 Real-Time Sync & Local Storage
* **Robust Offline Backup**: Powered by a secure local **Room (SQLite)** Database to store configuration states, synchronization metadata, and audited transaction records.
* **Remote Sync Delivery**: Optionally synchronize verified payment items to your private central corporate ledger via our remote Synchronization URL webhook feature.
* **Order Polling Engine**: In-app scheduling service that regularly polls the status of orders, ensuring dashboard statistics and statuses are up-to-date.

### 🎨 Human-Centered Material 3 Design
* **Slate/Neon Visual Harmony**: Modern, eye-safe high-end dark slate typography featuring beautiful rounded card containers, neon status indicators, and sleek entry layouts.
* **Edge-to-Edge Experience**: Smooth interactive feedback, tactile button responses, and responsive scaling optimized for diverse Android dimensions.

---

## 🛠️ Performance & Security Features
* **Zero-Cloud Camera Analysis**: Frame parsing evaluates entirely on physical hardware — no images or streams ever leave your mobile device.
* **API Ingress Isolation**: Client-side cryptography keeps secure consumer credentials tightly restricted to local SQL environments.

---

## 📦 Getting Started
1. Install the built **debug package APK** (`app-debug.apk`).
2. Grant standard dynamic **Camera** and **SMS Receipt** privileges when prompted.
3. Rapid-test the application flows using the **"Populate Demo Credentials"** sandbox configuration under Settings!
