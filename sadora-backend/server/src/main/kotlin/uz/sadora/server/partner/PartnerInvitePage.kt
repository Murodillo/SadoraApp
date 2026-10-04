package uz.sadora.server.partner

import uz.sadora.contract.Language

/** The one-screen page behind a Yaqinim link: install the app, type this code. */
internal object PartnerInvitePage {

    fun render(code: String, language: Language): String {
        val t = Words(language)
        val shown = PartnerService.display(code)
        return """
            <!doctype html>
            <html lang="${language.name.lowercase()}">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="robots" content="noindex">
            <title>SADORA · ${t.title}</title>
            <style>
              body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#FBF6F4;color:#2B2125}
              main{max-width:440px;margin:0 auto;padding:40px 20px;text-align:center}
              h1{font-size:24px;margin:0 0 12px}
              p{line-height:1.5;color:#5B4D52}
              .code{font-size:32px;letter-spacing:4px;font-weight:700;background:#fff;border-radius:16px;padding:18px;margin:24px 0}
              a.btn{display:block;background:#C2527A;color:#fff;text-decoration:none;border-radius:14px;padding:14px;font-weight:600;margin-top:12px}
              a.alt{background:#fff;color:#C2527A;border:1px solid #E7C9D4}
            </style>
            </head>
            <body><main>
            <h1>${t.title}</h1>
            <p>${t.lead}</p>
            <div class="code">$shown</div>
            <a class="btn" href="sadora://yaqinim/$code">${t.open}</a>
            <a class="btn alt" href="https://play.google.com/store/apps/details?id=uz.sadora.app">Google Play</a>
            <p>${t.steps}</p>
            </main></body></html>
        """.trimIndent()
    }

    private class Words(private val language: Language) {
        private fun pick(uz: String, ru: String, en: String) = when (language) {
            Language.UZ -> uz
            Language.RU -> ru
            Language.EN -> en
        }

        val title get() = pick("Sizni Yaqinim sifatida taklif qilishdi", "Вас пригласили как близкого", "You are invited as her person")
        val lead get() = pick(
            "Sadora ilovasini o'rnating va shu kodni kiriting — u holatini siz bilan ulashadi.",
            "Установите приложение Sadora и введите этот код — она поделится с вами своим состоянием.",
            "Install the Sadora app and enter this code — she will share how she is with you.",
        )
        val open get() = pick("Ilovada ochish", "Открыть в приложении", "Open in the app")
        val steps get() = pick(
            "Ilovada: «Yaqinim taklif qildi» → kod → telefon raqam.",
            "В приложении: «Меня пригласил близкий» → код → номер телефона.",
            "In the app: “I have an invite” → code → phone number.",
        )
    }
}
