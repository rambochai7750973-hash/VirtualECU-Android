package com.virtualecu.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.virtualecu.android.model.PidInfo
import com.virtualecu.android.ui.theme.AccentBlue
import com.virtualecu.android.ui.theme.AccentGreen
import com.virtualecu.android.ui.theme.AccentOrange
import com.virtualecu.android.ui.theme.AccentPurple
import com.virtualecu.android.ui.theme.AccentRed
import com.virtualecu.android.ui.theme.DarkCard
import com.virtualecu.android.ui.theme.SliderActive
import com.virtualecu.android.ui.theme.SliderTrack
import com.virtualecu.android.ui.theme.TextMuted
import com.virtualecu.android.ui.theme.TextSecondary

@Composable
fun PidCard(
    pid: PidInfo,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (pid.pid.toIntOrNull()?.let { it % 4 }) {
        0 -> AccentGreen
        1 -> AccentBlue
        2 -> AccentOrange
        3 -> AccentPurple
        else -> AccentBlue
    }

    val borderColor by animateColorAsState(
        targetValue = if (pid.value > 0) accentColor.copy(alpha = 0.3f) else DarkCard,
        label = "border"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PID ${pid.pid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = pid.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = formatPidValue(pid.value, pid.pid),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pid.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            var sliderValue by remember(pid.value) { mutableFloatStateOf(pid.value) }

            Slider(
                value = sliderValue.coerceIn(pid.min, pid.max),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChanged(sliderValue) },
                valueRange = pid.min..pid.max,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = SliderActive,
                    inactiveTrackColor = SliderTrack
                )
            )
        }
    }
}

private fun formatPidValue(value: Float, pid: String): String {
    return when (pid) {
        "0C" -> String.format("%.0f", value)
        "21", "31" -> String.format("%.0f", value)
        "10" -> String.format("%.1f", value)
        else -> String.format("%.1f", value)
    }
}
