package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object MoneyUtils {
    private val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun toBengaliDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(bengaliDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = bengaliDigits.indexOf(ch)
            if (idx >= 0) {
                sb.append(('0'.code + idx).toChar())
            } else if (ch in '0'..'9' || ch == '.') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseBdtToPaisa(raw: String): Long? {
        val normalized = normalizeDigits(raw.trim().replace(",", "").replace("৳", ""))
        if (normalized.isEmpty()) return null
        val parts = normalized.split('.')
        if (parts.size > 2) return null
        val whole = if (parts[0].isEmpty()) 0L else parts[0].toLongOrNull() ?: return null
        val frac = if (parts.size == 2) {
            val f = parts[1]
            when {
                f.isEmpty() -> 0L
                f.length == 1 -> (f.toLongOrNull() ?: return null) * 10L
                else -> f.substring(0, 2).toLongOrNull() ?: return null
            }
        } else 0L
        return whole * 100L + frac
    }

    fun formatBdt(paisa: Long, useBengaliDigits: Boolean = true, showSign: Boolean = false): String {
        val isNegative = paisa < 0L
        val absPaisa = abs(paisa)
        val taka = absPaisa / 100L
        val remPaisa = absPaisa % 100L
        val groupedTaka = formatSouthAsianGrouping(taka)
        val amountStr = if (remPaisa == 0L) {
            groupedTaka
        } else {
            val paisaStr = remPaisa.toString().padStart(2, '0')
            "$groupedTaka.$paisaStr"
        }
        val prefix = when {
            isNegative -> "-৳"
            showSign && paisa > 0L -> "+৳"
            else -> "৳"
        }
        val full = "$prefix$amountStr"
        return if (useBengaliDigits) toBengaliDigits(full) else full
    }

    private fun formatSouthAsianGrouping(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val lastThree = s.takeLast(3)
        var rest = s.dropLast(3)
        val groups = mutableListOf<String>()
        while (rest.length > 2) {
            groups.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) {
            groups.add(0, rest)
        }
        return groups.joinToString(",") + "," + lastThree
    }

    fun formatDateBn(millis: Long): String {
        if (millis <= 0L) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        return toBengaliDigits(sdf.format(Date(millis)))
    }

    fun formatDateTimeBn(millis: Long): String {
        if (millis <= 0L) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        return toBengaliDigits(sdf.format(Date(millis)))
    }

    fun startOfTodayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun startOfWeekMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfTodayMillis(nowMillis)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        return cal.timeInMillis
    }

    fun startOfMonthMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfTodayMillis(nowMillis)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    fun startOfYearMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfTodayMillis(nowMillis)
        cal.set(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

object AccountingEngine {

    /**
     * Computes Partner commission expense in paisa.
     * - Samad Bhai & Jahir Mama: ৳5 per ৳1,000 (500 paisa / 100,000 paisa) ONLY when sending from bKash.
     *   Cash / UCB Bank / receiving = ৳0.
     * - Ariful Islam: uses customizable commissionPerThousandPaisa (default 0L) when sending, or manual override.
     */
    fun calculatePartnerCommissionExpense(
        partner: Partner,
        account: Account,
        amountPaisa: Long,
        isSendingToPartner: Boolean,
        manualOverridePaisa: Long? = null
    ): Long {
        if (!isSendingToPartner || amountPaisa <= 0L) return 0L
        if (partner.canCustomizeCommission && manualOverridePaisa != null) {
            return manualOverridePaisa.coerceAtLeast(0L)
        }
        if (partner.bKashOnlyCommission && account.category != "BKASH") {
            return 0L
        }
        if (partner.commissionPerThousandPaisa <= 0L) return 0L
        return (amountPaisa * partner.commissionPerThousandPaisa) / 100_000L
    }

    /**
     * Computes deterministic duplicate-protection key if externalTxId is supplied.
     */
    fun buildDuplicateKey(type: String, externalTxId: String, idempotencyKey: String): String {
        val cleanExt = externalTxId.trim().uppercase(Locale.ENGLISH).replace(Regex("[^A-Z0-9_\\-]"), "_")
        return if (cleanExt.isNotEmpty()) {
            "ext_${type}_$cleanExt"
        } else {
            val cleanIdem = idempotencyKey.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            "req_${cleanIdem.ifEmpty { System.nanoTime().toString() }}"
        }
    }

    /**
     * Computes full DashboardSummary strictly from approved, non-reversed ledger transactions
     * and the 11 accounts.
     *
     * Rules:
     * - Internal Transfer is NOT profit.
     * - Capital Deposit is NOT profit.
     * - Capital Withdrawal is NOT expense.
     * - Loan Principal is NOT profit or expense.
     * - Partner Commission is Expense.
     * - Customer/Recharge/Bill Commission is Income.
     * - Shop Expense is Expense.
     * - Net Profit = Commission Income - Partner Commission Expense - Shop Expenses.
     * - Loan Interest Income & Interest Expense are reported separately.
     */
    fun computeDashboardSummary(
        accounts: List<Account>,
        transactions: List<LedgerTransaction>,
        nowMillis: Long = System.currentTimeMillis(),
        loans: List<LoanRecord> = emptyList()
    ): DashboardSummary {
        val accountMap = accounts.associateBy { it.id }
        val cash = accountMap[BusinessConstants.ACC_CASH]?.balancePaisa ?: 0L
        val totalBkash = listOf(
            BusinessConstants.ACC_BKASH_1,
            BusinessConstants.ACC_BKASH_2,
            BusinessConstants.ACC_BKASH_3,
            BusinessConstants.ACC_BKASH_4,
            BusinessConstants.ACC_BKASH_5
        ).sumOf { accountMap[it]?.balancePaisa ?: 0L }
        val nagad = accountMap[BusinessConstants.ACC_NAGAD]?.balancePaisa ?: 0L
        val rocket = accountMap[BusinessConstants.ACC_ROCKET]?.balancePaisa ?: 0L
        val upay = accountMap[BusinessConstants.ACC_UPAY]?.balancePaisa ?: 0L
        val ucb = accountMap[BusinessConstants.ACC_UCB_BANK]?.balancePaisa ?: 0L
        val easyRecharge = accountMap[BusinessConstants.ACC_EASY_RECHARGE]?.balancePaisa ?: 0L
        val totalBalance = accounts.sumOf { it.balancePaisa }

        val activeTxs = transactions.filter { !it.reversed && it.type != TxType.REVERSAL }
        val todayStart = MoneyUtils.startOfTodayMillis(nowMillis)

        var totalCapital = 0L
        var totalCommIncome = 0L
        var totalPartnerCommExpense = 0L
        var totalShopExpense = 0L
        var interestIncome = 0L
        var interestExpense = 0L

        var todayVolume = 0L
        var todayCommIncome = 0L
        var todayPartnerCommExpense = 0L
        var todayShopExpense = 0L

        for (tx in activeTxs) {
            totalCapital += tx.capitalDeltaPaisa
            totalCommIncome += tx.commissionIncomePaisa
            totalPartnerCommExpense += tx.commissionExpensePaisa
            totalShopExpense += tx.expenseAmountPaisa
            interestIncome += tx.interestIncomePaisa
            interestExpense += tx.interestExpensePaisa

            if (tx.businessDateMillis >= todayStart) {
                todayVolume += maxOf(tx.receiveAmountPaisa, tx.payAmountPaisa)
                todayCommIncome += tx.commissionIncomePaisa
                todayPartnerCommExpense += tx.commissionExpensePaisa
                todayShopExpense += tx.expenseAmountPaisa
            }
        }

        val totalExpense = totalPartnerCommExpense + totalShopExpense
        val netProfitLoss = totalCommIncome - totalPartnerCommExpense - totalShopExpense
        val todayExpense = todayPartnerCommExpense + todayShopExpense
        val todayNet = todayCommIncome - todayPartnerCommExpense - todayShopExpense

        val totalLoanReceivable = loans
            .filter { it.direction == "RECEIVABLE" && it.status != "PAID" }
            .sumOf { it.totalRemainingPaisa }
        val totalLoanPayable = loans
            .filter { it.direction == "PAYABLE" && it.status != "PAID" }
            .sumOf { it.totalRemainingPaisa }

        return DashboardSummary(
            cashBalancePaisa = cash,
            totalBkashBalancePaisa = totalBkash,
            nagadBalancePaisa = nagad,
            rocketBalancePaisa = rocket,
            upayBalancePaisa = upay,
            ucbBankBalancePaisa = ucb,
            easyRechargeBalancePaisa = easyRecharge,
            totalAccountBalancePaisa = totalBalance,
            totalCapitalPaisa = totalCapital,
            totalCommissionIncomePaisa = totalCommIncome,
            totalPartnerCommissionExpensePaisa = totalPartnerCommExpense,
            totalShopExpensePaisa = totalShopExpense,
            totalExpensePaisa = totalExpense,
            netProfitLossPaisa = netProfitLoss,
            interestIncomePaisa = interestIncome,
            interestExpensePaisa = interestExpense,
            totalLoanReceivablePaisa = totalLoanReceivable,
            totalLoanPayablePaisa = totalLoanPayable,
            todayVolumePaisa = todayVolume,
            todayCommissionPaisa = todayCommIncome,
            todayExpensePaisa = todayExpense,
            todayNetProfitLossPaisa = todayNet
        )
    }

    fun txTypeLabelBn(type: String): String = when (type) {
        TxType.BALANCE_ADD -> "ব্যালেন্স যোগ"
        TxType.BALANCE_WITHDRAW -> "ব্যালেন্স উত্তোলন"
        TxType.CUSTOMER_TX -> "গ্রাহক লেনদেন"
        TxType.INTERNAL_TRANSFER -> "অ্যাকাউন্ট ট্রান্সফার"
        TxType.MOBILE_RECHARGE -> "মোবাইল রিচার্জ"
        TxType.PARTNER_SEND -> "পার্টনারকে প্রেরণ"
        TxType.PARTNER_RECEIVE -> "পার্টনার থেকে গ্রহণ"
        TxType.LOAN_DISBURSE -> "ঋণ প্রদান/গ্রহণ"
        TxType.LOAN_REPAYMENT -> "ঋণ পরিশোধ"
        TxType.BILL_PAYMENT -> "বিল পেমেন্ট"
        TxType.SHOP_EXPENSE -> "দোকান খরচ"
        TxType.REVERSAL -> "লেনদেন Reversal"
        else -> type
    }
}
