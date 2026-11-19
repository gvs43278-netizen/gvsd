package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.Account
import com.example.domain.BillType
import com.example.domain.BusinessConstants
import com.example.domain.ExpenseCategory
import com.example.domain.MoneyUtils
import com.example.domain.TxDraft
import com.example.domain.TxType
import com.example.ui.components.AccountPickerField
import com.example.ui.components.BdtAmountField
import com.example.ui.components.DatePickerField
import java.util.UUID

@Composable
fun BillsScreen(
    accounts: List<Account>,
    isOwner: Boolean,
    isBusy: Boolean,
    onSubmitTx: (TxDraft, () -> Unit) -> Unit
) {
    var billType by remember { mutableStateOf(BillType.ELECTRICITY) }
    var customerName by remember { mutableStateOf("") }
    var receiveAccId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var receiveAmountText by remember { mutableStateOf("") }
    var payAccId by remember { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var payAmountText by remember { mutableStateOf("") }
    var commissionText by remember { mutableStateOf("") }
    var externalTxId by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("বিল পেমেন্ট (Bill Payment)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "গ্রাহকের কাছ থেকে টাকা গ্রহণ ও বিল পরিশোধের অ্যাকাউন্ট আলাদাভাবে সংরক্ষিত হবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = billType == BillType.ELECTRICITY,
                    onClick = { billType = BillType.ELECTRICITY },
                    label = { Text("Electricity Bill") }
                )
                FilterChip(
                    selected = billType == BillType.WIFI,
                    onClick = { billType = BillType.WIFI },
                    label = { Text("WiFi Bill") }
                )
            }
        }
        item {
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("গ্রাহকের নাম / মিটার বা আইডি") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            AccountPickerField(
                label = "গ্রাহক টাকা জমা দিয়েছেন (অ্যাকাউন্ট)",
                accounts = accounts,
                selectedAccountId = receiveAccId,
                onAccountSelected = { receiveAccId = it.id },
                showBalance = isOwner
            )
        }
        item {
            BdtAmountField(
                value = receiveAmountText,
                onValueChange = { receiveAmountText = it },
                label = "গ্রাহকের কাছ থেকে গ্রহণ",
                testTag = "bill_receive_amount"
            )
        }
        item {
            AccountPickerField(
                label = "যে অ্যাকাউন্ট থেকে বিল পরিশোধ হয়েছে",
                accounts = accounts,
                selectedAccountId = payAccId,
                onAccountSelected = { payAccId = it.id },
                showBalance = isOwner
            )
        }
        item {
            BdtAmountField(
                value = payAmountText,
                onValueChange = { payAmountText = it },
                label = "বিল পরিশোধের পরিমাণ",
                testTag = "bill_pay_amount"
            )
        }
        item {
            BdtAmountField(
                value = commissionText,
                onValueChange = { commissionText = it },
                label = "কমিশন আয় (ঐচ্ছিক)"
            )
        }
        item { DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it }) }
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
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("নোট") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (errorMsg != null) {
            item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(10.dp))
                }
            }
        }
        item {
            Button(
                onClick = {
                    val recPaisa = if (receiveAmountText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(receiveAmountText) ?: -1L)
                    val payPaisa = MoneyUtils.parseBdtToPaisa(payAmountText) ?: -1L
                    if (payPaisa <= 0L || recPaisa < 0L) {
                        errorMsg = "সঠিক বিল পরিশোধের পরিমাণ লিখুন।"
                        return@Button
                    }
                    errorMsg = null
                    val commPaisa = if (commissionText.isBlank()) 0L else (MoneyUtils.parseBdtToPaisa(commissionText) ?: 0L)
                    val label = BillType.labelBn(billType)
                    val draft = TxDraft(
                        type = TxType.BILL_PAYMENT,
                        subType = billType,
                        title = "$label${if (customerName.isNotBlank()) " - ${customerName.trim()}" else ""}",
                        partyName = customerName.trim(),
                        receiveAccountId = if (recPaisa > 0L) receiveAccId else "",
                        receiveAmountPaisa = recPaisa,
                        payAccountId = payAccId,
                        payAmountPaisa = payPaisa,
                        commissionIncomePaisa = commPaisa,
                        externalTxId = externalTxId.trim(),
                        idempotencyKey = UUID.randomUUID().toString(),
                        note = note.trim(),
                        businessDateMillis = dateMillis
                    )
                    onSubmitTx(draft) {
                        customerName = ""
                        receiveAmountText = ""
                        payAmountText = ""
                        commissionText = ""
                        externalTxId = ""
                        note = ""
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("bill_submit")
            ) {
                Text(if (isOwner) "বিল পেমেন্ট সংরক্ষণ করুন" else "অনুমোদনের জন্য জমা দিন")
            }
        }
    }
}

@Composable
fun ExpensesScreen(
    accounts: List<Account>,
    isOwner: Boolean,
    isBusy: Boolean,
    onSubmitTx: (TxDraft, () -> Unit) -> Unit
) {
    var category by remember { mutableStateOf(ExpenseCategory.SHOP_RENT) }
    var accountId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var amountText by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var description by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("দোকান খরচ (Shop Expense)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "সমস্ত দোকান খরচ নিট লাভ-ক্ষতি থেকে বাদ যাবে।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ExpenseCategory.WIFI_BILL,
                    ExpenseCategory.ELECTRICITY_BILL,
                    ExpenseCategory.SHOP_RENT,
                    ExpenseCategory.OTHER
                ).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(ExpenseCategory.labelBn(cat)) }
                    )
                }
            }
        }
        item {
            AccountPickerField(
                label = "খরচের অ্যাকাউন্ট",
                accounts = accounts,
                selectedAccountId = accountId,
                onAccountSelected = { accountId = it.id },
                showBalance = isOwner
            )
        }
        item {
            BdtAmountField(
                value = amountText,
                onValueChange = { amountText = it },
                label = "খরচের পরিমাণ",
                testTag = "expense_amount"
            )
        }
        item { DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it }) }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("খরচের বিবরণ") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (errorMsg != null) {
            item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(10.dp))
                }
            }
        }
        item {
            Button(
                onClick = {
                    val amt = MoneyUtils.parseBdtToPaisa(amountText)
                    if (amt == null || amt <= 0L) {
                        errorMsg = "সঠিক খরচের পরিমাণ লিখুন।"
                        return@Button
                    }
                    errorMsg = null
                    val label = ExpenseCategory.labelBn(category)
                    val draft = TxDraft(
                        type = TxType.SHOP_EXPENSE,
                        subType = category,
                        title = "খরচ: $label",
                        payAccountId = accountId,
                        payAmountPaisa = amt,
                        expenseAmountPaisa = amt,
                        idempotencyKey = UUID.randomUUID().toString(),
                        note = description.trim(),
                        businessDateMillis = dateMillis
                    )
                    onSubmitTx(draft) {
                        amountText = ""
                        description = ""
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("expense_submit")
            ) {
                Text(if (isOwner) "খরচ সংরক্ষণ করুন" else "অনুমোদনের জন্য জমা দিন")
            }
        }
    }
}
