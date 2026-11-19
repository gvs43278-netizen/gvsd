package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackupRestoreManager
import com.example.data.BusinessRepository
import com.example.data.isQuotaExceededError
import com.example.data.toBengaliFirestoreMessage
import com.example.domain.Account
import com.example.domain.AccountingEngine
import com.example.domain.AuditLog
import com.example.domain.DashboardSummary
import com.example.domain.EmployeeAccess
import com.example.domain.LedgerTransaction
import com.example.domain.LoanRecord
import com.example.domain.LoanRepayment
import com.example.domain.Partner
import com.example.domain.PendingTransaction
import com.example.domain.TxDraft
import com.example.domain.UiState
import com.example.domain.UserRoleState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class BusinessViewModel(
    private val repository: BusinessRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _roleState = MutableStateFlow<UserRoleState>(UserRoleState.Checking)
    val roleState: StateFlow<UserRoleState> = _roleState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _quotaExceededWarning = MutableStateFlow(false)
    val quotaExceededWarning: StateFlow<Boolean> = _quotaExceededWarning.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        refreshRole()
    }

    fun refreshRole() {
        viewModelScope.launch {
            _roleState.value = UserRoleState.Checking
            _roleState.value = repository.resolveUserRole()
        }
    }

    fun verifyAndBindOwnerUid() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.verifyAndBindOwnerUid()
            _isBusy.value = false
            res.onSuccess {
                _events.emit("Owner UID নিরাপদভাবে যাচাই ও সংরক্ষণ করা হয়েছে।")
                refreshRole()
            }.onFailure { err ->
                if (isQuotaExceededError(err)) _quotaExceededWarning.value = true
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    val accountsState: StateFlow<UiState<List<Account>>> = _roleState.flatMapLatest { role ->
        when (role) {
            is UserRoleState.VerifiedOwner, is UserRoleState.ActiveEmployee ->
                repository.observeAccounts().toUiStateFlow("accounts")
            else -> flowOf(UiState.Success(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val partnersState: StateFlow<UiState<List<Partner>>> = _roleState.flatMapLatest { role ->
        when (role) {
            is UserRoleState.VerifiedOwner, is UserRoleState.ActiveEmployee ->
                repository.observePartners().toUiStateFlow("partners")
            else -> flowOf(UiState.Success(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val transactionsState: StateFlow<UiState<List<LedgerTransaction>>> = _roleState.flatMapLatest { role ->
        when (role) {
            is UserRoleState.VerifiedOwner -> repository.observeTransactions().toUiStateFlow("transactions")
            else -> flowOf(UiState.Success(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val pendingTransactionsState: StateFlow<UiState<List<PendingTransaction>>> = _roleState.flatMapLatest { role ->
        when (role) {
            is UserRoleState.VerifiedOwner -> repository.observeAllPendingTransactions().toUiStateFlow("pending_transactions")
            is UserRoleState.ActiveEmployee -> repository.observeMyPendingTransactions().toUiStateFlow("pending_transactions")
            else -> flowOf(UiState.Success(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val loansState: StateFlow<UiState<List<LoanRecord>>> = _roleState.flatMapLatest { role ->
        when (role) {
            is UserRoleState.VerifiedOwner -> repository.observeLoans().toUiStateFlow("loans")
            else -> flowOf(UiState.Success(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val employeesState: StateFlow<UiState<List<EmployeeAccess>>> = _roleState.flatMapLatest { role ->
        when (role) {
            is UserRoleState.VerifiedOwner -> repository.observeEmployees().toUiStateFlow("employee_access")
            else -> flowOf(UiState.Success(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> = _roleState.flatMapLatest { role ->
        when (role) {
            is UserRoleState.VerifiedOwner -> repository.observeAuditLogs().toUiStateFlow("audit_logs")
            else -> flowOf(UiState.Success(emptyList()))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        accountsState,
        transactionsState
    ) { accState, txState ->
        val accs = (accState as? UiState.Success)?.data ?: emptyList()
        val txs = (txState as? UiState.Success)?.data ?: emptyList()
        AccountingEngine.computeDashboardSummary(accs, txs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardSummary())

    fun observeLoanRepayments(loanId: String): Flow<List<LoanRepayment>> {
        return repository.observeLoanRepayments(loanId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
    }

    // --- ACTIONS ---

    fun submitTransaction(draft: TxDraft, onSuccess: () -> Unit = {}) {
        val role = _roleState.value
        viewModelScope.launch {
            _isBusy.value = true
            val result: Result<String> = when (role) {
                is UserRoleState.VerifiedOwner -> {
                    repository.executeOwnerDirectTransaction(draft, submitterName = "Owner")
                }
                is UserRoleState.ActiveEmployee -> {
                    repository.submitEmployeePendingTransaction(draft, submitterName = role.name)
                }
                else -> Result.failure(IllegalStateException("অনুমোদিত ভূমিকা পাওয়া যায়নি।"))
            }
            _isBusy.value = false
            result.onSuccess {
                val msg = if (role is UserRoleState.ActiveEmployee) {
                    "লেনদেনটি অনুমোদনের জন্য Pending তালিকায় পাঠানো হয়েছে।"
                } else {
                    "লেনদেন সফলভাবে সম্পন্ন ও সংরক্ষণ হয়েছে।"
                }
                _events.emit(msg)
                onSuccess()
            }.onFailure { err ->
                if (isQuotaExceededError(err)) _quotaExceededWarning.value = true
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun approvePending(pending: PendingTransaction) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.approvePendingTransaction(pending)
            _isBusy.value = false
            res.onSuccess {
                _events.emit("Pending লেনদেন অনুমোদন ও ব্যালেন্স আপডেট হয়েছে।")
            }.onFailure { err ->
                if (isQuotaExceededError(err)) _quotaExceededWarning.value = true
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun rejectPending(pendingId: String, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.rejectPendingTransaction(pendingId, reason.ifBlank { "Owner কর্তৃক বাতিল" })
            _isBusy.value = false
            res.onSuccess {
                _events.emit("Pending লেনদেন বাতিল করা হয়েছে।")
            }.onFailure { err ->
                if (isQuotaExceededError(err)) _quotaExceededWarning.value = true
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun reverseTransaction(tx: LedgerTransaction, reason: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.reverseTransaction(tx, reason)
            _isBusy.value = false
            res.onSuccess {
                _events.emit("লেনদেন সফলভাবে Reversal করা হয়েছে।")
                onSuccess()
            }.onFailure { err ->
                if (isQuotaExceededError(err)) _quotaExceededWarning.value = true
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun createLoan(
        direction: String,
        personName: String,
        phone: String,
        accountId: String,
        principalPaisa: Long,
        interestPaisa: Long,
        startDateMillis: Long,
        dueDateMillis: Long,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.createLoan(
                direction, personName, phone, accountId,
                principalPaisa, interestPaisa, startDateMillis, dueDateMillis, note
            )
            _isBusy.value = false
            res.onSuccess {
                _events.emit("ঋণের হিসাব সফলভাবে সংরক্ষিত হয়েছে।")
                onSuccess()
            }.onFailure { err ->
                if (isQuotaExceededError(err)) _quotaExceededWarning.value = true
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun recordLoanRepayment(
        loan: LoanRecord,
        principalPayPaisa: Long,
        interestPayPaisa: Long,
        accountId: String,
        repaymentDateMillis: Long,
        note: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.recordLoanRepayment(
                loan, principalPayPaisa, interestPayPaisa, accountId, repaymentDateMillis, note
            )
            _isBusy.value = false
            res.onSuccess {
                _events.emit("ঋণ পরিশোধ সফলভাবে যুক্ত হয়েছে।")
                onSuccess()
            }.onFailure { err ->
                if (isQuotaExceededError(err)) _quotaExceededWarning.value = true
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun updatePartnerCommission(partnerId: String, perThousandPaisa: Long) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updatePartnerCommissionRate(partnerId, perThousandPaisa)
            _isBusy.value = false
            res.onSuccess {
                _events.emit("পার্টনার কমিশন রেট আপডেট হয়েছে।")
            }.onFailure { err ->
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun addEmployee(name: String, email: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.addEmployee(name, email)
            _isBusy.value = false
            res.onSuccess {
                _events.emit("কর্মচারী সফলভাবে যুক্ত হয়েছে।")
                onSuccess()
            }.onFailure { err ->
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun toggleEmployeeActive(employee: EmployeeAccess) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.toggleEmployeeActive(employee)
            _isBusy.value = false
            res.onSuccess {
                _events.emit("কর্মচারীর অবস্থা পরিবর্তন করা হয়েছে।")
            }.onFailure { err ->
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun removeEmployee(email: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.removeEmployee(email)
            _isBusy.value = false
            res.onSuccess {
                _events.emit("কর্মচারী বাদ দেওয়া হয়েছে।")
            }.onFailure { err ->
                _events.emit(toBengaliFirestoreMessage(err))
            }
        }
    }

    fun buildBackupJson(): String {
        val accs = (accountsState.value as? UiState.Success)?.data ?: emptyList()
        val parts = (partnersState.value as? UiState.Success)?.data ?: emptyList()
        val txs = (transactionsState.value as? UiState.Success)?.data ?: emptyList()
        val loans = (loansState.value as? UiState.Success)?.data ?: emptyList()
        val emps = (employeesState.value as? UiState.Success)?.data ?: emptyList()
        return BackupRestoreManager.buildBackupJson(accs, parts, txs, loans, emps)
    }

    fun restoreBackup(context: Context, rawJson: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val accs = (accountsState.value as? UiState.Success)?.data ?: emptyList()
            val parts = (partnersState.value as? UiState.Success)?.data ?: emptyList()
            val txs = (transactionsState.value as? UiState.Success)?.data ?: emptyList()
            val loans = (loansState.value as? UiState.Success)?.data ?: emptyList()
            val emps = (employeesState.value as? UiState.Success)?.data ?: emptyList()

            val res = BackupRestoreManager.createSafetyBackupAndRestore(
                context = context,
                repository = repository,
                currentAccounts = accs,
                currentPartners = parts,
                currentTransactions = txs,
                currentLoans = loans,
                currentEmployees = emps,
                backupJsonText = rawJson
            )
            _isBusy.value = false
            res.onSuccess { safetyFile ->
                _events.emit("ব্যাকআপ রিস্টোর সফল! পূর্বে Safety Backup ($safetyFile) সংরক্ষণ করা হয়েছে।")
            }.onFailure { err ->
                _events.emit(err.localizedMessage ?: "ভুল বা অসম্পূর্ণ ব্যাকআপ ফাইল।")
            }
        }
    }

    private fun <T> Flow<List<T>>.toUiStateFlow(tagName: String): Flow<UiState<List<T>>> {
        return this
            .map<List<T>, UiState<List<T>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w("BusinessVM", "Error observing $tagName", rootError)
                val quota = isQuotaExceededError(rootError)
                if (quota) _quotaExceededWarning.value = true
                emit(UiState.Error(toBengaliFirestoreMessage(rootError), isQuotaExceeded = quota))
            }
    }
}
