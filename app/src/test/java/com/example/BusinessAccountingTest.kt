package com.example

import com.example.data.BackupRestoreManager
import com.example.domain.Account
import com.example.domain.AccountingEngine
import com.example.domain.BalanceAddSource
import com.example.domain.BalanceWithdrawReason
import com.example.domain.BusinessConstants
import com.example.domain.LedgerTransaction
import com.example.domain.LoanRecord
import com.example.domain.MoneyUtils
import com.example.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BusinessAccountingTest {

    @Test
    fun `initial 11 accounts start at zero balance`() {
        val accounts = BusinessConstants.DEFAULT_ACCOUNTS
        assertEquals(11, accounts.size)
        assertTrue(accounts.all { it.balancePaisa == 0L })

        val summary = AccountingEngine.computeDashboardSummary(accounts, emptyList())
        assertEquals(0L, summary.totalAccountBalancePaisa)
        assertEquals(0L, summary.totalCapitalPaisa)
        assertEquals(0L, summary.netProfitLossPaisa)
        assertEquals(0L, summary.totalLoanReceivablePaisa)
        assertEquals(0L, summary.totalLoanPayablePaisa)
    }

    @Test
    fun `owner identity constants match requirement`() {
        assertEquals("ashrafmahmudsany2@gmail.com", BusinessConstants.OWNER_EMAIL)
        assertEquals("yRuPuAgCe2V2Y8avgK9U8n4sTgj1", BusinessConstants.LEGACY_SUPER_ADMIN_UID)
    }

    @Test
    fun `internal transfer and capital movements do not affect net profit`() {
        val accounts = BusinessConstants.DEFAULT_ACCOUNTS.map {
            when (it.id) {
                BusinessConstants.ACC_CASH -> it.copy(balancePaisa = 1_000_000L) // 10,000 BDT
                BusinessConstants.ACC_BKASH_1 -> it.copy(balancePaisa = 500_000L) // 5,000 BDT
                else -> it
            }
        }
        val now = System.currentTimeMillis()
        val txs = listOf(
            LedgerTransaction(
                id = "t1",
                type = TxType.BALANCE_ADD,
                subType = BalanceAddSource.CAPITAL,
                title = "Capital Add",
                receiveAccountId = BusinessConstants.ACC_CASH,
                receiveAmountPaisa = 1_000_000L,
                capitalDeltaPaisa = 1_000_000L,
                businessDateMillis = now
            ),
            LedgerTransaction(
                id = "t2",
                type = TxType.BALANCE_WITHDRAW,
                subType = BalanceWithdrawReason.CAPITAL_WITHDRAWAL,
                title = "Capital Withdraw",
                payAccountId = BusinessConstants.ACC_CASH,
                payAmountPaisa = 300_000L,
                capitalDeltaPaisa = -300_000L,
                businessDateMillis = now
            ),
            LedgerTransaction(
                id = "t3",
                type = TxType.INTERNAL_TRANSFER,
                title = "Internal Transfer",
                receiveAccountId = BusinessConstants.ACC_BKASH_1,
                receiveAmountPaisa = 200_000L,
                payAccountId = BusinessConstants.ACC_CASH,
                payAmountPaisa = 200_000L,
                businessDateMillis = now
            )
        )

        val summary = AccountingEngine.computeDashboardSummary(accounts, txs, nowMillis = now)
        assertEquals(1_000_000L, summary.cashBalancePaisa)
        assertEquals(500_000L, summary.totalBkashBalancePaisa)
        assertEquals(1_500_000L, summary.totalAccountBalancePaisa)
        assertEquals(700_000L, summary.totalCapitalPaisa)
        assertEquals(0L, summary.totalCommissionIncomePaisa)
        assertEquals(0L, summary.totalExpensePaisa)
        assertEquals(0L, summary.netProfitLossPaisa)
    }

    @Test
    fun `partner commission rules for Samad, Jahir, and Ariful`() {
        val samad = BusinessConstants.DEFAULT_PARTNERS.first { it.id == BusinessConstants.PARTNER_SAMAD }
        val jahir = BusinessConstants.DEFAULT_PARTNERS.first { it.id == BusinessConstants.PARTNER_JAHIR }
        val ariful = BusinessConstants.DEFAULT_PARTNERS.first { it.id == BusinessConstants.PARTNER_ARIFUL }

        val bKashAcc = BusinessConstants.DEFAULT_ACCOUNTS.first { it.id == BusinessConstants.ACC_BKASH_1 }
        val cashAcc = BusinessConstants.DEFAULT_ACCOUNTS.first { it.id == BusinessConstants.ACC_CASH }

        // 10,000 BDT (1,000,000 paisa) via bKash -> ৳50 (5,000 paisa)
        assertEquals(
            5_000L,
            AccountingEngine.calculatePartnerCommissionExpense(samad, bKashAcc, 1_000_000L, isSendingToPartner = true)
        )
        assertEquals(
            5_000L,
            AccountingEngine.calculatePartnerCommissionExpense(jahir, bKashAcc, 1_000_000L, isSendingToPartner = true)
        )
        // Cash send or receiving from partner -> ৳0 commission
        assertEquals(
            0L,
            AccountingEngine.calculatePartnerCommissionExpense(samad, cashAcc, 1_000_000L, isSendingToPartner = true)
        )
        assertEquals(
            0L,
            AccountingEngine.calculatePartnerCommissionExpense(samad, bKashAcc, 1_000_000L, isSendingToPartner = false)
        )
        // Ariful default ৳0, customizable
        assertEquals(
            0L,
            AccountingEngine.calculatePartnerCommissionExpense(ariful, bKashAcc, 1_000_000L, isSendingToPartner = true)
        )
        val arifulCustom = ariful.copy(commissionPerThousandPaisa = 300L) // ৳3 per 1000
        assertEquals(
            3_000L,
            AccountingEngine.calculatePartnerCommissionExpense(arifulCustom, cashAcc, 1_000_000L, isSendingToPartner = true)
        )
    }

    @Test
    fun `loan receivable and payable totals update cleanly without affecting net profit`() {
        val now = System.currentTimeMillis()
        val receivableActive = LoanRecord(
            id = "l1",
            direction = "RECEIVABLE",
            personName = "Rahim",
            accountId = BusinessConstants.ACC_CASH,
            principalPaisa = 500_000L, // 5,000 BDT
            paidPrincipalPaisa = 0L,
            startDateMillis = now,
            dueDateMillis = now + 86_400_000L,
            status = "ACTIVE"
        )
        val payablePartial = LoanRecord(
            id = "l2",
            direction = "PAYABLE",
            personName = "Karim",
            accountId = BusinessConstants.ACC_UCB_BANK,
            principalPaisa = 800_000L, // 8,000 BDT
            paidPrincipalPaisa = 300_000L, // 3,000 BDT repaid
            startDateMillis = now,
            dueDateMillis = now + 86_400_000L,
            status = "PARTIAL"
        )
        val loanDisburseTx = LedgerTransaction(
            id = "tx_loan_1",
            type = TxType.LOAN_DISBURSE,
            subType = "RECEIVABLE",
            title = "ধার প্রদান: Rahim",
            payAccountId = BusinessConstants.ACC_CASH,
            payAmountPaisa = 500_000L,
            businessDateMillis = now
        )

        val summaryActive = AccountingEngine.computeDashboardSummary(
            accounts = BusinessConstants.DEFAULT_ACCOUNTS,
            transactions = listOf(loanDisburseTx),
            nowMillis = now,
            loans = listOf(receivableActive, payablePartial)
        )
        assertEquals(500_000L, summaryActive.totalLoanReceivablePaisa)
        assertEquals(500_000L, summaryActive.totalLoanPayablePaisa)
        assertEquals(0L, summaryActive.netProfitLossPaisa)
        assertEquals(0L, summaryActive.totalExpensePaisa)

        // One-tap full settlement clears remaining receivable/payable
        val settledLoans = listOf(
            receivableActive.copy(paidPrincipalPaisa = 500_000L, status = "PAID"),
            payablePartial.copy(paidPrincipalPaisa = 800_000L, status = "PAID")
        )
        val summarySettled = AccountingEngine.computeDashboardSummary(
            accounts = BusinessConstants.DEFAULT_ACCOUNTS,
            transactions = listOf(loanDisburseTx),
            nowMillis = now,
            loans = settledLoans
        )
        assertEquals(0L, summarySettled.totalLoanReceivablePaisa)
        assertEquals(0L, summarySettled.totalLoanPayablePaisa)
        assertEquals(0L, summarySettled.netProfitLossPaisa)
    }

    @Test
    fun `mobile recharge source accounts contain exact 9 allowed accounts`() {
        val expected = listOf(
            BusinessConstants.ACC_EASY_RECHARGE,
            BusinessConstants.ACC_BKASH_1,
            BusinessConstants.ACC_BKASH_2,
            BusinessConstants.ACC_BKASH_3,
            BusinessConstants.ACC_BKASH_4,
            BusinessConstants.ACC_BKASH_5,
            BusinessConstants.ACC_NAGAD,
            BusinessConstants.ACC_UPAY,
            BusinessConstants.ACC_ROCKET
        )
        assertEquals(expected, BusinessConstants.RECHARGE_SOURCE_ACCOUNT_IDS)
        assertFalse(BusinessConstants.ACC_CASH in BusinessConstants.RECHARGE_SOURCE_ACCOUNT_IDS)
        assertFalse(BusinessConstants.ACC_UCB_BANK in BusinessConstants.RECHARGE_SOURCE_ACCOUNT_IDS)
    }

    @Test
    fun `backup json builder and strict validator reject malformed and accept valid data`() {
        val validJson = BackupRestoreManager.buildBackupJson(
            accounts = BusinessConstants.DEFAULT_ACCOUNTS,
            partners = BusinessConstants.DEFAULT_PARTNERS,
            transactions = emptyList(),
            loans = emptyList(),
            employees = emptyList()
        )
        val parsed = BackupRestoreManager.validateAndParseBackupJson(validJson)
        assertTrue(parsed.isSuccess)
        assertEquals(11, parsed.getOrThrow().accounts.size)

        // Malformed JSON rejected
        assertTrue(BackupRestoreManager.validateAndParseBackupJson("{bad json").isFailure)

        // Wrong app tag rejected
        assertTrue(
            BackupRestoreManager.validateAndParseBackupJson(
                """{"app":"OtherApp","schemaVersion":1,"accounts":[],"partners":[],"transactions":[],"loans":[]}"""
            ).isFailure
        )
    }

    @Test
    fun `duplicate key builder normalizes externalTxId and currency parser supports Bengali digits`() {
        assertEquals("ext_CUSTOMER_TX_ABC123", AccountingEngine.buildDuplicateKey(TxType.CUSTOMER_TX, " abc123 ", "id1"))
        assertEquals(150_50L, MoneyUtils.parseBdtToPaisa("১৫০.৫০"))
        assertEquals("৳১,৫০০", MoneyUtils.formatBdt(150_000L))
    }
}
