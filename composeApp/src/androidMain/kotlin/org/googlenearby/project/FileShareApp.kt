package org.googlenearby.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.items

enum class SelectedStrategy { P2P_CLUSTER, P2P_STAR, BOTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileShareApp(
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    discoveredEndpoints: List<Endpoint>,
    connectionInfoText: String,
    isDeviceConnected: Boolean,
    selectedFileUri: Uri?,
    selectedStrategy: SelectedStrategy,
    onStrategySelected: (SelectedStrategy) -> Unit,
    onStartAdvertising: (SelectedStrategy) -> Unit,
    onStartDiscovering: (SelectedStrategy) -> Unit,
    onStopAll: () -> Unit,
    onEndpointSelected: (String) -> Unit,
    onSendFile: () -> Unit,
    onPickFile: () -> Unit
) {
    Scaffold(
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectionInfoCard(
                connectionInfoText = connectionInfoText,
                isDeviceConnected = isDeviceConnected,
                selectedStrategy = selectedStrategy
            )

            Spacer(modifier = Modifier.height(16.dp))

            StrategySelector(
                selectedStrategy = selectedStrategy,
                onStrategySelected = onStrategySelected
            )

            Spacer(modifier = Modifier.height(16.dp))

            ConnectionButtons(
                isAdvertising = isAdvertising,
                isDiscovering = isDiscovering,
                onStartAdvertising = { onStartAdvertising(selectedStrategy) },
                onStartDiscovering = { onStartDiscovering(selectedStrategy) },
                onStopAll = onStopAll
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isDeviceConnected) {
                FileSelectionButtons(
                    selectedFileUri = selectedFileUri,
                    onPickFile = onPickFile,
                    onSendFile = onSendFile
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DiscoveredDevicesList(
                discoveredEndpoints = discoveredEndpoints,
                onEndpointSelected = onEndpointSelected
            )
        }
    }
}

@Composable
fun ConnectionInfoCard(
    connectionInfoText: String,
    isDeviceConnected: Boolean,
    selectedStrategy: SelectedStrategy
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(connectionInfoText, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isDeviceConnected) "Connected" else "Not Connected",
                color = if (isDeviceConnected) Color.Green else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Current Strategy: $selectedStrategy", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable
fun StrategySelector(
    selectedStrategy: SelectedStrategy,
    onStrategySelected: (SelectedStrategy) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StrategyButton(
            text = "CLUSTER",
            isSelected = selectedStrategy == SelectedStrategy.P2P_CLUSTER,
            onClick = { onStrategySelected(SelectedStrategy.P2P_CLUSTER) },
            modifier = Modifier.weight( 1.4f)
        )
        StrategyButton(
            text = "STAR",
            isSelected = selectedStrategy == SelectedStrategy.P2P_STAR,
            onClick = { onStrategySelected(SelectedStrategy.P2P_STAR) },
            modifier = Modifier.weight(1f)
        )
        StrategyButton(
            text = "BOTH",
            isSelected = selectedStrategy == SelectedStrategy.BOTH,
            onClick = { onStrategySelected(SelectedStrategy.BOTH) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StrategyButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}




@Composable
fun ConnectionButtons(
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    onStartAdvertising: () -> Unit,
    onStartDiscovering: () -> Unit,
    onStopAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp), // Optional padding around the row
        horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between the buttons
    ) {
        Button(
            onClick = if (isAdvertising) onStopAll else onStartAdvertising,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAdvertising) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1f) // Each button takes equal space in the row
        ) {
            Text(if (isAdvertising) "Stop Advertising" else "Advertise")
        }
        Button(
            onClick = if (isDiscovering) onStopAll else onStartDiscovering,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDiscovering) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1f) // Each button takes equal space in the row
        ) {
            Text(if (isDiscovering) "Stop Discovering" else "Discover")
        }
    }
}


@Composable
fun FileSelectionButtons(
    selectedFileUri: Uri?,
    onPickFile: () -> Unit,
    onSendFile: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onPickFile,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pick File to Send")
        }

        selectedFileUri?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Selected: ${it.lastPathSegment}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSendFile,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Send File")
            }
        }
    }
}

@Composable
fun DiscoveredDevicesList(
    discoveredEndpoints: List<Endpoint>,
    onEndpointSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (discoveredEndpoints.isNotEmpty()) {
            Text(
                "Discovered Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(discoveredEndpoints) { endpoint ->
                    EndpointItem(endpoint = endpoint, onEndpointSelected = onEndpointSelected)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndpointItem(
    endpoint: Endpoint,
    onEndpointSelected: (String) -> Unit
) {
    Card(
        onClick = { onEndpointSelected(endpoint.id) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(endpoint.name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}


@Preview(showBackground = true)
@Composable
fun FileShareAppPreview() {
    MaterialTheme {
        FileShareApp(
            isAdvertising = false,
            isDiscovering = true,
            discoveredEndpoints = listOf(
                Endpoint("1", "Device 1"),
                Endpoint("2", "Device 2")
            ),
            connectionInfoText = "Discovering devices...",
            isDeviceConnected = false,
            selectedFileUri = null,
            selectedStrategy = SelectedStrategy.P2P_CLUSTER,
            onStrategySelected = {},
            onStartAdvertising = {},
            onStartDiscovering = {},
            onStopAll = {},
            onEndpointSelected = {},
            onSendFile = {},
            onPickFile = {}
        )
    }
}