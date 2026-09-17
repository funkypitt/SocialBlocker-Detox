package com.parentcontrol.socialblocker.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.parentcontrol.socialblocker.App
import com.parentcontrol.socialblocker.BlockPreferences
import com.parentcontrol.socialblocker.Blocker
import com.parentcontrol.socialblocker.MathProblems
import com.parentcontrol.socialblocker.R
import com.parentcontrol.socialblocker.data.FontChoice
import com.parentcontrol.socialblocker.data.Hours
import com.parentcontrol.socialblocker.data.Range
import com.parentcontrol.socialblocker.data.TextSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class Screen {
    data object Home : Screen()
    data object Hours : Screen()
    data object Challenge : Screen()
    data object Settings : Screen()
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    /** Bumped whenever the blocking state may have changed; screens re-read the preferences. */
    var version by mutableIntStateOf(0)
}

/** One site the blocker knows. Substack is the one that stays free to change while blocking. */
class Site(val name: String, val ungated: Boolean, val get: (BlockPreferences) -> Boolean, val set: (BlockPreferences, Boolean) -> Unit)

val SITES = listOf(
    Site("YouTube", false, { it.isYoutubeBlocked }, { p, v -> p.setYoutubeBlocked(v) }),
    Site("Instagram", false, { it.isInstagramBlocked }, { p, v -> p.setInstagramBlocked(v) }),
    Site("TikTok", false, { it.isTiktokBlocked }, { p, v -> p.setTiktokBlocked(v) }),
    Site("Reddit", false, { it.isRedditBlocked }, { p, v -> p.setRedditBlocked(v) }),
    Site("X", false, { it.isXBlocked }, { p, v -> p.setXBlocked(v) }),
    Site("Substack", true, { it.isSubstackBlocked }, { p, v -> p.setSubstackBlocked(v) })
)

/** The hour blocking resumes, when the clock is inside one of the allowed ranges. */
fun openUntil(ranges: List<Range>): Int? {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return ranges.firstOrNull { r -> if (r.start <= r.end) h >= r.start && h < r.end else h >= r.start || h < r.end }?.end
}

fun openVpnSettings(context: Context) {
    try { context.startActivity(Intent("android.net.vpn.SETTINGS")) } catch (e: Exception) {
        runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
    }
}

fun batteryExempt(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

fun askBatteryExemption(context: Context) {
    if (!batteryExempt(context)) runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${context.packageName}")))
    }
}

