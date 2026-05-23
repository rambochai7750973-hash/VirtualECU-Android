package com.virtualecu.android.ui.components

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
import com.virtualecu.android.model.RawPidEntry
import com.virtualecu.android.ui.theme.AccentBlue
import com.virtualecu.android.ui.theme.AccentGreen
import com.virtualecu.android.ui.theme.AccentOrange
import com.virtualecu.android.ui.theme.AccentPurple
import com.virtualecu.android.ui.theme.DarkCard
import com.virtualecu.android.ui.theme.SliderActive
import com.virtualecu.android.ui.theme.SliderTrack
import com.virtualecu.android.ui.theme.TextSecondary

@Composable
fun PidCard(
    entry: RawPidEntry,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = when (entry.key.hashCode() % 4) {
        0 -> AccentGreen; 1 -> AccentBlue; 2 -> AccentOrange; 3 -> AccentPurple
        else -> AccentBlue
    }

    val numValue = entry.rawValue.toFloatOrNull()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = entry.rawValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            if (numValue != null && numValue in 0f..100f) {
                Spacer(modifier = Modifier.height(8.dp))
                var slider by remember(numValue) { mutableFloatStateOf(numValue) }
                Slider(
                    value = slider.coerceIn(0f, 100f),
                    onValueChange = { slider = it },
                    onValueChangeFinished = { onValueChanged(slider) },
                    valueRange = 0f..100f,
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
}
