package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.BusinessRepository
import com.example.data.attemptAutoSignIn
import com.example.data.authStateFlow
import com.example.data.signOut
import com.example.domain.UiState
import com.example.domain.UserRoleState
import com.example.ui.BusinessViewModel
import com.example.ui.components.QuotaWarningBanner
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AdminSettingsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BillsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LoansScreen
import com.example.ui.screens.OwnerUidVerificationScreen
import com.example.ui.screens.PartnersScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.TxFormMode
import com.example.ui.screens.UnauthorizedScreen
import com.example.ui.theme.BusinessManagerTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class AppDestination(
    val route: String,
    val labelBn: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Filled.Dashboard),
    TRANSACTIONS("transactions", "লেনদেন", Icons.Filled.SwapHoriz),
    ACCOUNTS("accounts", "Accounts", Icons.Filled.AccountBalanceWallet),
    PARTNERS("partners", "Partners", Icons.Filled.Group),
    LOANS("loans", "Loans", Icons.Filled.Payments),
    BILLS("bills", "Bills", Icons.Filled.CreditCard),
    EXPENSES("expenses", "Expenses", Icons.Filled.ReceiptLong),
    HISTORY("history", "History", Icons.Filled.History),
    REPORTS("reports", "Reports", Icons.Filled.Assessment),
    ADMIN_SETTINGS("admin_settings", "Admin Settings", Icons.Filled.AdminPanelSettings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BusinessManagerTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: BusinessViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        BusinessViewModel(BusinessRepository(db, auth), uid)
                    }
                }
            )
            AuthenticatedAppShell(
                viewModel = viewModel,
                onSignOut = {
                    signOut(credentialManager, scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedAppShell(
    viewModel: BusinessViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val roleState by viewModel.roleState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val quotaWarning by viewModel.quotaExceededWarning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    when (val role = roleState) {
        is UserRoleState.Checking -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Firebase ভূমিকা যাচাই হচ্ছে...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is UserRoleState.OwnerNeedsUidVerification -> {
            OwnerUidVerificationScreen(
                currentUid = role.currentUid,
                email = role.email,
                storedOwnerUid = role.storedOwnerUid,
                isBusy = isBusy,
                onConfirmBindUid = { viewModel.verifyAndBindOwnerUid() },
                onSignOut = onSignOut
            )
        }
        is UserRoleState.Unauthorized -> {
            UnauthorizedScreen(
                uid = role.uid,
                email = role.email,
                reason = role.reason,
                onRetry = { viewModel.refreshRole() },
                onSignOut = onSignOut
            )
        }
        is UserRoleState.VerifiedOwner, is UserRoleState.ActiveEmployee -> {
            val isOwner = role is UserRoleState.VerifiedOwner
            val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
            val partnersState by viewModel.partnersState.collectAsStateWithLifecycle()
            val txsState by viewModel.transactionsState.collectAsStateWithLifecycle()
            val pendingState by viewModel.pendingTransactionsState.collectAsStateWithLifecycle()
            val loansState by viewModel.loansState.collectAsStateWithLifecycle()
            val employeesState by viewModel.employeesState.collectAsStateWithLifecycle()
            val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
            val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()

            val accounts = (accountsState as? UiState.Success)?.data ?: emptyList()
            val partners = (partnersState as? UiState.Success)?.data ?: emptyList()
            val transactions = (txsState as? UiState.Success)?.data ?: emptyList()
            val pendingTxs = (pendingState as? UiState.Success)?.data ?: emptyList()
            val loans = (loansState as? UiState.Success)?.data ?: emptyList()
            val employees = (employeesState as? UiState.Success)?.data ?: emptyList()
            val auditLogs = (auditLogsState as? UiState.Success)?.data ?: emptyList()

            val destinations = remember(isOwner) {
                if (isOwner) {
                    AppDestination.entries
                } else {
                    listOf(
                        AppDestination.TRANSACTIONS,
                        AppDestination.PARTNERS,
                        AppDestination.BILLS,
                        AppDestination.EXPENSES,
                        AppDestination.ACCOUNTS
                    )
                }
            }

            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route ?: destinations.first().route
            val currentDest = AppDestination.entries.firstOrNull { it.route == currentRoute } ?: destinations.first()

            var txInitialMode by remember { mutableStateOf(TxFormMode.CUSTOMER) }
            var selectedAccountId by remember { mutableStateOf<String?>(null) }
            var showMoreSheet by remember { mutableStateOf(false) }

            fun navigateTo(dest: AppDestination) {
                navController.navigate(dest.route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }

            if (showMoreSheet) {
                BackHandler { showMoreSheet = false }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 680.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(12.dp))
                            destinations.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentRoute == dest.route,
                                    onClick = { navigateTo(dest) },
                                    icon = { Icon(dest.icon, contentDescription = dest.labelBn) },
                                    label = { Text(dest.labelBn, maxLines = 1) },
                                    modifier = Modifier.testTag("rail_${dest.route}")
                                )
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = currentDest.labelBn,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isOwner) "Owner • Business Manager Pro" else "Employee (${(role as UserRoleState.ActiveEmployee).name})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                actions = {
                                    if (!isOwner) {
                                        IconButton(onClick = onSignOut) {
                                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "সাইন আউট")
                                        }
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            if (!useNavigationRail) {
                                val phoneTabs = if (isOwner) {
                                    listOf(
                                        AppDestination.DASHBOARD,
                                        AppDestination.TRANSACTIONS,
                                        AppDestination.ACCOUNTS,
                                        AppDestination.REPORTS
                                    )
                                } else {
                                    listOf(
                                        AppDestination.TRANSACTIONS,
                                        AppDestination.PARTNERS,
                                        AppDestination.BILLS,
                                        AppDestination.EXPENSES
                                    )
                                }
                                NavigationBar {
                                    phoneTabs.forEach { dest ->
                                        NavigationBarItem(
                                            selected = currentRoute == dest.route,
                                            onClick = { navigateTo(dest) },
                                            icon = { Icon(dest.icon, contentDescription = dest.labelBn) },
                                            label = { Text(dest.labelBn, maxLines = 1) },
                                            modifier = Modifier.testTag("nav_${dest.route}")
                                        )
                                    }
                                    if (isOwner) {
                                        NavigationBarItem(
                                            selected = currentDest !in phoneTabs,
                                            onClick = { showMoreSheet = true },
                                            icon = { Icon(Icons.Filled.Apps, contentDescription = "আরও") },
                                            label = { Text("আরও", maxLines = 1) },
                                            modifier = Modifier.testTag("nav_more")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            QuotaWarningBanner(visible = quotaWarning)

                            NavHost(
                                navController = navController,
                                startDestination = destinations.first().route,
                                modifier = Modifier.weight(1f)
                            ) {
                                composable(AppDestination.DASHBOARD.route) {
                                    DashboardScreen(
                                        summary = summary,
                                        accounts = accounts,
                                        pendingTransactions = pendingTxs,
                                        isOwner = isOwner,
                                        onOpenBalanceAdd = {
                                            txInitialMode = TxFormMode.BALANCE_ADD
                                            navigateTo(AppDestination.TRANSACTIONS)
                                        },
                                        onOpenBalanceWithdraw = {
                                            txInitialMode = TxFormMode.BALANCE_WITHDRAW
                                            navigateTo(AppDestination.TRANSACTIONS)
                                        },
                                        onOpenCustomerTx = {
                                            txInitialMode = TxFormMode.CUSTOMER
                                            navigateTo(AppDestination.TRANSACTIONS)
                                        },
                                        onOpenPendingQueue = {
                                            txInitialMode = TxFormMode.PENDING_QUEUE
                                            navigateTo(AppDestination.TRANSACTIONS)
                                        },
                                        onOpenAccountDetail = { acc ->
                                            selectedAccountId = acc.id
                                            navigateTo(AppDestination.ACCOUNTS)
                                        }
                                    )
                                }

                                composable(AppDestination.TRANSACTIONS.route) {
                                    TransactionsScreen(
                                        accounts = accounts,
                                        pendingTransactions = pendingTxs,
                                        isOwner = isOwner,
                                        isBusy = isBusy,
                                        initialMode = txInitialMode,
                                        onSubmitTx = { draft, onDone -> viewModel.submitTransaction(draft, onDone) },
                                        onApprovePending = { viewModel.approvePending(it) },
                                        onRejectPending = { id, reason -> viewModel.rejectPending(id, reason) }
                                    )
                                }

                                composable(AppDestination.ACCOUNTS.route) {
                                    AccountsScreen(
                                        accounts = accounts,
                                        transactions = transactions,
                                        isOwner = isOwner,
                                        selectedAccountId = selectedAccountId,
                                        onSelectAccount = { selectedAccountId = it },
                                        onOpenBalanceAdd = {
                                            txInitialMode = TxFormMode.BALANCE_ADD
                                            navigateTo(AppDestination.TRANSACTIONS)
                                        },
                                        onOpenBalanceWithdraw = {
                                            txInitialMode = TxFormMode.BALANCE_WITHDRAW
                                            navigateTo(AppDestination.TRANSACTIONS)
                                        },
                                        onOpenTransfer = {
                                            txInitialMode = TxFormMode.TRANSFER
                                            navigateTo(AppDestination.TRANSACTIONS)
                                        }
                                    )
                                }

                                composable(AppDestination.PARTNERS.route) {
                                    PartnersScreen(
                                        partners = partners,
                                        accounts = accounts,
                                        isOwner = isOwner,
                                        isBusy = isBusy,
                                        onSubmitTx = { draft, onDone -> viewModel.submitTransaction(draft, onDone) },
                                        onUpdatePartnerCommission = { id, rate -> viewModel.updatePartnerCommission(id, rate) }
                                    )
                                }

                                composable(AppDestination.LOANS.route) {
                                    LoansScreen(
                                        loans = loans,
                                        accounts = accounts,
                                        isBusy = isBusy,
                                        observeRepayments = { viewModel.observeLoanRepayments(it) },
                                        onCreateLoan = { d, n, p, a, pr, i, s, du, no, done ->
                                            viewModel.createLoan(d, n, p, a, pr, i, s, du, no, done)
                                        },
                                        onRecordRepayment = { l, pp, ip, a, dt, no, done ->
                                            viewModel.recordLoanRepayment(l, pp, ip, a, dt, no, done)
                                        }
                                    )
                                }

                                composable(AppDestination.BILLS.route) {
                                    BillsScreen(
                                        accounts = accounts,
                                        isOwner = isOwner,
                                        isBusy = isBusy,
                                        onSubmitTx = { draft, onDone -> viewModel.submitTransaction(draft, onDone) }
                                    )
                                }

                                composable(AppDestination.EXPENSES.route) {
                                    ExpensesScreen(
                                        accounts = accounts,
                                        isOwner = isOwner,
                                        isBusy = isBusy,
                                        onSubmitTx = { draft, onDone -> viewModel.submitTransaction(draft, onDone) }
                                    )
                                }

                                composable(AppDestination.HISTORY.route) {
                                    HistoryScreen(
                                        transactions = transactions,
                                        auditLogs = auditLogs,
                                        accounts = accounts,
                                        isBusy = isBusy,
                                        onReverseTx = { tx, reason, done -> viewModel.reverseTransaction(tx, reason, done) }
                                    )
                                }

                                composable(AppDestination.REPORTS.route) {
                                    ReportsScreen(
                                        accounts = accounts,
                                        partners = partners,
                                        transactions = transactions,
                                        pendingTransactions = pendingTxs,
                                        loans = loans,
                                        onNotify = { msg ->
                                            // Show snackbar via coroutine
                                        }
                                    )
                                }

                                composable(AppDestination.ADMIN_SETTINGS.route) {
                                    val ownerRole = role as? UserRoleState.VerifiedOwner
                                    AdminSettingsScreen(
                                        ownerUid = ownerRole?.uid ?: "",
                                        ownerEmail = ownerRole?.email ?: "",
                                        employees = employees,
                                        isBusy = isBusy,
                                        onReverifyUid = { viewModel.verifyAndBindOwnerUid() },
                                        onAddEmployee = { n, e, done -> viewModel.addEmployee(n, e, done) },
                                        onToggleEmployeeActive = { viewModel.toggleEmployeeActive(it) },
                                        onRemoveEmployee = { viewModel.removeEmployee(it) },
                                        onBuildBackupJson = { viewModel.buildBackupJson() },
                                        onRestoreBackup = { raw -> viewModel.restoreBackup(context, raw) },
                                        onNotify = {},
                                        onSignOut = onSignOut
                                    )
                                }
                            }
                        }
                    }
                }

                if (showMoreSheet && isOwner) {
                    ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "সকল মডিউল",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val extraDestinations = listOf(
                                AppDestination.PARTNERS,
                                AppDestination.LOANS,
                                AppDestination.BILLS,
                                AppDestination.EXPENSES,
                                AppDestination.HISTORY,
                                AppDestination.ADMIN_SETTINGS
                            )
                            extraDestinations.forEach { dest ->
                                Surface(
                                    onClick = {
                                        showMoreSheet = false
                                        navigateTo(dest)
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (currentRoute == dest.route) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("more_${dest.route}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(dest.icon, contentDescription = dest.labelBn, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(14.dp))
                                        Text(dest.labelBn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