// ---------------------------------------------------------------------------------------------
// Home: the state, the sites, the hours. Starting and stopping is the row at the bottom.
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val b = app.block
    val tick = rememberTick()
    var menu by remember { mutableStateOf(false) }
    var alwaysOn by remember { mutableStateOf(false) }
    val v = nav.version
    val enabled = remember(v) { b.isBlockingEnabled }
    val requireMath = remember(v) { b.isRequireMathToUnblock }
    val ranges = remember(v) { Hours.parse(b.schedule) }
    val chosen = remember(v) { SITES.filter { it.get(b) } }

    fun started() {
        Blocker.start(context, b)
        nav.version++
        askBatteryExemption(context)
        if (!b.hasShownAlwaysOnHint()) { b.setShownAlwaysOnHint(true); alwaysOn = true }
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) started()
    }
    fun toggle() {
        tick()
        if (enabled) {
            if (requireMath) nav.push(Screen.Challenge) else { Blocker.stop(context, b); nav.version++ }
        } else {
            val prepare = VpnService.prepare(context)
            if (prepare != null) consent.launch(prepare) else started()
        }
    }
    val actionLabel = stringResource(when { !enabled -> R.string.start; requireMath -> R.string.stop_math; else -> R.string.stop })

    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.app_title), onBack = null, trailing = "⋯", onTrailing = { menu = true })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // ---- the state, inverted while blocking ----
                val names = chosen.joinToString(", ") { it.name }
                val open = if (enabled && ranges.isNotEmpty()) openUntil(ranges) else null
                val detail = when {
                    !enabled -> stringResource(R.string.detail_off)
                    chosen.isEmpty() -> stringResource(R.string.detail_nothing)
                    open != null -> stringResource(R.string.detail_open_now, open)
                    ranges.isEmpty() -> stringResource(R.string.detail_all_day, names)
                    else -> stringResource(R.string.detail_hours, names, Hours.short(ranges))
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = 14.dp).background(if (enabled) colors.fg else colors.bg)
                        .padding(horizontal = rowPadH, vertical = 18.dp)
                ) {
                    T(stringResource(if (enabled) R.string.state_on else R.string.state_off), size = typo.tile * 1.3f, color = if (enabled) colors.bg else colors.fg, maxLines = 1)
                    Small(detail, color = if (enabled) colors.bg.copy(alpha = 0.7f) else colors.dim, maxLines = 4)
                }
                // ---- the sites ----
                Small(
                    stringResource(if (enabled) R.string.sites_locked else R.string.sites),
                    Modifier.padding(horizontal = rowPadH).padding(top = 18.dp, bottom = 2.dp), maxLines = 2
                )
                SITES.forEach { s ->
                    val on = remember(v) { s.get(b) }
                    val locked = enabled && !s.ungated
                    ValueRow(s.name, stringResource(if (on) R.string.blocked else R.string.open), strong = on, locked = locked) {
                        tick(); s.set(b, !on); nav.version++
                    }
                }
                Rule(Modifier.padding(vertical = 8.dp))
                // ---- when, and what stopping costs ----
                TextRow(
                    if (ranges.isEmpty()) stringResource(R.string.hours_none) else Hours.short(ranges),
                    secondary = stringResource(R.string.hours), size = typo.title
                ) { nav.push(Screen.Hours) }
                // the gate cannot be removed while blocking, or unticking it would be the way out
                Column(
                    Modifier.fillMaxWidth().noRippleClickable(enabled = !enabled) { tick(); b.setRequireMathToUnblock(!requireMath); nav.version++ }
                        .padding(horizontal = rowPadH, vertical = rowPadV * 0.7f)
                ) {
                    T(stringResource(if (requireMath) R.string.on else R.string.off), size = typo.title, color = if (enabled) colors.dim else colors.fg, maxLines = 1)
                    Small(stringResource(R.string.math_label) + if (enabled) " · " + stringResource(R.string.locked) else "", maxLines = 2)
                }
                VSpace(12.dp)
            }
            Rule()
            TextRow(actionLabel, size = typo.title) { toggle() }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        if (menu) TextMenu(null, listOf(
            MenuItem(actionLabel) { toggle() },
            MenuItem(stringResource(R.string.hours)) { nav.push(Screen.Hours) }
        ), onDismiss = { menu = false }, footer = listOf(
            MenuItem(if (colors.isDark) stringResource(R.string.theme_light) else stringResource(R.string.theme_dark)) { app.prefs.toggleTheme(colors.isDark) },
            MenuItem(stringResource(R.string.settings)) { nav.push(Screen.Settings) }
        ))
        if (alwaysOn) TextMenu(
            stringResource(R.string.always_on_hint),
            listOf(MenuItem(stringResource(R.string.open_vpn_settings)) { openVpnSettings(context) }),
            onDismiss = { alwaysOn = false },
            footer = listOf(MenuItem(stringResource(R.string.later)) { }),
            titleLines = 5
        )
    }
}

/** A name on the left, its state on the right. Locked rows are dim and ignore taps. */
@Composable
fun ValueRow(label: String, value: String, strong: Boolean, locked: Boolean, onClick: () -> Unit) {
    val colors = LocalColors.current
    val typo = LocalTypo.current
    Row(
        Modifier.fillMaxWidth().noRippleClickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = rowPadH, vertical = rowPadV * 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(label, Modifier.weight(1f), size = typo.title, color = if (locked) colors.dim else colors.fg, maxLines = 1, align = TextAlign.Start)
        T(value, size = typo.small, color = if (strong && !locked) colors.fg else colors.dim, maxLines = 1, align = TextAlign.End)
    }
}

/** A hairline, [fraction] of it in the foreground colour. */
@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        drawRect(colors.rule, topLeft = Offset(0f, size.height / 3), size = Size(size.width, size.height / 3))
        drawRect(colors.fg, size = Size(size.width * fraction, size.height))
    }
}

// ---------------------------------------------------------------------------------------------
// Allowed hours: the ranges when the sites open. Outside them, and with none, they are blocked.
// ---------------------------------------------------------------------------------------------

private data class HourAsk(val index: Int, val from: Int? = null)   // index -1 = a new range

