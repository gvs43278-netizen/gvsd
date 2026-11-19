package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.Account
import com.example.domain.DashboardSummary
import com.example.domain.MoneyUtils
import com.example.domain.PendingTransaction
import com.example.ui.components.AccountMiniCard
import com.example.ui.components.StatusChip
import com.example.ui.theme.LossRed
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.ProfitGreen

@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    accounts: List<Account>,
    pendingTransactions: List<PendingTransaction>,
    isOwner: Boolean,
    onOpenBalanceAdd: () -> Unit,
    onOpenBalanceWithdraw: () -> Unit,
    onOpenCustomerTx: () -> Unit,
    onOpenPendingQueue: () -> Unit,
    onOpenAccountDetail: (Account) -> Unit
) {
    val pendingCount = pendingTransactions.count { it.status == "PENDING" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Today's Summary Hero Strip
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "মোট অ্যাকাউন্ট ব্যালেন্স",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = MoneyUtils.formatBdt(summary.totalAccountBalancePaisa),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.testTag("dashboard_total_balance")
                            )
                        }
                        if (pendingCount > 0) {
                            Surface(
                                onClick = onOpenPendingQueue,
                                shape = RoundedCornerShape(999.dp),
                                color = PendingAmber
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Approval,
                                        contentDescription = "Pending",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${MoneyUtils.toBengaliDigits(pendingCount.toString())} Pending",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Today's 4 metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TodayMetricPill(
                            label = "আজকের লেনদেন",
                            value = MoneyUtils.formatBdt(summary.todayVolumePaisa),
                            modifier = Modifier.weight(1f)
                        )
                        TodayMetricPill(
                            label = "আজকের কমিশন",
                            value = MoneyUtils.formatBdt(summary.todayCommissionPaisa),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TodayMetricPill(
                            label = "আজকের খরচ",
                            value = MoneyUtils.formatBdt(summary.todayExpensePaisa),
                            modifier = Modifier.weight(1f)
                        )
                        TodayMetricPill(
                            label = "আজকের লাভ/ক্ষতি",
                            value = MoneyUtils.formatBdt(summary.todayNetProfitLossPaisa, showSign = true),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Owner Quick Action Buttons (Balance Add & Balance Withdraw)
        if (isOwner) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenBalanceAdd,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_balance_add_btn")
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Balance Add")
                        }
                        OutlinedButton(
                            onClick = onOpenBalanceWithdraw,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_balance_withdraw_btn")
                        ) {
                            Icon(Icons.Filled.RemoveCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Balance Withdraw")
                        }
                    }

                    FilledTonalButton(
                        onClick = onOpenCustomerTx,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dashboard_new_tx_btn")
                    ) {
                        Icon(Icons.Filled.CompareArrows, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("নতুন গ্রাহক লেনদেন / ট্রান্সফার / রিচার্জ")
                    }
                }
            }
        }

        // 3. Required Financial Summary Grid
        item {
            Text(
                text = "ব্যবসার আর্থিক সারসংক্ষেপ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard(
                        title = "Cash Balance",
                        amount = MoneyUtils.formatBdt(summary.cashBalancePaisa),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "Total bKash Balance",
                        amount = MoneyUtils.formatBdt(summary.totalBkashBalancePaisa),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard(
                        title = "Nagad Balance",
                        amount = MoneyUtils.formatBdt(summary.nagadBalancePaisa),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "Rocket Balance",
                        amount = MoneyUtils.formatBdt(summary.rocketBalancePaisa),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard(
                        title = "Upay Balance",
                        amount = MoneyUtils.formatBdt(summary.upayBalancePaisa),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "UCB Bank Balance",
                        amount = MoneyUtils.formatBdt(summary.ucbBankBalancePaisa),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard(
                        title = "Easy Recharge Balance",
                        amount = MoneyUtils.formatBdt(summary.easyRechargeBalancePaisa),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "Total Capital (মূলধন)",
                        amount = MoneyUtils.formatBdt(summary.totalCapitalPaisa),
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard(
                        title = "Commission Income",
                        amount = MoneyUtils.formatBdt(summary.totalCommissionIncomePaisa),
                        accentColor = ProfitGreen,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = "Total Expense",
                        amount = MoneyUtils.formatBdt(summary.totalExpensePaisa),
                        accentColor = LossRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Net Profit / Loss Prominent Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (summary.netProfitLossPaisa >= 0) {
                            ProfitGreen.copy(alpha = 0.1f)
                        } else {
                            LossRed.copy(alpha = 0.1f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (summary.netProfitLossPaisa >= 0) ProfitGreen.copy(alpha = 0.4f) else LossRed.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Net Profit / Loss (নিট লাভ/ক্ষতি)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "কমিশন আয় - পার্টনার কমিশন - দোকান খরচ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (summary.netProfitLossPaisa >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                contentDescription = null,
                                tint = if (summary.netProfitLossPaisa >= 0) ProfitGreen else LossRed
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = MoneyUtils.formatBdt(summary.netProfitLossPaisa, showSign = true),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (summary.netProfitLossPaisa >= 0) ProfitGreen else LossRed,
                                modifier = Modifier.testTag("dashboard_net_profit")
                            )
                        }
                    }
                }

                // Separate Interest Income / Expense card per Section 15
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ঋণের সুদ আয় (আলাদা হিসাব)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MoneyUtils.formatBdt(summary.interestIncomePaisa), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ProfitGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("ঋণের সুদ ব্যয় (আলাদা হিসাব)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MoneyUtils.formatBdt(summary.interestExpensePaisa), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LossRed)
                        }
                    }
                }
            }
        }

        // 4. All 11 Accounts Balance Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "১১টি অ্যাকাউন্টের ব্যালেন্স",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                StatusChip(
                    label = "${MoneyUtils.toBengaliDigits(accounts.size.toString())}টি অ্যাকাউন্ট",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        items(accounts, key = { it.id }) { account ->
            AccountMiniCard(
                account = account,
                onClick = { onOpenAccountDetail(account) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TodayMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.16f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.88f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}
