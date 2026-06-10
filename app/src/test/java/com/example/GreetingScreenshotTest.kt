package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.ApiConfigEntity
import com.example.data.model.CachedOrderEntity
import com.example.data.model.VerifyLogEntity
import com.example.ui.MainViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var viewModel: MainViewModel
  private lateinit var database: AppDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    // Initialize the database with beautiful, realistic mock data for our screenshots
    database = AppDatabase.getDatabase(context)

    runBlocking {
      database.apiConfigDao().saveConfig(
        ApiConfigEntity(
          id = 1,
          wooUrl = "https://demo.woonotify.com",
          consumerKey = "ck_demo_f4c398e09f5bc3a218d6e9871ab112f4",
          consumerSecret = "cs_demo_78c2e1047fa918b320d7d96a12b43ef8",
          syncServerUrl = "https://demo.woonotify.com/api/audit",
          autoVerify = true,
          senderFilter = "bKash,Nagad,16247,SSLCommerz"
        )
      )

      database.cachedOrderDao().insertCachedOrders(
        listOf(
          CachedOrderEntity(
            id = 1042L,
            number = "1042",
            status = "pending",
            total = "1500.00",
            currency = "BDT",
            dateCreated = "2026-06-10T08:00:00",
            paymentMethod = "bkash",
            paymentMethodTitle = "bKash Mobile Wallet",
            transactionId = "TXN9876543210",
            customerName = "Ariful Islam",
            customerEmail = "mdarifulislam3579@gmail.com",
            customerPhone = "+8801700000000",
            itemsSummary = "1x WooNotify Premium License Key",
            rawJson = "{}"
          ),
          CachedOrderEntity(
            id = 1041L,
            number = "1041",
            status = "on-hold",
            total = "65.50",
            currency = "USD",
            dateCreated = "2026-06-10T07:30:00",
            paymentMethod = "stripe",
            paymentMethodTitle = "Credit/Debit Card",
            transactionId = "ch_3Mv8Y2LkdIwXbvG61L",
            customerName = "Jessica Harlow",
            customerEmail = "jessica.h@example.com",
            customerPhone = "+15550199211",
            itemsSummary = "2x E-Commerce Sync Plugins",
            rawJson = "{}"
          )
        )
      )

      database.verifyLogDao().insertLog(
        VerifyLogEntity(
          id = 1L,
          orderId = 1039L,
          orderNumber = "1039",
          customerName = "Zayn Malik",
          orderTotal = "420.00",
          orderTransactionId = "NAGAD554433",
          smsTransactionId = "NAGAD554433",
          smsSender = "Nagad",
          smsBody = "Nagad Cash-In of BDT 420.00 received. TxId: NAGAD554433",
          verificationStatus = "VERIFIED",
          wooUpdated = true,
          serverSynced = true,
          serverResponse = "OK",
          timestamp = System.currentTimeMillis() - 7200000
        )
      )
    }

    viewModel = MainViewModel(context)
  }

  @Test
  fun test_1_dashboard_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        DashboardScreen(
          viewModel = viewModel,
          onNavigateToMatch = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/1_dashboard.png")
  }

  @Test
  fun test_2_match_workshop_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MatchEngineScreen(
          viewModel = viewModel,
          onNavigateToLogs = {},
          onNavigateToOrders = {},
          onNavigateToSms = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/2_match_workshop.png")
  }

  @Test
  fun test_3_logs_history_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        LogsScreen(
          viewModel = viewModel
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/3_logs_history.png")
  }

  @Test
  fun test_4_settings_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        SettingsScreen(
          viewModel = viewModel
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/4_settings.png")
  }
}
