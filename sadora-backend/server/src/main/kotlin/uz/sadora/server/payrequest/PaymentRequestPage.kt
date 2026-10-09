package uz.sadora.server.payrequest

import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.Language
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequestKind
import uz.sadora.contract.PaymentRequestStatus
import uz.sadora.server.share.DoctorPage

/**
 * Her request in a browser, for someone without the app: who asks, for what, how much,
 * and a Payme and a Click button. Her name and her note are escaped; the token goes only
 * into this page's own links.
 */
internal object PaymentRequestPage {

    fun render(view: PaymentRequestService.WebRequest, token: String, language: Language): String {
        val t = Words(language)
        val e = DoctorPage::escape
        val request = view.request
        val body = StringBuilder()
        body.append("<section>")
        body.append("<h1>").append(t.asks(e(view.ownerName))).append(" 💝</h1>")
        val what = if (request.kind == PaymentRequestKind.PREMIUM) {
            "Sadora Premium · ${t.period(request.period)}"
        } else {
            t.consultation + (request.doctorName?.let { " · ${e(it)}" } ?: "")
        }
        body.append("<p class=\"big\">").append(what).append("</p>")
        request.note?.let { body.append("<p class=\"note\">“").append(e(it)).append("”</p>") }

        when (request.status) {
            PaymentRequestStatus.PAID -> body.append("<p class=\"done\">").append(t.paid).append("</p>")
            PaymentRequestStatus.OPEN -> {
                val premium = request.kind == PaymentRequestKind.PREMIUM
                val plans = if (premium) view.plans else emptyList()
                val chosen = plans.firstOrNull { it.period == request.period }
                val options = if (premium) plans else listOf(null)
                options.forEach { plan ->
                    val amount = plan?.priceMinor ?: request.amountMinor
                    body.append("<div class=\"plan").append(if (plan == null || plan == chosen) " chosen" else "").append("\">")
                    plan?.let { body.append("<b>").append(t.period(it.period)).append("</b> · ") }
                    body.append(sum(amount)).append(" so'm<div class=\"buttons\">")
                    view.providers.filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }.forEach { provider ->
                        val key = provider.name.lowercase()
                        body.append("<a class=\"pay ").append(key).append("\" href=\"/pr/").append(token).append("/pay?provider=").append(key)
                        plan?.let { body.append("&amp;plan=").append(it.id) }
                        body.append("\">").append(if (provider == PaymentProvider.PAYME) "Payme" else "Click").append("</a>")
                    }
                    body.append("</div></div>")
                }
                if (view.providers.none { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }) {
                    body.append("<p class=\"muted\">").append(t.noProviders).append("</p>")
                }
                body.append("<p class=\"muted\">").append(t.after).append("</p>")
            }
            else -> body.append("<p class=\"muted\">").append(t.closed).append("</p>")
        }
        body.append("</section>")
        return page(t.title(view.ownerName), language, body.toString(), t.footer)
    }

    fun gone(language: Language): String {
        val t = Words(language)
        return page("SADORA", language, "<section><h1>${t.goneTitle}</h1><p class=\"muted\">${t.closed}</p></section>", t.footer)
    }

    private fun sum(minor: Long): String = (minor / 100).toString().reversed().chunked(3).joinToString(" ").reversed()

    private fun page(title: String, language: Language, body: String, footer: String) = """
        <!doctype html>
        <html lang="${language.name.lowercase()}">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex, nofollow">
        <meta name="referrer" content="no-referrer">
        <title>${DoctorPage.escape(title)}</title>
        <style>
          body{margin:0;background:#f7f5ff;color:#1a1630;font:16px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;overflow-wrap:anywhere}
          .top{padding:14px 18px;background:linear-gradient(90deg,#7b61ff,#ff6fb8);color:#fff;font-weight:800;letter-spacing:.18em}
          main{max-width:560px;margin:0 auto;padding:16px}
          section{background:#fff;border-radius:18px;padding:18px;margin:0 0 14px;box-shadow:0 6px 24px rgba(123,97,255,.08)}
          h1{margin:0 0 8px;font-size:22px}.big{font-size:18px;font-weight:700;margin:4px 0 10px;color:#6247e0}
          .note{font-style:italic;background:#f7f5ff;border-radius:12px;padding:10px 12px}
          .plan{border:2px solid #ece8ff;border-radius:14px;padding:12px;margin:10px 0}.plan.chosen{border-color:#7b61ff}
          .buttons{display:flex;gap:10px;margin-top:10px}
          .pay{flex:1;text-align:center;padding:12px;border-radius:12px;color:#fff;font-weight:700;text-decoration:none}
          .payme{background:#33cccc}.click{background:#0073ff}
          .done{font-size:18px;font-weight:700;color:#21a366}
          .muted{color:#6f6a8a;font-size:14px}footer{color:#6f6a8a;font-size:13px;text-align:center;padding:8px 16px 28px}
        </style>
        </head>
        <body><div class="top">SADORA</div><main>$body</main><footer>$footer</footer></body></html>
    """.trimIndent()

    private class Words(private val language: Language) {
        private fun pick(uz: String, ru: String, en: String) = when (language) {
            Language.UZ -> uz
            Language.RU -> ru
            Language.EN -> en
        }

        fun title(name: String) = pick("$name yordam so'rayapti", "$name просит помощи", "$name is asking for help")
        fun asks(name: String) = pick("$name sizdan yordam so'rayapti", "$name просит вас о помощи", "$name is asking for your help")
        fun period(period: BillingPeriod?) =
            if (period == BillingPeriod.YEAR) pick("1 yil", "1 год", "1 year") else pick("1 oy", "1 месяц", "1 month")
        val consultation = pick("Shifokor konsultatsiyasi", "Консультация врача", "Doctor consultation")
        val paid = pick("To'langan ✓ Rahmat!", "Оплачено ✓ Спасибо!", "Paid ✓ Thank you!")
        val closed = pick("Bu so'rov yopilgan.", "Эта просьба закрыта.", "This request is closed.")
        val goneTitle = pick("Havola ishlamaydi", "Ссылка не работает", "This link no longer works")
        val after = pick(
            "To'lovdan keyin u avtomatik ravishda unga ochiladi.",
            "После оплаты всё откроется у неё автоматически.",
            "Once paid, it opens for her automatically.",
        )
        val noProviders = pick("Hozircha to'lov qabul qilinmayapti.", "Оплата пока недоступна.", "Payment is not available yet.")
        val footer = pick("Sadora — ayollar salomatligi ilovasi", "Sadora — приложение о женском здоровье", "Sadora — a women's health app")
    }
}
