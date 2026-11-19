package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.Account
import com.example.domain.BalanceAddSource
import com.example.domain.BalanceWithdrawReason
import com.example.domain.BusinessConstants
import com.example.domain.MoneyUtils
import com.example.domain.PendingTransaction
import com.example.domain.TxDraft
import com.example.domain.TxType
import com.example.ui.components.AccountPickerField
import com.example.ui.components.BdtAmountField
import com.example.ui.components.ConfirmTransactionDialog
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusChip
import com.example.ui.components.pendingStatusColor
import com.example.ui.theme.LossRed
import com.example.ui.theme.ProfitGreen
import java.util.UUID

enum class TxFormMode(val labelBn: String) {
    CUSTOMER("গ্রাহক লেনদেন"),
    TRANSFER("অ্যাকাউন্ট ট্রান্সফার"),
    RECHARGE("মোবাইল রিচার্জ"),
    BALANCE_ADD("ব্যালেন্স যোগ"),
    BALANCE_WITHDRAW("ব্যালেন্স উত্তোলন"),
    PENDING_QUEUE("Pending তালিকা")
}

@Composable
fun TransactionsScreen(
    accounts: List<Account>,
    pendingTransactions: List<PendingTransaction>,
    isOwner: Boolean,
    isBusy: Boolean,
    initialMode: TxFormMode = TxFormMode.CUSTOMER,
    onSubmitTx: (TxDraft, () -> Unit) -> Unit,
    onApprovePending: (PendingTransaction) -> Unit,
    onRejectPending: (String, String) -> Unit
) {
    val availableModes = remember(isOwner) {
        if (isOwner) {
            TxFormMode.entries
        } else {
            listOf(TxFormMode.CUSTOMER, TxFormMode.TRANSFER, TxFormMode.RECHARGE, TxFormMode.PENDING_QUEUE)
        }
    }
    var selectedMode by remember(initialMode) {
        mutableStateOf(if (initialMode in availableModes) initialMode else TxFormMode.CUSTOMER)
    }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableModes.forEach { mode ->
                val badge = if (mode == TxFormMode.PENDING_QUEUE) {
                    val count = pendingTransactions.count { it.status == "PENDING" }
                    if (count > 0) " (${MoneyUtils.toBengaliDigits(count.toString())})" else ""
                } else ""
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = {
                        selectedMode = mode
                        localError = null
                    },
                    label = { Text("${mode.labelBn}$badge") },
                    modifier = Modifier.testTag("tx_mode_${mode.name.lowercase()}")
                )
            }
        }

        if (localError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = localError!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        when (selectedMode) {
            TxFormMode.BALANCE_ADD -> BalanceAddForm(
                accounts = accounts,
                isBusy = isBusy,
                onError = { localError = it },
                onSubmit = { draft, clear ->
                    localError = null
                    onSubmitTx(draft, clear)
                }
            )
            TxFormMode.BALANCE_WITHDRAW -> BalanceWithdrawForm(
                accounts = accounts,
                isBusy = isBusy,
                onError = { localError = it },
                onSubmit = { draft, clear ->
                    localError = null
                    onSubmitTx(draft, clear)
                }
            )
            TxFormMode.CUSTOMER -> CustomerTransactionForm(
                accounts = accounts,
                isOwner = isOwner,
                isBusy = isBusy,
                onError = { localError = it },
                onSubmit = { draft, clear ->
                    localError = null
                    onSubmitTx(draft, clear)
                }
            )
            TxFormMode.TRANSFER -> InternalTransferForm(
                accounts = accounts,
                isOwner = isOwner,
                isBusy = isBusy,
                onError = { localError = it },
                onSubmit = { draft, clear ->
                    localError = null
                    onSubmitTx(draft, clear)
                }
            )
            TxFormMode.RECHARGE -> MobileRechargeForm(
                accounts = accounts,
                isOwner = isOwner,
                isBusy = isBusy,
                onError = { localError = it },
                onSubmit = { draft, clear ->
                    localError = null
                    onSubmitTx(draft, clear)
                }
            )
            TxFormMode.PENDING_QUEUE -> PendingQueueSection(
                pendingTransactions = pendingTransactions,
                isOwner = isOwner,
                isBusy = isBusy,
                onApprove = onApprovePending,
                onReject = onRejectPending
            )
        }
    }
}

