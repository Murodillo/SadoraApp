package uz.sadora.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.time.Clock
import uz.sadora.app.data.NotificationsController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.MedStatus
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.CircleIconButton
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SettingsRow

/**
 * What the bell on Today opens.
 *
 * It used to open the settings switches, so the unread dot — which lights for a dose
 * still waiting today — led to a page that did not mention the dose. Now it leads to the
 * dose first, then to what was actually sent to her phone; the switches are one tap on.
 */
@Composable
fun NotificationInboxScreen(
    state: AppState,
    notifications: NotificationsController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
) {
    val t = strings.settings
    val c = Sadora.colors
    LaunchedEffect(Unit) { notifications.loadSent() }

    val due = state.medications.filter { it.status == MedStatus.Pending }
    val sent = notifications.sent

    Column {
        SadoraTopBar(
            t.notificationsTitle,
            onBack = onClose,
            trailing = {
                CircleIconButton(SadoraIcons.Settings, contentDescription = t.inboxSettings) {
                    onOpen(Route.Notifications)
                }
            },
        )
        ScreenContent {
            if (due.isNotEmpty()) {
                item { CardLabel(t.inboxDueToday) }
                item {
                    SadoraCard {
                        due.forEach { dose ->
                            SettingsRow(
                                SadoraIcons.Pill,
                                t.inboxDoseDue("${dose.emoji} ${dose.name}", dose.time),
                                onClick = { onOpen(Route.Medications) },
                            )
                        }
                    }
                }
            }

            if (sent.isNotEmpty()) {
                item { CardLabel(t.inboxEarlier) }
                item {
                    SadoraCard {
                        val now = Clock.System.now()
                        sent.forEach { message ->
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                Text(message.title, style = Sadora.type.h3, color = c.text)
                                Text(message.body, style = Sadora.type.body, color = c.muted)
                                Text(
                                    strings.dates.ago(message.sentAt ?: message.scheduledFor, now),
                                    style = Sadora.type.body,
                                    color = c.muted2,
                                )
                            }
                        }
                    }
                }
            }

            if (due.isEmpty() && sent.isEmpty()) {
                item {
                    EmptyState(
                        title = t.inboxEmpty,
                        body = t.inboxEmptyBody,
                        actionText = t.inboxSettings,
                        onAction = { onOpen(Route.Notifications) },
                        glyph = "🔔",
                    )
                }
            }
        }
    }
}
