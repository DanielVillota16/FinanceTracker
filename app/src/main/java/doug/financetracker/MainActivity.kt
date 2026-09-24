package doug.financetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import doug.financetracker.ui.navigation.Routes
import doug.financetracker.ui.screens.addtransaction.AddTransactionScreen
import doug.financetracker.ui.screens.pending.PendingScreen
import doug.financetracker.ui.screens.settings.SettingsScreen
import doug.financetracker.ui.screens.transactions.TransactionsScreen
import doug.financetracker.ui.theme.FinanceTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceTrackerTheme {
                FinanceTrackerRoot()
            }
        }
    }
}

private enum class TopDestination(
    val label: String,
    val icon: ImageVector,
    val route: String
) {
    TRANSACTIONS("Transactions", Icons.Filled.Home, Routes.TRANSACTIONS),
    PENDING("Pending", Icons.Filled.Inbox, Routes.PENDING),
    ADD("Add", Icons.Filled.Add, "add_transaction?editId=-1&pendingId=-1"),
    SETTINGS("Settings", Icons.Filled.Settings, Routes.SETTINGS)
}

@Composable
fun FinanceTrackerRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val snackbar = remember { SnackbarHostState() }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopDestination.entries.forEach { dest ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route?.substringBefore("?") == dest.route.substringBefore("?")
                } == true
                item(
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                    selected = selected,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.TRANSACTIONS,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.TRANSACTIONS) {
                    TransactionsScreen(
                        onAdd = { navController.navigate(Routes.addTransaction()) },
                        onEdit = { id -> navController.navigate(Routes.addTransaction(id)) }
                    )
                }
                composable(Routes.PENDING) {
                    PendingScreen(
                        onEdit = { pendingId ->
                            navController.navigate(Routes.pendingEdit(pendingId))
                        },
                        snackbar = snackbar
                    )
                }
                composable(
                    route = Routes.ADD_TRANSACTION,
                    arguments = listOf(
                        navArgument("editId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("pendingId") { type = NavType.LongType; defaultValue = -1L }
                    )
                ) { entry ->
                    val editId = entry.arguments?.getLong("editId")?.takeIf { it >= 0 }
                    val pendingId = entry.arguments?.getLong("pendingId")?.takeIf { it >= 0 }
                    AddTransactionScreen(
                        editId = editId,
                        pendingId = pendingId,
                        onDone = {
                            navController.popBackStack(
                                Routes.TRANSACTIONS,
                                inclusive = false,
                                saveState = false
                            )
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenPending = { navController.navigate(Routes.PENDING) }
                    )
                }
            }
        }
    }
}