@Composable
private fun BalanceAddForm(
    accounts: List<Account>,
    isBusy: Boolean,
    onError: (String) -> Unit,
    onSubmit: (TxDraft, () -> Unit) -> Unit
) {
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id ?: BusinessConstants.ACC_CASH) }
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var source by remember { mutableStateOf(BalanceAddSource.CAPITAL) }
    var note by remember { mutableStateOf("") }
    var pendingConfirm by remember { mutableStateOf<TxDraft?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ব্যালেন্স যোগ (Balance Add)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "মূলধন যোগ করলে তা লাভ হিসেবে গণনা হবে না।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            AccountPickerField(
                label = "অ্যাকাউন্ট নির্বাচন",
                accounts = accounts,
                selectedAccountId = accountId,
                onAccountSelected = { accountId = it.id },
                testTag = "balance_add_account"
            )
        }
        item {
            BdtAmountField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "টাকার পরিমাণ",
                testTag = "balance_add_amount"
            )
        }
        item {
            Text("টাকার উৎস নির্বাচন করুন", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BalanceAddSource.CAPITAL to "মূলধন (Capital)",
                    BalanceAddSource.OTHER_TRANSFER to "অন্য উৎস থেকে স্থানান্তর",
                    BalanceAddSource.ADJUSTMENT to "অন্যান্য সমন্বয়"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = source == code,
                        onClick = { source = code },
                        label = { Text(label) }
                    )
                }
            }
        }
        item {
            DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("বিবরণ") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val paisa = MoneyUtils.parseBdtToPaisa(amountText)
                    if (paisa == null || paisa <= 0L) {
                        onError("সঠিক টাকার পরিমাণ লিখুন।")
                        return@Button
                    }
                    val accName = accounts.firstOrNull { it.id == accountId }?.displayName ?: accountId
                    val sourceLabel = when (source) {
                        BalanceAddSource.CAPITAL -> "মূলধন"
                        BalanceAddSource.OTHER_TRANSFER -> "অন্য উৎস"
                        else -> "সমন্বয়"
                    }
                    pendingConfirm = TxDraft(
                        type = TxType.BALANCE_ADD,
                        subType = source,
                        title = "ব্যালেন্স যোগ ($sourceLabel) - $accName",
                        receiveAccountId = accountId,
                        receiveAmountPaisa = paisa,
                        capitalDeltaPaisa = if (source == BalanceAddSource.CAPITAL) paisa else 0L,
                        idempotencyKey = UUID.randomUUID().toString(),
                        note = note.trim(),
                        businessDateMillis = dateMillis
                    )
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("balance_add_submit")
            ) {
                Text("ব্যালেন্স যোগ নিশ্চিত করুন")
            }
        }
    }

    pendingConfirm?.let { draft ->
        ConfirmTransactionDialog(
            title = "Balance Add নিশ্চিতকরণ",
            summaryLines = listOf(
                "অ্যাকাউন্ট" to (accounts.firstOrNull { it.id == draft.receiveAccountId }?.displayName ?: draft.receiveAccountId),
                "পরিমাণ" to MoneyUtils.formatBdt(draft.receiveAmountPaisa),
                "উৎস" to draft.subType,
                "তারিখ" to MoneyUtils.formatDateBn(draft.businessDateMillis)
            ),
            onConfirm = {
                pendingConfirm = null
                onSubmit(draft) {
                    amountText = ""
                    note = ""
                }
            },
            onDismiss = { pendingConfirm = null }
        )
    }
}