@Composable
fun HoursScreen(nav: Nav, app: App) {
    val typo = LocalTypo.current
    val b = app.block
    val ranges = remember(nav.version) { Hours.parse(b.schedule) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var ask by remember { mutableStateOf<HourAsk?>(null) }
    fun save(list: List<Range>) { b.schedule = Hours.format(list.distinct().sortedBy { it.start }); nav.version++ }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.hours), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Small(stringResource(R.string.hours_hint), Modifier.padding(horizontal = rowPadH).padding(top = 16.dp, bottom = 6.dp), maxLines = 6)
                ranges.forEachIndexed { i, r ->
                    TextRow(r.label, secondary = if (r.start > r.end) stringResource(R.string.overnight) else null) { picked = i }
                }
                if (ranges.isEmpty()) Small(stringResource(R.string.hours_none), Modifier.padding(horizontal = rowPadH, vertical = 10.dp))
            }
            Rule()
            TextRow(stringResource(R.string.new_hours), size = typo.title) { ask = HourAsk(-1) }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        picked?.let { i ->
            val r = ranges.getOrNull(i)
            if (r != null) TextMenu(r.label, listOf(
                MenuItem(stringResource(R.string.change)) { ask = HourAsk(i) },
                MenuItem(stringResource(R.string.delete)) { save(ranges.filterIndexed { j, _ -> j != i }) }
            ), onDismiss = { picked = null })
        }
        ask?.let { a ->
            val old = ranges.getOrNull(a.index)
            if (a.from == null) {
                HourPrompt(stringResource(R.string.from_prompt), old?.start, reject = null, onDone = { h -> ask = a.copy(from = h) }, onCancel = { ask = null })
            } else {
                HourPrompt(stringResource(R.string.until_prompt, a.from), old?.end, reject = a.from, onDone = { h ->
                    val r = Range(a.from, h)
                    save(if (a.index < 0) ranges + r else ranges.mapIndexed { j, x -> if (j == a.index) r else x })
                    ask = null
                }, onCancel = { ask = null })
            }
        }
    }
}

@Composable
fun HourPrompt(title: String, initial: Int?, reject: Int?, onDone: (Int) -> Unit, onCancel: () -> Unit) {
    var bad by remember { mutableStateOf(false) }
    TextPrompt(
        title + if (bad) "  " + stringResource(R.string.bad_hour) else "",
        initial = initial?.toString() ?: "",
        keyboard = KeyboardType.Number,
        selectAll = true,
        onDone = { text -> val h = Hours.hourOf(text); if (h != null && h != reject) onDone(h) else bad = true },
        onCancel = onCancel
    )
}

// ---------------------------------------------------------------------------------------------
// The math gate: five problems, each harder, before blocking stops.
// ---------------------------------------------------------------------------------------------

