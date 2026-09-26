package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.audio.LightAudio
import com.thelightphone.sdk.audio.LightAudioUsage
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** One adjustable row on the setup screen: each ▲/▼ press moves the time by [stepSeconds]. */
enum class TimeUnit(val label: String, val stepSeconds: Int, val maxSteps: Int) {
    Hours("HOURS", 3600, 23),
    Minutes("MIN", 60, 59),
    Seconds("SEC", 15, 3),
    ;

    fun valueIn(totalSeconds: Int) = when (this) {
        Hours -> totalSeconds / 3600
        Minutes -> totalSeconds % 3600 / 60
        Seconds -> totalSeconds % 60
    }

    // Stepping the total carries up (0:45 ▲ sec -> 1:00) and borrows down (5:00 ▼ sec -> 4:45).
    // With nothing to borrow the row wraps to its max; past 23 hours wraps to 0.

    fun stepUp(totalSeconds: Int): Int {
        val next = totalSeconds + stepSeconds
        return if (next > MAX_SETTING_SECONDS) next - DAY_SECONDS else next
    }

    fun stepDown(totalSeconds: Int): Int =
        if (totalSeconds >= stepSeconds) totalSeconds - stepSeconds else totalSeconds + stepSeconds * maxSteps

    private companion object {
        const val DAY_SECONDS = 24 * 3600
        const val MAX_SETTING_SECONDS = 23 * 3600 + 59 * 60 + 45
    }
}

class HomeScreenViewModel(private val audio: LightAudio) : LightViewModel<Unit>() {
    enum class Mode { Setup, Running, Paused }

    val mode = MutableStateFlow(Mode.Setup)
    /** The time set on the setup screen, in seconds. */
    val setting = MutableStateFlow(0)
    val remaining = MutableStateFlow(Duration.ZERO)
    private var tickJob: Job? = null

    private val sampleRate by lazy { audio.capabilities.sampleRate.takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE }
    private val alarmVoice by lazy { audio.newVoice(usage = LightAudioUsage.Alarm, sampleRate = sampleRate) }
    private val ringPcm by lazy { synthesizeRing(sampleRate) }

    fun increment(unit: TimeUnit) {
        setting.value = unit.stepUp(setting.value)
    }

    fun decrement(unit: TimeUnit) {
        setting.value = unit.stepDown(setting.value)
    }

    fun setTime(totalSeconds: Int) {
        setting.value = totalSeconds
    }

    fun start() {
        if (setting.value <= 0) return
        remaining.value = setting.value.seconds
        resume()
    }

    fun pause() {
        stopTicking()
        mode.value = Mode.Paused
    }

    fun resume() {
        if (remaining.value <= Duration.ZERO) return
        mode.value = Mode.Running
        // track the end time rather than subtracting each tick, so a late tick doesn't cause drift
        val endTime = System.currentTimeMillis() + remaining.value.inWholeMilliseconds
        tickJob = viewModelScope.launch {
            while (remaining.value > Duration.ZERO) {
                remaining.value = (endTime - System.currentTimeMillis()).coerceAtLeast(0).milliseconds
                delay(100)
            }
            // only reached on finish; cancel()/pause() cancel the job first
            mode.value = Mode.Setup
            alarmVoice.play(ringPcm)
        }
    }