@Composable
private fun BalanceWithdrawForm(
    accounts: List<Account>,
    isBusy: Boolean,
    onError: (String) -> Unit,
    onSubmit: (TxDraft, () -> Unit) -> Unit
) {
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id ?: BusinessConstants.ACC_CASH) }
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var reason by remember { mutableStateOf(BalanceWithdrawReason.CAPITAL_WITHDRAWAL) }
    var note by remember { mutableStateOf("") }
    var pendingConfirm by remember { mutableStateOf<TxDraft?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ব্যালেন্স উত্তোলন (Balance Withdraw)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "মূলধন বা মালিকানা উত্তোলন ব্যবসার খরচ হিসেবে গণনা হবে না।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            AccountPickerField(
                label = "অ্যাকাউন্ট নির্বাচন",
                accounts = accounts,
                selectedAccountId = accountId,
                onAccountSelected = { accountId = it.id },
                testTag = "balance_withdraw_account"
            )
        }
        item {
            BdtAmountField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "উত্তোলনের পরিমাণ",
                testTag = "balance_withdraw_amount"
            )
        }
        item {
            Text("উত্তোলনের কারণ", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    BalanceWithdrawReason.CAPITAL_WITHDRAWAL to "মূলধন উত্তোলন",
                    BalanceWithdrawReason.OWNER_DRAW to "মালিকানা উত্তোলন",
                    BalanceWithdrawReason.ADJUSTMENT to "সমন্বয়"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = reason == code,
                        onClick = { reason = code },
                        label = { Text(label) }
                    )
                }
            }
        }
        item {
            DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("উত্তোলনের বিবরণ") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val paisa = MoneyUtils.parseBdtToPaisa(amountText)
                    if (paisa == null || paisa <= 0L) {
                        onError("সঠিক টাকার পরিমাণ লিখুন।")
                        return@Button
                    }
                    val accName = accounts.firstOrNull { it.id == accountId }?.displayName ?: accountId
                    pendingConfirm = TxDraft(
                        type = TxType.BALANCE_WITHDRAW,
                        subType = reason,
                        title = "ব্যালেন্স উত্তোলন - $accName",
                        payAccountId = accountId,
                        payAmountPaisa = paisa,
                        capitalDeltaPaisa = if (reason == BalanceWithdrawReason.CAPITAL_WITHDRAWAL) -paisa else 0L,
                        idempotencyKey = UUID.randomUUID().toString(),
                        note = note.trim(),
                        businessDateMillis = dateMillis
                    )
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("balance_withdraw_submit")
            ) {
                Text("উত্তোলন নিশ্চিত করুন")
            }
        }
    }

    pendingConfirm?.let { draft ->
        ConfirmTransactionDialog(
            title = "Balance Withdraw নিশ্চিতকরণ",
            summaryLines = listOf(
                "অ্যাকাউন্ট" to (accounts.firstOrNull { it.id == draft.payAccountId }?.displayName ?: draft.payAccountId),
                "পরিমাণ" to MoneyUtils.formatBdt(draft.payAmountPaisa),
                "কারণ" to draft.subType
            ),
            onConfirm = {
                pendingConfirm = null
                onSubmit(draft) {
                    amountText = ""
                    note = ""
                }
            },
            onDismiss = { pendingConfirm = null }
        )
    }
}

