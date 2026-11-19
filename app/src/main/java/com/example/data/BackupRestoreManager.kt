package com.example.data

import android.content.Context
import com.example.domain.Account
import com.example.domain.BusinessConstants
import com.example.domain.EmployeeAccess
import com.example.domain.LedgerTransaction
import com.example.domain.LoanRecord
import com.example.domain.Partner
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ParsedBackupData(
    val exportedAtMillis: Long,
    val accounts: List<Account>,
    val partners: List<Partner>,
    val transactions: List<LedgerTransaction>,
    val loans: List<LoanRecord>,
    val employees: List<EmployeeAccess>
)

object BackupRestoreManager {
    private const val SCHEMA_VERSION = 1
    private const val APP_TAG = "Business Manager Pro"

    fun buildBackupJson(
        accounts: List<Account>,
        partners: List<Partner>,
        transactions: List<LedgerTransaction>,
        loans: List<LoanRecord>,
        employees: List<EmployeeAccess>,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject().apply {
            put("app", APP_TAG)
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAtMillis", nowMillis)
            put("accounts", JSONArray().apply {
                accounts.forEach { acc ->
                    put(JSONObject().apply {
                        put("id", acc.id)
                        put("name", acc.name)
                        put("category", acc.category)
                        put("lastFourDigits", acc.lastFourDigits)
                        put("balancePaisa", acc.balancePaisa)
                        put("sortOrder", acc.sortOrder)
                    })
                }
            })
            put("partners", JSONArray().apply {
                partners.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("commissionPerThousandPaisa", p.commissionPerThousandPaisa)
                        put("bKashOnlyCommission", p.bKashOnlyCommission)
                        put("canCustomizeCommission", p.canCustomizeCommission)
                        put("totalSentPaisa", p.totalSentPaisa)
                        put("totalReceivedPaisa", p.totalReceivedPaisa)
                        put("totalCommissionExpensePaisa", p.totalCommissionExpensePaisa)
                    })
                }
            })
            put("transactions", JSONArray().apply {
                transactions.forEach { tx ->
                    put(JSONObject().apply {
                        put("id", tx.id)
                        put("type", tx.type)
                        put("subType", tx.subType)
                        put("title", tx.title)
                        put("partyName", tx.partyName)
                        put("phone", tx.phone)
                        put("receiveAccountId", tx.receiveAccountId)
                        put("receiveAmountPaisa", tx.receiveAmountPaisa)
                        put("payAccountId", tx.payAccountId)
                        put("payAmountPaisa", tx.payAmountPaisa)
                        put("commissionIncomePaisa", tx.commissionIncomePaisa)
                        put("commissionExpensePaisa", tx.commissionExpensePaisa)
                        put("expenseAmountPaisa", tx.expenseAmountPaisa)
                        put("interestIncomePaisa", tx.interestIncomePaisa)
                        put("interestExpensePaisa", tx.interestExpensePaisa)
                        put("capitalDeltaPaisa", tx.capitalDeltaPaisa)
                        put("externalTxId", tx.externalTxId)
                        put("idempotencyKey", tx.idempotencyKey)
                        put("note", tx.note)
                        put("businessDateMillis", tx.businessDateMillis)
                        put("partnerId", tx.partnerId)
                        put("loanId", tx.loanId)
                        put("operatorName", tx.operatorName)
                        put("submittedByUid", tx.submittedByUid)
                        put("submittedByName", tx.submittedByName)
                        put("approvedByUid", tx.approvedByUid)
                        put("reversed", tx.reversed)
                        put("reversedByTxId", tx.reversedByTxId)
                        put("reversalOfTxId", tx.reversalOfTxId)
                        put("reversalReason", tx.reversalReason)
                    })
                }
            })
            put("loans", JSONArray().apply {
                loans.forEach { loan ->
                    put(JSONObject().apply {
                        put("id", loan.id)
                        put("direction", loan.direction)
                        put("personName", loan.personName)
                        put("phone", loan.phone)
                        put("accountId", loan.accountId)
                        put("principalPaisa", loan.principalPaisa)
                        put("interestPaisa", loan.interestPaisa)
                        put("paidPrincipalPaisa", loan.paidPrincipalPaisa)
                        put("paidInterestPaisa", loan.paidInterestPaisa)
                        put("startDateMillis", loan.startDateMillis)
                        put("dueDateMillis", loan.dueDateMillis)
                        put("status", loan.status)
                        put("note", loan.note)
                        put("createdByUid", loan.createdByUid)
                    })
                }
            })
            put("employees", JSONArray().apply {
                employees.forEach { emp ->
                    put(JSONObject().apply {
                        put("email", emp.email)
                        put("name", emp.name)
                        put("role", emp.role)
                        put("active", emp.active)
                        put("linkedUid", emp.linkedUid)
                        put("addedByUid", emp.addedByUid)
                    })
                }
            })
        }
        return root.toString(2)
    }

    /**
     * Strictly validates JSON backup integrity before allowing restore.
     * Rejects malformed, wrong-app, or incomplete account/transaction backups.
     */
    fun validateAndParseBackupJson(rawJson: String): Result<ParsedBackupData> {
        return try {
            val root = JSONObject(rawJson)
            if (root.optString("app") != APP_TAG) {
                return Result.failure(IllegalArgumentException("ভুল ব্যাকআপ ফাইল: এটি Business Manager Pro-এর ব্যাকআপ নয়।"))
            }
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
                return Result.failure(IllegalArgumentException("অসমর্থিত ব্যাকআপ সংস্করণ।"))
            }
            val accountsJson = root.optJSONArray("accounts")
                ?: return Result.failure(IllegalArgumentException("অসম্পূর্ণ ব্যাকআপ: accounts অংশ অনুপস্থিত।"))
            val partnersJson = root.optJSONArray("partners")
                ?: return Result.failure(IllegalArgumentException("অসম্পূর্ণ ব্যাকআপ: partners অংশ অনুপস্থিত।"))
            val txJson = root.optJSONArray("transactions")
                ?: return Result.failure(IllegalArgumentException("অসম্পূর্ণ ব্যাকআপ: transactions অংশ অনুপস্থিত।"))
            val loansJson = root.optJSONArray("loans")
                ?: return Result.failure(IllegalArgumentException("অসম্পূর্ণ ব্যাকআপ: loans অংশ অনুপস্থিত।"))
            val empJson = root.optJSONArray("employees") ?: JSONArray()

            val expectedAccountIds = BusinessConstants.DEFAULT_ACCOUNTS.map { it.id }.toSet()
            val parsedAccounts = mutableListOf<Account>()
            for (i in 0 until accountsJson.length()) {
                val obj = accountsJson.getJSONObject(i)
                val id = obj.getString("id")
                if (id !in expectedAccountIds) {
                    return Result.failure(IllegalArgumentException("অবৈধ অ্যাকাউন্ট আইডি পাওয়া গেছে: $id"))
                }
                parsedAccounts.add(
                    Account(
                        id = id,
                        name = obj.getString("name"),
                        category = obj.getString("category"),
                        lastFourDigits = obj.optString("lastFourDigits", ""),
                        balancePaisa = obj.getLong("balancePaisa"),
                        sortOrder = obj.getLong("sortOrder")
                    )
                )
            }
            if (parsedAccounts.map { it.id }.toSet() != expectedAccountIds) {
                return Result.failure(IllegalArgumentException("অসম্পূর্ণ ব্যাকআপ: ১১টি অ্যাকাউন্টের সম্পূর্ণ তালিকা নেই।"))
            }

            val parsedPartners = mutableListOf<Partner>()
            for (i in 0 until partnersJson.length()) {
                val obj = partnersJson.getJSONObject(i)
                parsedPartners.add(
                    Partner(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        commissionPerThousandPaisa = obj.getLong("commissionPerThousandPaisa"),
                        bKashOnlyCommission = obj.getBoolean("bKashOnlyCommission"),
                        canCustomizeCommission = obj.getBoolean("canCustomizeCommission"),
                        totalSentPaisa = obj.getLong("totalSentPaisa"),
                        totalReceivedPaisa = obj.getLong("totalReceivedPaisa"),
                        totalCommissionExpensePaisa = obj.getLong("totalCommissionExpensePaisa")
                    )
                )
            }

            val parsedTxs = mutableListOf<LedgerTransaction>()
            for (i in 0 until txJson.length()) {
                val obj = txJson.getJSONObject(i)
                val id = obj.getString("id")
                val type = obj.getString("type")
                val title = obj.getString("title")
                val bizDate = obj.getLong("businessDateMillis")
                if (id.isBlank() || type.isBlank() || title.isBlank() || bizDate <= 0L) {
                    return Result.failure(IllegalArgumentException("অসম্পূর্ণ লেনদেন রেকর্ড (#$i)।"))
                }
                parsedTxs.add(
                    LedgerTransaction(
                        id = id,
                        type = type,
                        subType = obj.optString("subType", ""),
                        title = title,
                        partyName = obj.optString("partyName", ""),
                        phone = obj.optString("phone", ""),
                        receiveAccountId = obj.optString("receiveAccountId", ""),
                        receiveAmountPaisa = obj.optLong("receiveAmountPaisa", 0L),
                        payAccountId = obj.optString("payAccountId", ""),
                        payAmountPaisa = obj.optLong("payAmountPaisa", 0L),
                        commissionIncomePaisa = obj.optLong("commissionIncomePaisa", 0L),
                        commissionExpensePaisa = obj.optLong("commissionExpensePaisa", 0L),
                        expenseAmountPaisa = obj.optLong("expenseAmountPaisa", 0L),
                        interestIncomePaisa = obj.optLong("interestIncomePaisa", 0L),
                        interestExpensePaisa = obj.optLong("interestExpensePaisa", 0L),
                        capitalDeltaPaisa = obj.optLong("capitalDeltaPaisa", 0L),
                        externalTxId = obj.optString("externalTxId", ""),
                        idempotencyKey = obj.optString("idempotencyKey", "restored_$id"),
                        note = obj.optString("note", ""),
                        businessDateMillis = bizDate,
                        partnerId = obj.optString("partnerId", ""),
                        loanId = obj.optString("loanId", ""),
                        operatorName = obj.optString("operatorName", ""),
                        submittedByUid = obj.optString("submittedByUid", ""),
                        submittedByName = obj.optString("submittedByName", "Owner"),
                        approvedByUid = obj.optString("approvedByUid", ""),
                        reversed = obj.optBoolean("reversed", false),
                        reversedByTxId = obj.optString("reversedByTxId", ""),
                        reversalOfTxId = obj.optString("reversalOfTxId", ""),
                        reversalReason = obj.optString("reversalReason", "")
                    )
                )
            }

            val parsedLoans = mutableListOf<LoanRecord>()
            for (i in 0 until loansJson.length()) {
                val obj = loansJson.getJSONObject(i)
                parsedLoans.add(
                    LoanRecord(
                        id = obj.getString("id"),
                        direction = obj.getString("direction"),
                        personName = obj.getString("personName"),
                        phone = obj.optString("phone", ""),
                        accountId = obj.getString("accountId"),
                        principalPaisa = obj.getLong("principalPaisa"),
                        interestPaisa = obj.optLong("interestPaisa", 0L),
                        paidPrincipalPaisa = obj.optLong("paidPrincipalPaisa", 0L),
                        paidInterestPaisa = obj.optLong("paidInterestPaisa", 0L),
                        startDateMillis = obj.getLong("startDateMillis"),
                        dueDateMillis = obj.getLong("dueDateMillis"),
                        status = obj.getString("status"),
                        note = obj.optString("note", ""),
                        createdByUid = obj.optString("createdByUid", "")
                    )
                )
            }

            val parsedEmps = mutableListOf<EmployeeAccess>()
            for (i in 0 until empJson.length()) {
                val obj = empJson.getJSONObject(i)
                parsedEmps.add(
                    EmployeeAccess(
                        email = obj.getString("email"),
                        name = obj.getString("name"),
                        role = obj.optString("role", "EMPLOYEE"),
                        active = obj.optBoolean("active", true),
                        linkedUid = obj.optString("linkedUid", ""),
                        addedByUid = obj.optString("addedByUid", "")
                    )
                )
            }

            Result.success(
                ParsedBackupData(
                    exportedAtMillis = root.optLong("exportedAtMillis", 0L),
                    accounts = parsedAccounts,
                    partners = parsedPartners,
                    transactions = parsedTxs,
                    loans = parsedLoans,
                    employees = parsedEmps
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("ভুল বা ক্ষতিগ্রস্ত JSON ব্যাকআপ ফাইল: ${e.localizedMessage}"))
        }
    }

    /**
     * Creates an automatic local Safety Backup + Firestore Safety Backup log,
     * then restores the validated backup data into Firestore.
     */
    suspend fun createSafetyBackupAndRestore(
        context: Context,
        repository: BusinessRepository,
        currentAccounts: List<Account>,
        currentPartners: List<Partner>,
        currentTransactions: List<LedgerTransaction>,
        currentLoans: List<LoanRecord>,
        currentEmployees: List<EmployeeAccess>,
        backupJsonText: String
    ): Result<String> {
        return try {
            val parsed = validateAndParseBackupJson(backupJsonText).getOrElse { return Result.failure(it) }
            val uid = Firebase.auth.currentUser?.uid
                ?: return Result.failure(IllegalStateException("Owner লগইন প্রয়োজন।"))
            val email = Firebase.auth.currentUser?.email ?: BusinessConstants.OWNER_EMAIL

            // 1. Automatic Safety Backup BEFORE restore
            val safetyJson = buildBackupJson(
                accounts = currentAccounts,
                partners = currentPartners,
                transactions = currentTransactions,
                loans = currentLoans,
                employees = currentEmployees
            )
            val safetyDir = File(context.filesDir, "safety_backups").apply { mkdirs() }
            val safetyFileName = "safety_backup_${System.currentTimeMillis()}.json"
            File(safetyDir, safetyFileName).writeText(safetyJson)

            val safetyId = "safe_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            repository.db.collection("safety_backups").document(safetyId).set(
                mapOf(
                    "createdByUid" to uid,
                    "reason" to "Pre-restore automatic safety backup",
                    "transactionCount" to currentTransactions.size.toLong(),
                    "accountCount" to currentAccounts.size.toLong(),
                    "localFileName" to safetyFileName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 2. Restore Accounts & Partners (update balances to match backup)
            val batch = repository.db.batch()
            for (acc in parsed.accounts) {
                val ref = repository.db.collection("accounts").document(acc.id)
                batch.update(
                    ref,
                    mapOf(
                        "balancePaisa" to acc.balancePaisa,
                        "name" to acc.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            for (partner in parsed.partners) {
                val ref = repository.db.collection("partners").document(partner.id)
                batch.update(
                    ref,
                    mapOf(
                        "commissionPerThousandPaisa" to partner.commissionPerThousandPaisa,
                        "totalSentPaisa" to partner.totalSentPaisa,
                        "totalReceivedPaisa" to partner.totalReceivedPaisa,
                        "totalCommissionExpensePaisa" to partner.totalCommissionExpensePaisa,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()

            // 3. Restore missing transactions (preserving ledger immutability)
            val existingTxIds = repository.db.collection("transactions").get().await().documents.map { it.id }.toSet()
            val missingTxs = parsed.transactions.filter { it.id !in existingTxIds }
            missingTxs.chunked(400).forEach { chunk ->
                val txBatch = repository.db.batch()
                for (tx in chunk) {
                    txBatch.set(
                        repository.db.collection("transactions").document(tx.id),
                        mapOf(
                            "type" to tx.type,
                            "subType" to tx.subType,
                            "title" to tx.title.take(160),
                            "partyName" to tx.partyName.take(120),
                            "phone" to tx.phone.take(30),
                            "receiveAccountId" to tx.receiveAccountId,
                            "receiveAmountPaisa" to tx.receiveAmountPaisa,
                            "payAccountId" to tx.payAccountId,
                            "payAmountPaisa" to tx.payAmountPaisa,
                            "commissionIncomePaisa" to tx.commissionIncomePaisa,
                            "commissionExpensePaisa" to tx.commissionExpensePaisa,
                            "expenseAmountPaisa" to tx.expenseAmountPaisa,
                            "interestIncomePaisa" to tx.interestIncomePaisa,
                            "interestExpensePaisa" to tx.interestExpensePaisa,
                            "capitalDeltaPaisa" to tx.capitalDeltaPaisa,
                            "externalTxId" to tx.externalTxId.take(100),
                            "idempotencyKey" to tx.idempotencyKey.take(128),
                            "note" to tx.note.take(500),
                            "businessDateMillis" to tx.businessDateMillis,
                            "partnerId" to tx.partnerId,
                            "loanId" to tx.loanId,
                            "operatorName" to tx.operatorName.take(60),
                            "submittedByUid" to tx.submittedByUid.ifBlank { uid },
                            "submittedByName" to tx.submittedByName.ifBlank { "Owner" },
                            "approvedByUid" to uid,
                            "reversed" to tx.reversed,
                            "reversedByTxId" to tx.reversedByTxId,
                            "reversalOfTxId" to tx.reversalOfTxId,
                            "reversalReason" to tx.reversalReason,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                txBatch.commit().await()
            }

            // 4. Restore missing loans
            val existingLoanIds = repository.db.collection("loans").get().await().documents.map { it.id }.toSet()
            val missingLoans = parsed.loans.filter { it.id !in existingLoanIds }
            if (missingLoans.isNotEmpty()) {
                val loanBatch = repository.db.batch()
                for (loan in missingLoans) {
                    loanBatch.set(
                        repository.db.collection("loans").document(loan.id),
                        mapOf(
                            "direction" to loan.direction,
                            "personName" to loan.personName,
                            "phone" to loan.phone,
                            "accountId" to loan.accountId,
                            "principalPaisa" to loan.principalPaisa,
                            "interestPaisa" to loan.interestPaisa,
                            "paidPrincipalPaisa" to loan.paidPrincipalPaisa,
                            "paidInterestPaisa" to loan.paidInterestPaisa,
                            "startDateMillis" to loan.startDateMillis,
                            "dueDateMillis" to loan.dueDateMillis,
                            "status" to loan.status,
                            "note" to loan.note,
                            "createdByUid" to uid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                loanBatch.commit().await()
            }

            repository.writeAuditLog(
                action = "BACKUP_RESTORED",
                actorUid = uid,
                actorEmail = email,
                targetId = safetyId,
                summary = "ব্যাকআপ রিস্টোর সম্পন্ন (Safety Backup: $safetyFileName)"
            )

            Result.success(safetyFileName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "restore")
            Result.failure(e)
        }
    }
}
