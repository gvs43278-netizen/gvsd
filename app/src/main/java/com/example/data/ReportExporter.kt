package com.example.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.domain.Account
import com.example.domain.AccountingEngine
import com.example.domain.LedgerTransaction
import com.example.domain.LoanRecord
import com.example.domain.MoneyUtils
import com.example.domain.Partner
import com.example.domain.PendingTransaction
import com.example.domain.ReportCategory
import java.io.FileOutputStream
import java.io.OutputStream

data class ReportRow(
    val col1: String,
    val col2: String,
    val col3: String,
    val col4: String
)

data class GeneratedReportData(
    val title: String,
    val subtitle: String,
    val headers: List<String>,
    val rows: List<ReportRow>,
    val footerSummary: String
)

object ReportExporter {

    fun buildReportData(
        category: ReportCategory,
        accounts: List<Account>,
        partners: List<Partner>,
        transactions: List<LedgerTransaction>,
        pendingTransactions: List<PendingTransaction>,
        loans: List<LoanRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): GeneratedReportData {
        val activeTxs = transactions.filter { !it.reversed }
        val accountMap = accounts.associateBy { it.id }

        return when (category) {
            ReportCategory.DAILY,
            ReportCategory.WEEKLY,
            ReportCategory.MONTHLY,
            ReportCategory.YEARLY -> {
                val startMillis = when (category) {
                    ReportCategory.DAILY -> MoneyUtils.startOfTodayMillis(nowMillis)
                    ReportCategory.WEEKLY -> MoneyUtils.startOfWeekMillis(nowMillis)
                    ReportCategory.MONTHLY -> MoneyUtils.startOfMonthMillis(nowMillis)
                    else -> MoneyUtils.startOfYearMillis(nowMillis)
                }
                val filtered = activeTxs.filter { it.businessDateMillis >= startMillis }
                val commIn = filtered.sumOf { it.commissionIncomePaisa }
                val exp = filtered.sumOf { it.commissionExpensePaisa + it.expenseAmountPaisa }
                val net = commIn - exp
                GeneratedReportData(
                    title = "${category.titleBn} - Business Manager Pro",
                    subtitle = "প্রস্তুতের তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)} | মোট এন্ট্রি: ${MoneyUtils.toBengaliDigits(filtered.size.toString())}",
                    headers = listOf("তারিখ", "বিবরণ", "গ্রহণ / প্রদান", "কমিশন / খরচ"),
                    rows = filtered.map { tx ->
                        ReportRow(
                            col1 = MoneyUtils.formatDateBn(tx.businessDateMillis),
                            col2 = tx.title,
                            col3 = "${MoneyUtils.formatBdt(tx.receiveAmountPaisa)} / ${MoneyUtils.formatBdt(tx.payAmountPaisa)}",
                            col4 = "আয়: ${MoneyUtils.formatBdt(tx.commissionIncomePaisa)} | ব্যয়: ${MoneyUtils.formatBdt(tx.expenseAmountPaisa + tx.commissionExpensePaisa)}"
                        )
                    },
                    footerSummary = "মোট কমিশন আয়: ${MoneyUtils.formatBdt(commIn)} | মোট খরচ: ${MoneyUtils.formatBdt(exp)} | নিট লাভ/ক্ষতি: ${MoneyUtils.formatBdt(net)}"
                )
            }

            ReportCategory.ACCOUNT_WISE -> {
                val total = accounts.sumOf { it.balancePaisa }
                GeneratedReportData(
                    title = "অ্যাকাউন্টভিত্তিক রিপোর্ট - Business Manager Pro",
                    subtitle = "মোট ১১টি অ্যাকাউন্ট | তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("অ্যাকাউন্ট", "ধরন", "শেষ ৪ ডিজিট", "বর্তমান ব্যালেন্স"),
                    rows = accounts.map { acc ->
                        ReportRow(
                            col1 = acc.name,
                            col2 = acc.category,
                            col3 = acc.lastFourDigits.ifBlank { "-" },
                            col4 = MoneyUtils.formatBdt(acc.balancePaisa)
                        )
                    },
                    footerSummary = "সর্বমোট অ্যাকাউন্ট ব্যালেন্স: ${MoneyUtils.formatBdt(total)}"
                )
            }

            ReportCategory.EMPLOYEE_WISE -> {
                val grouped = activeTxs.groupBy { it.submittedByName.ifBlank { it.submittedByUid } }
                GeneratedReportData(
                    title = "কর্মচারীভিত্তিক রিপোর্ট - Business Manager Pro",
                    subtitle = "তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("কর্মচারী", "মোট লেনদেন", "মোট পরিমাণ", "কমিশন আয়"),
                    rows = grouped.map { (name, list) ->
                        val vol = list.sumOf { maxOf(it.receiveAmountPaisa, it.payAmountPaisa) }
                        val comm = list.sumOf { it.commissionIncomePaisa }
                        ReportRow(
                            col1 = name,
                            col2 = MoneyUtils.toBengaliDigits(list.size.toString()),
                            col3 = MoneyUtils.formatBdt(vol),
                            col4 = MoneyUtils.formatBdt(comm)
                        )
                    },
                    footerSummary = "মোট অবদানকারী: ${MoneyUtils.toBengaliDigits(grouped.size.toString())}"
                )
            }

            ReportCategory.COMMISSION -> {
                val commTxs = activeTxs.filter { it.commissionIncomePaisa > 0L || it.commissionExpensePaisa > 0L }
                val totalIn = commTxs.sumOf { it.commissionIncomePaisa }
                val totalOut = commTxs.sumOf { it.commissionExpensePaisa }
                GeneratedReportData(
                    title = "কমিশন রিপোর্ট - Business Manager Pro",
                    subtitle = "তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("তারিখ", "ধরন ও বিবরণ", "কমিশন আয়", "পার্টনার কমিশন ব্যয়"),
                    rows = commTxs.map { tx ->
                        ReportRow(
                            col1 = MoneyUtils.formatDateBn(tx.businessDateMillis),
                            col2 = tx.title,
                            col3 = MoneyUtils.formatBdt(tx.commissionIncomePaisa),
                            col4 = MoneyUtils.formatBdt(tx.commissionExpensePaisa)
                        )
                    },
                    footerSummary = "মোট কমিশন আয়: ${MoneyUtils.formatBdt(totalIn)} | পার্টনার কমিশন ব্যয়: ${MoneyUtils.formatBdt(totalOut)}"
                )
            }

            ReportCategory.PARTNER -> {
                GeneratedReportData(
                    title = "পার্টনার রিপোর্ট - Business Manager Pro",
                    subtitle = "তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("পার্টনার", "পাঠানো হয়েছে", "গ্রহণ করা হয়েছে", "নিট পাওনা ও কমিশন ব্যয়"),
                    rows = partners.map { p ->
                        ReportRow(
                            col1 = p.name,
                            col2 = MoneyUtils.formatBdt(p.totalSentPaisa),
                            col3 = MoneyUtils.formatBdt(p.totalReceivedPaisa),
                            col4 = "নিট: ${MoneyUtils.formatBdt(p.netBalancePaisa)} | কমিশন: ${MoneyUtils.formatBdt(p.totalCommissionExpensePaisa)}"
                        )
                    },
                    footerSummary = "মোট পার্টনার কমিশন ব্যয়: ${MoneyUtils.formatBdt(partners.sumOf { it.totalCommissionExpensePaisa })}"
                )
            }

            ReportCategory.LOAN -> {
                val payableRem = loans.filter { it.direction == "PAYABLE" }.sumOf { it.totalRemainingPaisa }
                val receivableRem = loans.filter { it.direction == "RECEIVABLE" }.sumOf { it.totalRemainingPaisa }
                GeneratedReportData(
                    title = "ঋণ ও দেনা-পাওনা রিপোর্ট - Business Manager Pro",
                    subtitle = "তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("ব্যক্তি ও ধরন", "আসল ও সুদ", "পরিশোধিত", "বাকি ও অবস্থা"),
                    rows = loans.map { l ->
                        val dirLabel = if (l.direction == "PAYABLE") "ধার নিয়েছি" else "ধার দিয়েছি"
                        val statusLabel = if (l.isOverdue(nowMillis)) "মেয়াদোত্তীর্ণ (Overdue)" else l.status
                        ReportRow(
                            col1 = "${l.personName} ($dirLabel)",
                            col2 = "আসল ${MoneyUtils.formatBdt(l.principalPaisa)} + সুদ ${MoneyUtils.formatBdt(l.interestPaisa)}",
                            col3 = "আসল ${MoneyUtils.formatBdt(l.paidPrincipalPaisa)} + সুদ ${MoneyUtils.formatBdt(l.paidInterestPaisa)}",
                            col4 = "${MoneyUtils.formatBdt(l.totalRemainingPaisa)} ($statusLabel)"
                        )
                    },
                    footerSummary = "বাকি দেনা (Payable): ${MoneyUtils.formatBdt(payableRem)} | বাকি পাওনা (Receivable): ${MoneyUtils.formatBdt(receivableRem)}"
                )
            }

            ReportCategory.EXPENSE -> {
                val expTxs = activeTxs.filter { it.expenseAmountPaisa > 0L }
                val total = expTxs.sumOf { it.expenseAmountPaisa }
                GeneratedReportData(
                    title = "খরচ রিপোর্ট - Business Manager Pro",
                    subtitle = "তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("তারিখ", "খরচের খাত", "অ্যাকাউন্ট", "পরিমাণ"),
                    rows = expTxs.map { tx ->
                        ReportRow(
                            col1 = MoneyUtils.formatDateBn(tx.businessDateMillis),
                            col2 = tx.title,
                            col3 = accountMap[tx.payAccountId]?.name ?: tx.payAccountId,
                            col4 = MoneyUtils.formatBdt(tx.expenseAmountPaisa)
                        )
                    },
                    footerSummary = "মোট দোকান খরচ: ${MoneyUtils.formatBdt(total)}"
                )
            }

            ReportCategory.PROFIT_LOSS -> {
                val summary = AccountingEngine.computeDashboardSummary(accounts, transactions, nowMillis)
                GeneratedReportData(
                    title = "লাভ-ক্ষতি রিপোর্ট - Business Manager Pro",
                    subtitle = "তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("হিসাবের খাত", "ধরন", "মন্তব্য", "পরিমাণ"),
                    rows = listOf(
                        ReportRow("মোট মূলধন (Capital)", "মূলধন", "লাভ-ক্ষতিতে গণনা নয়", MoneyUtils.formatBdt(summary.totalCapitalPaisa)),
                        ReportRow("কমিশন আয়", "আয়", "গ্রাহক, রিচার্জ ও বিল কমিশন", MoneyUtils.formatBdt(summary.totalCommissionIncomePaisa)),
                        ReportRow("পার্টনার কমিশন ব্যয়", "ব্যয়", "পার্টনার লেনদেন কমিশন", MoneyUtils.formatBdt(summary.totalPartnerCommissionExpensePaisa)),
                        ReportRow("দোকান খরচ", "ব্যয়", "ভাড়া, বিদ্যুৎ, WiFi ও অন্যান্য", MoneyUtils.formatBdt(summary.totalShopExpensePaisa)),
                        ReportRow("নিট লাভ / ক্ষতি", "ফলাফল", "আয় - পার্টনার কমিশন - দোকান খরচ", MoneyUtils.formatBdt(summary.netProfitLossPaisa)),
                        ReportRow("ঋণের সুদ আয়", "আলাদা হিসাব", "ধার দেওয়া ঋণের সুদ", MoneyUtils.formatBdt(summary.interestIncomePaisa)),
                        ReportRow("ঋণের সুদ ব্যয়", "আলাদা হিসাব", "ধার নেওয়া ঋণের সুদ", MoneyUtils.formatBdt(summary.interestExpensePaisa))
                    ),
                    footerSummary = "নিট লাভ/ক্ষতি: ${MoneyUtils.formatBdt(summary.netProfitLossPaisa)}"
                )
            }

            ReportCategory.PENDING -> {
                GeneratedReportData(
                    title = "Pending লেনদেন রিপোর্ট - Business Manager Pro",
                    subtitle = "তারিখ: ${MoneyUtils.formatDateTimeBn(nowMillis)}",
                    headers = listOf("তারিখ", "বিবরণ", "জমাদানকারী", "অবস্থা ও পরিমাণ"),
                    rows = pendingTransactions.map { p ->
                        val amt = maxOf(p.receiveAmountPaisa, p.payAmountPaisa)
                        ReportRow(
                            col1 = MoneyUtils.formatDateBn(p.businessDateMillis),
                            col2 = p.title,
                            col3 = p.submittedByName,
                            col4 = "${p.status} (${MoneyUtils.formatBdt(amt)})"
                        )
                    },
                    footerSummary = "মোট Pending: ${MoneyUtils.toBengaliDigits(pendingTransactions.count { it.status == "PENDING" }.toString())}"
                )
            }
        }
    }

