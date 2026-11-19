package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.domain.AuditLog
import com.example.domain.LedgerTransaction
import com.example.domain.MoneyUtils
import com.example.domain.TxType
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.LossRed
import com.example.ui.theme.ProfitGreen

@Composable
fun HistoryScreen(
    transactions: List<LedgerTransaction>,
    auditLogs: List<AuditLog>,
    accounts: List<Account>,
    isBusy: Boolean,
    onReverseTx: (LedgerTransaction, String, () -> Unit) -> Unit
) {
    var showAuditLogs by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var reverseTarget by remember { mutableStateOf<LedgerTransaction?>(null) }
    var reverseReason by remember { mutableStateOf("") }

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val filteredTxs = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else {
            val q = searchQuery.trim().lowercase()
            transactions.filter {
                it.title.lowercase().contains(q) ||
                    it.partyName.lowercase().contains(q) ||
                    it.externalTxId.lowercase().contains(q) ||
                    it.note.lowercase().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showAuditLogs,
                onClick = { showAuditLogs = false },
                label = { Text("লেনদেন ইতিহাস (${MoneyUtils.toBengaliDigits(transactions.size.toString())})") },
                modifier = Modifier.testTag("history_tab_tx")
            )
            FilterChip(
                selected = showAuditLogs,
                onClick = { showAuditLogs = true },
                label = { Text("Audit Log (${MoneyUtils.toBengaliDigits(auditLogs.size.toString())})") },
                modifier = Modifier.testTag("history_tab_audit")
            )
        }

        if (!showAuditLogs) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("খুঁজুন (নাম, Transaction ID, বিবরণ)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("history_search_input")
            )

            if (filteredTxs.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.History,
                        title = "কোনো লেনদেন পাওয়া যায়নি",
                        subtitle = "অনুমোদিত লেনদেনগুলো এখানে বিস্তারিত ইতিহাসসহ প্রদর্শিত হবে।"
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTxs, key = { it.id }) { tx ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("history_tx_${tx.id}"),
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
                                    Text(tx.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (tx.reversed) {
                                        StatusChip("রিভার্সড", LossRed)
                                    } else {
                                        StatusChip(AccountingEngine.txTypeLabelBn(tx.type), MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Text(
                                    "তারিখ: ${MoneyUtils.formatDateBn(tx.businessDateMillis)} | জমা: ${tx.submittedByName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (tx.receiveAmountPaisa > 0L) {
                                    val accName = accountMap[tx.receiveAccountId]?.displayName ?: tx.receiveAccountId
                                    Text("জমা (+): $accName → ${MoneyUtils.formatBdt(tx.receiveAmountPaisa)}", color = ProfitGreen, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (tx.payAmountPaisa > 0L) {
                                    val accName = accountMap[tx.payAccountId]?.displayName ?: tx.payAccountId
                                    Text("প্রদান (-): $accName → ${MoneyUtils.formatBdt(tx.payAmountPaisa)}", color = LossRed, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (tx.commissionIncomePaisa > 0L) {
                                    Text("কমিশন আয়: ${MoneyUtils.formatBdt(tx.commissionIncomePaisa)}", style = MaterialTheme.typography.bodySmall, color = ProfitGreen)
                                }
                                if (tx.commissionExpensePaisa > 0L) {
                                    Text("পার্টনার কমিশন ব্যয়: ${MoneyUtils.formatBdt(tx.commissionExpensePaisa)}", style = MaterialTheme.typography.bodySmall, color = LossRed)
                                }
                                if (tx.externalTxId.isNotBlank()) {
                                    Text("TrxID: ${tx.externalTxId}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (tx.note.isNotBlank()) {
                                    Text("নোট: ${tx.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (tx.reversed && tx.reversalReason.isNotBlank()) {
                                    Text("রিভার্সাল কারণ: ${tx.reversalReason}", style = MaterialTheme.typography.bodySmall, color = LossRed)
                                }

                                if (!tx.reversed && tx.type != TxType.REVERSAL && tx.type != TxType.LOAN_DISBURSE && tx.type != TxType.LOAN_REPAYMENT) {
                                    OutlinedButton(
                                        onClick = {
                                            reverseTarget = tx
                                            reverseReason = ""
                                        },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LossRed),
                                        modifier = Modifier.fillMaxWidth().testTag("reverse_tx_${tx.id}")
                                    ) {
                                        Icon(Icons.Filled.Undo, contentDescription = null)
                                        Text(" লেনদেন সংশোধন / Reversal করুন")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(auditLogs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(MoneyUtils.formatDateTimeBn(log.createdAt?.toDate()?.time ?: 0L), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                            Text("Actor: ${log.actorEmail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    reverseTarget?.let { tx ->
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            title = { Text("লেনদেন Reversal নিশ্চিতকরণ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${tx.title} লেনদেনটি Reversal করলে সংশ্লিষ্ট অ্যাকাউন্টগুলোর ব্যালেন্স পূর্বের অবস্থায় ফিরে যাবে।")
                    OutlinedTextField(
                        value = reverseReason,
                        onValueChange = { reverseReason = it },
                        label = { Text("Reversal কারণ লিখুন") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                    onClick = {
                        onReverseTx(tx, reverseReason.ifBlank { "Owner কর্তৃক সংশোধন" }) {
                            reverseTarget = null
                        }
                    }
                ) { Text("Reversal নিশ্চিত করুন") }
            },
            dismissButton = {
                OutlinedButton(onClick = { reverseTarget = null }) { Text("বাতিল") }
            }
        )
    }
}
