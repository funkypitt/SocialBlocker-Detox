package com.parentcontrol.socialblocker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.parentcontrol.socialblocker.App
import com.parentcontrol.socialblocker.R
import com.parentcontrol.socialblocker.sections.Discovery
import com.parentcontrol.socialblocker.sections.Exports
import com.parentcontrol.socialblocker.sections.LocalDayClock
import com.parentcontrol.socialblocker.sections.Pin
import com.parentcontrol.socialblocker.sections.RulesRepo
import com.parentcontrol.socialblocker.sections.SectionGuardService
import com.parentcontrol.socialblocker.sections.SectionPrefs
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------------------------------
// The family build: the lock screen, and the sections with a quota.
// ---------------------------------------------------------------------------------------------

/** The whole app behind the parent's PIN. Wrong answers cost a two-second wait, then longer. */
@Composable
fun PinScreen(prefs: SectionPrefs, onUnlocked: () -> Unit) {
    val colors = LocalColors.current
    val typo = LocalTypo.current
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var wrong by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(waitUntil) { while (System.currentTimeMillis() < waitUntil) { delay(250); now = System.currentTimeMillis() }; now = System.currentTimeMillis() }
    val waiting = now < waitUntil
    fun submit() {
        if (waiting) return
        val pin = field.text.trim()
        if (prefs.checkPin(pin)) { field = TextFieldValue(""); onUnlocked() }
        else { wrong++; field = TextFieldValue(""); waitUntil = System.currentTimeMillis() + (if (wrong >= 5) 30_000L else 2_000L) }
    }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.app_title), onBack = null)
            VSpace(24.dp)
            Small(stringResource(R.string.pin_enter), Modifier.padding(horizontal = rowPadH))
            ReaderTextField(
                value = field,
                onValueChange = { if (it.text.length <= Pin.MAX_LENGTH && it.text.all { c -> c.isDigit() }) field = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = rowPadH, vertical = 10.dp).focusRequester(focus),
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
                password = true,
                keyboard = KeyboardType.NumberPassword
            )
            Rule()
            if (wrong > 0) Small(stringResource(R.string.pin_wrong) + if (waiting) " · ${(waitUntil - now + 999) / 1000} s" else "", Modifier.padding(horizontal = rowPadH, vertical = 10.dp), color = colors.fg)
            TextRow(stringResource(R.string.action_ok), size = typo.title, inverted = field.text.length >= Pin.MIN_LENGTH && !waiting) { submit() }
        }
    }
}

/** Set a new PIN: the digits twice. Returns through [onDone] with the PIN, or null when cancelled. */
@Composable
fun NewPinFlow(onDone: (String?) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var bad by remember { mutableStateOf(false) }
    if (first == null) {
        TextPrompt(stringResource(R.string.pin_new) + if (bad) "  (4–8)" else "", keyboard = KeyboardType.NumberPassword, password = true,
            onDone = { if (Pin.valid(it)) { first = it; bad = false } else bad = true }, onCancel = { onDone(null) })
    } else {
        TextPrompt(stringResource(R.string.pin_confirm) + if (bad) "  " + stringResource(R.string.pin_mismatch) else "", keyboard = KeyboardType.NumberPassword, password = true,
            onDone = { if (it == first) onDone(it) else { bad = true; first = null } }, onCancel = { onDone(null) })
    }
}

private enum class PinAsk { SET, CHANGE_CURRENT, CHANGE_NEW, REMOVE }

/** A tappable row whose explanation needs more than one line: the title, then the hint under it. */
@Composable
private fun HintRow(title: String, hint: String, onClick: () -> Unit) {
    val typo = LocalTypo.current
    Column(Modifier.tapRow(onClick = onClick)) {
        T(title, size = typo.title, maxLines = 2)
        Small(hint, maxLines = 6)
    }
}

