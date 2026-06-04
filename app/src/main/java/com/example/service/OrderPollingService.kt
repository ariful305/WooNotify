package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.repository.VerifierRepository
import kotlinx.coroutines.*

class OrderPollingService : Service() {

    private val TAG = "OrderPollingService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var pollingJob: Job? = null

    private lateinit var repository: VerifierRepository
    private val seenOrderIds = mutableSetOf<Long>()
    private var isFirstFetch = true

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 8888
        private const val LIVE_ORDER_NOTIFICATION_ID_OFFSET = 10000
        private const val CHANNEL_SERVICE_ID = "order_tracker_service_channel"
        private const val CHANNEL_NOTIFICATIONS_ID = "woo_order_notifications"

        fun startService(context: Context) {
            val intent = Intent(context, OrderPollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OrderPollingService::class.java)
            context.stopService(intent)
        }

        @Suppress("DEPRECATION")
        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (OrderPollingService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OrderPollingService onCreate")
        
        val db = AppDatabase.getDatabase(applicationContext)
        repository = VerifierRepository(db.apiConfigDao(), db.verifyLogDao(), db.cachedOrderDao())
        
        createNotificationChannels()
        showServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OrderPollingService started")
        
        // Ensure service notification is displayed
        showServiceNotification()
        
        // Start polling coroutine loop
        startOrderPollingLoop()
        
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "OrderPollingService onDestroy")
        pollingJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel 1: Persistent Service indicator (Low importance to not make noise)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Real-time WooCommerce Order Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps real-time WooCommerce order tracking active in background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // Channel 2: High priority Push Alerts for incoming WooCommerce orders
            val pulseChannel = NotificationChannel(
                CHANNEL_NOTIFICATIONS_ID,
                "WooCommerce Order Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sends urgent alerts for newly received WooCommerce orders on this phone"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(pulseChannel)
        }
    }

    private fun showServiceNotification() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle("Real-time Order Tracker")
            .setContentText("WooCommerce polling service is active on your phone.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun startOrderPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val config = repository.getConfig()
                    if (config != null && config.isValid) {
                        Log.d(TAG, "Polling WooCommerce orders in background...")
                        val orders = repository.fetchOrders(status = "pending,on-hold,processing")
                        
                        if (isFirstFetch) {
                            // Suppress alerts for existing historical orders on first start
                            orders.forEach { seenOrderIds.add(it.id) }
                            isFirstFetch = false
                            Log.d(TAG, "Suppressed ${orders.size} pre-existing orders on startup.")
                        } else {
                            // Find and notify on truly new WooCommerce orders
                            for (order in orders) {
                                if (!seenOrderIds.contains(order.id)) {
                                    seenOrderIds.add(order.id)
                                    val transactionId = order.transactionId.ifBlank { "N/A" }
                                    sendPushNotification(order.id, order.number, transactionId)
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "Order Polling Service: Api credentials config is invalid or empty. Waiting...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background polling exception encounter: ${e.message}", e)
                }
                
                // Poll every 15 seconds to ensure fast real-time notifications on order receipt
                delay(15000)
            }
        }
    }

    private fun sendPushNotification(orderId: Long, orderNumber: String, transactionId: String) {
        Log.d(TAG, "Sending Push notification for new WooCommerce order: $orderNumber")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            orderId.toInt(),
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val message = "New Order Received. Transaction ID: $transactionId. Please verify."

        val notification = NotificationCompat.Builder(this, CHANNEL_NOTIFICATIONS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Order #$orderNumber")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(LIVE_ORDER_NOTIFICATION_ID_OFFSET + orderId.toInt(), notification)
    }
}
