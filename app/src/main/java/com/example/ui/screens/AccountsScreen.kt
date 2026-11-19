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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.Account
import com.example.domain.LedgerTransaction
import com.example.domain.MoneyUtils
import com.example.ui.components.AccountMiniCard
import com.example.ui.theme.LossRed
import com.example.ui.theme.ProfitGreen

@Composable
fun AccountsScreen(
    accounts: List<Account>,
    transactions: List<LedgerTransaction>,
    isOwner: Boolean,
    selectedAccountId: String?,
    onSelectAccount: (String?) -> Unit,
    onOpenBalanceAdd: () -> Unit,
    onOpenBalanceWithdraw: () -> Unit,
    onOpenTransfer: () -> Unit
) {
    val totalBalance = accounts.sumOf { it.balancePaisa }
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val accountTxs = if (selectedAccount != null && isOwner) {
        transactions.filter {
            it.receiveAccountId == selectedAccount.id || it.payAccountId == selectedAccount.id
        }.take(25)
    } else emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "মোট ১১টি অ্যাকাউন্ট ব্যালেন্স",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = MoneyUtils.formatBdt(totalBalance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (isOwner) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onOpenBalanceAdd, modifier = Modifier.weight(1f)) {
                                Text("যোগ")
                            }
                            OutlinedButton(onClick = onOpenBalanceWithdraw, modifier = Modifier.weight(1f)) {
                                Text("উত্তোলন")
                            }
                            OutlinedButton(onClick = onOpenTransfer, modifier = Modifier.weight(1f)) {
                                Text("ট্রান্সফার")
                            }
                        }
                    }
                }
            }
        }

        items(accounts, key = { it.id }) { acc ->
            AccountMiniCard(
                account = acc,
                onClick = {
                    onSelectAccount(if (selectedAccountId == acc.id) null else acc.id)
                }
            )
        }

        if (selectedAccount != null && isOwner) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${selectedAccount.displayName} - সাম্প্রতিক হিসাব",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (accountTxs.isEmpty()) {
                item {
                    Text(
                        text = "এই অ্যাকাউন্টে এখনও কোনো লেনদেন নেই।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(accountTxs, key = { it.id }) { tx ->
                    val isIn = tx.receiveAccountId == selectedAccount.id
                    val amt = if (isIn) tx.receiveAmountPaisa else tx.payAmountPaisa
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(MoneyUtils.formatDateBn(tx.businessDateMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = (if (isIn) "+" else "-") + MoneyUtils.formatBdt(amt),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isIn) ProfitGreen else LossRed
                            )
                        }
                    }
                }
            }
        }
    }
}