    fun writeCsv(report: GeneratedReportData, outputStream: OutputStream) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            // UTF-8 BOM for Microsoft Excel Bengali compatibility
            writer.write("\uFEFF")
            writer.write("\"${escapeCsv(report.title)}\"\n")
            writer.write("\"${escapeCsv(report.subtitle)}\"\n\n")
            writer.write(report.headers.joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n")
            for (row in report.rows) {
                writer.write(
                    listOf(row.col1, row.col2, row.col3, row.col4)
                        .joinToString(",") { "\"${escapeCsv(it)}\"" } + "\n"
                )
            }
            writer.write("\n\"${escapeCsv(report.footerSummary)}\"\n")
        }
    }

    private fun escapeCsv(s: String): String = s.replace("\"", "\"\"")

    fun createPdfDocument(report: GeneratedReportData): PdfDocument {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val lineHeight = 20f

        val titlePaint = Paint().apply {
            color = Color.parseColor("#065F46")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#065F46")
        }
        val rowPaint = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 0.8f
        }

        val colWidths = floatArrayOf(90f, 165f, 130f, 138f)
        val rowsPerPage = 30
        val chunks = if (report.rows.isEmpty()) listOf(emptyList()) else report.rows.chunked(rowsPerPage)

        chunks.forEachIndexed { pageIndex, pageRows ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = margin + 12f
            canvas.drawText(report.title, margin, y, titlePaint)
            y += 18f
            canvas.drawText(report.subtitle, margin, y, subtitlePaint)
            y += 18f

            // Table Header
            canvas.drawRect(margin, y, pageWidth - margin, y + 24f, headerBgPaint)
            var x = margin + 4f
            report.headers.forEachIndexed { idx, h ->
                canvas.drawText(h.take(24), x, y + 16f, headerPaint)
                x += colWidths.getOrElse(idx) { 120f }
            }
            y += 28f

            for (row in pageRows) {
                var rx = margin + 4f
                listOf(row.col1, row.col2, row.col3, row.col4).forEachIndexed { idx, cell ->
                    canvas.drawText(cell.take(28), rx, y + 12f, rowPaint)
                    rx += colWidths.getOrElse(idx) { 120f }
                }
                y += lineHeight
                canvas.drawLine(margin, y, pageWidth - margin, y, borderPaint)
            }

            if (pageIndex == chunks.lastIndex) {
                y += 20f
                canvas.drawText(report.footerSummary, margin, y, titlePaint.apply { textSize = 11f })
            }

            document.finishPage(page)
        }

        return document
    }

    fun writePdf(report: GeneratedReportData, outputStream: OutputStream) {
        val doc = createPdfDocument(report)
        try {
            doc.writeTo(outputStream)
        } finally {
            doc.close()
        }
    }

    fun printReport(context: Context, report: GeneratedReportData) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val jobName = "${report.title} Print"
        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("business_report.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback
                ) {
                    val doc = createPdfDocument(report)
                    try {
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            doc.writeTo(out)
                        }
                        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    } finally {
                        doc.close()
                    }
                }
            },
            null
        )
    }
}
