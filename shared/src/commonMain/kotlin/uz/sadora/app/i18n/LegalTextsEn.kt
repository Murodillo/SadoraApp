package uz.sadora.app.i18n

/**
 * English. A translation of [LegalTextsUz], which governs — [translationNotice] says so
 * on the screen, because a clause that drifts in translation must not quietly become the
 * obligation.
 */
object LegalTextsEn : LegalTexts {

    override val effectiveDate = "3 September 2026"
    override val translationNotice =
        "Translation for convenience. The Uzbek version is the legally binding one."

    override val termsTitle = "Terms of Use"
    override val privacyTitle = "Privacy Policy"

    override val terms = listOf(
        LegalSection(
            "1. About these terms",
            listOf(
                "SADORA is a women's health tracking app. By starting to use it you " +
                    "accept these terms. If you do not agree with them, do not use the app.",
                "If the terms change we will tell you inside the app and ask you to " +
                    "accept the new version.",
            ),
        ),
        LegalSection(
            "2. SADORA is not a medical service",
            listOf(
                "The predictions, findings and suggestions in the app are general " +
                    "information. They do not replace a doctor's diagnosis, advice or " +
                    "treatment plan.",
                "Cycle predictions are a statistical calculation and must not be used as " +
                    "contraception.",
                "For any decision about your health, see a doctor. In an emergency, call " +
                    "emergency services.",
            ),
        ),
        LegalSection(
            "3. Your account",
            listOf(
                "An account is opened with your phone number and a one-time SMS code. " +
                    "Keeping control of the device the code arrives on, and of the number " +
                    "itself, is yours to do.",
                "The app may be used from the age of 13. Under 18, it is used with the " +
                    "consent of a parent or legal guardian.",
                "Do not share one account with other people: the findings are tuned to " +
                    "one person's data.",
            ),
        ),
        LegalSection(
            "4. What you may not do",
            listOf(
                "Using the app for unlawful purposes, attempting to reach other users' " +
                    "data, overloading the service or working around its protections is " +
                    "prohibited.",
                "The app's code, design and content may not be copied or resold without " +
                    "SADORA's written permission.",
            ),
        ),
        LegalSection(
            "5. Paid subscription",
            listOf(
                "SADORA Premium works on a subscription. Payment is taken from your App " +
                    "Store or Google Play account and the subscription renews " +
                    "automatically.",
                "You can cancel at any time in your store's settings. Cancelling takes " +
                    "effect at the end of the current period.",
                "Refunds follow the rules of the store you subscribed through.",
            ),
        ),
        LegalSection(
            "6. Your content",
            listOf(
                "What you record in the app is yours. You give us the right to store and " +
                    "process it only in order to provide the service.",
                "If you delete your account, your content is deleted with it.",
            ),
        ),
        LegalSection(
            "7. Availability",
            listOf(
                "The service is provided \"as is\". Maintenance, updates or causes " +
                    "outside your control may make the app temporarily unavailable.",
                "To the extent the law allows, SADORA is not liable for indirect damages.",
            ),
        ),
        LegalSection(
            "8. Suspending an account",
            listOf(
                "If these terms are broken we may restrict or close your account. We will " +
                    "explain why as far as we are able to.",
                "You can also delete your account at any time under Profile → Privacy and " +
                    "security.",
            ),
        ),
        LegalSection(
            "9. Contact",
            listOf(
                "Questions: support@sadora.uz",
            ),
        ),
    )

    override val privacy = listOf(
        LegalSection(
            "1. What we collect",
            listOf(
                "Account data: phone number, name and language choice.",
                "Health data: cycle dates, symptoms, mood, sleep, nutrition, water, " +
                    "medication doses and anything else you record.",
                "Device data: an install identifier, the app version and the operating " +
                    "system. Never a hardware identifier.",
            ),
        ),
        LegalSection(
            "2. What we use it for",
            listOf(
                "To calculate your cycle, send reminders and prepare personal findings — " +
                    "that is, to do what the app is for.",
                "Your data is used for AI findings only if you have given separate " +
                    "consent for that.",
                "Anonymous analytics are optional and can be turned off at any time.",
            ),
        ),
        LegalSection(
            "3. Who can see it",
            listOf(
                "We do not sell or give your health data to advertisers, insurers or " +
                    "employers.",
                "Data reaches only the technical partners the service cannot run without " +
                    "(servers and message delivery), and only as much of it as they need.",
                "Data may be disclosed where the law requires it; where we are able to, " +
                    "we will tell you.",
            ),
        ),
        LegalSection(
            "4. How we store it",
            listOf(
                "Data is encrypted in transit and on the server.",
                "Your sign-in key is held in your device's secure storage — Keychain on " +
                    "iOS, Keystore on Android.",
            ),
        ),
        LegalSection(
            "5. How long we keep it",
            listOf(
                "For as long as your account is active. If you delete the account, the " +
                    "data is erased completely within 30 days.",
            ),
        ),
        LegalSection(
            "6. Your rights",
            listOf(
                "You have the right to see, correct, export and delete your data.",
                "Consents you have given can be withdrawn at any time under Profile → " +
                    "Privacy and security.",
            ),
        ),
        LegalSection(
            "7. Contact",
            listOf(
                "Privacy questions: privacy@sadora.uz",
            ),
        ),
    )
}
