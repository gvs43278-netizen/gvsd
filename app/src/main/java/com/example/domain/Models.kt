package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object BusinessConstants {
    const val OWNER_EMAIL = "ashrafmahmudsany2@gmail.com"
    const val LEGACY_SUPER_ADMIN_UID = "yRuPuAgCe2V2Y8avgK9U8n4sTgj1"

    const val ACC_CASH = "cash"
    const val ACC_BKASH_1 = "bkash_1"
    const val ACC_BKASH_2 = "bkash_2"
    const val ACC_BKASH_3 = "bkash_3"
    const val ACC_BKASH_4 = "bkash_4"
    const val ACC_BKASH_5 = "bkash_5"
    const val ACC_NAGAD = "nagad"
    const val ACC_ROCKET = "rocket"
    const val ACC_UPAY = "upay"
    const val ACC_UCB_BANK = "ucb_bank"
    const val ACC_EASY_RECHARGE = "easy_recharge"

    const val PARTNER_SAMAD = "samad_bhai"
    const val PARTNER_JAHIR = "jahir_mama"
    const val PARTNER_ARIFUL = "ariful_islam"

    val DEFAULT_ACCOUNTS: List<Account> = listOf(
        Account(id = ACC_CASH, name = "Cash", category = "CASH", lastFourDigits = "", balancePaisa = 0L, sortOrder = 1L),
        Account(id = ACC_BKASH_1, name = "bKash-1", category = "BKASH", lastFourDigits = "0198", balancePaisa = 0L, sortOrder = 2L),
        Account(id = ACC_BKASH_2, name = "bKash-2", category = "BKASH", lastFourDigits = "4557", balancePaisa = 0L, sortOrder = 3L),
        Account(id = ACC_BKASH_3, name = "bKash-3", category = "BKASH", lastFourDigits = "9927", balancePaisa = 0L, sortOrder = 4L),
        Account(id = ACC_BKASH_4, name = "bKash-4", category = "BKASH", lastFourDigits = "3094", balancePaisa = 0L, sortOrder = 5L),
        Account(id = ACC_BKASH_5, name = "bKash-5", category = "BKASH", lastFourDigits = "2452", balancePaisa = 0L, sortOrder = 6L),
        Account(id = ACC_NAGAD, name = "Nagad", category = "NAGAD", lastFourDigits = "", balancePaisa = 0L, sortOrder = 7L),
        Account(id = ACC_ROCKET, name = "Rocket", category = "ROCKET", lastFourDigits = "", balancePaisa = 0L, sortOrder = 8L),
        Account(id = ACC_UPAY, name = "Upay", category = "UPAY", lastFourDigits = "", balancePaisa = 0L, sortOrder = 9L),
        Account(id = ACC_UCB_BANK, name = "UCB Bank", category = "BANK", lastFourDigits = "", balancePaisa = 0L, sortOrder = 10L),
        Account(id = ACC_EASY_RECHARGE, name = "Easy Recharge", category = "RECHARGE", lastFourDigits = "", balancePaisa = 0L, sortOrder = 11L)
    )

    val DEFAULT_PARTNERS: List<Partner> = listOf(
        Partner(
            id = PARTNER_SAMAD,
            name = "Samad Bhai",
            commissionPerThousandPaisa = 500L, // ৳5 per 1000
            bKashOnlyCommission = true,
            canCustomizeCommission = false
        ),
        Partner(
            id = PARTNER_JAHIR,
            name = "Jahir Mama",
            commissionPerThousandPaisa = 500L, // ৳5 per 1000
            bKashOnlyCommission = true,
            canCustomizeCommission = false
        ),
        Partner(
            id = PARTNER_ARIFUL,
            name = "Ariful Islam",
            commissionPerThousandPaisa = 0L, // Default ৳0, Owner customizable
            bKashOnlyCommission = false,
            canCustomizeCommission = true
        )
    )

    val TELECOM_OPERATORS = listOf("Grameenphone", "Robi", "Banglalink", "Airtel", "Teletalk")
}

object TxType {
    const val BALANCE_ADD = "BALANCE_ADD"
    const val BALANCE_WITHDRAW = "BALANCE_WITHDRAW"
    const val CUSTOMER_TX = "CUSTOMER_TX"
    const val INTERNAL_TRANSFER = "INTERNAL_TRANSFER"
    const val MOBILE_RECHARGE = "MOBILE_RECHARGE"
    const val PARTNER_SEND = "PARTNER_SEND"
    const val PARTNER_RECEIVE = "PARTNER_RECEIVE"
    const val LOAN_DISBURSE = "LOAN_DISBURSE"
    const val LOAN_REPAYMENT = "LOAN_REPAYMENT"
    const val BILL_PAYMENT = "BILL_PAYMENT"
    const val SHOP_EXPENSE = "SHOP_EXPENSE"
    const val REVERSAL = "REVERSAL"
}

