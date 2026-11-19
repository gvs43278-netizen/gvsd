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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.domain.AccountingEngine
import com.example.domain.BusinessConstants
import com.example.domain.MoneyUtils
import com.example.domain.Partner
import com.example.domain.TxDraft
import com.example.domain.TxType
import com.example.ui.components.AccountPickerField
import com.example.ui.components.BdtAmountField
import com.example.ui.components.DatePickerField
import com.example.ui.components.StatusChip
import com.example.ui.theme.LossRed
import com.example.ui.theme.ProfitGreen
import java.util.UUID

@Composable
fun PartnersScreen(
    partners: List<Partner>,
    accounts: List<Account>,
    isOwner: Boolean,
    isBusy: Boolean,
    onSubmitTx: (TxDraft, () -> Unit) -> Unit,
    onUpdatePartnerCommission: (String, Long) -> Unit
) {
    var activePartner by remember { mutableStateOf<Partner?>(null) }
    var isSending by remember { mutableStateOf(true) }
    var commissionEditPartner by remember { mutableStateOf<Partner?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("পার্টনার ম্যানেজমেন্ট", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "পার্টনার কমিশন ব্যবসার আয় নয় — এটি কমিশন ব্যয়/দায় হিসেবে গণনা হয়।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(partners, key = { it.id }) { partner ->
            Card(
                modifier = Modifier.fillMaxWidth().testTag("partner_card_${partner.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(partner.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val ruleLabel = when {
                                partner.bKashOnlyCommission ->
                                    "bKash-এ পাঠালে প্রতি হাজারে ${MoneyUtils.formatBdt(partner.commissionPerThousandPaisa)} ব্যয় | Cash/UCB = ৳০"
                                partner.commissionPerThousandPaisa > 0L ->
                                    "প্রতি হাজারে ${MoneyUtils.formatBdt(partner.commissionPerThousandPaisa)} (কাস্টমাইজযোগ্য)"
                                else -> "ডিফল্ট কমিশন ৳০ (কাস্টমাইজযোগ্য)"
                            }
                            Text(ruleLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusChip(
                            label = if (partner.netBalancePaisa >= 0) "পাওনা ${MoneyUtils.formatBdt(partner.netBalancePaisa)}"
                            else "দেনা ${MoneyUtils.formatBdt(-partner.netBalancePaisa)}",
                            color = if (partner.netBalancePaisa >= 0) ProfitGreen else LossRed
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("পাঠানো: ${MoneyUtils.formatBdt(partner.totalSentPaisa)}", style = MaterialTheme.typography.bodySmall)
                        Text("গ্রহণ: ${MoneyUtils.formatBdt(partner.totalReceivedPaisa)}", style = MaterialTheme.typography.bodySmall)
                        Text("কমিশন ব্যয়: ${MoneyUtils.formatBdt(partner.totalCommissionExpensePaisa)}", style = MaterialTheme.typography.bodySmall, color = LossRed)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                activePartner = partner
                                isSending = true
                            },
                            modifier = Modifier.weight(1f).testTag("partner_send_${partner.id}")
                        ) { Text("টাকা পাঠান") }

                        OutlinedButton(
                            onClick = {
                                activePartner = partner
                                isSending = false
                            },
                            modifier = Modifier.weight(1f).testTag("partner_receive_${partner.id}")
                        ) { Text("টাকা গ্রহণ") }
                    }

                    if (isOwner && partner.canCustomizeCommission) {
                        OutlinedButton(
                            onClick = { commissionEditPartner = partner },
                            modifier = Modifier.fillMaxWidth().testTag("partner_edit_comm_${partner.id}")
                        ) {
                            Text("কমিশন রেট পরিবর্তন করুন")
                        }
                    }
                }
            }
        }
    }

    activePartner?.let { partner ->
        PartnerTransactionDialog(
            partner = partner,
            accounts = accounts,
            isSending = isSending,
            isBusy = isBusy,
            onDismiss = { activePartner = null },
            onSubmit = { draft ->
                onSubmitTx(draft) { activePartner = null }
            }
        )
    }

    commissionEditPartner?.let { partner ->
        var rateText by remember(partner) {
            mutableStateOf((partner.commissionPerThousandPaisa / 100.0).let { if (it == 0.0) "0" else it.toString() })
        }
        AlertDialog(
            onDismissRequest = { commissionEditPartner = null },
            title = { Text("${partner.name} - কমিশন নির্ধারণ") },
            text = {
                BdtAmountField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = "প্রতি ৳১,০০০-এ কমিশন (টাকা)"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paisa = MoneyUtils.parseBdtToPaisa(rateText) ?: 0L
                        onUpdatePartnerCommission(partner.id, paisa)
                        commissionEditPartner = null
                    }
                ) { Text("সংরক্ষণ") }
            },
            dismissButton = {
                OutlinedButton(onClick = { commissionEditPartner = null }) { Text("বাতিল") }
            }
        )
    }
}

