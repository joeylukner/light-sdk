package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HomeScreenViewModel : LightViewModel<Unit>() {
    private val duration = 60.seconds

    val remaining = MutableStateFlow(duration)
    val isRunning = MutableStateFlow(false)
    private var tickJob: Job? = null

    fun startOrPause() {
        if (isRunning.value) pause() else start()
    }

    fun reset() {
        pause()
        remaining.value = duration
    }

    private fun start() {
        if (remaining.value <= Duration.ZERO) return
        isRunning.value = true
        // track the end time rather than subtracting each tick, so a late tick doesn't cause drift
        val endTime = System.currentTimeMillis() + remaining.value.inWholeMilliseconds
        tickJob = viewModelScope.launch {
            while (remaining.value > Duration.ZERO) {
                remaining.value = (endTime - System.currentTimeMillis()).coerceAtLeast(0).milliseconds
                delay(100)
            }
            isRunning.value = false
        }
    }

    private fun pause() {
        tickJob?.cancel()
        tickJob = null
        isRunning.value = false
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel(): HomeScreenViewModel {
        return HomeScreenViewModel()
    }

    @Composable
    override fun Content() {
        val remaining by viewModel.remaining.collectAsState()
        val isRunning by viewModel.isRunning.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        // round up so the display reads 1:00 at the start and only hits 0:00 when finished
        val totalSeconds = (remaining.inWholeMilliseconds + 999) / 1000

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LightText(
                    text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                    variant = LightTextVariant.Heading,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
                LightText(
                    text = if (isRunning) "Pause" else "Start",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier
                        .lightClickable { viewModel.startOrPause() }
                        .padding(vertical = 12.dp),
                )
                LightText(
                    text = "Reset",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier
                        .lightClickable { viewModel.reset() }
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
