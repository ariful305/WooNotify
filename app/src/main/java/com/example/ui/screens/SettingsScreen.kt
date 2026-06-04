package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import android.util.Log
import org.json.JSONObject
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.example.data.model.ApiConfigEntity
import com.example.service.OrderPollingService
import com.example.ui.ConnectionTestState
import com.example.ui.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val databaseConfig by viewModel.apiConfig.collectAsState()
    val testState by viewModel.connectionTestState.collectAsState()

    val context = LocalContext.current
    var serviceActive by remember { mutableStateOf(false) }

    var wooUrl by remember { mutableStateOf("") }
    var consumerKey by remember { mutableStateOf("") }
    var consumerSecret by remember { mutableStateOf("") }
    var syncServerUrl by remember { mutableStateOf("") }
    var autoVerify by remember { mutableStateOf(false) }

    var isSecretVisible by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }

    // Synchronize local states with Room values when loaded
    LaunchedEffect(databaseConfig) {
        wooUrl = databaseConfig.wooUrl
        consumerKey = databaseConfig.consumerKey
        consumerSecret = databaseConfig.consumerSecret
        syncServerUrl = databaseConfig.syncServerUrl
        autoVerify = databaseConfig.autoVerify
        serviceActive = OrderPollingService.isServiceRunning(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = "Verification Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Configure store endpoints and automatic verification rules",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // CONNECTION FORM
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "WooCommerce API Credentials",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            
            Button(
                onClick = { showQrScannerDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp).testTag("qr_scan_trigger")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Scan QR",
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Scan QR Setup",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = wooUrl,
                    onValueChange = { wooUrl = it },
                    label = { Text("WordPress Shop Base URL") },
                    placeholder = { Text("https://my-woocommerce-site.com") },
                    modifier = Modifier.fillMaxWidth().testTag("woo_url_input"),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true
                )

                OutlinedTextField(
                    value = consumerKey,
                    onValueChange = { consumerKey = it },
                    label = { Text("Consumer Key (ck_...)") },
                    placeholder = { Text("ck_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") },
                    modifier = Modifier.fillMaxWidth().testTag("consumer_key_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = consumerSecret,
                    onValueChange = { consumerSecret = it },
                    label = { Text("Consumer Secret (cs_...)") },
                    placeholder = { Text("cs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") },
                    modifier = Modifier.fillMaxWidth().testTag("consumer_secret_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                            Icon(
                                imageVector = if (isSecretVisible) Icons.Default.Info else Icons.Default.Lock,
                                contentDescription = "Toggle Secret Display"
                            )
                        }
                    }
                )
            }
        }

        // TELEMETRY POST URL
        Text(
            text = "Custom Verification Log Server API",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = syncServerUrl,
                    onValueChange = { syncServerUrl = it },
                    label = { Text("Audit Delivery Posting URL") },
                    placeholder = { Text("https://api.domain.com/verify-transaction") },
                    modifier = Modifier.fillMaxWidth().testTag("sync_server_url_input"),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "If set, successfully verified SMS order transactions will be POSTed to this endpoint URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // AUTO-MATCHING RULES
        Text(
            text = "Automated Engine Rules",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Instant Auto-Verify on SMS arrival",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "If enabled, incoming bKash, SSLCommerz, Nagad, or other payment SMS containing a valid txn token will immediately auto-verify pending WooCommerce orders in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = autoVerify,
                    onCheckedChange = { autoVerify = it },
                    modifier = Modifier.testTag("auto_verify_switch")
                )
            }
        }

        // BACKGROUND SERVICE TRACKER
        Text(
            text = "Background Tracker & Notifications",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Real-time WooCommerce Order Service",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Runs a high-priority persistent background service on your phone that constantly tracks new orders and alerts you instantly on receipt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (serviceActive) "Status: ACTIVE (Continuous Tracking)" else "Status: INACTIVE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (serviceActive) Color(0xFF059669) else MaterialTheme.colorScheme.error
                    )
                }
                
                Switch(
                    checked = serviceActive,
                    onCheckedChange = { active ->
                        if (active) {
                            OrderPollingService.startService(context)
                        } else {
                            OrderPollingService.stopService(context)
                        }
                        serviceActive = active
                    },
                    modifier = Modifier.testTag("service_status_switch")
                )
            }
        }

        // INLINE CONNECTION TEST STATUS
        AnimatedVisibility(
            visible = testState !is ConnectionTestState.Idle,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (testState) {
                        is ConnectionTestState.Success -> Color(0xFFD1FAE5)
                        is ConnectionTestState.Error -> Color(0xFFFEE2E2)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (testState) {
                        is ConnectionTestState.Testing -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Testing WooCommerce Connection...", fontSize = 13.sp)
                        }
                        is ConnectionTestState.Success -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = (testState as ConnectionTestState.Success).message,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF065F46)
                            )
                        }
                        is ConnectionTestState.Error -> {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = (testState as ConnectionTestState.Error).message,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF991B1B)
                            )
                        }
                        else -> {}
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (testState !is ConnectionTestState.Testing) {
                        IconButton(onClick = { viewModel.clearConnectionTestState() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // ACTION ACTIONS BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Save settings
            Button(
                onClick = {
                    val config = ApiConfigEntity(
                        wooUrl = wooUrl.trim(),
                        consumerKey = consumerKey.trim(),
                        consumerSecret = consumerSecret.trim(),
                        syncServerUrl = syncServerUrl.trim(),
                        autoVerify = autoVerify
                    )
                    viewModel.updateApiConfig(config)
                    viewModel.clearConnectionTestState()
                },
                modifier = Modifier.weight(1.3f).height(52.dp).testTag("save_settings_button"),
                shape = RoundedCornerShape(26.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }

            // Test credentials
            OutlinedButton(
                onClick = {
                    val tempConfig = ApiConfigEntity(
                        wooUrl = wooUrl.trim(),
                        consumerKey = consumerKey.trim(),
                        consumerSecret = consumerSecret.trim(),
                        syncServerUrl = syncServerUrl.trim(),
                        autoVerify = autoVerify
                    )
                    viewModel.updateApiConfig(tempConfig)
                    viewModel.testCredentials()
                },
                modifier = Modifier.weight(1f).height(52.dp).testTag("test_settings_button"),
                shape = RoundedCornerShape(26.dp),
                enabled = wooUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
            ) {
                Text("Test API Connect", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }

    // QR SCANNER AND AUTO-POPULATE DIALOG
    if (showQrScannerDialog) {
        var rawQrInput by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showQrScannerDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Instant QR Connect")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Auto-connect to WooCommerce using one of these methods:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // METHOD 1: Instant Live QR Code Scanner
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Live Camera QR Viewfinder",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Real Camera Scanner View using local on-device ML Kit
                            CameraScannerView(
                                onResult = { rawData ->
                                    val config = parseWooCommerceQr(rawData)
                                    if (config != null) {
                                        wooUrl = config.url
                                        consumerKey = config.consumerKey
                                        consumerSecret = config.consumerSecret
                                        if (config.syncServerUrl.isNotBlank()) {
                                            syncServerUrl = config.syncServerUrl
                                        }
                                        Toast.makeText(context, "Successfully applied WooCommerce credentials!", Toast.LENGTH_LONG).show()
                                        showQrScannerDialog = false
                                    } else {
                                        Toast.makeText(context, "Invalid WooCommerce configuration scan payload.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onClose = {
                                    showQrScannerDialog = false
                                }
                            )
                            
                            // Intelligent Clipboard Scanner fallback & Autofill
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            val clipboardValue = clipboardManager.getText()?.text ?: ""
                            val detectedConfig = remember(clipboardValue) { parseWooCommerceQr(clipboardValue) }
                            
                            if (detectedConfig != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFD1FAE5), shape = RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Detected",
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "WooCommerce QR detected on clipboard!",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF047857)
                                        )
                                        Text(
                                            text = detectedConfig.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF065F46)
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        wooUrl = detectedConfig.url
                                        consumerKey = detectedConfig.consumerKey
                                        consumerSecret = detectedConfig.consumerSecret
                                        if (detectedConfig.syncServerUrl.isNotBlank()) {
                                            syncServerUrl = detectedConfig.syncServerUrl
                                        }
                                        Toast.makeText(context, "Credentials applied from Clipboard!", Toast.LENGTH_SHORT).show()
                                        showQrScannerDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("clipboard_auto_fill_button")
                                ) {
                                    Text("⚡ Auto-Connect from Clipboard")
                                }
                            } else {
                                Text(
                                    text = "Point camera at WooCommerce QR setup code, or copy it to clipboard to automatically connect.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    // METHOD 2: Paste Clipboard QR payload
                    OutlinedTextField(
                        value = rawQrInput,
                        onValueChange = { rawQrInput = it },
                        label = { Text("Method 2: Paste QR Payload or Link") },
                        placeholder = { Text("Paste JSON config or link...") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("qr_paste_input"),
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    
                    Button(
                        onClick = {
                            val config = parseWooCommerceQr(rawQrInput)
                            if (config != null) {
                                wooUrl = config.url
                                consumerKey = config.consumerKey
                                consumerSecret = config.consumerSecret
                                if (config.syncServerUrl.isNotBlank()) {
                                    syncServerUrl = config.syncServerUrl
                                }
                                Toast.makeText(context, "Credentials successfully applied!", Toast.LENGTH_SHORT).show()
                                showQrScannerDialog = false
                            } else {
                                Toast.makeText(context, "Failed to parse. Please check structure.", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = rawQrInput.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("qr_apply_payload_button")
                    ) {
                        Text("Apply Clipboard QR")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // METHOD 3: Mock/Sandbox Load
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Method 3: Quick Demo Credentials",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                wooUrl = "https://demo.woonotify.com"
                                consumerKey = "ck_demo_f4c398e09f5bc3a218d6e9871ab112f4"
                                consumerSecret = "cs_demo_78c2e1047fa918b320d7d96a12b43ef8"
                                syncServerUrl = "https://demo.woonotify.com/api/audit"
                                autoVerify = true
                                Toast.makeText(context, "Demo credentials loaded!", Toast.LENGTH_SHORT).show()
                                showQrScannerDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("qr_load_demo_button")
                        ) {
                            Text("⚡ Populate Demo Credentials")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showQrScannerDialog = false },
                    modifier = Modifier.testTag("qr_dismiss_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Robust JSON & Deep Link parser for WooCommerce QR Code Connection setup
 */
fun parseWooCommerceQr(rawValue: String): WooCommerceQrConfig? {
    val trimmed = rawValue.trim()
    if (trimmed.isEmpty()) return null

    // Method 1: JSON Config
    try {
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val jsonObject = JSONObject(trimmed)
            val url = jsonObject.optString("url", jsonObject.optString("wooUrl", ""))
            val key = jsonObject.optString("consumer_key", jsonObject.optString("consumerKey", jsonObject.optString("key", "")))
            val secret = jsonObject.optString("consumer_secret", jsonObject.optString("consumerSecret", jsonObject.optString("secret", "")))
            val syncUrl = jsonObject.optString("sync_server_url", jsonObject.optString("syncServerUrl", jsonObject.optString("syncUrl", "")))
            
            if (url.isNotBlank() && key.isNotBlank() && secret.isNotBlank()) {
                return WooCommerceQrConfig(url, key, secret, syncUrl)
            }
        }
    } catch (e: Exception) {
        Log.e("SettingsScreenQrParser", "JSON parsing failed", e)
    }

    // Method 2: Schema URL Pattern (e.g. woonotify://connect?url=...&key=...&secret=...)
    try {
        if (trimmed.contains("connect?") || trimmed.contains("?") || trimmed.contains("//")) {
            val uri = android.net.Uri.parse(trimmed)
            var url = uri.getQueryParameter("url") ?: uri.getQueryParameter("wooUrl")
            var key = uri.getQueryParameter("key") ?: uri.getQueryParameter("consumer_key") ?: uri.getQueryParameter("consumerKey")
            var secret = uri.getQueryParameter("secret") ?: uri.getQueryParameter("consumer_secret") ?: uri.getQueryParameter("consumerSecret")
            var syncUrl = uri.getQueryParameter("sync_url") ?: uri.getQueryParameter("sync_server_url") ?: uri.getQueryParameter("syncServerUrl")

            if (url.isNullOrBlank() || key.isNullOrBlank() || secret.isNullOrBlank()) {
                val parts = trimmed.split("?", "&", ";")
                val params = mutableMapOf<String, String>()
                for (part in parts) {
                    val pair = part.split("=")
                    if (pair.size == 2) {
                        params[pair[0].trim().lowercase()] = pair[1].trim()
                    }
                }
                url = params["url"] ?: params["woourl"]
                key = params["key"] ?: params["consumer_key"] ?: params["consumerkey"]
                secret = params["secret"] ?: params["consumer_secret"] ?: params["consumersecret"]
                syncUrl = params["sync_url"] ?: params["sync_server_url"] ?: params["syncserverurl"]
            }

            if (!url.isNullOrBlank() && !key.isNullOrBlank() && !secret.isNullOrBlank()) {
                return WooCommerceQrConfig(
                    android.net.Uri.decode(url),
                    android.net.Uri.decode(key),
                    android.net.Uri.decode(secret),
                    syncUrl?.let { android.net.Uri.decode(it) } ?: ""
                )
            }
        }
    } catch (e: Exception) {
        Log.e("SettingsScreenQrParser", "Uri parsing failed", e)
    }

    return null
}

data class WooCommerceQrConfig(
    val url: String,
    val consumerKey: String,
    val consumerSecret: String,
    val syncServerUrl: String = ""
)

@androidx.camera.core.ExperimentalGetImage
class BarcodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let { value ->
                            onQrCodeScanned(value)
                        }
                    }
                }
                .addOnFailureListener {
                    // Failures ignored
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScannerView(
    onResult: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color.Black, shape = RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
            
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = androidx.camera.core.Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val selector = CameraSelector.DEFAULT_BACK_CAMERA
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            
                            var hasFound = false
                            imageAnalysis.setAnalyzer(executor, BarcodeAnalyzer { result ->
                                if (!hasFound) {
                                    hasFound = true
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        onResult(result)
                                    }
                                }
                            })

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("CameraScannerView", "Camera binding failed", e)
                        }
                    }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Drawing scanning box boundary over preview
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val len = 20.dp.toPx()
                val w = size.width
                val h = size.height
                val color = Color(0xFF10B981) // Grid green
                
                // Top Left
                drawLine(color, androidx.compose.ui.geometry.Offset(20.dp.toPx(), 20.dp.toPx()), androidx.compose.ui.geometry.Offset(20.dp.toPx() + len, 20.dp.toPx()), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(20.dp.toPx(), 20.dp.toPx()), androidx.compose.ui.geometry.Offset(20.dp.toPx(), 20.dp.toPx() + len), stroke)
                
                // Top Right
                drawLine(color, androidx.compose.ui.geometry.Offset(w - 20.dp.toPx(), 20.dp.toPx()), androidx.compose.ui.geometry.Offset(w - 20.dp.toPx() - len, 20.dp.toPx()), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w - 20.dp.toPx(), 20.dp.toPx()), androidx.compose.ui.geometry.Offset(w - 20.dp.toPx(), 20.dp.toPx() + len), stroke)
                
                // Bottom Left
                drawLine(color, androidx.compose.ui.geometry.Offset(20.dp.toPx(), h - 20.dp.toPx()), androidx.compose.ui.geometry.Offset(20.dp.toPx() + len, h - 20.dp.toPx()), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(20.dp.toPx(), h - 20.dp.toPx()), androidx.compose.ui.geometry.Offset(20.dp.toPx(), h - 20.dp.toPx() - len), stroke)
                
                // Bottom Right
                drawLine(color, androidx.compose.ui.geometry.Offset(w - 20.dp.toPx(), h - 20.dp.toPx()), androidx.compose.ui.geometry.Offset(w - 20.dp.toPx() - len, h - 20.dp.toPx()), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(w - 20.dp.toPx(), h - 20.dp.toPx()), androidx.compose.ui.geometry.Offset(w - 20.dp.toPx(), h - 20.dp.toPx() - len), stroke)
            }

            // Animated scanning laser line
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(2.dp)
                    .background(Color(0xFFEF4444))
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "WooNotify needs access to your camera to scan credentials.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

