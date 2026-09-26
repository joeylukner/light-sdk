package com.thelightphone.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow

private const val MAX_DIGITS = 6
private const val MAX_CUSTOM_SECONDS = 23 * 3600 + 59 * 60 + 59

/** Right-aligns typed digits into HHMMSS, e.g. "130" -> "000130". */
fun padCustomDigits(digits: String) = digits.padStart(MAX_DIGITS, '0')

/**
 * Converts typed digits to seconds. Like a microwave, fields may overflow ("90" sec = 1:30),
 * and the result is capped at 23:59:59 to match the hours row on the setup screen.
 */
fun customDigitsToSeconds(digits: String): Int {
    val padded = padCustomDigits(digits)
    val hours = padded.substring(0, 2).toInt()
    val minutes = padded.substring(2, 4).toInt()
    val seconds = padded.substring(4, 6).toInt()
    return minOf(hours * 3600 + minutes * 60 + seconds, MAX_CUSTOM_SECONDS)
}

class CustomTimerViewModel : LightViewModel<Int>() {
    /** Digits typed so far, most recent last; they fill the display from the right. */
    val digits = MutableStateFlow("")

    fun type(digit: Int) {
        // a leading 0 wouldn't change the display, so ignore it
        if (digits.value.length >= MAX_DIGITS || (digits.value.isEmpty() && digit == 0)) return
        digits.value += digit
    }
}

/** Keypad entry for an exact duration; SET goes back with the total in seconds, × goes back without one. */
class CustomTimerScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Int, CustomTimerViewModel>(sealedActivity) {

    override val viewModelClass: Class<CustomTimerViewModel>
        get() = CustomTimerViewModel::class.java

    override fun createViewModel() = CustomTimerViewModel()

    @Composable
    override fun Content() {
        val digits by viewModel.digits.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val padded = padCustomDigits(digits)
        val total = customDigitsToSeconds(digits)

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Set Custom Timer"),
                    rightButton = if (total > 0) LightBarButton.Text("SET", onClick = { goBack(total) }) else null,
                )
                // full-size Title is too wide for HH:MM:SS
                Text(
                    text = "${padded.substring(0, 2)}:${padded.substring(2, 4)}:${padded.substring(4, 6)}",
                    style = LightThemeTokens.typography.title.copy(fontSize = 78.sp, lineHeight = 86.sp),
                    color = LightThemeTokens.colors.content,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
                        KeypadRow {
                            row.forEach { digit -> DigitKey(digit) }
                        }
                    }
                    KeypadRow {
                        Box(Modifier.size(KEY_SIZE))
                        DigitKey(0)
                        Box(
                            modifier = Modifier
                                .size(KEY_SIZE)
                                .lightClickable { goBack() },
                            contentAlignment = Alignment.Center,
                        ) {
                            LightIcon(icon = LightIcons.CLOSE, contentDescription = "close")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun KeypadRow(content: @Composable () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KEY_SPACING, Alignment.CenterHorizontally),
        ) {
            content()
        }
    }

    @Composable
    private fun DigitKey(digit: Int) {
        Box(
            modifier = Modifier
                .size(KEY_SIZE)
                .lightClickable { viewModel.type(digit) },
            contentAlignment = Alignment.Center,
        ) {
            LightText(text = digit.toString(), variant = LightTextVariant.Subtitle)
        }
    }

    companion object {
        private val KEY_SIZE = 72.dp
        private val KEY_SPACING = 20.dp
    }
}
