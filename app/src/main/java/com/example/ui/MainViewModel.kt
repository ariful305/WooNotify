package com.example.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.database.AppDatabase
import com.example.data.model.ApiConfigEntity
import com.example.data.model.SmsMsg
import com.example.data.model.VerifyLogEntity
import com.example.data.model.WooCommerceOrder
import com.example.data.repository.VerifierRepository
import com.example.util.SmsArrivalEventBus
import com.example.util.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OrdersUiState {
    object Idle : OrdersUiState
    object Loading : OrdersUiState
    data class Success(val orders: List<WooCommerceOrder>) : OrdersUiState
    data class Error(val message: String) : OrdersUiState
}

sealed interface ConnectionTestState {
    object Idle : ConnectionTestState
    object Testing : ConnectionTestState
    data class Success(val message: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}

sealed interface MatchActionState {
    object Idle : MatchActionState
    object Processing : MatchActionState
    data class Success(val log: VerifyLogEntity) : MatchActionState
    data class Error(val message: String) : MatchActionState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    private val db = AppDatabase.getDatabase(application)
    private val repository = VerifierRepository(db.apiConfigDao(), db.verifyLogDao())

    private val seenOrderIds = mutableSetOf<Long>()
    private var isFirstOrderFetch = true

    // UI Configuration State
    val apiConfig: StateFlow<ApiConfigEntity> = repository.configFlow
        .map { it ?: ApiConfigEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ApiConfigEntity())

    // WooCommerce Orders State
    private val _ordersUiState = MutableStateFlow<OrdersUiState>(OrdersUiState.Idle)
    val ordersUiState: StateFlow<OrdersUiState> = _ordersUiState.asStateFlow()

    private val _ordersList = MutableStateFlow<List<WooCommerceOrder>>(emptyList())
    val ordersList: StateFlow<List<WooCommerceOrder>> = _ordersList.asStateFlow()

    // SMS Inbox State
    private val _smsList = MutableStateFlow<List<SmsMsg>>(emptyList())
    val smsList: StateFlow<List<SmsMsg>> = _smsList.asStateFlow()

    private val _smsPermissionGranted = MutableStateFlow(false)
    val smsPermissionGranted: StateFlow<Boolean> = _smsPermissionGranted.asStateFlow()

    // Verification Audit Logs
    val verificationLogs: StateFlow<List<VerifyLogEntity>> = repository.allLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Interactive Verification Workspace State
    private val _selectedOrderForVerify = MutableStateFlow<WooCommerceOrder?>(null)
    val selectedOrderForVerify: StateFlow<WooCommerceOrder?> = _selectedOrderForVerify.asStateFlow()

    private val _selectedSmsForVerify = MutableStateFlow<SmsMsg?>(null)
    val selectedSmsForVerify: StateFlow<SmsMsg?> = _selectedSmsForVerify.asStateFlow()

    private val _matchActionState = MutableStateFlow<MatchActionState>(MatchActionState.Idle)
    val matchActionState: StateFlow<MatchActionState> = _matchActionState.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    // Live Instant Notifications (Broadcasting matched arriving SMS to UI)
    private val _notificationMessage = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val notificationMessage: SharedFlow<String> = _notificationMessage.asSharedFlow()

    init {
        // Observe live incoming SMS messages from the broadcast receiver bus!
        viewModelScope.launch {
            SmsArrivalEventBus.incomingSmsFlow.collect { incomingSms ->
                Log.d(TAG, "Shared event bus captured SMS from: ${incomingSms.sender}")
                // Add to head of list
                _smsList.update { list -> listOf(incomingSms) + list }

                // Check for Auto-Verify triggers!
                checkInstantAutoMatch(incomingSms)
            }
        }
        
        // Start polling WooCommerce for new orders in real-time
        startOrderPolling()
    }

    /**
     * Set whether the user has approved READ_SMS permissions.
     */
    fun setSmsPermissionGranted(granted: Boolean) {
        _smsPermissionGranted.value = granted
        if (granted) {
            refreshSmsInbox()
        }
    }

    /**
     * Reads past SMS inbox messages.
     */
    fun refreshSmsInbox() {
        if (!_smsPermissionGranted.value) return
        viewModelScope.launch {
            val inbox = withContext(Dispatchers.IO) {
                SmsReader.readInbox(getApplication(), limit = 80)
            }
            _smsList.value = inbox
        }
    }

    /**
     * Saves user configuration modifications into database.
     */
    fun updateApiConfig(config: ApiConfigEntity) {
        viewModelScope.launch {
            repository.saveConfig(config)
        }
    }

    /**
     * Tests connectivity to the configured WooCommerce REST interface.
     */
    fun testCredentials() {
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            try {
                val orders = repository.fetchOrders()
                _connectionTestState.value = ConnectionTestState.Success(
                    "Connected successfully! Retrieved ${orders.size} open orders."
                )
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState.Error(
                    "Connection failed: ${e.localizedMessage ?: "Please confirm endpoint and credentials keys details"}"
                )
            }
        }
    }

    fun clearConnectionTestState() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    /**
     * Pulls open / pending orders from WooCommerce.
     */
    fun refreshOrders(statusFilter: String? = "pending,on-hold,processing") {
        viewModelScope.launch {
            _ordersUiState.value = OrdersUiState.Loading
            try {
                val orders = repository.fetchOrders(status = statusFilter)
                _ordersList.value = orders
                _ordersUiState.value = OrdersUiState.Success(orders)
            } catch (e: Exception) {
                _ordersUiState.value = OrdersUiState.Error(
                    e.localizedMessage ?: "Failed to contact WooCommerce store"
                )
            }
        }
    }

