package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest

sealed class Screen(val title: String, val icon: ImageVector) {
    object Dashboard : Screen("Orders", Icons.Default.ShoppingCart)
    object SmsInbox : Screen("SMS Inbox", Icons.Default.Email)
    object MatchEngine : Screen("Workshop", Icons.Default.CheckCircle)
    object Logs : Screen("History", Icons.Default.Info)
    object Settings : Screen("Config", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = "android.permission.POST_NOTIFICATIONS"
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }
        
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
                val snackbarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current

                // Listen to reactive event bus toast notifications
                LaunchedEffect(Unit) {
                    viewModel.notificationMessage.collectLatest { msg ->
                        snackbarHostState.showSnackbar(
                            message = msg,
                            actionLabel = "Dismiss",
                            duration = SnackbarDuration.Short
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = when (currentScreen) {
                                        Screen.Dashboard -> "Order Verification"
                                        Screen.SmsInbox -> "Local SMS Inbox"
                                        Screen.MatchEngine -> "Match Workshop"
                                        Screen.Logs -> "Verification Audit Logs"
                                        Screen.Settings -> "Verification Settings"
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 22.sp
                                    )
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground
                            ),
                            navigationIcon = {
                                if (currentScreen != Screen.Dashboard) {
                                    IconButton(onClick = { currentScreen = Screen.Dashboard }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Go back",
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 8.dp
                        ) {
                            val items = listOf(
                                Screen.Dashboard,
                                Screen.SmsInbox,
                                Screen.MatchEngine,
                                Screen.Logs,
                                Screen.Settings
                            )
                            items.forEach { screen ->
                                NavigationBarItem(
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen },
                                    label = { Text(screen.title) },
                                    icon = {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title
                                        )
                                    }
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (currentScreen) {
                            Screen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateToMatch = { currentScreen = Screen.MatchEngine }
                                )
                            }
                            Screen.SmsInbox -> {
                                SmsScreen(
                                    viewModel = viewModel,
                                    onNavigateToMatch = { currentScreen = Screen.MatchEngine }
                                )
                            }
                            Screen.MatchEngine -> {
                                MatchEngineScreen(
                                    viewModel = viewModel,
                                    onNavigateToLogs = { currentScreen = Screen.Logs },
                                    onNavigateToOrders = { currentScreen = Screen.Dashboard },
                                    onNavigateToSms = { currentScreen = Screen.SmsInbox }
                                )
                            }
                            Screen.Logs -> {
                                LogsScreen(
                                    viewModel = viewModel
                                )
                            }
                            Screen.Settings -> {
                                SettingsScreen(
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
