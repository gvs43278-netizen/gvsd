package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.Account
import com.example.domain.AccountingEngine
import com.example.domain.AuditLog
import com.example.domain.BalanceAddSource
import com.example.domain.BusinessConstants
import com.example.domain.EmployeeAccess
import com.example.domain.LedgerTransaction
import com.example.domain.LoanRecord
import com.example.domain.LoanRepayment
import com.example.domain.OwnerConfig
import com.example.domain.Partner
import com.example.domain.PendingTransaction
import com.example.domain.SafetyBackupRecord
import com.example.domain.TxDraft
import com.example.domain.TxType
import com.example.domain.UserRoleState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

class BusinessRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("লগইন ছাড়া Firestore অ্যাক্সেস করা যাবে না।")
    }

    private fun requireUserEmail(): String {
        return auth.currentUser?.email?.lowercase(Locale.ENGLISH)?.trim()
            ?: throw IllegalStateException("ব্যবহারকারীর ইমেইল পাওয়া যায়নি।")
    }

    /**
     * Strictly verifies Firebase Auth UID & email against /app_config/owner and /employee_access/{email}.
     * NEVER guesses or auto-grants Owner access if UID does not match /app_config/owner.ownerUid.
     */
    suspend fun resolveUserRole(): UserRoleState {
        val user = auth.currentUser ?: return UserRoleState.Unauthorized("", "", "সাইন ইন করা নেই")
        val uid = user.uid
        val email = (user.email ?: "").lowercase(Locale.ENGLISH).trim()
        val emailVerified = user.isEmailVerified

        if (!emailVerified || email.isBlank()) {
            return UserRoleState.Unauthorized(uid, email, "যাচাইকৃত Google ইমেইল পাওয়া যায়নি।")
        }

        if (email == BusinessConstants.OWNER_EMAIL) {
            return try {
                val snap = db.collection("app_config").document("owner").get().await()
                if (!snap.exists()) {
                    UserRoleState.OwnerNeedsUidVerification(
                        currentUid = uid,
                        email = email,
                        storedOwnerUid = BusinessConstants.LEGACY_SUPER_ADMIN_UID
                    )
                } else {
                    val config = snap.toObject(OwnerConfig::class.java)
                    if (config != null && config.ownerUid == uid && config.ownerEmail == BusinessConstants.OWNER_EMAIL) {
                        ensureBusinessDefaultsSeeded(uid)
                        UserRoleState.VerifiedOwner(uid = uid, email = email)
                    } else {
                        UserRoleState.OwnerNeedsUidVerification(
                            currentUid = uid,
                            email = email,
                            storedOwnerUid = config?.ownerUid ?: BusinessConstants.LEGACY_SUPER_ADMIN_UID
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, "app_config/owner")
                UserRoleState.Unauthorized(uid, email, toBengaliFirestoreMessage(e))
            }
        }

        return try {
            val empSnap = db.collection("employee_access").document(email).get().await()
            if (empSnap.exists()) {
                val emp = empSnap.toObject(EmployeeAccess::class.java)
                if (emp != null && emp.active) {
                    UserRoleState.ActiveEmployee(uid = uid, email = email, name = emp.name.ifBlank { email })
                } else {
                    UserRoleState.Unauthorized(uid, email, "আপনার কর্মচারী অ্যাকাউন্টটি বর্তমানে নিষ্ক্রিয় আছে।")
                }
            } else {
                UserRoleState.Unauthorized(
                    uid = uid,
                    email = email,
                    reason = "এই Gmail ($email) এখনও Owner কর্তৃক অনুমোদিত নয়।"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "employee_access/$email")
            UserRoleState.Unauthorized(uid, email, toBengaliFirestoreMessage(e))
        }
    }

    /**
     * Safely binds verified Owner's real Firebase Auth UID into /app_config/owner,
     * initializes all 11 accounts at ৳0 and 3 partners if missing, and writes an Audit Log.
     */
    suspend fun verifyAndBindOwnerUid(): Result<Unit> {
        val docPath = "app_config/owner"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            require(email == BusinessConstants.OWNER_EMAIL && auth.currentUser?.isEmailVerified == true) {
                "শুধুমাত্র যাচাইকৃত Owner Gmail (${BusinessConstants.OWNER_EMAIL}) UID আপডেট করতে পারবেন।"
            }

            val payload = mapOf(
                "ownerEmail" to BusinessConstants.OWNER_EMAIL,
                "ownerUid" to uid,
                "legacySuperAdminUid" to BusinessConstants.LEGACY_SUPER_ADMIN_UID,
                "uidVerified" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("app_config").document("owner").set(payload).await()
            ensureBusinessDefaultsSeeded(uid)
            writeAuditLog(
                action = "OWNER_UID_VERIFIED",
                actorUid = uid,
                actorEmail = email,
                targetId = "owner",
                summary = "Owner UID নিরাপদভাবে যাচাই ও আপডেট করা হয়েছে: $uid"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Seeds all 11 accounts (initial balance ৳0) and 3 partners only if they do not exist yet.
     * Never overwrites existing balances or creates demo transactions.
     */
    private suspend fun ensureBusinessDefaultsSeeded(ownerUid: String) {
        val existingAccounts = db.collection("accounts").get().await()
        val existingAccIds = existingAccounts.documents.map { it.id }.toSet()

        val existingPartners = db.collection("partners").get().await()
        val existingPartnerIds = existingPartners.documents.map { it.id }.toSet()

        val missingAccounts = BusinessConstants.DEFAULT_ACCOUNTS.filter { it.id !in existingAccIds }
        val missingPartners = BusinessConstants.DEFAULT_PARTNERS.filter { it.id !in existingPartnerIds }

        if (missingAccounts.isEmpty() && missingPartners.isEmpty()) return

        val batch = db.batch()
        for (acc in missingAccounts) {
            val ref = db.collection("accounts").document(acc.id)
            batch.set(
                ref,
                mapOf(
                    "name" to acc.name,
                    "category" to acc.category,
                    "lastFourDigits" to acc.lastFourDigits,
                    "balancePaisa" to 0L,
                    "sortOrder" to acc.sortOrder,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        for (partner in missingPartners) {
            val ref = db.collection("partners").document(partner.id)
            batch.set(
                ref,
                mapOf(
                    "name" to partner.name,
                    "commissionPerThousandPaisa" to partner.commissionPerThousandPaisa,
                    "bKashOnlyCommission" to partner.bKashOnlyCommission,
                    "canCustomizeCommission" to partner.canCustomizeCommission,
                    "totalSentPaisa" to 0L,
                    "totalReceivedPaisa" to 0L,
                    "totalCommissionExpensePaisa" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
    }

    // --- REALTIME SNAPSHOT FLOWS ---

    fun observeAccounts(): Flow<List<Account>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("accounts")
                .orderBy("sortOrder", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(Account::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logFlowError(error, OperationType.LIST, "accounts", uid); throw error }
        )
    }

    fun observePartners(): Flow<List<Partner>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("partners")
                .snapshots()
                .map { snap -> snap.toObjects(Partner::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logFlowError(error, OperationType.LIST, "partners", uid); throw error }
        )
    }

    fun observeTransactions(): Flow<List<LedgerTransaction>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("transactions")
                .orderBy("businessDateMillis", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(LedgerTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logFlowError(error, OperationType.LIST, "transactions", uid); throw error }
        )
    }

    fun observeAllPendingTransactions(): Flow<List<PendingTransaction>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("pending_transactions")
                .snapshots()
                .map { snap ->
                    snap.toObjects(PendingTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.businessDateMillis }
                }
                .catch { error -> logFlowError(error, OperationType.LIST, "pending_transactions", uid); throw error }
        )
    }

    fun observeMyPendingTransactions(): Flow<List<PendingTransaction>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("pending_transactions")
                .whereEqualTo("submittedByUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PendingTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.businessDateMillis }
                }
                .catch { error -> logFlowError(error, OperationType.LIST, "pending_transactions", uid); throw error }
        )
    }

    fun observeLoans(): Flow<List<LoanRecord>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("loans")
                .snapshots()
                .map { snap ->
                    snap.toObjects(LoanRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.startDateMillis }
                }
                .catch { error -> logFlowError(error, OperationType.LIST, "loans", uid); throw error }
        )
    }

    fun observeLoanRepayments(loanId: String): Flow<List<LoanRepayment>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("loans").document(loanId).collection("repayments")
                .snapshots()
                .map { snap ->
                    snap.toObjects(LoanRepayment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.repaymentDateMillis }
                }
                .catch { error -> logFlowError(error, OperationType.LIST, "loans/$loanId/repayments", uid); throw error }
        )
    }

    fun observeEmployees(): Flow<List<EmployeeAccess>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("employee_access")
                .snapshots()
                .map { snap -> snap.toObjects(EmployeeAccess::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error -> logFlowError(error, OperationType.LIST, "employee_access", uid); throw error }
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("audit_logs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error -> logFlowError(error, OperationType.LIST, "audit_logs", uid); throw error }
        )
    }

    private fun logFlowError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val ex = firestoreError ?: (error as? Exception)
        if (ex != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(ex, op, path)
        }
    }

    // --- ATOMIC OWNER DIRECT TRANSACTION ---

    suspend fun executeOwnerDirectTransaction(
        draft: TxDraft,
        submitterName: String
    ): Result<String> {
        val path = "transactions"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            val newTxId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val dedupKey = AccountingEngine.buildDuplicateKey(draft.type, draft.externalTxId, draft.idempotencyKey)

            db.runTransaction { transaction ->
                // 1. ALL READS FIRST
                val keyRef = db.collection("transaction_keys").document(dedupKey)
                if (transaction.get(keyRef).exists()) {
                    throw IllegalStateException("ডুপ্লিকেট লেনদেন প্রতিরোধ করা হয়েছে! এই Transaction ID বা অনুরোধ ইতিমধ্যে ব্যবহৃত হয়েছে।")
                }

                val receiveAccRef = if (draft.receiveAccountId.isNotBlank()) {
                    db.collection("accounts").document(draft.receiveAccountId)
                } else null

                val payAccRef = if (draft.payAccountId.isNotBlank() && draft.payAccountId != draft.receiveAccountId) {
                    db.collection("accounts").document(draft.payAccountId)
                } else if (draft.payAccountId.isNotBlank() && draft.payAccountId == draft.receiveAccountId) {
                    receiveAccRef
                } else null

                val receiveSnap = receiveAccRef?.let { transaction.get(it) }
                val paySnap = if (payAccRef != null && payAccRef != receiveAccRef) {
                    transaction.get(payAccRef)
                } else if (payAccRef == receiveAccRef) {
                    receiveSnap
                } else null

                val partnerRef = if (draft.partnerId.isNotBlank()) {
                    db.collection("partners").document(draft.partnerId)
                } else null
                val partnerSnap = partnerRef?.let { transaction.get(it) }

                // Validate balances & compute deltas
                if (receiveAccRef != null && (receiveSnap == null || !receiveSnap.exists())) {
                    throw IllegalStateException("গ্রহণকারী অ্যাকাউন্ট (${draft.receiveAccountId}) পাওয়া যায়নি।")
                }
                if (payAccRef != null && (paySnap == null || !paySnap.exists())) {
                    throw IllegalStateException("প্রদানকারী অ্যাকাউন্ট (${draft.payAccountId}) পাওয়া যায়নি।")
                }

                val sameAccount = receiveAccRef != null && payAccRef != null && receiveAccRef.path == payAccRef.path
                if (sameAccount && draft.type == TxType.INTERNAL_TRANSFER) {
                    throw IllegalStateException("প্রেরক ও প্রাপক অ্যাকাউন্ট একই হতে পারবে না।")
                }

                if (sameAccount && receiveSnap != null) {
                    val currentBal = receiveSnap.getLong("balancePaisa") ?: 0L
                    val netDelta = draft.receiveAmountPaisa - draft.payAmountPaisa
                    val newBal = currentBal + netDelta
                    if (newBal < 0L) {
                        val name = receiveSnap.getString("name") ?: draft.payAccountId
                        throw IllegalStateException("$name অ্যাকাউন্টে পর্যাপ্ত ব্যালেন্স নেই।")
                    }
                    transaction.update(
                        receiveAccRef!!,
                        mapOf("balancePaisa" to newBal, "updatedAt" to FieldValue.serverTimestamp())
                    )
                } else {
                    if (payAccRef != null && paySnap != null && draft.payAmountPaisa > 0L) {
                        val currentPay = paySnap.getLong("balancePaisa") ?: 0L
                        val newPay = currentPay - draft.payAmountPaisa
                        if (newPay < 0L) {
                            val name = paySnap.getString("name") ?: draft.payAccountId
                            throw IllegalStateException("$name অ্যাকাউন্টে পর্যাপ্ত ব্যালেন্স নেই।")
                        }
                        transaction.update(
                            payAccRef,
                            mapOf("balancePaisa" to newPay, "updatedAt" to FieldValue.serverTimestamp())
                        )
                    }
                    if (receiveAccRef != null && receiveSnap != null && draft.receiveAmountPaisa > 0L) {
                        val currentRec = receiveSnap.getLong("balancePaisa") ?: 0L
                        val newRec = currentRec + draft.receiveAmountPaisa
                        transaction.update(
                            receiveAccRef,
                            mapOf("balancePaisa" to newRec, "updatedAt" to FieldValue.serverTimestamp())
                        )
                    }
                }

                // Partner ledger updates
                if (partnerRef != null && partnerSnap != null && partnerSnap.exists()) {
                    val sent = partnerSnap.getLong("totalSentPaisa") ?: 0L
                    val received = partnerSnap.getLong("totalReceivedPaisa") ?: 0L
                    val commExp = partnerSnap.getLong("totalCommissionExpensePaisa") ?: 0L
                    when (draft.type) {
                        TxType.PARTNER_SEND -> {
                            transaction.update(
                                partnerRef,
                                mapOf(
                                    "totalSentPaisa" to (sent + draft.payAmountPaisa),
                                    "totalCommissionExpensePaisa" to (commExp + draft.commissionExpensePaisa),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                        TxType.PARTNER_RECEIVE -> {
                            transaction.update(
                                partnerRef,
                                mapOf(
                                    "totalReceivedPaisa" to (received + draft.receiveAmountPaisa),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                }

                // Write duplicate-protection key
                transaction.set(
                    keyRef,
                    mapOf(
                        "key" to dedupKey,
                        "txId" to newTxId,
                        "createdByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                // Write immutable ledger transaction
                val txRef = db.collection("transactions").document(newTxId)
                transaction.set(
                    txRef,
                    buildTransactionMap(
                        draft = draft,
                        idempotencyKey = dedupKey,
                        submittedByUid = uid,
                        submittedByName = submitterName,
                        approvedByUid = uid
                    )
                )

                // Write audit log
                val auditId = "aud_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                transaction.set(
                    db.collection("audit_logs").document(auditId),
                    mapOf(
                        "action" to draft.type,
                        "actorUid" to uid,
                        "actorEmail" to email,
                        "targetId" to newTxId,
                        "summary" to draft.title,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                newTxId
            }.await()

            Result.success(newTxId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- EMPLOYEE PENDING SUBMISSION ---

    suspend fun submitEmployeePendingTransaction(
        draft: TxDraft,
        submitterName: String
    ): Result<String> {
        val path = "pending_transactions"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            val pendingId = "pend_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val dedupKey = AccountingEngine.buildDuplicateKey(draft.type, draft.externalTxId, draft.idempotencyKey)

            // Pre-check duplicate externalTxId key before submitting
            val keySnap = db.collection("transaction_keys").document(dedupKey).get().await()
            if (keySnap.exists()) {
                return Result.failure(
                    IllegalStateException("ডুপ্লিকেট লেনদেন প্রতিরোধ করা হয়েছে! এই Transaction ID ইতিমধ্যে অনুমোদিত লেনদেনে আছে।")
                )
            }

            val payload = mapOf(
                "status" to "PENDING",
                "type" to draft.type,
                "subType" to draft.subType,
                "title" to draft.title.take(160),
                "partyName" to draft.partyName.take(120),
                "phone" to draft.phone.take(30),
                "receiveAccountId" to draft.receiveAccountId,
                "receiveAmountPaisa" to draft.receiveAmountPaisa,
                "payAccountId" to draft.payAccountId,
                "payAmountPaisa" to draft.payAmountPaisa,
                "commissionIncomePaisa" to draft.commissionIncomePaisa,
                "commissionExpensePaisa" to draft.commissionExpensePaisa,
                "expenseAmountPaisa" to draft.expenseAmountPaisa,
                "interestIncomePaisa" to draft.interestIncomePaisa,
                "interestExpensePaisa" to draft.interestExpensePaisa,
                "capitalDeltaPaisa" to draft.capitalDeltaPaisa,
                "externalTxId" to draft.externalTxId.take(100),
                "idempotencyKey" to dedupKey,
                "note" to draft.note.take(500),
                "businessDateMillis" to draft.businessDateMillis,
                "partnerId" to draft.partnerId,
                "loanId" to draft.loanId,
                "operatorName" to draft.operatorName.take(60),
                "submittedByUid" to uid,
                "submittedByEmail" to email,
                "submittedByName" to submitterName.take(120),
                "reviewedByUid" to "",
                "approvedTxId" to "",
                "rejectionReason" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("pending_transactions").document(pendingId).set(payload).await()
            writeAuditLog(
                action = "PENDING_SUBMITTED",
                actorUid = uid,
                actorEmail = email,
                targetId = pendingId,
                summary = "কর্মচারী Pending লেনদেন জমা দিয়েছেন: ${draft.title}"
            )
            Result.success(pendingId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- OWNER APPROVAL / REJECTION OF PENDING TRANSACTIONS ---

    suspend fun approvePendingTransaction(pending: PendingTransaction): Result<String> {
        val path = "pending_transactions/${pending.id}"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            val newTxId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val dedupKey = AccountingEngine.buildDuplicateKey(pending.type, pending.externalTxId, pending.idempotencyKey)

            db.runTransaction { transaction ->
                val pendRef = db.collection("pending_transactions").document(pending.id)
                val pendSnap = transaction.get(pendRef)
                if (!pendSnap.exists() || pendSnap.getString("status") != "PENDING") {
                    throw IllegalStateException("এই লেনদেনটি ইতিমধ্যে অনুমোদিত বা বাতিল হয়েছে।")
                }

                val keyRef = db.collection("transaction_keys").document(dedupKey)
                if (transaction.get(keyRef).exists()) {
                    throw IllegalStateException("ডুপ্লিকেট লেনদেন প্রতিরোধ করা হয়েছে! এই Transaction ID ইতিমধ্যে ব্যবহৃত হয়েছে।")
                }

                val receiveAccRef = if (pending.receiveAccountId.isNotBlank()) {
                    db.collection("accounts").document(pending.receiveAccountId)
                } else null
                val payAccRef = if (pending.payAccountId.isNotBlank() && pending.payAccountId != pending.receiveAccountId) {
                    db.collection("accounts").document(pending.payAccountId)
                } else if (pending.payAccountId.isNotBlank() && pending.payAccountId == pending.receiveAccountId) {
                    receiveAccRef
                } else null

                val receiveSnap = receiveAccRef?.let { transaction.get(it) }
                val paySnap = if (payAccRef != null && payAccRef != receiveAccRef) {
                    transaction.get(payAccRef)
                } else if (payAccRef == receiveAccRef) {
                    receiveSnap
                } else null

                val partnerRef = if (pending.partnerId.isNotBlank()) {
                    db.collection("partners").document(pending.partnerId)
                } else null
                val partnerSnap = partnerRef?.let { transaction.get(it) }

                val sameAccount = receiveAccRef != null && payAccRef != null && receiveAccRef.path == payAccRef.path
                if (sameAccount && receiveSnap != null) {
                    val cur = receiveSnap.getLong("balancePaisa") ?: 0L
                    val next = cur + pending.receiveAmountPaisa - pending.payAmountPaisa
                    if (next < 0L) throw IllegalStateException("অ্যাকাউন্টে পর্যাপ্ত ব্যালেন্স নেই।")
                    transaction.update(receiveAccRef!!, mapOf("balancePaisa" to next, "updatedAt" to FieldValue.serverTimestamp()))
                } else {
                    if (payAccRef != null && paySnap != null && pending.payAmountPaisa > 0L) {
                        val curPay = paySnap.getLong("balancePaisa") ?: 0L
                        val nextPay = curPay - pending.payAmountPaisa
                        if (nextPay < 0L) {
                            val name = paySnap.getString("name") ?: pending.payAccountId
                            throw IllegalStateException("$name অ্যাকাউন্টে পর্যাপ্ত ব্যালেন্স নেই।")
                        }
                        transaction.update(payAccRef, mapOf("balancePaisa" to nextPay, "updatedAt" to FieldValue.serverTimestamp()))
                    }
                    if (receiveAccRef != null && receiveSnap != null && pending.receiveAmountPaisa > 0L) {
                        val curRec = receiveSnap.getLong("balancePaisa") ?: 0L
                        val nextRec = curRec + pending.receiveAmountPaisa
                        transaction.update(receiveAccRef, mapOf("balancePaisa" to nextRec, "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }

                if (partnerRef != null && partnerSnap != null && partnerSnap.exists()) {
                    val sent = partnerSnap.getLong("totalSentPaisa") ?: 0L
                    val received = partnerSnap.getLong("totalReceivedPaisa") ?: 0L
                    val commExp = partnerSnap.getLong("totalCommissionExpensePaisa") ?: 0L
                    when (pending.type) {
                        TxType.PARTNER_SEND -> transaction.update(
                            partnerRef,
                            mapOf(
                                "totalSentPaisa" to (sent + pending.payAmountPaisa),
                                "totalCommissionExpensePaisa" to (commExp + pending.commissionExpensePaisa),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        TxType.PARTNER_RECEIVE -> transaction.update(
                            partnerRef,
                            mapOf(
                                "totalReceivedPaisa" to (received + pending.receiveAmountPaisa),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                transaction.set(
                    keyRef,
                    mapOf(
                        "key" to dedupKey,
                        "txId" to newTxId,
                        "createdByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                val draft = TxDraft(
                    type = pending.type,
                    subType = pending.subType,
                    title = pending.title,
                    partyName = pending.partyName,
                    phone = pending.phone,
                    receiveAccountId = pending.receiveAccountId,
                    receiveAmountPaisa = pending.receiveAmountPaisa,
                    payAccountId = pending.payAccountId,
                    payAmountPaisa = pending.payAmountPaisa,
                    commissionIncomePaisa = pending.commissionIncomePaisa,
                    commissionExpensePaisa = pending.commissionExpensePaisa,
                    expenseAmountPaisa = pending.expenseAmountPaisa,
                    interestIncomePaisa = pending.interestIncomePaisa,
                    interestExpensePaisa = pending.interestExpensePaisa,
                    capitalDeltaPaisa = pending.capitalDeltaPaisa,
                    externalTxId = pending.externalTxId,
                    idempotencyKey = dedupKey,
                    note = pending.note,
                    businessDateMillis = pending.businessDateMillis,
                    partnerId = pending.partnerId,
                    loanId = pending.loanId,
                    operatorName = pending.operatorName
                )

                transaction.set(
                    db.collection("transactions").document(newTxId),
                    buildTransactionMap(
                        draft = draft,
                        idempotencyKey = dedupKey,
                        submittedByUid = pending.submittedByUid,
                        submittedByName = pending.submittedByName,
                        approvedByUid = uid
                    )
                )

                transaction.update(
                    pendRef,
                    mapOf(
                        "status" to "APPROVED",
                        "reviewedByUid" to uid,
                        "approvedTxId" to newTxId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val auditId = "aud_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                transaction.set(
                    db.collection("audit_logs").document(auditId),
                    mapOf(
                        "action" to "TX_APPROVED",
                        "actorUid" to uid,
                        "actorEmail" to email,
                        "targetId" to newTxId,
                        "summary" to "Pending অনুমোদন: ${pending.title} (${pending.submittedByName})",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                newTxId
            }.await()

            Result.success(newTxId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectPendingTransaction(pendingId: String, reason: String): Result<Unit> {
        val path = "pending_transactions/$pendingId"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            db.collection("pending_transactions").document(pendingId).update(
                mapOf(
                    "status" to "REJECTED",
                    "reviewedByUid" to uid,
                    "rejectionReason" to reason.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                action = "TX_REJECTED",
                actorUid = uid,
                actorEmail = email,
                targetId = pendingId,
                summary = "Pending বাতিল: $reason"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- OWNER TRANSACTION REVERSAL ---

    suspend fun reverseTransaction(tx: LedgerTransaction, reason: String): Result<String> {
        val path = "transactions/${tx.id}"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            val revTxId = "rev_${UUID.randomUUID().toString().replace("-", "").take(16)}"

            db.runTransaction { transaction ->
                val origRef = db.collection("transactions").document(tx.id)
                val origSnap = transaction.get(origRef)
                if (!origSnap.exists()) throw IllegalStateException("লেনদেন পাওয়া যায়নি।")
                if (origSnap.getBoolean("reversed") == true) {
                    throw IllegalStateException("এই লেনদেনটি ইতিমধ্যে Reversal করা হয়েছে।")
                }

                val receiveAccRef = if (tx.receiveAccountId.isNotBlank()) db.collection("accounts").document(tx.receiveAccountId) else null
                val payAccRef = if (tx.payAccountId.isNotBlank()) db.collection("accounts").document(tx.payAccountId) else null

                val receiveSnap = receiveAccRef?.let { transaction.get(it) }
                val paySnap = if (payAccRef != null && payAccRef != receiveAccRef) {
                    transaction.get(payAccRef)
                } else if (payAccRef == receiveAccRef) {
                    receiveSnap
                } else null

                val partnerRef = if (tx.partnerId.isNotBlank()) db.collection("partners").document(tx.partnerId) else null
                val partnerSnap = partnerRef?.let { transaction.get(it) }

                val sameAccount = receiveAccRef != null && payAccRef != null && receiveAccRef.path == payAccRef.path
                if (sameAccount && receiveSnap != null) {
                    val cur = receiveSnap.getLong("balancePaisa") ?: 0L
                    val restored = cur - tx.receiveAmountPaisa + tx.payAmountPaisa
                    transaction.update(receiveAccRef!!, mapOf("balancePaisa" to restored, "updatedAt" to FieldValue.serverTimestamp()))
                } else {
                    if (receiveAccRef != null && receiveSnap != null && tx.receiveAmountPaisa > 0L) {
                        val cur = receiveSnap.getLong("balancePaisa") ?: 0L
                        transaction.update(receiveAccRef, mapOf("balancePaisa" to (cur - tx.receiveAmountPaisa), "updatedAt" to FieldValue.serverTimestamp()))
                    }
                    if (payAccRef != null && paySnap != null && tx.payAmountPaisa > 0L) {
                        val cur = paySnap.getLong("balancePaisa") ?: 0L
                        transaction.update(payAccRef, mapOf("balancePaisa" to (cur + tx.payAmountPaisa), "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }

                if (partnerRef != null && partnerSnap != null && partnerSnap.exists()) {
                    val sent = partnerSnap.getLong("totalSentPaisa") ?: 0L
                    val received = partnerSnap.getLong("totalReceivedPaisa") ?: 0L
                    val commExp = partnerSnap.getLong("totalCommissionExpensePaisa") ?: 0L
                    when (tx.type) {
                        TxType.PARTNER_SEND -> transaction.update(
                            partnerRef,
                            mapOf(
                                "totalSentPaisa" to (sent - tx.payAmountPaisa).coerceAtLeast(0L),
                                "totalCommissionExpensePaisa" to (commExp - tx.commissionExpensePaisa).coerceAtLeast(0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        TxType.PARTNER_RECEIVE -> transaction.update(
                            partnerRef,
                            mapOf(
                                "totalReceivedPaisa" to (received - tx.receiveAmountPaisa).coerceAtLeast(0L),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                transaction.update(
                    origRef,
                    mapOf(
                        "reversed" to true,
                        "reversedByTxId" to revTxId,
                        "reversalReason" to reason.take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val revPayload = mapOf(
                    "type" to TxType.REVERSAL,
                    "subType" to tx.type,
                    "title" to "রিভার্সাল: ${tx.title}".take(160),
                    "partyName" to tx.partyName,
                    "phone" to tx.phone,
                    "receiveAccountId" to tx.payAccountId,
                    "receiveAmountPaisa" to tx.payAmountPaisa,
                    "payAccountId" to tx.receiveAccountId,
                    "payAmountPaisa" to tx.receiveAmountPaisa,
                    "commissionIncomePaisa" to 0L,
                    "commissionExpensePaisa" to 0L,
                    "expenseAmountPaisa" to 0L,
                    "interestIncomePaisa" to 0L,
                    "interestExpensePaisa" to 0L,
                    "capitalDeltaPaisa" to (-tx.capitalDeltaPaisa),
                    "externalTxId" to "",
                    "idempotencyKey" to "rev_${tx.id}",
                    "note" to reason.take(500),
                    "businessDateMillis" to System.currentTimeMillis(),
                    "partnerId" to tx.partnerId,
                    "loanId" to tx.loanId,
                    "operatorName" to tx.operatorName,
                    "submittedByUid" to uid,
                    "submittedByName" to "Owner",
                    "approvedByUid" to uid,
                    "reversed" to false,
                    "reversedByTxId" to "",
                    "reversalOfTxId" to tx.id,
                    "reversalReason" to reason.take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(db.collection("transactions").document(revTxId), revPayload)

                val auditId = "aud_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                transaction.set(
                    db.collection("audit_logs").document(auditId),
                    mapOf(
                        "action" to "TX_REVERSED",
                        "actorUid" to uid,
                        "actorEmail" to email,
                        "targetId" to tx.id,
                        "summary" to "লেনদেন রিভার্সাল (${tx.title}): $reason",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                revTxId
            }.await()

            Result.success(revTxId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- LOANS & REPAYMENTS ---

    suspend fun createLoan(
        direction: String,
        personName: String,
        phone: String,
        accountId: String,
        principalPaisa: Long,
        interestPaisa: Long,
        startDateMillis: Long,
        dueDateMillis: Long,
        note: String
    ): Result<String> {
        val path = "loans"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            val loanId = "loan_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val dedupKey = "req_$loanId"

            db.runTransaction { transaction ->
                val accRef = db.collection("accounts").document(accountId)
                val accSnap = transaction.get(accRef)
                if (!accSnap.exists()) throw IllegalStateException("অ্যাকাউন্ট পাওয়া যায়নি।")

                val currentBal = accSnap.getLong("balancePaisa") ?: 0L
                val nextBal = if (direction == "PAYABLE") {
                    currentBal + principalPaisa
                } else {
                    val after = currentBal - principalPaisa
                    if (after < 0L) throw IllegalStateException("ধার দেওয়ার জন্য পর্যাপ্ত ব্যালেন্স নেই।")
                    after
                }

                transaction.update(accRef, mapOf("balancePaisa" to nextBal, "updatedAt" to FieldValue.serverTimestamp()))

                transaction.set(
                    db.collection("loans").document(loanId),
                    mapOf(
                        "direction" to direction,
                        "personName" to personName.take(120),
                        "phone" to phone.take(30),
                        "accountId" to accountId,
                        "principalPaisa" to principalPaisa,
                        "interestPaisa" to interestPaisa,
                        "paidPrincipalPaisa" to 0L,
                        "paidInterestPaisa" to 0L,
                        "startDateMillis" to startDateMillis,
                        "dueDateMillis" to dueDateMillis,
                        "status" to "ACTIVE",
                        "note" to note.take(500),
                        "createdByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val title = if (direction == "PAYABLE") "ধার গ্রহণ: $personName" else "ধার প্রদান: $personName"
                val draft = TxDraft(
                    type = TxType.LOAN_DISBURSE,
                    subType = direction,
                    title = title,
                    partyName = personName,
                    phone = phone,
                    receiveAccountId = if (direction == "PAYABLE") accountId else "",
                    receiveAmountPaisa = if (direction == "PAYABLE") principalPaisa else 0L,
                    payAccountId = if (direction == "RECEIVABLE") accountId else "",
                    payAmountPaisa = if (direction == "RECEIVABLE") principalPaisa else 0L,
                    note = note,
                    businessDateMillis = startDateMillis,
                    loanId = loanId
                )

                transaction.set(
                    db.collection("transactions").document(txId),
                    buildTransactionMap(draft, dedupKey, uid, "Owner", uid)
                )

                transaction.set(
                    db.collection("transaction_keys").document(dedupKey),
                    mapOf("key" to dedupKey, "txId" to txId, "createdByUid" to uid, "createdAt" to FieldValue.serverTimestamp())
                )

                val auditId = "aud_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                transaction.set(
                    db.collection("audit_logs").document(auditId),
                    mapOf(
                        "action" to "LOAN_CREATED",
                        "actorUid" to uid,
                        "actorEmail" to email,
                        "targetId" to loanId,
                        "summary" to title,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                loanId
            }.await()

            Result.success(loanId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordLoanRepayment(
        loan: LoanRecord,
        principalPayPaisa: Long,
        interestPayPaisa: Long,
        accountId: String,
        repaymentDateMillis: Long,
        note: String
    ): Result<Unit> {
        val path = "loans/${loan.id}"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            val repId = "rep_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val dedupKey = "req_$repId"

            db.runTransaction { transaction ->
                val loanRef = db.collection("loans").document(loan.id)
                val loanSnap = transaction.get(loanRef)
                if (!loanSnap.exists()) throw IllegalStateException("ঋণের রেকর্ড পাওয়া যায়নি।")

                val accRef = db.collection("accounts").document(accountId)
                val accSnap = transaction.get(accRef)
                if (!accSnap.exists()) throw IllegalStateException("অ্যাকাউন্ট পাওয়া যায়নি।")

                val principalTotal = loanSnap.getLong("principalPaisa") ?: 0L
                val interestTotal = loanSnap.getLong("interestPaisa") ?: 0L
                val paidP = loanSnap.getLong("paidPrincipalPaisa") ?: 0L
                val paidI = loanSnap.getLong("paidInterestPaisa") ?: 0L

                val newPaidP = paidP + principalPayPaisa
                val newPaidI = paidI + interestPayPaisa
                if (newPaidP > principalTotal || newPaidI > interestTotal) {
                    throw IllegalStateException("পরিশোধের পরিমাণ বাকি আসল বা সুদের চেয়ে বেশি হতে পারবে না।")
                }

                val totalOutOrIn = principalPayPaisa + interestPayPaisa
                val curBal = accSnap.getLong("balancePaisa") ?: 0L
                val nextBal = if (loan.direction == "PAYABLE") {
                    val after = curBal - totalOutOrIn
                    if (after < 0L) throw IllegalStateException("ঋণ পরিশোধের জন্য পর্যাপ্ত ব্যালেন্স নেই।")
                    after
                } else {
                    curBal + totalOutOrIn
                }

                val newStatus = if (newPaidP >= principalTotal && newPaidI >= interestTotal) "PAID" else "PARTIAL"

                transaction.update(accRef, mapOf("balancePaisa" to nextBal, "updatedAt" to FieldValue.serverTimestamp()))
                transaction.update(
                    loanRef,
                    mapOf(
                        "paidPrincipalPaisa" to newPaidP,
                        "paidInterestPaisa" to newPaidI,
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                transaction.set(
                    loanRef.collection("repayments").document(repId),
                    mapOf(
                        "loanId" to loan.id,
                        "principalPaidPaisa" to principalPayPaisa,
                        "interestPaidPaisa" to interestPayPaisa,
                        "accountId" to accountId,
                        "repaymentDateMillis" to repaymentDateMillis,
                        "note" to note.take(300),
                        "txId" to txId,
                        "createdByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                val title = if (loan.direction == "PAYABLE") "ঋণ পরিশোধ: ${loan.personName}" else "পাওনা আদায়: ${loan.personName}"
                val draft = TxDraft(
                    type = TxType.LOAN_REPAYMENT,
                    subType = loan.direction,
                    title = title,
                    partyName = loan.personName,
                    phone = loan.phone,
                    receiveAccountId = if (loan.direction == "RECEIVABLE") accountId else "",
                    receiveAmountPaisa = if (loan.direction == "RECEIVABLE") totalOutOrIn else 0L,
                    payAccountId = if (loan.direction == "PAYABLE") accountId else "",
                    payAmountPaisa = if (loan.direction == "PAYABLE") totalOutOrIn else 0L,
                    interestIncomePaisa = if (loan.direction == "RECEIVABLE") interestPayPaisa else 0L,
                    interestExpensePaisa = if (loan.direction == "PAYABLE") interestPayPaisa else 0L,
                    note = note,
                    businessDateMillis = repaymentDateMillis,
                    loanId = loan.id
                )

                transaction.set(
                    db.collection("transactions").document(txId),
                    buildTransactionMap(draft, dedupKey, uid, "Owner", uid)
                )
                transaction.set(
                    db.collection("transaction_keys").document(dedupKey),
                    mapOf("key" to dedupKey, "txId" to txId, "createdByUid" to uid, "createdAt" to FieldValue.serverTimestamp())
                )

                val auditId = "aud_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                transaction.set(
                    db.collection("audit_logs").document(auditId),
                    mapOf(
                        "action" to "LOAN_REPAYMENT",
                        "actorUid" to uid,
                        "actorEmail" to email,
                        "targetId" to loan.id,
                        "summary" to title,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- PARTNER COMMISSION SETTINGS ---

    suspend fun updatePartnerCommissionRate(partnerId: String, perThousandPaisa: Long): Result<Unit> {
        val path = "partners/$partnerId"
        return try {
            val uid = requireUserId()
            val email = requireUserEmail()
            db.collection("partners").document(partnerId).update(
                mapOf(
                    "commissionPerThousandPaisa" to perThousandPaisa.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                action = "PARTNER_COMMISSION_UPDATED",
                actorUid = uid,
                actorEmail = email,
                targetId = partnerId,
                summary = "পার্টনার ($partnerId) কমিশন রেট আপডেট: প্রতি হাজারে ${perThousandPaisa / 100.0} টাকা"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- EMPLOYEE MANAGEMENT ---

    suspend fun addEmployee(name: String, rawEmail: String): Result<Unit> {
        val emailClean = rawEmail.lowercase(Locale.ENGLISH).trim()
        val path = "employee_access/$emailClean"
        return try {
            val uid = requireUserId()
            val ownerEmail = requireUserEmail()
            require(emailClean != BusinessConstants.OWNER_EMAIL) { "Owner ইমেইলকে কর্মচারী হিসেবে যোগ করা যাবে না।" }
            require(emailClean.contains("@") && emailClean.contains(".")) { "সঠিক Gmail ঠিকানা লিখুন।" }

            db.collection("employee_access").document(emailClean).set(
                mapOf(
                    "email" to emailClean,
                    "name" to name.trim().take(100),
                    "role" to "EMPLOYEE",
                    "active" to true,
                    "linkedUid" to "",
                    "addedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            writeAuditLog(
                action = "EMPLOYEE_ADDED",
                actorUid = uid,
                actorEmail = ownerEmail,
                targetId = emailClean,
                summary = "নতুন কর্মচারী যুক্ত: $name ($emailClean)"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleEmployeeActive(employee: EmployeeAccess): Result<Unit> {
        val path = "employee_access/${employee.email}"
        return try {
            val uid = requireUserId()
            val ownerEmail = requireUserEmail()
            val nextActive = !employee.active
            db.collection("employee_access").document(employee.email).update(
                mapOf(
                    "active" to nextActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                action = "EMPLOYEE_UPDATED",
                actorUid = uid,
                actorEmail = ownerEmail,
                targetId = employee.email,
                summary = "${employee.name} অবস্থা পরিবর্তন: ${if (nextActive) "সক্রিয়" else "নিষ্ক্রিয়"}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeEmployee(employeeEmail: String): Result<Unit> {
        val path = "employee_access/$employeeEmail"
        return try {
            val uid = requireUserId()
            val ownerEmail = requireUserEmail()
            db.collection("employee_access").document(employeeEmail).delete().await()
            writeAuditLog(
                action = "EMPLOYEE_REMOVED",
                actorUid = uid,
                actorEmail = ownerEmail,
                targetId = employeeEmail,
                summary = "কর্মচারী বাদ দেওয়া হয়েছে: $employeeEmail"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun writeAuditLog(
        action: String,
        actorUid: String,
        actorEmail: String,
        targetId: String,
        summary: String
    ) {
        try {
            val logId = "aud_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            db.collection("audit_logs").document(logId).set(
                mapOf(
                    "action" to action.take(60),
                    "actorUid" to actorUid,
                    "actorEmail" to actorEmail.take(128),
                    "targetId" to targetId.take(128),
                    "summary" to summary.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "audit_logs")
        }
    }

    private fun buildTransactionMap(
        draft: TxDraft,
        idempotencyKey: String,
        submittedByUid: String,
        submittedByName: String,
        approvedByUid: String
    ): Map<String, Any> = mapOf(
        "type" to draft.type,
        "subType" to draft.subType,
        "title" to draft.title.take(160),
        "partyName" to draft.partyName.take(120),
        "phone" to draft.phone.take(30),
        "receiveAccountId" to draft.receiveAccountId,
        "receiveAmountPaisa" to draft.receiveAmountPaisa,
        "payAccountId" to draft.payAccountId,
        "payAmountPaisa" to draft.payAmountPaisa,
        "commissionIncomePaisa" to draft.commissionIncomePaisa,
        "commissionExpensePaisa" to draft.commissionExpensePaisa,
        "expenseAmountPaisa" to draft.expenseAmountPaisa,
        "interestIncomePaisa" to draft.interestIncomePaisa,
        "interestExpensePaisa" to draft.interestExpensePaisa,
        "capitalDeltaPaisa" to draft.capitalDeltaPaisa,
        "externalTxId" to draft.externalTxId.take(100),
        "idempotencyKey" to idempotencyKey.take(128),
        "note" to draft.note.take(500),
        "businessDateMillis" to draft.businessDateMillis,
        "partnerId" to draft.partnerId,
        "loanId" to draft.loanId,
        "operatorName" to draft.operatorName.take(60),
        "submittedByUid" to submittedByUid,
        "submittedByName" to submittedByName.take(120),
        "approvedByUid" to approvedByUid,
        "reversed" to false,
        "reversedByTxId" to "",
        "reversalOfTxId" to "",
        "reversalReason" to "",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
