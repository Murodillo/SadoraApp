package uz.sadora.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import uz.sadora.app.AppControllers
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.nav.AppLink
import uz.sadora.app.nav.AppLinks
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CircleIconButton
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraToast
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.SettingsRow
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.onboarding.LanguageSwitch

/**
 * The app for a follower-only account: the person they follow, and settings.
 *
 * No tabs and no tracking of their own. One person fills the screen; several get a row
 * of names above it. A code from a link or a push about her lands here too.
 */
@Composable
fun PartnerShell(
    state: AppState,
    controllers: AppControllers,
    onSignedOut: () -> Unit,
) {
    val partner = controllers.partner
    val t = strings.partner
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var codeSheet by remember { mutableStateOf(false) }
    var sheetCode by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    val following = partner.following
    val current = selected?.takeIf { id -> following.any { it.linkId == id } } ?: following.firstOrNull()?.linkId
    // Read inside the resume effect, which outlives the composition that made it.
    val shown by rememberUpdatedState(current)

    // Only with a session: on sign-out the store is replaced while this screen is still
    // fading out, and a read from it then failed with 401 into the next account's controller.
    val signedIn = controllers.account.isSignedIn
    LaunchedEffect(partner, signedIn) { if (signedIn) partner.refresh(silent = false) }
    // Her status can change while the app sat in the background: she said yes, paused,
    // or ticked something new — the list and the person on screen are both read again.
    LifecycleResumeEffect(partner) {
        val job = scope.launch {
            if (!controllers.account.isSignedIn) return@launch
            partner.refresh()
            shown?.let { partner.loadView(it, silent = true) }
        }
        onPauseOrDispose { job.cancel() }
    }

    val link = AppLinks.pending
    LaunchedEffect(link) {
        if (link is AppLink.Partner) {
            AppLinks.consume()
            val code = link.code
            if (code != null) {
                sheetCode = code
                codeSheet = true
            } else {
                partner.refresh()
                shown?.let { partner.loadView(it, silent = true) }
            }
        }
    }

    SystemBackHandler(enabled = showSettings) { showSettings = false }

    Box(Modifier.fillMaxSize()) {
        if (showSettings) {
            PartnerSettings(
                state = state,
                controllers = controllers,
                onFollowAnother = {
                    showSettings = false
                    sheetCode = ""
                    codeSheet = true
                },
                onSignedOut = onSignedOut,
                onClose = { showSettings = false },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                SadoraTopBar(
                    t.title,
                    trailing = {
                        CircleIconButton(SadoraIcons.Settings, contentDescription = t.settingsTitle, onClick = { showSettings = true })
                    },
                )
                when {
                    current == null && partner.state == null -> Box(Modifier.fillMaxSize())
                    current == null -> EmptyState(
                        title = t.emptyFollowingTitle,
                        body = t.emptyFollowingBody,
                        actionText = t.haveCode,
                        onAction = {
                            sheetCode = ""
                            codeSheet = true
                        },
                        modifier = Modifier.padding(top = 80.dp),
                    )
                    else -> PartnerViewBody(
                        linkId = current,
                        partner = partner,
                        onLeft = { toast = it },
                        header = if (following.size < 2) null else {
                            {
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        following.forEach { person ->
                                            SelectChip(person.name, selected = person.linkId == current, onClick = { selected = person.linkId })
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        PartnerCodeSheet(
            visible = codeSheet,
            partner = partner,
            initialCode = sheetCode,
            onDismiss = { codeSheet = false },
            onAccepted = { followed ->
                codeSheet = false
                selected = followed.linkId
                toast = t.requestSent(followed.name)
            },
        )

        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)) {
            SadoraToast(message = toast, onTimeout = { toast = null })
        }
    }
}

/** Settings for a follower-only account: who they are, the language, the way out. */
@Composable
private fun PartnerSettings(
    state: AppState,
    controllers: AppControllers,
    onFollowAnother: () -> Unit,
    onSignedOut: () -> Unit,
    onClose: () -> Unit,
) {
    val t = strings.partner
    val profile = strings.profile
    val settings = strings.settings
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val account = controllers.account
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SadoraTopBar(t.settingsTitle, onBack = onClose)
        ScreenContent {
            item {
                SadoraCard {
                    Text(state.name.ifBlank { profile.unnamed }, style = Sadora.type.h2, color = c.text)
                    Text(
                        "+${uz.sadora.contract.UzbekPhone.COUNTRY_CODE} ${uz.sadora.contract.UzbekPhone.format(state.phone)}",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }
            item {
                SadoraCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.language, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
                        LanguageSwitch(selected = state.language, onSelect = {
                            state.language = it
                            scope.launch { account.saveProfile() }
                        })
                    }
                }
            }
            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(SadoraIcons.Plus, t.followAnother, onClick = onFollowAnother)
                }
            }
            item {
                SadoraButton(
                    if (account.busy) profile.signingOut else profile.signOut,
                    tone = ButtonTone.Secondary,
                    enabled = !account.busy,
                    onClick = {
                        scope.launch {
                            account.signOut()
                            onSignedOut()
                        }
                    },
                )
            }
            item { SadoraButton(settings.deleteAccount, { confirmDelete = true }, tone = ButtonTone.Ghost) }
        }
    }

    SadoraDialog(
        visible = confirmDelete,
        title = settings.deleteAccountConfirm,
        body = settings.deleteAccountBody,
        confirmText = settings.deleteAccount,
        onConfirm = {
            confirmDelete = false
            scope.launch { if (account.deleteAccount(reason = null)) onSignedOut() }
        },
        onDismiss = { confirmDelete = false },
    )
}
