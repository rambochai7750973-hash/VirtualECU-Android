package com.virtualecu.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.virtualecu.android.ui.components.PidCard
import com.virtualecu.android.ui.components.StatsCard
import com.virtualecu.android.model.RawPidEntry
import com.virtualecu.android.ui.theme.AccentBlue
import com.virtualecu.android.ui.theme.AccentGreen
import com.virtualecu.android.ui.theme.DarkCard
import com.virtualecu.android.ui.theme.DarkSurface
import com.virtualecu.android.ui.theme.TextMuted
import com.virtualecu.android.ui.theme.TextSecondary
import com.virtualecu.android.viewmodel.ECUViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToPeriodic: () -> Unit,
    onNavigateToLog: () -> Unit,
    viewModel: ECUViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var ipInput by remember(state.baseIp) { mutableStateOf(state.baseIp) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Virtual ECU",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("ECU IP") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor = AccentBlue,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Button(
                        onClick = {
                            viewModel.setBaseIp(ipInput)
                            if (state.connected) viewModel.disconnect()
                            else viewModel.connect()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.connected) AccentGreen else AccentBlue
                        )
                    ) {
                        Text(if (state.connected) "Disconnect" else "Connect")
                    }
                }
            }

            if (state.loading) {
                item {
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            state.error?.let { error ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Tips: Ensure phone is connected to VirtualECU WiFi. Default IP: 192.168.4.1",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (state.connected) {
                item {
                    StatsCard(
                        txCount = state.txCount,
                        rxCount = state.rxCount,
                        uptimeSeconds = state.uptime
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToPeriodic() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                        ) {
                            Text("Periodic Messages")
                        }
                        Button(
                            onClick = { onNavigateToLog() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                        ) {
                            Text("CAN Log")
                        }
                    }
                }

                item {
                    Text(
                        text = "PID Signals",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(
                    items = state.pids,
                    key = { it.key }
                ) { entry ->
                    PidCard(
                        entry = entry,
                        onValueChanged = { value ->
                            viewModel.setPidOverride(entry.key, value)
                        }
                    )
                }
            } else {
                item {
                    Text(
                        text = "Connect to VirtualECU WiFi hotspot to view signals",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }
    }
}
