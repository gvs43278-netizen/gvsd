package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ReportExporter
import com.example.domain.Account
import com.example.domain.LedgerTransaction
import com.example.domain.LoanRecord
import com.example.domain.Partner
import com.example.domain.PendingTransaction
import com.example.domain.ReportCategory

@Composable
fun ReportsScreen(
    accounts: List<Account>,
    partners: List<Partner>,
    transactions: List<LedgerTransaction>,
    pendingTransactions: List<PendingTransaction>,
    loans: List<LoanRecord>,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(ReportCategory.DAILY) }

    val reportData = remember(
        selectedCategory, accounts, partners, transactions, pendingTransactions, loans
    ) {
        ReportExporter.buildReportData(
            category = selectedCategory,
            accounts = accounts,
            partners = partners,
            transactions = transactions,
            pendingTransactions = pendingTransactions,
            loans = loans
        )
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writePdf(reportData, out)
                }
                onNotify("PDF রিপোর্ট সফলভাবে সেভ হয়েছে।")
            } catch (e: Exception) {
                onNotify("PDF সেভ করতে সমস্যা হয়েছে: ${e.localizedMessage}")
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writeCsv(reportData, out)
                }
                onNotify("CSV / Excel রিপোর্ট সফলভাবে সেভ হয়েছে।")
            } catch (e: Exception) {
                onNotify("CSV সেভ করতে সমস্যা হয়েছে: ${e.localizedMessage}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.titleBn) },
                    modifier = Modifier.testTag("report_cat_${cat.name.lowercase()}")
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { pdfLauncher.launch("${selectedCategory.name.lowercase()}_report.pdf") },
                modifier = Modifier.weight(1f).testTag("export_pdf_button")
            ) {
                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("PDF")
            }
            OutlinedButton(
                onClick = { csvLauncher.launch("${selectedCategory.name.lowercase()}_report.csv") },
                modifier = Modifier.weight(1f).testTag("export_csv_button")
            ) {
                Icon(Icons.Filled.TableChart, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("CSV/Excel")
            }
            OutlinedButton(
                onClick = { ReportExporter.printReport(context, reportData) },
                modifier = Modifier.weight(1f).testTag("print_report_button")
            ) {
                Icon(Icons.Filled.Print, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Print")
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(reportData.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(reportData.subtitle, style = MaterialTheme.typography.bodySmall)
                        Text(reportData.footerSummary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (reportData.rows.isEmpty()) {
                item {
                    Text(
                        text = "এই রিপোর্টের জন্য এখনও কোনো তথ্য নেই।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(reportData.rows) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(row.col2, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(row.col1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(row.col3, style = MaterialTheme.typography.bodySmall)
                            Text(row.col4, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