@Composable
private fun CustomerTransactionForm(
    accounts: List<Account>,
    isOwner: Boolean,
    isBusy: Boolean,
    onError: (String) -> Unit,
    onSubmit: (TxDraft, () -> Unit) -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var receiveAccId by remember(accounts) { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var receiveAmount by remember { mutableStateOf("") }
    var payAccId by remember(accounts) { mutableStateOf(BusinessConstants.ACC_CASH) }
    var payAmount by remember { mutableStateOf("") }
    var commissionText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var externalTxId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("গ্রাহক লেনদেন (Customer Transaction)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "গ্রহণ ও প্রদানের পরিমাণ আলাদাভাবে সংরক্ষিত হবে। কমিশন স্বয়ংক্রিয়ভাবে যোগ হবে না—প্রয়োজন হলে নিজে লিখুন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("গ্রাহকের নাম") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
            )
        }
        item {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("মোবাইল নম্বর (ঐচ্ছিক)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            AccountPickerField(
                label = "টাকা গ্রহণের অ্যাকাউন্ট (+)",
                accounts = accounts,
                selectedAccountId = receiveAccId,
                onAccountSelected = { receiveAccId = it.id },
                showBalance = isOwner,
                testTag = "customer_receive_account"
            )
        }
        item {
            BdtAmountField(
                value = receiveAmount,
                onValueChange = { receiveAmount = it },
                label = "টাকা গ্রহণের পরিমাণ",
                testTag = "customer_receive_amount"
            )
        }
        item {
            AccountPickerField(
                label = "টাকা প্রদানের অ্যাকাউন্ট (-)",
                accounts = accounts,
                selectedAccountId = payAccId,
                onAccountSelected = { payAccId = it.id },
                showBalance = isOwner,
                testTag = "customer_pay_account"
            )
        }
        item {
            BdtAmountField(
                value = payAmount,
                onValueChange = { payAmount = it },
                label = "টাকা প্রদানের পরিমাণ",
                testTag = "customer_pay_amount"
            )
        }
        item {
            BdtAmountField(
                value = commissionText,
                onValueChange = { commissionText = it },
                label = "কমিশন (ঐচ্ছিক, ম্যানুয়ালি লিখুন)",
                testTag = "customer_commission_amount"
            )
        }
        item {
            DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
        }
        item {
            OutlinedTextField(
                value = externalTxId,
                onValueChange = { externalTxId = it },
                label = { Text("Transaction ID (ঐচ্ছিক - ডুপ্লিকেট সুরক্ষিত)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("customer_tx_id_input")
            )
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("নোট") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    if (customerName.isBlank()) {
                        onError("গ্রাহকের নাম লিখুন।")
                        return@Button
                    }
                    val recPaisa = MoneyUtils.parseBdtToPaisa(receiveAmount) ?: 0L
                    val payPaisa = MoneyUtils.parseBdtToPaisa(payAmount) ?: 0L
                    if (recPaisa <= 0L && payPaisa <= 0L) {
                        onError("গ্রহণ বা প্রদানের পরিমাণ লিখুন।")
                        return@Button
                    }
                    val commPaisa = if (commissionText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(commissionText) ?: 0L)
                    val draft = TxDraft(
                        type = TxType.CUSTOMER_TX,
                        title = "গ্রাহক: ${customerName.trim()}",
                        partyName = customerName.trim(),
                        phone = phone.trim(),
                        receiveAccountId = if (recPaisa > 0L) receiveAccId else "",
                        receiveAmountPaisa = recPaisa,
                        payAccountId = if (payPaisa > 0L) payAccId else "",
                        payAmountPaisa = payPaisa,
                        commissionIncomePaisa = commPaisa,
                        externalTxId = externalTxId.trim(),
                        idempotencyKey = UUID.randomUUID().toString(),
                        note = note.trim(),
                        businessDateMillis = dateMillis
                    )
                    onSubmit(draft) {
                        customerName = ""
                        phone = ""
                        receiveAmount = ""
                        payAmount = ""
                        commissionText = ""
                        externalTxId = ""
                        note = ""
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("customer_tx_submit")
            ) {
                Text(if (isOwner) "লেনদেন সংরক্ষণ করুন" else "অনুমোদনের জন্য জমা দিন")
            }
        }
    }
}

@Composable
private fun InternalTransferForm(
    accounts: List<Account>,
    isOwner: Boolean,
    isBusy: Boolean,
    onError: (String) -> Unit,
    onSubmit: (TxDraft, () -> Unit) -> Unit
) {
    var fromAccId by remember(accounts) { mutableStateOf(BusinessConstants.ACC_CASH) }
    var toAccId by remember(accounts) { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Internal Account Transfer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "এক অ্যাকাউন্ট থেকে অন্য অ্যাকাউন্টে স্থানান্তর লাভ বা খরচ হিসেবে গণনা হবে না।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            AccountPickerField(
                label = "যে অ্যাকাউন্ট থেকে যাবে (-)",
                accounts = accounts,
                selectedAccountId = fromAccId,
                onAccountSelected = { fromAccId = it.id },
                showBalance = isOwner,
                testTag = "transfer_from_account"
            )
        }
        item {
            AccountPickerField(
                label = "যে অ্যাকাউন্টে যাবে (+)",
                accounts = accounts,
                selectedAccountId = toAccId,
                onAccountSelected = { toAccId = it.id },
                showBalance = isOwner,
                testTag = "transfer_to_account"
            )
        }
        item {
            BdtAmountField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "স্থানান্তরের পরিমাণ",
                testTag = "transfer_amount"
            )
        }
        item {
            DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("নোট") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val paisa = MoneyUtils.parseBdtToPaisa(amountText)
                    if (paisa == null || paisa <= 0L) {
                        onError("সঠিক পরিমাণ লিখুন।")
                        return@Button
                    }
                    if (fromAccId == toAccId) {
                        onError("দুটি ভিন্ন অ্যাকাউন্ট নির্বাচন করুন।")
                        return@Button
                    }
                    val fromName = accounts.firstOrNull { it.id == fromAccId }?.name ?: fromAccId
                    val toName = accounts.firstOrNull { it.id == toAccId }?.name ?: toAccId
                    val draft = TxDraft(
                        type = TxType.INTERNAL_TRANSFER,
                        title = "ট্রান্সফার: $fromName → $toName",
                        receiveAccountId = toAccId,
                        receiveAmountPaisa = paisa,
                        payAccountId = fromAccId,
                        payAmountPaisa = paisa,
                        idempotencyKey = UUID.randomUUID().toString(),
                        note = note.trim(),
                        businessDateMillis = dateMillis
                    )
                    onSubmit(draft) {
                        amountText = ""
                        note = ""
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("transfer_submit")
            ) {
                Text(if (isOwner) "ট্রান্সফার সম্পন্ন করুন" else "অনুমোদনের জন্য জমা দিন")
            }
        }
    }
}

