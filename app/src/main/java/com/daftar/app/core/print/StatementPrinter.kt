package com.daftar.app.core.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.daftar.app.domain.model.ShopProfile

/** One summary cell of the print masthead (per-currency balance, IN/OUT/NET…). */
data class PrintSummaryCell(val heading: String, val amount: String, val status: String)

/**
 * Everything needed to render a v18-style print document. Each statement
 * screen builds one of these from its own UI state.
 */
data class StatementPrintSpec(
    /** Also used as the suggested file name ("statement-haji-dawood"). */
    val jobName: String,
    val docTitle: String,
    val pashtoTitle: String,
    val metaLeftLabel: String,
    val metaLeftValue: String,
    val metaLeftSub: String,
    val metaRightLabel: String,
    val metaRightValue: String,
    val metaRightSub: String,
    val summary: List<PrintSummaryCell>,
    val columns: List<String>,
    /** One list of cell strings per row, aligned with [columns]. */
    val rows: List<List<String>>,
    val profile: ShopProfile,
    val issuedLabel: String,
)

/**
 * Android port of v18's buildStatementPrintHtml + window.print(): renders the
 * statement into a self-contained A4 HTML document and hands it to the system
 * print service, where "Save as PDF" is available — the same flow the
 * prototype relies on in the browser.
 */
object StatementPrinter {

    // The WebView must stay reachable until the print job takes ownership.
    private var activeWebView: WebView? = null

    fun print(context: Context, spec: StatementPrintSpec) {
        val webView = WebView(context)
        activeWebView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(
                    spec.jobName,
                    view.createPrintDocumentAdapter(spec.jobName),
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build(),
                )
                activeWebView = null
            }
        }
        webView.loadDataWithBaseURL(null, buildHtml(spec), "text/html", "utf-8", null)
    }

    /** v18 print CSS: A4, Georgia serif, dark table head, zebra rows, rotated stamp. */
    private fun buildHtml(spec: StatementPrintSpec): String {
        val summaryCells = spec.summary.joinToString("") { cell ->
            """<div class="cell"><div class="h">${esc(cell.heading)}</div>""" +
                """<div class="a">${esc(cell.amount)}</div>""" +
                """<div class="s">${esc(cell.status)}</div></div>"""
        }
        val headRow = spec.columns.joinToString("") { "<th>${esc(it)}</th>" }
        val bodyRows = if (spec.rows.isEmpty()) {
            """<tr><td class="empty" colspan="${spec.columns.size}">NO TRANSACTIONS IN SELECTED PERIOD</td></tr>"""
        } else {
            spec.rows.joinToString("") { row ->
                "<tr>" + row.joinToString("") { "<td>${esc(it)}</td>" } + "</tr>"
            }
        }
        return """
            <html><head><meta charset="utf-8"><style>
            @page { size: A4; margin: 18mm; }
            body { font-family: Georgia, 'Times New Roman', serif; color: #161C1F; font-size: 12px; }
            .masthead { border-bottom: 2.5px solid #161C1F; padding-bottom: 10px; }
            .shop { font-size: 22px; font-weight: 600; }
            .tagline { font-size: 11px; color: #6B7478; margin-top: 2px; }
            .doc-title { margin-top: 10px; font-size: 14px; letter-spacing: 0.2em; text-transform: uppercase; }
            .pashto { direction: rtl; font-size: 12px; color: #6B7478; }
            .meta { display: flex; justify-content: space-between; margin-top: 12px; font-size: 11px; }
            .meta .lbl { text-transform: uppercase; letter-spacing: 0.15em; font-size: 9px; color: #6B7478; }
            .meta .val { font-weight: 600; margin-top: 2px; }
            .summary { display: flex; gap: 10px; margin-top: 14px; }
            .summary .cell { flex: 1; border: 1px solid #d8d2c0; border-radius: 6px; padding: 8px; text-align: center; }
            .summary .h { font-size: 9px; letter-spacing: 0.2em; color: #6B7478; }
            .summary .a { font-size: 15px; font-weight: 600; margin-top: 3px; }
            .summary .s { font-size: 8px; letter-spacing: 0.15em; color: #6B7478; margin-top: 2px; }
            table { width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 10.5px; }
            th { background: #161C1F; color: #F3EEE1; text-align: left; padding: 6px 8px;
                 font-size: 9px; letter-spacing: 0.12em; text-transform: uppercase; }
            td { padding: 6px 8px; border-bottom: 1px solid #e5dfcd; }
            tr:nth-child(even) td { background: #faf6ea; }
            td.empty { text-align: center; color: #6B7478; letter-spacing: 0.15em; font-size: 10px; padding: 24px; }
            .footer { display: flex; justify-content: space-between; margin-top: 20px;
                      border-top: 2px solid #161C1F; padding-top: 8px; font-size: 9px;
                      letter-spacing: 0.15em; text-transform: uppercase; color: #6B7478; }
            .stamp { margin-top: 18px; text-align: right; }
            .stamp .ring { display: inline-block; width: 84px; height: 84px; border: 2.5px solid #A8541A;
                           border-radius: 50%; transform: rotate(-12deg); color: #A8541A; font-size: 11px;
                           text-align: center; line-height: 1.4; padding-top: 24px; box-sizing: border-box; }
            </style></head><body>
            <div class="masthead">
              <div class="shop">${esc(spec.profile.shopName)} · ${esc(spec.profile.ownerName)}</div>
              <div class="tagline">${esc(spec.profile.tagline)} · ${esc(spec.profile.city.displayName)} · ${esc(spec.profile.registration)}</div>
              <div class="doc-title">${esc(spec.docTitle)}</div>
              <div class="pashto">${esc(spec.pashtoTitle)}</div>
            </div>
            <div class="meta">
              <div><div class="lbl">${esc(spec.metaLeftLabel)}</div><div class="val">${esc(spec.metaLeftValue)}</div><div>${esc(spec.metaLeftSub)}</div></div>
              <div style="text-align:right"><div class="lbl">${esc(spec.metaRightLabel)}</div><div class="val">${esc(spec.metaRightValue)}</div><div>${esc(spec.metaRightSub)}</div></div>
            </div>
            <div class="summary">$summaryCells</div>
            <table><thead><tr>$headRow</tr></thead><tbody>$bodyRows</tbody></table>
            <div class="footer"><span>Page 1 of 1</span><span>Generated ${esc(spec.issuedLabel)}</span></div>
            <div class="stamp"><span class="ring">مهر صرافي<br>KBL · 2026</span></div>
            </body></html>
        """.trimIndent()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