    /**
     * Selection handles in workshop
     */
    fun selectOrderForVerification(order: WooCommerceOrder?) {
        _selectedOrderForVerify.value = order
    }

    fun selectSmsForVerification(sms: SmsMsg?) {
        _selectedSmsForVerify.value = sms
    }

    fun resetMatchActionState() {
        _matchActionState.value = MatchActionState.Idle
    }

    /**
     * Active Verification trigger. Matches the selected order with a specified transaction ID.
     */
    fun executeMatching(
        order: WooCommerceOrder,
        smsTxnId: String,
        smsSender: String,
        smsBody: String,
        updateWooCommerce: Boolean,
        syncCustomServer: Boolean
    ) {
        viewModelScope.launch {
            _matchActionState.value = MatchActionState.Processing
            try {
                val logItem = repository.verifyAndSync(
                    order = order,
                    smsTxnId = smsTxnId,
                    smsSender = smsSender,
                    smsBody = smsBody,
                    updateWooCommerce = updateWooCommerce,
                    syncCustomServer = syncCustomServer
                )
                
                _matchActionState.value = MatchActionState.Success(logItem)
                _notificationMessage.tryEmit("Order #${order.number} successfully verified for txn: $smsTxnId")

                // Update loaded list to remove or update order
                _ordersList.update { currentList ->
                    currentList.filter { it.id != order.id }
                }
                
                if (_selectedOrderForVerify.value?.id == order.id) {
                    _selectedOrderForVerify.value = null
                }
                if (_selectedSmsForVerify.value?.body?.contains(smsTxnId) == true) {
                    _selectedSmsForVerify.value = null
                }

            } catch (e: Exception) {
                _matchActionState.value = MatchActionState.Error(e.localizedMessage ?: "Verification pipeline failure")
            }
        }
    }

    /**
     * Checks an incoming SMS on arrival to see if there is an active matching WooCommerce order
     */
    private suspend fun checkInstantAutoMatch(incomingSms: SmsMsg) {
        val config = repository.getConfig() ?: return
        if (!config.autoVerify) return

        if (incomingSms.parsedTxnIds.isEmpty()) return

        // Fetch current orders if empty
        var currentOrdersList = _ordersList.value
        if (currentOrdersList.isEmpty()) {
            try {
                currentOrdersList = repository.fetchOrders(status = "pending,on-hold")
                _ordersList.value = currentOrdersList
            } catch (e: Exception) {
                Log.e(TAG, "Failed background order fetch for auto-match: ${e.message}")
                return
            }
        }

        for (order in currentOrdersList) {
            val orderTxID = order.transactionId.trim().uppercase()
            if (orderTxID.isNotBlank()) {
                for (smsTxnId in incomingSms.parsedTxnIds) {
                    if (orderTxID == smsTxnId.trim().uppercase()) {
                        // Check if already processed
                        val isProcessed = repository.hasTransactionBeenProcessed(smsTxnId)
                        if (!isProcessed) {
                            Log.d(TAG, "Instant Auto-Match detected Order #${order.number} <-> SMS TxToken: $smsTxnId!")
                            
                            // Automatically run verification!
                            try {
                                repository.verifyAndSync(
                                    order = order,
                                    smsTxnId = smsTxnId,
                                    smsSender = incomingSms.sender,
                                    smsBody = incomingSms.body,
                                    updateWooCommerce = true,
                                    syncCustomServer = config.syncServerUrl.isNotBlank()
                                )
                                
                                _notificationMessage.tryEmit("AutoV: Order #${order.number} automatically verified with Txn: $smsTxnId!")
                                
                                // Clean up local list
                                _ordersList.update { list -> list.filter { it.id != order.id } }
                            } catch (e: Exception) {
                                Log.e(TAG, "Instant auto match sync failed: ${e.message}")
                            }
                            return // Processed matched pairs
                        }
                    }
                }
            }
        }
    }

    /**
     * Purges match audit history from db
     */
    fun clearMatchLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    /**
     * Start periodic polling of WooCommerce orders to trigger real-time notifications for newly created orders.
     */
    private fun startOrderPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val config = repository.getConfig()
                    if (config != null && config.isValid) {
                        val orders = repository.fetchOrders(status = "pending,on-hold,processing")
                        if (isFirstOrderFetch) {
                            // On first run, initialize our set of seen order IDs so we only alert on newly created ones
                            orders.forEach { seenOrderIds.add(it.id) }
                            isFirstOrderFetch = false
                        } else {
                            // On subsequent polls, detect newly created orders
                            for (order in orders) {
                                if (!seenOrderIds.contains(order.id)) {
                                    seenOrderIds.add(order.id)
                                    val transactionId = order.transactionId.ifBlank { "N/A" }
                                    sendLocalNotification(order.id, order.number, transactionId)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background order polling encountered exception: ${e.message}")
                }
                // Poll every 15 seconds
                kotlinx.coroutines.delay(15000)
            }
        }
    }

    /**
     * Sends a local notification representing a real-time push event for newly received WooCommerce orders.
     */
    private fun sendLocalNotification(orderId: Long, orderNumber: String, transactionId: String) {
        val context = getApplication<Application>()
        val channelId = "woo_order_notifications"
        val channelName = "WooCommerce Order Notifications"
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for newly received WooCommerce orders"
                enableLights(true)
                lightColor = android.graphics.Color.MAGENTA
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val notifyIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            orderId.toInt(),
            notifyIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val message = "New Order Received. Transaction ID: $transactionId. Please verify."
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Order #$orderNumber")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        notificationManager.notify(orderId.toInt(), builder.build())
    }
}