    /** Returns to setup, keeping the last-set time. */
    fun cancel() {
        stopTicking()
        mode.value = Mode.Setup
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    override fun onCleared() {
        alarmVoice.release()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_SAMPLE_RATE = 48_000
        private const val BELL_FREQ_HZ = 330.0
        private const val STRIKES = 4
        private const val STRIKE_SPACING_MS = 900
        private const val RING_OUT_MS = 2600
        private const val DECAY_PER_SEC = 1.6
        private const val FADE_MS = 5
        private const val VOLUME = 0.6

        // inharmonic partials (multiplier to amplitude) give a struck-metal / singing-bowl character
        private val BELL_PARTIALS = listOf(1.0 to 1.0, 2.76 to 0.5, 5.4 to 0.25, 8.93 to 0.1)

        // overlapping bell strikes in one mono 16-bit buffer, so a single voice plays the whole ring (~5.3s)
        private fun synthesizeRing(sampleRate: Int): ShortArray {
            fun frames(ms: Int) = sampleRate * ms / 1000
            val strikeFrames = frames(RING_OUT_MS)
            val fadeFrames = frames(FADE_MS)
            val strike = DoubleArray(strikeFrames) { i ->
                val t = i.toDouble() / sampleRate
                // short fade in/out so the strike doesn't click
                val edge = minOf(1.0, i.toDouble() / fadeFrames, (strikeFrames - 1 - i).toDouble() / fadeFrames)
                val wave = BELL_PARTIALS.sumOf { (mult, amp) -> amp * sin(2.0 * PI * BELL_FREQ_HZ * mult * t) }
                wave * exp(-t * DECAY_PER_SEC) * edge
            }
            val mix = DoubleArray(frames(STRIKE_SPACING_MS) * (STRIKES - 1) + strikeFrames)
            repeat(STRIKES) { n ->
                val start = frames(STRIKE_SPACING_MS) * n
                for (i in strike.indices) mix[start + i] += strike[i]
            }
            // peak-normalize so overlapping strikes never clip
            val peak = mix.maxOf { abs(it) }
            return ShortArray(mix.size) { (mix[it] / peak * VOLUME * Short.MAX_VALUE).toInt().toShort() }
        }
    }
}

@InitialScreen
class HomeScreen(private val sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel(): HomeScreenViewModel {
        return HomeScreenViewModel(DefaultLightAudio(sealedActivity))
    }

    @Composable
    override fun Content() {
        val mode by viewModel.mode.collectAsState()
        val setting by viewModel.setting.collectAsState()
        val remaining by viewModel.remaining.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val isSetup = mode == HomeScreenViewModel.Mode.Setup

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = if (isSetup) LightBarButton.Text("CLEAR", onClick = { viewModel.setTime(0) }) else null,
                    rightButton = if (isSetup) {
                        LightBarButton.LightIcon(
                            icon = LightIcons.PENCIL,
                            contentDescription = "set custom timer",
                            onClick = { navigateTo(::CustomTimerScreen, viewModel::setTime) },
                        )
                    } else null,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isSetup) {
                        TimeUnit.entries.forEach { unit -> SetupRow(unit, unit.valueIn(setting)) }
                    } else {
                        Countdown(remaining)
                    }
                }
                LightBottomBar(
                    items = when {
                        isSetup && setting > 0 -> listOf(LightBarButton.Text("START", onClick = { viewModel.start() }))
                        isSetup -> emptyList()
                        else -> listOf(
                            LightBarButton.Text("CANCEL", onClick = { viewModel.cancel() }),
                            if (mode == HomeScreenViewModel.Mode.Running) {
                                LightBarButton.Text("PAUSE", onClick = { viewModel.pause() })
                            } else {
                                LightBarButton.Text("RESUME", onClick = { viewModel.resume() })
                            },
                        )
                    },
                )
            }
        }
    }

    /** ▼ on the left, value + unit in the middle, ▲ on the right. */
    @Composable
    private fun SetupRow(unit: TimeUnit, value: Int) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightIcon(
                icon = LightIcons.DOWN,
                contentDescription = "decrease ${unit.label.lowercase()}",
                modifier = Modifier
                    // the chevron drawables sit off-center vertically; nudge them level with the digits
                    .offset(y = CHEVRON_NUDGE)
                    .lightClickable { viewModel.decrement(unit) }
                    .padding(12.dp),
            )
            Spacer(Modifier.weight(1f))
            // fixed widths keep the digits and labels lined up across rows as values change
            LightText(
                text = value.toString(),
                variant = LightTextVariant.Title,
                align = TextAlign.End,
                modifier = Modifier.width(NUMBER_WIDTH),
            )
            LightText(
                text = unit.label,
                variant = LightTextVariant.Heading,
                modifier = Modifier
                    .width(LABEL_WIDTH)
                    .padding(start = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            LightIcon(
                icon = LightIcons.UP,
                contentDescription = "increase ${unit.label.lowercase()}",
                modifier = Modifier
                    .offset(y = -CHEVRON_NUDGE)
                    .lightClickable { viewModel.increment(unit) }
                    .padding(12.dp),
            )
        }
    }

    @Composable
    private fun Countdown(remaining: Duration) {
        // round up so the display starts at the full set time and only hits 0 when finished
        val total = ((remaining.inWholeMilliseconds + 999) / 1000).toInt()
        val (h, m, s) = TimeUnit.entries.map { it.valueIn(total) }
        // H:MM:SS is too wide for Title, so drop down a size
        if (h > 0) {
            LightText(text = "%d:%02d:%02d".format(h, m, s), variant = LightTextVariant.Subtitle)
        } else {
            LightText(text = "%d:%02d".format(m, s), variant = LightTextVariant.Title)
        }
    }

    companion object {
        private val NUMBER_WIDTH = 110.dp
        private val LABEL_WIDTH = 130.dp
        private val CHEVRON_NUDGE = (-5).dp
    }
}