object BalanceAddSource {
    const val CAPITAL = "CAPITAL"
    const val OTHER_TRANSFER = "OTHER_TRANSFER"
    const val ADJUSTMENT = "ADJUSTMENT"
}

object BalanceWithdrawReason {
    const val CAPITAL_WITHDRAWAL = "CAPITAL_WITHDRAWAL"
    const val OWNER_DRAW = "OWNER_DRAW"
    const val ADJUSTMENT = "ADJUSTMENT"
}

object ExpenseCategory {
    const val WIFI_BILL = "WIFI_BILL"
    const val ELECTRICITY_BILL = "ELECTRICITY_BILL"
    const val SHOP_RENT = "SHOP_RENT"
    const val OTHER = "OTHER"

    fun labelBn(code: String): String = when (code) {
        WIFI_BILL -> "WiFi Bill"
        ELECTRICITY_BILL -> "Electricity Bill"
        SHOP_RENT -> "দোকান ভাড়া"
        else -> "অন্যান্য খরচ"
    }
}

object BillType {
    const val ELECTRICITY = "ELECTRICITY"
    const val WIFI = "WIFI"

    fun labelBn(code: String): String = when (code) {
        ELECTRICITY -> "Electricity Bill"
        WIFI -> "WiFi Bill"
        else -> code
    }
}