@Composable
fun ChallengeScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val scope = rememberCoroutineScope()
    val problems = remember { MathProblems(context.resources) }
    var level by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(problems.generate(0)) }
    var wrong by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf(TextFieldValue("")) }
    var feedback by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val diff = listOf(R.string.diff_1, R.string.diff_2, R.string.diff_3, R.string.diff_4, R.string.diff_5)
    val sCorrect = stringResource(R.string.correct)
    val sWrong = stringResource(R.string.wrong)
    val sNumber = stringResource(R.string.enter_number)
    BackHandler { nav.pop() }
    LaunchedEffect(level) { runCatching { focus.requestFocus() } }

    fun check() {
        if (busy) return
        val typed = answer.text.trim().replace(',', '.').replace('−', '-')
        if (typed.isEmpty()) return
        val n = typed.toDoubleOrNull()
        if (n == null) { feedback = sNumber; return }
        if (problem.isRight(n)) {
            feedback = sCorrect; busy = true
            scope.launch {
                delay(800)
                if (level + 1 >= MathProblems.TOTAL_LEVELS) {
                    Blocker.stop(context, app.block); nav.version++; nav.pop()
                } else {
                    level++; wrong = 0; problem = problems.generate(level); answer = TextFieldValue(""); feedback = ""; busy = false
                }
            }
        } else {
            wrong++; feedback = sWrong; answer = TextFieldValue("")
        }
    }

    Page {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenTitle(stringResource(R.string.challenge_title), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Small(stringResource(R.string.challenge_subtitle), Modifier.padding(horizontal = rowPadH).padding(top = 16.dp), maxLines = 4)
                Small(
                    stringResource(R.string.challenge_question_counter, level + 1, MathProblems.TOTAL_LEVELS) + " · " + stringResource(diff[level]),
                    Modifier.padding(horizontal = rowPadH).padding(top = 14.dp, bottom = 6.dp), color = colors.fg, maxLines = 1
                )
                Progress((level + if (busy) 1 else 0) / MathProblems.TOTAL_LEVELS.toFloat(), Modifier.padding(horizontal = rowPadH))
                T(problem.question, Modifier.padding(horizontal = rowPadH).padding(top = 22.dp, bottom = 14.dp), align = TextAlign.Start)
                Row(Modifier.fillMaxWidth().padding(horizontal = rowPadH), verticalAlignment = Alignment.CenterVertically) {
                    ReaderTextField(
                        value = answer,
                        onValueChange = { if (!busy) answer = it },
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp).focusRequester(focus),
                        placeholder = stringResource(R.string.challenge_answer_hint),
                        imeAction = ImeAction.Done,
                        onImeAction = { check() },
                        keyboard = KeyboardType.Decimal
                    )
                    // number pads do not all carry a minus key; some answers are negative
                    T("±", Modifier.noRippleClickable {
                        val t = answer.text
                        val flipped = if (t.startsWith("-")) t.drop(1) else "-$t"
                        answer = TextFieldValue(flipped, TextRange(flipped.length))
                    }.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), color = colors.dim, align = TextAlign.End)
                }
                Rule(color = colors.fg, modifier = Modifier.padding(horizontal = rowPadH))
                if (feedback.isNotEmpty()) Small(feedback, Modifier.padding(horizontal = rowPadH).padding(top = 12.dp), color = if (busy) colors.fg else colors.dim, maxLines = 2)
                if (wrong >= 2 && !busy) Small(stringResource(R.string.hint, problem.hint), Modifier.padding(horizontal = rowPadH).padding(top = 8.dp), maxLines = 4)
                VSpace(12.dp)
            }
            Rule()
            TextRow(stringResource(R.string.challenge_submit), size = typo.title) { check() }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings: what keeps the blocking alive, the look
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(nav: Nav, app: App) {
    val context = LocalContext.current
    val s by app.prefs.settings.collectAsState()
    val colors = LocalColors.current
    val typo = LocalTypo.current
    var battOk by remember { mutableStateOf(batteryExempt(context)) }
    LaunchedEffect(Unit) { while (true) { delay(1500); battOk = batteryExempt(context) } }
    BackHandler { nav.pop() }
    Page {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.settings), onBack = { nav.pop() })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                VSpace(10.dp)
                TextRow(stringResource(R.string.always_on), secondary = stringResource(R.string.always_on_row_hint), size = typo.title) { openVpnSettings(context) }
                TextRow(stringResource(if (battOk) R.string.battery_ok else R.string.battery_fix), secondary = stringResource(R.string.battery_hint), size = typo.title) {
                    askBatteryExemption(context)
                }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(if (colors.isDark) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light), secondary = stringResource(R.string.colours)) { app.prefs.toggleTheme(colors.isDark) }
                TextRow(when (s.textSize) { TextSize.SMALL -> "S"; TextSize.MEDIUM -> "M"; TextSize.LARGE -> "L" }, secondary = stringResource(R.string.text_size)) {
                    app.prefs.setTextSize(when (s.textSize) { TextSize.SMALL -> TextSize.MEDIUM; TextSize.MEDIUM -> TextSize.LARGE; TextSize.LARGE -> TextSize.SMALL })
                }
                TextRow(when (s.font) { FontChoice.SANS -> "sans-serif"; FontChoice.SERIF -> "serif"; FontChoice.MONO -> "mono" }, secondary = stringResource(R.string.font)) {
                    app.prefs.setFont(when (s.font) { FontChoice.SANS -> FontChoice.SERIF; FontChoice.SERIF -> FontChoice.MONO; FontChoice.MONO -> FontChoice.SANS })
                }
                TextRow(if (s.haptics) stringResource(R.string.on) else stringResource(R.string.off), secondary = stringResource(R.string.haptics)) { app.prefs.setHaptics(!s.haptics) }
                Rule(Modifier.padding(vertical = 8.dp))
                TextRow(stringResource(R.string.app_name), secondary = stringResource(R.string.about)) { }
                TextRow(stringResource(R.string.credits)) { }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}