@Composable
private fun PartnerTransactionDialog(
    partner: Partner,
    accounts: List<Account>,
    isSending: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (TxDraft) -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var amountText by remember { mutableStateOf("") }
    var manualCommText by remember { mutableStateOf("") }
    var externalTxId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sendMode by remember { mutableStateOf(isSending) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val amountPaisa = MoneyUtils.parseBdtToPaisa(amountText) ?: 0L
    val manualCommPaisa = if (manualCommText.isBlank()) null else MoneyUtils.parseBdtToPaisa(manualCommText)
    val computedCommission = if (selectedAccount != null) {
        AccountingEngine.calculatePartnerCommissionExpense(
            partner = partner,
            account = selectedAccount,
            amountPaisa = amountPaisa,
            isSendingToPartner = sendMode,
            manualOverridePaisa = manualCommPaisa
        )
    } else 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${partner.name} লেনদেন", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sendMode,
                        onClick = { sendMode = true },
                        label = { Text("টাকা পাঠানো") }
                    )
                    FilterChip(
                        selected = !sendMode,
                        onClick = { sendMode = false },
                        label = { Text("টাকা গ্রহণ") }
                    )
                }
                AccountPickerField(
                    label = if (sendMode) "প্রদানের অ্যাকাউন্ট" else "গ্রহণের অ্যাকাউন্ট",
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onAccountSelected = { selectedAccountId = it.id }
                )
                BdtAmountField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "টাকার পরিমাণ",
                    testTag = "partner_tx_amount"
                )
                if (sendMode && partner.canCustomizeCommission) {
                    BdtAmountField(
                        value = manualCommText,
                        onValueChange = { manualCommText = it },
                        label = "কমিশন ব্যয় (ঐচ্ছিক)"
                    )
                }
                Text(
                    text = "হিসাবকৃত কমিশন ব্যয়: ${MoneyUtils.formatBdt(computedCommission)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (computedCommission > 0L) LossRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                DatePickerField(label = "তারিখ", dateMillis = dateMillis, onDateSelected = { dateMillis = it })
                OutlinedTextField(
                    value = externalTxId,
                    onValueChange = { externalTxId = it },
                    label = { Text("Transaction ID (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("নোট") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    if (amountPaisa <= 0L) {
                        errorMsg = "সঠিক টাকার পরিমাণ লিখুন।"
                        return@Button
                    }
                    val draft = TxDraft(
                        type = if (sendMode) TxType.PARTNER_SEND else TxType.PARTNER_RECEIVE,
                        title = if (sendMode) "পার্টনার প্রেরণ: ${partner.name}" else "পার্টনার গ্রহণ: ${partner.name}",
                        partyName = partner.name,
                        receiveAccountId = if (!sendMode) selectedAccountId else "",
                        receiveAmountPaisa = if (!sendMode) amountPaisa else 0L,
                        payAccountId = if (sendMode) selectedAccountId else "",
                        payAmountPaisa = if (sendMode) amountPaisa else 0L,
                        commissionExpensePaisa = computedCommission,
                        externalTxId = externalTxId.trim(),
                        idempotencyKey = UUID.randomUUID().toString(),
                        note = note.trim(),
                        businessDateMillis = dateMillis,
                        partnerId = partner.id
                    )
                    onSubmit(draft)
                },
                modifier = Modifier.testTag("partner_tx_confirm")
            ) { Text("নিশ্চিত করুন") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