@Keep
@IgnoreExtraProperties
data class OwnerConfig(
    @DocumentId val id: String = "",
    val ownerEmail: String = "",
    val ownerUid: String = "",
    val legacySuperAdminUid: String = "",
    val uidVerified: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EmployeeAccess(
    @DocumentId val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "EMPLOYEE",
    val active: Boolean = true,
    val linkedUid: String = "",
    val addedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Account(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "CASH",
    val lastFourDigits: String = "",
    val balancePaisa: Long = 0L,
    val sortOrder: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val displayName: String
        get() = if (lastFourDigits.isNotBlank()) "$name (•••• $lastFourDigits)" else name
}

@Keep
@IgnoreExtraProperties
data class Partner(
    @DocumentId val id: String = "",
    val name: String = "",
    val commissionPerThousandPaisa: Long = 0L,
    val bKashOnlyCommission: Boolean = false,
    val canCustomizeCommission: Boolean = false,
    val totalSentPaisa: Long = 0L,
    val totalReceivedPaisa: Long = 0L,
    val totalCommissionExpensePaisa: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val netBalancePaisa: Long
        get() = totalSentPaisa - totalReceivedPaisa
}

@Keep
@IgnoreExtraProperties
data class LedgerTransaction(
    @DocumentId val id: String = "",
    val type: String = "",
    val subType: String = "",
    val title: String = "",
    val partyName: String = "",
    val phone: String = "",
    val receiveAccountId: String = "",
    val receiveAmountPaisa: Long = 0L,
    val payAccountId: String = "",
    val payAmountPaisa: Long = 0L,
    val commissionIncomePaisa: Long = 0L,
    val commissionExpensePaisa: Long = 0L,
    val expenseAmountPaisa: Long = 0L,
    val interestIncomePaisa: Long = 0L,
    val interestExpensePaisa: Long = 0L,
    val capitalDeltaPaisa: Long = 0L,
    val externalTxId: String = "",
    val idempotencyKey: String = "",
    val note: String = "",
    val businessDateMillis: Long = 0L,
    val partnerId: String = "",
    val loanId: String = "",
    val operatorName: String = "",
    val submittedByUid: String = "",
    val submittedByName: String = "",
    val approvedByUid: String = "",
    val reversed: Boolean = false,
    val reversedByTxId: String = "",
    val reversalOfTxId: String = "",
    val reversalReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PendingTransaction(
    @DocumentId val id: String = "",
    val status: String = "PENDING",
    val type: String = "",
    val subType: String = "",
    val title: String = "",
    val partyName: String = "",
    val phone: String = "",
    val receiveAccountId: String = "",
    val receiveAmountPaisa: Long = 0L,
    val payAccountId: String = "",
    val payAmountPaisa: Long = 0L,
    val commissionIncomePaisa: Long = 0L,
    val commissionExpensePaisa: Long = 0L,
    val expenseAmountPaisa: Long = 0L,
    val interestIncomePaisa: Long = 0L,
    val interestExpensePaisa: Long = 0L,
    val capitalDeltaPaisa: Long = 0L,
    val externalTxId: String = "",
    val idempotencyKey: String = "",
    val note: String = "",
    val businessDateMillis: Long = 0L,
    val partnerId: String = "",
    val loanId: String = "",
    val operatorName: String = "",
    val submittedByUid: String = "",
    val submittedByEmail: String = "",
    val submittedByName: String = "",
    val reviewedByUid: String = "",
    val approvedTxId: String = "",
    val rejectionReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LoanRecord(
    @DocumentId val id: String = "",
    val direction: String = "PAYABLE", // PAYABLE or RECEIVABLE
    val personName: String = "",
    val phone: String = "",
    val accountId: String = "",
    val principalPaisa: Long = 0L,
    val interestPaisa: Long = 0L,
    val paidPrincipalPaisa: Long = 0L,
    val paidInterestPaisa: Long = 0L,
    val startDateMillis: Long = 0L,
    val dueDateMillis: Long = 0L,
    val status: String = "ACTIVE", // ACTIVE, PARTIAL, PAID
    val note: String = "",
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val remainingPrincipalPaisa: Long get() = (principalPaisa - paidPrincipalPaisa).coerceAtLeast(0L)
    val remainingInterestPaisa: Long get() = (interestPaisa - paidInterestPaisa).coerceAtLeast(0L)
    val totalRemainingPaisa: Long get() = remainingPrincipalPaisa + remainingInterestPaisa

    fun isOverdue(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return status != "PAID" && totalRemainingPaisa > 0L && dueDateMillis < nowMillis
    }
}

@Keep
@IgnoreExtraProperties
data class LoanRepayment(
    @DocumentId val id: String = "",
    val loanId: String = "",
    val principalPaidPaisa: Long = 0L,
    val interestPaidPaisa: Long = 0L,
    val accountId: String = "",
    val repaymentDateMillis: Long = 0L,
    val note: String = "",
    val txId: String = "",
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val targetId: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SafetyBackupRecord(
    @DocumentId val id: String = "",
    val createdByUid: String = "",
    val reason: String = "",
    val transactionCount: Long = 0L,
    val accountCount: Long = 0L,
    val localFileName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val isQuotaExceeded: Boolean = false) : UiState<Nothing>
}

sealed interface UserRoleState {
    data object Checking : UserRoleState
    data class OwnerNeedsUidVerification(
        val currentUid: String,
        val email: String,
        val storedOwnerUid: String?
    ) : UserRoleState
    data class VerifiedOwner(val uid: String, val email: String) : UserRoleState
    data class ActiveEmployee(val uid: String, val email: String, val name: String) : UserRoleState
    data class Unauthorized(val uid: String, val email: String, val reason: String) : UserRoleState
}

data class DashboardSummary(
    val cashBalancePaisa: Long = 0L,
    val totalBkashBalancePaisa: Long = 0L,
    val nagadBalancePaisa: Long = 0L,
    val rocketBalancePaisa: Long = 0L,
    val upayBalancePaisa: Long = 0L,
    val ucbBankBalancePaisa: Long = 0L,
    val easyRechargeBalancePaisa: Long = 0L,
    val totalAccountBalancePaisa: Long = 0L,
    val totalCapitalPaisa: Long = 0L,
    val totalCommissionIncomePaisa: Long = 0L,
    val totalPartnerCommissionExpensePaisa: Long = 0L,
    val totalShopExpensePaisa: Long = 0L,
    val totalExpensePaisa: Long = 0L,
    val netProfitLossPaisa: Long = 0L,
    val interestIncomePaisa: Long = 0L,
    val interestExpensePaisa: Long = 0L,
    val todayVolumePaisa: Long = 0L,
    val todayCommissionPaisa: Long = 0L,
    val todayExpensePaisa: Long = 0L,
    val todayNetProfitLossPaisa: Long = 0L
)

data class TxDraft(
    val type: String,
    val subType: String = "",
    val title: String,
    val partyName: String = "",
    val phone: String = "",
    val receiveAccountId: String = "",
    val receiveAmountPaisa: Long = 0L,
    val payAccountId: String = "",
    val payAmountPaisa: Long = 0L,
    val commissionIncomePaisa: Long = 0L,
    val commissionExpensePaisa: Long = 0L,
    val expenseAmountPaisa: Long = 0L,
    val interestIncomePaisa: Long = 0L,
    val interestExpensePaisa: Long = 0L,
    val capitalDeltaPaisa: Long = 0L,
    val externalTxId: String = "",
    val idempotencyKey: String = "",
    val note: String = "",
    val businessDateMillis: Long = System.currentTimeMillis(),
    val partnerId: String = "",
    val loanId: String = "",
    val operatorName: String = ""
)

enum class ReportCategory(val titleBn: String) {
    DAILY("দৈনিক রিপোর্ট"),
    WEEKLY("সাপ্তাহিক রিপোর্ট"),
    MONTHLY("মাসিক রিপোর্ট"),
    YEARLY("বাৎসরিক রিপোর্ট"),
    ACCOUNT_WISE("অ্যাকাউন্টভিত্তিক"),
    EMPLOYEE_WISE("কর্মচারীভিত্তিক"),
    COMMISSION("কমিশন রিপোর্ট"),
    PARTNER("পার্টনার রিপোর্ট"),
    LOAN("ঋণ রিপোর্ট"),
    EXPENSE("খরচ রিপোর্ট"),
    PROFIT_LOSS("লাভ-ক্ষতি রিপোর্ট"),
    PENDING("Pending রিপোর্ট")
}
