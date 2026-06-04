package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.model.WooCommerceOrder
import com.example.ui.MainViewModel
import com.example.ui.OrdersUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToMatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ordersState by viewModel.ordersUiState.collectAsState()
    val ordersList by viewModel.ordersList.collectAsState()
    val config by viewModel.apiConfig.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showOnlyWithTxId by remember { mutableStateOf(false) }

    // Tab state: 0 = Recent Orders, 1 = All Orders
    var selectedTab by remember { mutableStateOf(0) }
    
    // Details Popup State
    var selectedOrderForDetails by remember { mutableStateOf<WooCommerceOrder?>(null) }
    var showStatusDropdown by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Trigger initial refresh in background silently if config is valid to populate DB without blocking UI
    LaunchedEffect(config) {
        if (config.isValid && ordersList.isEmpty()) {
            // Fetch pending queue on launch
            viewModel.refreshOrders(statusFilter = null) // status null = fetch all to populate cache quickly
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("dashboard_screen")
    ) {
        // Warning Banner if API config is missing
        if (!config.isValid) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Config Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "API Credentials Missing",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Please go to 'API Config' tab and set up your WooCommerce Store URL and Consumer keys.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Stats Row
        val totalOrdersCount = ordersList.size
        val withTxIdCount = ordersList.count { it.transactionId.isNotBlank() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsCard(
                title = "Cached Total",
                count = totalOrdersCount.toString(),
                icon = Icons.Default.ShoppingCart,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )

            StatsCard(
                title = "Has TxID Token",
                count = withTxIdCount.toString(),
                icon = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )
        }

        // TAB BAR (Recent Orders vs All Orders)
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(bottom = 12.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recent Queue", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Synced (${ordersList.size})", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        // Search and Actions Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search number, name, phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            IconButton(
                onClick = {
                    // Refreshes the order items based on selected context tab
                    if (selectedTab == 0) {
                        viewModel.refreshOrders(statusFilter = "pending,on-hold,processing")
                    } else {
                        viewModel.refreshOrders(statusFilter = null)
                    }
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .testTag("refresh_orders_button"),
                enabled = config.isValid && ordersState !is OrdersUiState.Loading
            ) {
                if (ordersState is OrdersUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Manual sync pull")
                }
            }
        }

        // Filter Checkbox row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = showOnlyWithTxId,
                onCheckedChange = { showOnlyWithTxId = it }
            )
            Text(
                text = "Show only orders containing customer TxID",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { showOnlyWithTxId = !showOnlyWithTxId }
            )
        }

        // Filtering calculation
        val filteredOrders = remember(selectedTab, ordersList, searchQuery, showOnlyWithTxId) {
            ordersList.filter { order ->
                // Apply Tab constraint
                val statusMatches = if (selectedTab == 0) {
                    order.status.equals("pending", true) ||
                    order.status.equals("on-hold", true) ||
                    order.status.equals("processing", true)
                } else {
                    true // All Orders tab displays everything
                }

                // Apply search parameters
                val numMatches = order.number.contains(searchQuery, ignoreCase = true)
                val nameMatches = order.billing.fullName.contains(searchQuery, ignoreCase = true)
                val phoneMatches = order.billing.phone.contains(searchQuery, ignoreCase = true)
                val searchMatches = searchQuery.isBlank() || numMatches || nameMatches || phoneMatches

                // Apply TxID checkbox filter
                val txidFilter = !showOnlyWithTxId || order.transactionId.isNotBlank()

                statusMatches && searchMatches && txidFilter
            }
        }

        // List Header label
        Text(
            text = if (selectedTab == 0) "Action-Required Queue (${filteredOrders.size})" else "All Stored Client Orders (${filteredOrders.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No orders displayable",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank() || showOnlyWithTxId)
                            "Try clearing searches or checkboxes"
                        else "All actions are verified, synced, or the WooCommerce pending queue is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = Alignment.CenterHorizontally.let { TextAlign.Center },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    OrderCardItem(
                        order = order,
                        onSelectForVerify = {
                            viewModel.selectOrderForVerification(order)
                            onNavigateToMatch()
                        },
                        onCardClicked = {
                            selectedOrderForDetails = order
                        }
                    )
                }
            }
        }
    }

    // ORDER DETAIL DIALOG & WooCommerce STATUS CHANGER
    selectedOrderForDetails?.let { order ->
        AlertDialog(
            onDismissRequest = { selectedOrderForDetails = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Order Details #${order.number}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (order.status.lowercase()) {
                                    "completed" -> Color(0xFF10B981)
                                    "processing" -> Color(0xFF3B82F6)
                                    "on-hold" -> Color(0xFFF59E0B)
                                    "pending" -> Color(0xFFEF4444)
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = order.status.uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider()

                    // Customer information
                    Text("Customer Profile", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailRow(label = "Name:", value = order.billing.fullName)
                        DetailRow(label = "Email:", value = order.billing.email.ifBlank { "N/A" })
                        DetailRow(label = "Phone:", value = order.billing.phone.ifBlank { "N/A" })
                        DetailRow(label = "Address:", value = "${order.billing.address1}, ${order.billing.city}")
                    }

                    HorizontalDivider()

                    // Payment details
                    Text("Transaction & Payment", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DetailRow(label = "Method:", value = order.paymentMethodTitle)
                        DetailRow(
                            label = "User TxID:",
                            value = order.transactionId.ifBlank { "None supplied" },
                            isMonospace = true,
                            valueColor = if (order.transactionId.isNotBlank()) Color(0xFF0369A1) else Color.Red
                        )
                        DetailRow(label = "Date:", value = order.dateCreated)
                    }

                    HorizontalDivider()

                    // Items summary list
                    Text("Purchased Items", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        order.lineItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.quantity}x ${item.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${order.currency} ${item.total}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Checkout Price:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("${order.currency} ${order.total}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider()

                    // Change Status Action Drawer
                    Text("Change WooCommerce Order Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showStatusDropdown = !showStatusDropdown },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select New Status")
                            }
                        }
                    }

                    if (showStatusDropdown) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val statuses = listOf("pending", "on-hold", "processing", "completed", "cancelled")
                            statuses.forEach { status ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateOrderStatus(order.id, status)
                                            showStatusDropdown = false
                                            selectedOrderForDetails = null
                                        },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = status.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedOrderForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = if (isMonospace) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (isMonospace) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatsCard(
    title: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun OrderCardItem(
    order: WooCommerceOrder,
    onSelectForVerify: () -> Unit,
    onCardClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClicked() },
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Order #${order.number}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Customer: ${order.billing.fullName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Indicator and Total Tag in Row
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${order.currency} ${order.total}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (order.status.lowercase()) {
                                    "completed" -> Color(0xFFD1FAE5)
                                    "processing" -> Color(0xFFDBEAFE)
                                    "on-hold" -> Color(0xFFFEF3C7)
                                    "pending" -> Color(0xFFFEE2E2)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = order.status.uppercase(),
                            color = when (order.status.lowercase()) {
                                "completed" -> Color(0xFF065F46)
                                "processing" -> Color(0xFF1E40AF)
                                "on-hold" -> Color(0xFF92400E)
                                "pending" -> Color(0xFF991B1B)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Line items descriptions
            val itemsSummary = order.lineItems.joinToString(", ") { "${it.quantity}x ${it.name}" }
            Text(
                text = "Items: $itemsSummary",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Customer entered transaction ID matching tag
                if (order.transactionId.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "TxID Key Found",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "TxID: ${order.transactionId}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Missing local key",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = "No TxID Specified",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }

                // Match Button CTA
                Button(
                    onClick = {
                        onSelectForVerify()
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (order.transactionId.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (order.transactionId.isNotBlank())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Match",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
