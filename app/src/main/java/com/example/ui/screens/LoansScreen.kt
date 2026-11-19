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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
    var selectedDirection by remember { mutableStateOf("PAYABLE") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var repayTargetLoan by remember { mutableStateOf<LoanRecord?>(null) }
    var historyTargetLoan by remember { mutableStateOf<LoanRecord?>(null) }

    val filteredLoans = loans.filter { it.direction == selectedDirection }
    val totalRemaining = filteredLoans.sumOf { it.totalRemainingPaisa }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("add_loan_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "নতুন ঋণ যোগ")
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
                    selected = selectedDirection == "PAYABLE",
                    onClick = { selectedDirection = "PAYABLE" },
                    label = { Text("ধার নিয়েছি (Payable)") },
                    modifier = Modifier.testTag("loan_tab_payable")
                )
                FilterChip(
                    selected = selectedDirection == "RECEIVABLE",
                    onClick = { selectedDirection = "RECEIVABLE" },
                    label = { Text("ধার দিয়েছি (Receivable)") },
                    modifier = Modifier.testTag("loan_tab_receivable")
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedDirection == "PAYABLE") "মোট বাকি দেনা" else "মোট বাকি পাওনা",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = MoneyUtils.formatBdt(totalRemaining),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedDirection == "PAYABLE") LossRed else ProfitGreen
                    )
                }
            }

            if (filteredLoans.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.Payments,
                        title = "কোনো ঋণের হিসাব নেই",
                        subtitle = "নতুন ঋণ বা ধারের হিসাব যোগ করতে '+' বাটনে ট্যাপ করুন।"
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredLoans, key = { it.id }) { loan ->
                        val overdue = loan.isOverdue()
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("loan_card_${loan.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                if (overdue) LossRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
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
                                    Text(loan.personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    val badgeLabel = when {
                                        loan.status == "PAID" -> "পরিশোধিত"
                                        overdue -> "মেয়াদোত্তীর্ণ (Overdue)"
                                        loan.status == "PARTIAL" -> "আংশিক পরিশোধ"
                                        else -> "চলমান"
                                    }
                                    val badgeColor = when {
                                        loan.status == "PAID" -> ProfitGreen
                                        overdue -> LossRed
                                        else -> PendingAmber
                                    }
                                    StatusChip(label = badgeLabel, color = badgeColor)
                                }

                                Text(
                                    "তারিখ: ${MoneyUtils.formatDateBn(loan.startDateMillis)} | শেষ তারিখ: ${MoneyUtils.formatDateBn(loan.dueDateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("মূল টাকা: ${MoneyUtils.formatBdt(loan.principalPaisa)}", style = MaterialTheme.typography.bodySmall)
                                    Text("বাকি মূল: ${MoneyUtils.formatBdt(loan.remainingPrincipalPaisa)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("সুদ: ${MoneyUtils.formatBdt(loan.interestPaisa)}", style = MaterialTheme.typography.bodySmall)
                                    Text("বাকি সুদ: ${MoneyUtils.formatBdt(loan.remainingInterestPaisa)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (loan.status != "PAID") {
                                        Button(
                                            onClick = { repayTargetLoan = loan },
                                            modifier = Modifier.weight(1f).testTag("loan_repay_${loan.id}")
                                        ) {
                                            Text("পরিশোধ করুন")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { historyTargetLoan = loan },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.History, contentDescription = null)
                                        Text(" ইতিহাস")
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
                            Text(
                                "${MoneyUtils.formatDateBn(rep.repaymentDateMillis)}: আসল ${MoneyUtils.formatBdt(rep.principalPaidPaisa)}, সুদ ${MoneyUtils.formatBdt(rep.interestPaidPaisa)}",
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
        title = { Text("নতুন ঋণ যোগ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "PAYABLE", onClick = { direction = "PAYABLE" }, label = { Text("ধার নিয়েছি") })
                    FilterChip(selected = direction == "RECEIVABLE", onClick = { direction = "RECEIVABLE" }, label = { Text("ধার দিয়েছি") })
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("ব্যক্তির নাম") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("মোবাইল (ঐচ্ছিক)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AccountPickerField(label = "অ্যাকাউন্ট", accounts = accounts, selectedAccountId = accountId, onAccountSelected = { accountId = it.id })
                BdtAmountField(value = principalText, onValueChange = { principalText = it }, label = "মূল টাকা (Principal)")
                BdtAmountField(value = interestText, onValueChange = { interestText = it }, label = "সুদ (Interest - আলাদা)")
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
                }
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
    var principalText by remember { mutableStateOf("") }
    var interestText by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ঋণ পরিশোধ: ${loan.personName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "বাকি মূল: ${MoneyUtils.formatBdt(loan.remainingPrincipalPaisa)} | বাকি সুদ: ${MoneyUtils.formatBdt(loan.remainingInterestPaisa)}",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        principalText = (loan.remainingPrincipalPaisa / 100.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                        interestText = (loan.remainingInterestPaisa / 100.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("সম্পূর্ণ পরিশোধ (Full Settlement) পূরণ করুন") }
                AccountPickerField(label = "অ্যাকাউন্ট", accounts = accounts, selectedAccountId = accountId, onAccountSelected = { accountId = it.id })
                BdtAmountField(value = principalText, onValueChange = { principalText = it }, label = "আসল পরিশোধ (আংশিক/পূর্ণ)")
                BdtAmountField(value = interestText, onValueChange = { interestText = it }, label = "সুদ পরিশোধ")
                DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("নোট") }, modifier = Modifier.fillMaxWidth())
                if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val pPay = if (principalText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(principalText) ?: -1L)
                    val iPay = if (interestText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(interestText) ?: -1L)
                    if (pPay < 0L || iPay < 0L || (pPay + iPay) <= 0L) {
                        errorMsg = "সঠিক পরিশোধের পরিমাণ লিখুন।"
                        return@Button
                    }
                    onSubmit(pPay, iPay, accountId, dateMillis, note.trim())
                }
            ) { Text("পরিশোধ নিশ্চিত করুন") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}