@Composable
private fun MobileRechargeForm(
    accounts: List<Account>,
    isOwner: Boolean,
    isBusy: Boolean,
    onError: (String) -> Unit,
    onSubmit: (TxDraft, () -> Unit) -> Unit
) {
    var mobile by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf(BusinessConstants.TELECOM_OPERATORS.first()) }
    var operatorMenuExpanded by remember { mutableStateOf(false) }
    var rechargeAmountText by remember { mutableStateOf("") }
    var receivedAmountText by remember { mutableStateOf("") }
    var receiveAccId by remember(accounts) { mutableStateOf(BusinessConstants.ACC_CASH) }
    var commissionText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var externalTxId by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("মোবাইল রিচার্জ হিসাব", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "এটি শুধু Easy Recharge হিসাব রাখে — কোনো লাইভ টেলিকম রিচার্জ API সংযুক্ত নয়।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = { Text("গ্রাহকের মোবাইল নম্বর") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("recharge_phone")
            )
        }
        item {
            Column {
                OutlinedButton(
                    onClick = { operatorMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("অপারেটর: $operator")
                }
                DropdownMenu(
                    expanded = operatorMenuExpanded,
                    onDismissRequest = { operatorMenuExpanded = false }
                ) {
                    BusinessConstants.TELECOM_OPERATORS.forEach { op ->
                        DropdownMenuItem(
                            text = { Text(op) },
                            onClick = {
                                operator = op
                                operatorMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        item {
            BdtAmountField(
                value = rechargeAmountText,
                onValueChange = { rechargeAmountText = it },
                label = "রিচার্জের পরিমাণ (Easy Recharge থেকে কমবে)",
                testTag = "recharge_amount"
            )
        }
        item {
            AccountPickerField(
                label = "গ্রাহকের টাকা জমা অ্যাকাউন্ট",
                accounts = accounts,
                selectedAccountId = receiveAccId,
                onAccountSelected = { receiveAccId = it.id },
                showBalance = isOwner
            )
        }
        item {
            BdtAmountField(
                value = receivedAmountText,
                onValueChange = { receivedAmountText = it },
                label = "গ্রাহকের কাছ থেকে পাওয়া টাকা",
                testTag = "recharge_received_amount"
            )
        }
        item {
            BdtAmountField(
                value = commissionText,
                onValueChange = { commissionText = it },
                label = "কমিশন আয় (ঐচ্ছিক, ম্যানুয়ালি)",
                testTag = "recharge_commission"
            )
        }
        item {
            DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
        }
        item {
            OutlinedTextField(
                value = externalTxId,
                onValueChange = { externalTxId = it },
                label = { Text("Transaction ID (ঐচ্ছিক)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val rechPaisa = MoneyUtils.parseBdtToPaisa(rechargeAmountText)
                    if (rechPaisa == null || rechPaisa <= 0L) {
                        onError("সঠিক রিচার্জের পরিমাণ লিখুন।")
                        return@Button
                    }
                    if (mobile.isBlank()) {
                        onError("গ্রাহকের মোবাইল নম্বর লিখুন।")
                        return@Button
                    }
                    val recPaisa = if (receivedAmountText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(receivedAmountText) ?: 0L)
                    val commPaisa = if (commissionText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(commissionText) ?: 0L)
                    val draft = TxDraft(
                        type = TxType.MOBILE_RECHARGE,
                        subType = operator,
                        title = "রিচার্জ ($operator): ${mobile.trim()}",
                        partyName = mobile.trim(),
                        phone = mobile.trim(),
                        receiveAccountId = if (recPaisa > 0L) receiveAccId else "",
                        receiveAmountPaisa = recPaisa,
                        payAccountId = BusinessConstants.ACC_EASY_RECHARGE,
                        payAmountPaisa = rechPaisa,
                        commissionIncomePaisa = commPaisa,
                        externalTxId = externalTxId.trim(),
                        idempotencyKey = UUID.randomUUID().toString(),
                        operatorName = operator,
                        businessDateMillis = dateMillis
                    )
                    onSubmit(draft) {
                        mobile = ""
                        rechargeAmountText = ""
                        receivedAmountText = ""
                        commissionText = ""
                        externalTxId = ""
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("recharge_submit")
            ) {
                Text(if (isOwner) "রিচার্জ হিসাব সংরক্ষণ করুন" else "অনুমোদনের জন্য জমা দিন")
            }
        }
    }
}

@Composable
private fun PendingQueueSection(
    pendingTransactions: List<PendingTransaction>,
    isOwner: Boolean,
    isBusy: Boolean,
    onApprove: (PendingTransaction) -> Unit,
    onReject: (String, String) -> Unit
) {
    var rejectTargetId by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    if (pendingTransactions.isEmpty()) {
        Column(modifier = Modifier.padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.Filled.Approval,
                title = "কোনো Pending লেনদেন নেই",
                subtitle = if (isOwner) "কর্মচারীরা লেনদেন জমা দিলে এখানে অনুমোদনের জন্য দেখাবে।" else "আপনি এখনো কোনো লেনদেন জমা দেননি।"
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pendingTransactions, key = { it.id }) { p ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("pending_card_${p.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        StatusChip(label = p.status, color = pendingStatusColor(p.status))
                    }
                    Text(
                        "জমাদানকারী: ${p.submittedByName} | ${MoneyUtils.formatDateBn(p.businessDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (p.receiveAmountPaisa > 0L) {
                        Text("গ্রহণ (${p.receiveAccountId}): ${MoneyUtils.formatBdt(p.receiveAmountPaisa)}", color = ProfitGreen, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (p.payAmountPaisa > 0L) {
                        Text("প্রদান (${p.payAccountId}): ${MoneyUtils.formatBdt(p.payAmountPaisa)}", color = LossRed, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (p.commissionIncomePaisa > 0L) {
                        Text("কমিশন আয়: ${MoneyUtils.formatBdt(p.commissionIncomePaisa)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.rejectionReason.isNotBlank()) {
                        Text("বাতিলের কারণ: ${p.rejectionReason}", color = LossRed, style = MaterialTheme.typography.bodySmall)
                    }

                    if (isOwner && p.status == "PENDING") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onApprove(p) },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).testTag("approve_pending_${p.id}")
                            ) {
                                Text("অনুমোদন (Approve)")
                            }
                            OutlinedButton(
                                onClick = {
                                    rejectTargetId = p.id
                                    rejectReason = ""
                                },
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f).testTag("reject_pending_${p.id}")
                            ) {
                                Text("বাতিল (Reject)")
                            }
                        }
                    }
                }
            }
        }
    }

    rejectTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { rejectTargetId = null },
            title = { Text("লেনদেন বাতিল") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("বাতিলের কারণ লিখুন") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReject(targetId, rejectReason)
                        rejectTargetId = null
                    }
                ) { Text("নিশ্চিত করুন") }
            },
            dismissButton = {
                OutlinedButton(onClick = { rejectTargetId = null }) { Text("বন্ধ করুন") }
            }
        )
    }
}
