package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.MatchActionState

@Composable
fun MatchEngineScreen(
    viewModel: MainViewModel,
    onNavigateToLogs: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToSms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedOrder by viewModel.selectedOrderForVerify.collectAsState()
    val selectedSms by viewModel.selectedSmsForVerify.collectAsState()
    val matchState by viewModel.matchActionState.collectAsState()
    val config by viewModel.apiConfig.collectAsState()

    var manualTxId by remember { mutableStateOf("") }
    var updateWooCommerceSelected by remember { mutableStateOf(true) }
    var syncCustomServerSelected by remember { mutableStateOf(true) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var showSmsSelectDialog by remember { mutableStateOf(false) }
    val smsList by viewModel.smsList.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshSmsInbox()
    }

    // Derive target transaction ID to use
    val orderTxId = selectedOrder?.transactionId?.trim() ?: ""
    
    // Choose selected SMS ID or what is manually entered
    val finalSmsTxId = remember(selectedSms, manualTxId) {
        if (selectedSms != null && selectedSms!!.parsedTxnIds.isNotEmpty()) {
            selectedSms!!.parsedTxnIds.first()
        } else {
            manualTxId.trim()
        }
    }

    // Is there a correlation match?
    val matchStatus = remember(orderTxId, finalSmsTxId) {
        if (orderTxId.isBlank() || finalSmsTxId.isBlank()) {
            "AWAITING_FIELDS"
        } else if (orderTxId.equals(finalSmsTxId, ignoreCase = true)) {
            "MATCHED"
        } else {
            "MISMATCH"
        }
    }

    // React to matching success to show custom dialog and reset
    LaunchedEffect(matchState) {
        if (matchState is MatchActionState.Success) {
            showSuccessDialog = true
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false
                viewModel.resetMatchActionState()
                onNavigateToLogs()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verification Completed")
                }
            },
            text = {
                Text(
                    text = "The transaction verification was successfully processed.\n\n" +
                            "• WooCommerce Order updated: YES\n" +
                            "• Reporting Server synced: ${if (config.syncServerUrl.isNotBlank()) "YES" else "SKIPPED"}"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.resetMatchActionState()
                        onNavigateToLogs()
                    }
                ) {
                    Text("View Audit History")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("match_engine_screen")
    ) {
        Text(
            text = "Match Workshop & Verify",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. ORDER BLOCK
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                1.dp,
                if (selectedOrder != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step 1: WooCommerce Order selection",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    TextButton(
                        onClick = { onNavigateToOrders() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (selectedOrder != null) "Change Order" else "Select Order")
                    }
                }

                if (selectedOrder == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "No WooCommerce Order Selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val order = selectedOrder!!
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Order #${order.number}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${order.currency} ${order.total}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        Text(
                            text = "Customer: ${order.billing.fullName} (${order.billing.phone})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Order transaction token",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (order.transactionId.isNotBlank()) 
                                    "Customer Input TxID: ${order.transactionId}" 
                                else "Warning: Customer didn't supply TxID label in the checkout",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 2. CORRESPONDING TRANSACTION BLOCK
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                1.dp,
                if (selectedSms != null || manualTxId.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step 2: Payment SMS ID correlation",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    TextButton(
                        onClick = { showSmsSelectDialog = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (selectedSms != null) "Change SMS" else "Select SMS")
                    }
                }

                if (selectedSms == null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Manual Entry instead
                        OutlinedTextField(
                            value = manualTxId,
                            onValueChange = { manualTxId = it },
                            label = { Text("Or Type Transaction ID Manually") },
                            placeholder = { Text("e.g. 8K2J8Z9X") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_transaction_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "Or choose 'Select SMS' above to pull a parsed SMS from your phone inbox.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val sms = selectedSms!!
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SMS Sender: ${sms.sender}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { viewModel.selectSmsForVerification(null) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear SMS choice", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        
                        Text(
                            text = sms.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (sms.parsedTxnIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Auto-Parsed ID: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = sms.parsedTxnIds.first(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 12.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. DECISION BOARD - COMPARATIVE MATCHING ANALYTICS
        if (selectedOrder != null && (selectedSms != null || manualTxId.isNotBlank())) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (matchStatus) {
                        "MATCHED" -> Color(0xFFD1FAE5)     // green-50
                        "MISMATCH" -> Color(0xFFFEE2E2)    // red-50
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(
                    1.dp,
                    when (matchStatus) {
                        "MATCHED" -> Color(0xFF10B981).copy(alpha = 0.4f)
                        "MISMATCH" -> Color(0xFFEF4444).copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Match Validation Analysis",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = when (matchStatus) {
                            "MATCHED" -> Color(0xFF065F46)
                            "MISMATCH" -> Color(0xFF991B1B)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("WooCommerce Input", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (orderTxId.isNotBlank()) orderTxId else "MISSING",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (orderTxId.isNotBlank()) Color.Black else Color.Gray
                            )
                        }

                        Icon(
                            imageVector = if (matchStatus == "MATCHED") Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Analysis",
                            tint = when (matchStatus) {
                                "MATCHED" -> Color(0xFF059669)
                                "MISMATCH" -> Color(0xFFDC2626)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(32.dp)
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Mobile SMS / Input", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = finalSmsTxId,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (matchStatus) {
                            "MATCHED" -> {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "VERIFICATION MATCH CONFIRMED (100% Correlation)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF065F46)
                                )
                            }
                            else -> {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "CHARACTERS MISMATCH DETECTED. PLEASE INSPECT VALUES CAREFULLY.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Toggles / Options config
        Text(
            text = "Workflow Options",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = updateWooCommerceSelected,
                        onCheckedChange = { updateWooCommerceSelected = it }
                    )
                    Column {
                        Text(
                            text = "Auto-update WooCommerce order status",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Changes Status of WooCommerce Order #${selectedOrder?.number ?: ""} to PROCESSING and writes verified note in WordPress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = syncCustomServerSelected,
                        onCheckedChange = { syncCustomServerSelected = it },
                        enabled = config.syncServerUrl.isNotBlank()
                    )
                    Column {
                        Text(
                            text = "Sync details to custom reporting server API",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (config.syncServerUrl.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (config.syncServerUrl.isNotBlank()) 
                                "Sends verification audit payload directly via POST to ${config.syncServerUrl}" 
                            else "Configuration required (Go to settings to write custom reporting POST endpoint URL).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Action Buttons
        Button(
            onClick = {
                val order = selectedOrder
                if (order != null) {
                    viewModel.executeMatching(
                        order = order,
                        smsTxnId = finalSmsTxId,
                        smsSender = selectedSms?.sender ?: "Manual Entry",
                        smsBody = selectedSms?.body ?: "Manually authenticated via workshop dashboard UI.",
                        updateWooCommerce = updateWooCommerceSelected,
                        syncCustomServer = syncCustomServerSelected
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("execute_verification_button"),
            enabled = selectedOrder != null && finalSmsTxId.isNotBlank() && matchState !is MatchActionState.Processing,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (matchStatus == "MATCHED") Color(0xFF059669) else MaterialTheme.colorScheme.primary
            )
        ) {
            if (matchState is MatchActionState.Processing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (matchStatus == "MATCHED") "Verify & Sync Match (Confirmed)" else "Override Match & Save Regardless",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        if (matchState is MatchActionState.Error) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = (matchState as MatchActionState.Error).message,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // INLINE SMS SELECT DIALOG
        if (showSmsSelectDialog) {
            AlertDialog(
                onDismissRequest = { showSmsSelectDialog = false },
                title = { Text("Select Payment SMS") },
                text = {
                    Box(modifier = Modifier.heightIn(max = 350.dp)) {
                        if (smsList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No SMS messages found in phone inbox. Verify read permissions are granted.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(smsList) { sms ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectSmsForVerification(sms)
                                                showSmsSelectDialog = false
                                            },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = sms.sender,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                val readableDate = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(sms.timestamp))
                                                Text(
                                                    text = readableDate,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = sms.body,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSmsSelectDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