@Composable
fun SectionsScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val typo = LocalTypo.current
    val colors = LocalColors.current
    val prefs = app.sections
    val tick = rememberTick()
    var v by remember { mutableIntStateOf(0) }
    val rules = remember(v) { RulesRepo.rules(context) }
    val lang = context.resources.configuration.locales[0].language
    var serviceOn by remember { mutableStateOf(SectionGuardService.isEnabled(context)) }
    var dumps by remember { mutableIntStateOf(Discovery.count(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val clock = remember { LocalDayClock() }
    var minutesAsk by remember { mutableStateOf<Pair<String, Boolean>?>(null) }   // section key, weekend
    var rulesMenu by remember { mutableStateOf(false) }
    var pinAsk by remember { mutableStateOf<PinAsk?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<String?>(null) }   // the name of the export just saved
    val badFile = stringResource(R.string.sec_import_bad)
    val importedFmt = stringResource(R.string.sec_import_ok)
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    // the service is switched on in the system settings, and the counts move: look again now and then
    LaunchedEffect(Unit) {
        while (true) { serviceOn = SectionGuardService.isEnabled(context); dumps = Discovery.count(context); now = System.currentTimeMillis(); delay(1500) }
    }
    val saveRules = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && Exports.save(context, uri, Exports.RULES_FILE, RulesRepo.text(context))) saved = Exports.RULES_FILE
    }
    val saveDumps = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && Exports.save(context, uri, Exports.DISCOVERY_FILE, Discovery.all(context))) saved = Exports.DISCOVERY_FILE
    }
    val pickRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = RulesRepo.import(context, uri).fold({ importedFmt.format(it.version) }, { badFile })
        v++
    }
    BackHandler { nav.pop() }

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.sections), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Small(stringResource(R.string.sec_hint), Modifier.padding(horizontal = rowPadH).padding(top = 16.dp, bottom = 6.dp), maxLines = 12)
                // ---- the service ----
                val offSince = prefs.serviceOffSince
                HintRow(
                    when { serviceOn -> stringResource(R.string.sec_service_on); offSince > 0 -> stringResource(R.string.sec_service_off_since, timeFmt.format(Date(offSince))); else -> stringResource(R.string.sec_service_off) },
                    stringResource(R.string.sec_service_hint)
                ) { tick(); SectionGuardService.openSettings(context) }
                // ---- the PIN ----
                HintRow(stringResource(if (prefs.pinSet) R.string.pin_change else R.string.pin_set), stringResource(R.string.pin_set_hint)) {
                    tick(); pinAsk = if (prefs.pinSet) PinAsk.CHANGE_CURRENT else PinAsk.SET
                }
                if (prefs.pinSet) TextRow(stringResource(R.string.pin_remove), size = typo.title) { tick(); pinAsk = PinAsk.REMOVE }
                Rule(Modifier.padding(vertical = 8.dp))
                // ---- each app, each section ----
                val today = clock.dayKey(now)
                rules.apps.forEach { a ->
                    Small(a.name, Modifier.padding(horizontal = rowPadH).padding(top = 10.dp, bottom = 2.dp))
                    a.sections.forEach { s ->
                        val q = remember(v, now) { prefs.quota(s.key) }
                        ValueRow(s.name(lang), stringResource(if (q.enabled) R.string.on else R.string.off), strong = q.enabled, locked = false) {
                            tick(); prefs.setQuota(s.key, q.copy(enabled = !q.enabled)); v++
                        }
                        Column(Modifier.tapRow(enabled = q.enabled) { minutesAsk = s.key to false }) {
                            T(pluralStringResource(R.plurals.sec_minutes_value, q.weekdayMinutes, q.weekdayMinutes), size = typo.title, color = if (q.enabled) colors.fg else colors.dim, maxLines = 1)
                            Small(stringResource(R.string.sec_weekday))
                        }
                        Column(Modifier.tapRow(enabled = q.enabled) { minutesAsk = s.key to true }) {
                            T(pluralStringResource(R.plurals.sec_minutes_value, q.weekendMinutes, q.weekendMinutes), size = typo.title, color = if (q.enabled) colors.fg else colors.dim, maxLines = 1)
                            Small(stringResource(R.string.sec_weekend))
                        }
                        val spent = (prefs.spent(today, s.key) / 60_000L).toInt()
                        Column(Modifier.tapRow(enabled = false) { }) {
                            T(pluralStringResource(R.plurals.sec_today, spent, spent), size = typo.title, color = colors.dim, maxLines = 1)
                            Small(stringResource(R.string.sec_today_hint))
                        }
                    }
                }
                if (rules.apps.isEmpty()) Small(stringResource(R.string.sections_none), Modifier.padding(horizontal = rowPadH, vertical = 10.dp))
                Column(Modifier.tapRow { tick(); prefs.warnEnabled = !prefs.warnEnabled; v++ }) {
                    T(stringResource(if (remember(v) { prefs.warnEnabled }) R.string.on else R.string.off), size = typo.title, maxLines = 1)
                    Small(stringResource(R.string.sec_warn))
                }
                Rule(Modifier.padding(vertical = 8.dp))
                // ---- the rules ----
                HintRow(
                    stringResource(R.string.sec_rules, rules.version, stringResource(if (RulesRepo.source == RulesRepo.Source.CUSTOM) R.string.sec_rules_custom else R.string.sec_rules_bundled)),
                    stringResource(R.string.sec_rules_hint)
                ) { tick(); rulesMenu = true }
                // ---- discovery ----
                val discovering = prefs.discovering(now)
                HintRow(
                    if (discovering) stringResource(R.string.sec_discovery_on, timeFmt.format(Date(prefs.discoveryUntil))) else stringResource(R.string.sec_discovery_off),
                    stringResource(R.string.sec_discovery_hint)
                ) { tick(); prefs.discoveryUntil = if (discovering) 0L else System.currentTimeMillis() + 10 * 60_000L; now = System.currentTimeMillis() }
                TextRow(if (dumps == 0) stringResource(R.string.sec_dumps_none) else pluralStringResource(R.plurals.sec_dumps, dumps, dumps), secondary = if (dumps == 0) null else stringResource(R.string.sec_dumps_export), size = typo.title) {
                    if (dumps > 0) { tick(); saveDumps.launch(Exports.DISCOVERY_FILE) }
                }
                if (dumps > 0) TextRow(stringResource(R.string.sec_dumps_clear), size = typo.title) { tick(); Discovery.clear(context); dumps = 0 }
                Rule(Modifier.padding(vertical = 8.dp))
                Small(stringResource(R.string.sec_family_link), Modifier.padding(horizontal = rowPadH, vertical = 10.dp), maxLines = 6)
                VSpace(12.dp)
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        // ---- the sheets ----
        minutesAsk?.let { (key, weekend) ->
            val q = prefs.quota(key)
            var bad by remember { mutableStateOf(false) }
            val cur = if (weekend) q.weekendMinutes else q.weekdayMinutes
            TextPrompt(stringResource(R.string.sec_minutes_prompt) + if (bad) "  " + stringResource(R.string.budget_bad) else "", initial = cur.toString(), keyboard = KeyboardType.Number, selectAll = true,
                onDone = { text ->
                    val n = text.trim().toIntOrNull()
                    if (n != null && n in 0..1440) { prefs.setQuota(key, if (weekend) q.copy(weekendMinutes = n) else q.copy(weekdayMinutes = n)); v++; minutesAsk = null } else bad = true
                }, onCancel = { minutesAsk = null })
        }
        if (rulesMenu) TextMenu(stringResource(R.string.sec_rules_hint), buildList {
            add(MenuItem(stringResource(R.string.sec_import)) { pickRules.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
            add(MenuItem(stringResource(R.string.sec_export)) { saveRules.launch(Exports.RULES_FILE) })
            if (RulesRepo.source == RulesRepo.Source.CUSTOM) add(MenuItem(stringResource(R.string.sec_reset)) { RulesRepo.reset(context); v++ })
        }, onDismiss = { rulesMenu = false }, titleLines = 3)
        when (pinAsk) {
            PinAsk.SET, PinAsk.CHANGE_NEW -> NewPinFlow { pin -> if (pin != null) { prefs.setPin(pin); v++ }; pinAsk = null }
            PinAsk.CHANGE_CURRENT, PinAsk.REMOVE -> {
                var bad by remember { mutableStateOf(false) }
                TextPrompt(stringResource(R.string.pin_current) + if (bad) "  " + stringResource(R.string.pin_wrong) else "", keyboard = KeyboardType.NumberPassword, password = true,
                    onDone = { if (prefs.checkPin(it)) { if (pinAsk == PinAsk.REMOVE) { prefs.clearPin(); v++; pinAsk = null } else pinAsk = PinAsk.CHANGE_NEW } else bad = true },
                    onCancel = { pinAsk = null })
            }
            null -> {}
        }
        message?.let { TextMenu(it, emptyList(), onDismiss = { message = null }, footer = listOf(MenuItem(stringResource(R.string.action_ok)) { }), titleLines = 3) }
        saved?.let { name ->
            val shareTitle = stringResource(R.string.share_export)
            TextMenu(stringResource(R.string.export_saved, name), listOf(MenuItem(shareTitle) {
                Exports.share(context, name, if (name.endsWith(".json")) "application/json" else "text/plain", shareTitle)
            }), onDismiss = { saved = null })
        }
    }
}
