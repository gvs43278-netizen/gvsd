package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.Account
import com.example.domain.BusinessConstants
import com.example.domain.LoanRecord
import com.example.domain.LoanRepayment
import com.example.domain.MoneyUtils
import com.example.ui.components.AccountPickerField
import com.example.ui.components.BdtAmountField
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.LossRed
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.ProfitGreen
import kotlinx.coroutines.flow.Flow

@Composable
fun LoansScreen(
    loans: List<LoanRecord>,
    accounts: List<Account>,
    isBusy: Boolean,
    observeRepayments: (String) -> Flow<List<LoanRepayment>>,
    onCreateLoan: (
        direction: String,
        personName: String,
        phone: String,
        accountId: String,
        principalPaisa: Long,
        interestPaisa: Long,
        startDateMillis: Long,
        dueDateMillis: Long,
        note: String,
        onSuccess: () -> Unit
    ) -> Unit,
    onRecordRepayment: (
        loan: LoanRecord,
        principalPayPaisa: Long,
        interestPayPaisa: Long,
        accountId: String,
        repaymentDateMillis: Long,
        note: String,
        onSuccess: () -> Unit
    ) -> Unit
) {
    var selectedDirection by remember { mutableStateOf("RECEIVABLE") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var repayTargetLoan by remember { mutableStateOf<LoanRecord?>(null) }
    var historyTargetLoan by remember { mutableStateOf<LoanRecord?>(null) }

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val filteredLoans = remember(loans, selectedDirection) {
        loans.filter { it.direction == selectedDirection }
    }
    val totalRemaining = filteredLoans
        .filter { it.status != "PAID" }
        .sumOf { it.totalRemainingPaisa }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("add_loan_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "নতুন লোন যোগ")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedDirection == "RECEIVABLE",
                    onClick = { selectedDirection = "RECEIVABLE" },
                    label = { Text("লোন প্রদান (পাওনা)") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("loan_tab_receivable")
                )
                FilterChip(
                    selected = selectedDirection == "PAYABLE",
                    onClick = { selectedDirection = "PAYABLE" },
                    label = { Text("লোন গ্রহণ (দেনা)") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("loan_tab_payable")
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedDirection == "RECEIVABLE") {
                        ProfitGreen.copy(alpha = 0.1f)
                    } else {
                        LossRed.copy(alpha = 0.1f)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (selectedDirection == "RECEIVABLE") ProfitGreen.copy(alpha = 0.35f) else LossRed.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedDirection == "RECEIVABLE") "মোট লোন পাওনা" else "মোট লোন দেনা",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "লোন মূল টাকা লাভ-ক্ষতি হিসেবে গণনা হয় না",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = MoneyUtils.formatBdt(totalRemaining),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedDirection == "RECEIVABLE") ProfitGreen else LossRed,
                        modifier = Modifier.testTag("loan_summary_total")
                    )
                }
            }

            if (filteredLoans.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.Payments,
                        title = "কোনো লোন / ধারের হিসাব নেই",
                        subtitle = if (selectedDirection == "RECEIVABLE") {
                            "লোন প্রদান (পাওনা) যুক্ত করতে '+' বাটনে ট্যাপ করুন।"
                        } else {
                            "লোন গ্রহণ (দেনা) যুক্ত করতে '+' বাটনে ট্যাপ করুন।"
                        }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredLoans, key = { it.id }) { loan ->
                        val overdue = loan.isOverdue()
                        val accountDisplay = accountMap[loan.accountId]?.displayName ?: loan.accountId
                        val totalLoanAmount = loan.principalPaisa + loan.interestPaisa
                        val totalPaidAmount = loan.paidPrincipalPaisa + loan.paidInterestPaisa

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("loan_card_${loan.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    loan.status == "PAID" -> ProfitGreen.copy(alpha = 0.35f)
                                    overdue -> LossRed.copy(alpha = 0.55f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                }
                            )
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = loan.personName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (loan.phone.isNotBlank()) {
                                            Text(
                                                text = loan.phone,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    val badgeLabel = when {
                                        loan.status == "PAID" -> "Settled (পরিশোধিত)"
                                        overdue -> "Active • মেয়াদোত্তীর্ণ"
                                        loan.status == "PARTIAL" -> "Active • আংশিক"
                                        else -> "Active (সক্রিয়)"
                                    }
                                    val badgeColor = when {
                                        loan.status == "PAID" -> ProfitGreen
                                        overdue -> LossRed
                                        else -> PendingAmber
                                    }
                                    StatusChip(label = badgeLabel, color = badgeColor)
                                }

                                Text(
                                    text = "অ্যাকাউন্ট: $accountDisplay",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = "তারিখ: ${MoneyUtils.formatDateBn(loan.startDateMillis)} | পরিশোধের তারিখ: ${MoneyUtils.formatDateBn(loan.dueDateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "মোট লোন: ${MoneyUtils.formatBdt(totalLoanAmount)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "পরিশোধিত: ${MoneyUtils.formatBdt(totalPaidAmount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ProfitGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (loan.interestPaisa > 0L) {
                                            "মূল: ${MoneyUtils.formatBdt(loan.principalPaisa)} | সুদ: ${MoneyUtils.formatBdt(loan.interestPaisa)}"
                                        } else {
                                            "আসল: ${MoneyUtils.formatBdt(loan.principalPaisa)}"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "বাকি ব্যালেন্স: ${MoneyUtils.formatBdt(loan.totalRemainingPaisa)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (loan.totalRemainingPaisa > 0L) {
                                            if (selectedDirection == "RECEIVABLE") ProfitGreen else LossRed
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

                                if (loan.note.isNotBlank()) {
                                    Text(
                                        text = "নোট: ${loan.note}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (loan.status != "PAID" && loan.totalRemainingPaisa > 0L) {
                                        Button(
                                            onClick = { repayTargetLoan = loan },
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .testTag("loan_repay_${loan.id}")
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("রিভার্সাল / পরিশোধ")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { historyTargetLoan = loan },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("ইতিহাস")
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateLoanDialog(
            initialDirection = selectedDirection,
            accounts = accounts,
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onSave = { dir, name, phone, accId, principal, interest, start, due, note ->
                onCreateLoan(dir, name, phone, accId, principal, interest, start, due, note) {
                    showCreateDialog = false
                }
            }
        )
    }

    repayTargetLoan?.let { loan ->
        RepayLoanDialog(
            loan = loan,
            accounts = accounts,
            isBusy = isBusy,
            onDismiss = { repayTargetLoan = null },
            onSubmit = { pPay, iPay, accId, date, note ->
                onRecordRepayment(loan, pPay, iPay, accId, date, note) {
                    repayTargetLoan = null
                }
            }
        )
    }

    historyTargetLoan?.let { loan ->
        val repayments by observeRepayments(loan.id).collectAsStateWithLifecycle(initialValue = emptyList())
        AlertDialog(
            onDismissRequest = { historyTargetLoan = null },
            title = { Text("${loan.personName} - পরিশোধের ইতিহাস") },
            text = {
                if (repayments.isEmpty()) {
                    Text("এখনো কোনো কিস্তি পরিশোধ করা হয়নি।")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repayments.forEach { rep ->
                            val repAcc = accountMap[rep.accountId]?.displayName ?: rep.accountId
                            Text(
                                "${MoneyUtils.formatDateBn(rep.repaymentDateMillis)} ($repAcc): আসল ${MoneyUtils.formatBdt(rep.principalPaidPaisa)}, সুদ ${MoneyUtils.formatBdt(rep.interestPaidPaisa)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { historyTargetLoan = null }) { Text("বন্ধ করুন") }
            }
        )
    }
}

@Composable
private fun CreateLoanDialog(
    initialDirection: String,
    accounts: List<Account>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Long, Long, Long, Long, String) -> Unit
) {
    var direction by remember { mutableStateOf(initialDirection) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var principalText by remember { mutableStateOf("") }
    var interestText by remember { mutableStateOf("") }
    var startDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dueDate by remember { mutableLongStateOf(System.currentTimeMillis() + 30L * 86_400_000L) }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন লোন / ধার যোগ") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == "RECEIVABLE",
                        onClick = { direction = "RECEIVABLE" },
                        label = { Text("লোন প্রদান (পাওনা)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = direction == "PAYABLE",
                        onClick = { direction = "PAYABLE" },
                        label = { Text("লোন গ্রহণ (দেনা)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (direction == "RECEIVABLE") {
                        "নির্বাচিত অ্যাকাউন্ট থেকে টাকা কমবে এবং মোট লোন পাওনায় যোগ হবে।"
                    } else {
                        "নির্বাচিত অ্যাকাউন্টে টাকা যোগ হবে এবং মোট লোন দেনায় যোগ হবে।"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ব্যক্তির নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("loan_person_name")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AccountPickerField(
                    label = "অ্যাকাউন্ট",
                    accounts = accounts,
                    selectedAccountId = accountId,
                    onAccountSelected = { accountId = it.id }
                )
                BdtAmountField(
                    value = principalText,
                    onValueChange = { principalText = it },
                    label = "মূল টাকা (Principal)",
                    testTag = "loan_principal_input"
                )
                BdtAmountField(
                    value = interestText,
                    onValueChange = { interestText = it },
                    label = "সুদ (ঐচ্ছিক - আলাদা হিসাব)"
                )
                DatePickerField(label = "তারিখ", dateMillis = startDate, onDateSelected = { startDate = it })
                DatePickerField(label = "পরিশোধের শেষ তারিখ", dateMillis = dueDate, onDateSelected = { dueDate = it })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("নোট") }, modifier = Modifier.fillMaxWidth())
                if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val p = MoneyUtils.parseBdtToPaisa(principalText)
                    if (name.isBlank() || p == null || p <= 0L) {
                        errorMsg = "ব্যক্তির নাম এবং সঠিক মূল টাকা লিখুন।"
                        return@Button
                    }
                    val intr = if (interestText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(interestText) ?: 0L)
                    onSave(direction, name.trim(), phone.trim(), accountId, p, intr, startDate, dueDate, note.trim())
                },
                modifier = Modifier.testTag("loan_save_button")
            ) { Text("সংরক্ষণ") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
private fun RepayLoanDialog(
    loan: LoanRecord,
    accounts: List<Account>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Long, Long, String, Long, String) -> Unit
) {
    val defaultPrincipalText = remember(loan.id) {
        paisaToInputString(loan.remainingPrincipalPaisa)
    }
    val defaultInterestText = remember(loan.id) {
        if (loan.remainingInterestPaisa > 0L) paisaToInputString(loan.remainingInterestPaisa) else ""
    }

    var isFullSettlement by remember(loan.id) { mutableStateOf(true) }
    var principalText by remember(loan.id) { mutableStateOf(defaultPrincipalText) }
    var interestText by remember(loan.id) { mutableStateOf(defaultInterestText) }
    var accountId by remember(loan.id) {
        mutableStateOf(loan.accountId.ifBlank { BusinessConstants.ACC_CASH })
    }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedAccountName = accounts.firstOrNull { it.id == accountId }?.displayName ?: accountId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("রিভার্সাল / পরিশোধ: ${loan.personName}")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "বাকি মূল: ${MoneyUtils.formatBdt(loan.remainingPrincipalPaisa)}" +
                        if (loan.remainingInterestPaisa > 0L) " | বাকি সুদ: ${MoneyUtils.formatBdt(loan.remainingInterestPaisa)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isFullSettlement,
                        onClick = {
                            isFullSettlement = true
                            principalText = defaultPrincipalText
                            interestText = defaultInterestText
                            errorMsg = null
                        },
                        label = { Text("সম্পূর্ণ পরিশোধ (১ ট্যাপ)") },
                        modifier = Modifier.weight(1f).testTag("loan_settle_full_chip")
                    )
                    FilterChip(
                        selected = !isFullSettlement,
                        onClick = {
                            isFullSettlement = false
                            errorMsg = null
                        },
                        label = { Text("আংশিক পরিশোধ") },
                        modifier = Modifier.weight(1f).testTag("loan_settle_partial_chip")
                    )
                }

                AccountPickerField(
                    label = "অ্যাকাউন্ট নির্বাচন",
                    accounts = accounts,
                    selectedAccountId = accountId,
                    onAccountSelected = { accountId = it.id },
                    testTag = "loan_repay_account"
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (loan.direction == "RECEIVABLE") {
                            "$selectedAccountName অ্যাকাউন্টে টাকা ফেরত (+) হবে এবং মোট লোন পাওনা কমবে।"
                        } else {
                            "$selectedAccountName অ্যাকাউন্ট থেকে টাকা প্রদান (-) হবে এবং মোট লোন দেনা কমবে।"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (!isFullSettlement) {
                    BdtAmountField(
                        value = principalText,
                        onValueChange = { principalText = it },
                        label = "আসল পরিশোধ",
                        testTag = "loan_repay_principal_input"
                    )
                    if (loan.remainingInterestPaisa > 0L) {
                        BdtAmountField(
                            value = interestText,
                            onValueChange = { interestText = it },
                            label = "সুদ পরিশোধ"
                        )
                    }
                } else {
                    Text(
                        text = "মোট পরিশোধযোগ্য: ${MoneyUtils.formatBdt(loan.totalRemainingPaisa)} (অবস্থা: Settled হবে)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (loan.direction == "RECEIVABLE") ProfitGreen else LossRed
                    )
                }

                DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("নোট (ঐচ্ছিক)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                colors = if (loan.direction == "PAYABLE") {
                    ButtonDefaults.buttonColors(containerColor = LossRed)
                } else {
                    ButtonDefaults.buttonColors()
                },
                onClick = {
                    val pPay = if (isFullSettlement) {
                        loan.remainingPrincipalPaisa
                    } else if (principalText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(principalText) ?: -1L)

                    val iPay = if (isFullSettlement) {
                        loan.remainingInterestPaisa
                    } else if (interestText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(interestText) ?: -1L)

                    if (pPay < 0L || iPay < 0L || (pPay + iPay) <= 0L) {
                        errorMsg = "সঠিক পরিশোধের পরিমাণ লিখুন।"
                        return@Button
                    }
                    val finalNote = note.trim().ifBlank {
                        if (isFullSettlement) "সম্পূর্ণ পরিশোধ / রিভার্সাল" else "আংশিক পরিশোধ"
                    }
                    onSubmit(pPay, iPay, accountId, dateMillis, finalNote)
                },
                modifier = Modifier.testTag("loan_repay_confirm_button")
            ) {
                Text(if (isFullSettlement) "নিশ্চিত করুন (Settled)" else "আংশিক পরিশোধ নিশ্চিত")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

private fun paisaToInputString(paisa: Long): String {
    if (paisa <= 0L) return ""
    val whole = paisa / 100L
    val rem = paisa % 100L
    return if (rem == 0L) {
        whole.toString()
    } else {
        "$whole.${rem.toString().padStart(2, '0')}"
    }
}

