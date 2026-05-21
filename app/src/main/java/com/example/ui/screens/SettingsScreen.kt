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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ApiConfigEntity
import com.example.ui.ConnectionTestState
import com.example.ui.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val databaseConfig by viewModel.apiConfig.collectAsState()
    val testState by viewModel.connectionTestState.collectAsState()

    var wooUrl by remember { mutableStateOf("") }
    var consumerKey by remember { mutableStateOf("") }
    var consumerSecret by remember { mutableStateOf("") }
    var syncServerUrl by remember { mutableStateOf("") }
    var autoVerify by remember { mutableStateOf(false) }

    var isSecretVisible by remember { mutableStateOf(false) }

    // Synchronize local states with Room values when loaded
    LaunchedEffect(databaseConfig) {
        wooUrl = databaseConfig.wooUrl
        consumerKey = databaseConfig.consumerKey
        consumerSecret = databaseConfig.consumerSecret
        syncServerUrl = databaseConfig.syncServerUrl
        autoVerify = databaseConfig.autoVerify
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
        Text(
            text = "WooCommerce APi Credentials",
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
}
