package org.googlenearby.project

import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class SelectedStrategy { P2P_CLUSTER, P2P_STAR, BOTH }

data class Endpoint(val id: String, val name: String)

@Composable
fun FileShareApp(
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    discoveredEndpoints: List<Endpoint>,
    connectionInfoText: String,
    isDeviceConnected: Boolean,
    selectedFileUri: Uri?,
    isConnecting: Boolean,
    latency: Double,
    onStartAdvertising: (SelectedStrategy) -> Unit,
    onStartDiscovering: (SelectedStrategy) -> Unit,
    onStopAll: () -> Unit,
    onEndpointSelected: (String) -> Unit,
    onSendFile: () -> Unit,
    onPickFile: () -> Unit,
    wifiDirectPeers: List<WifiP2pDevice>,
    wifiDirectConnectionStatus: String,
    isWifiDirectConnected: Boolean,
    connectedWifiDirectDevices: List<WifiP2pDevice>,
    onStartWifiDirectDiscovery: () -> Unit,
    onConnectWifiDirectPeer: (WifiP2pDevice) -> Unit,
    onDisconnectWifiDirect: () -> Unit,
    onSendFileWifiDirect: (WifiP2pDevice, Uri) -> Unit,
) {
    var selectedStrategy by remember { mutableStateOf(SelectedStrategy.P2P_CLUSTER) }

    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    var isReceiverDialogVisible by remember { mutableStateOf(true) }

    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                ConnectionInfoCard(
                    connectionInfoText = connectionInfoText,
                    isDeviceConnected = isDeviceConnected,
                    isConnecting = isConnecting,
                    selectedStrategy = selectedStrategy,
                    latency = latency
                )

                Spacer(modifier = Modifier.height(16.dp))

                StrategySelector(
                    selectedStrategy = selectedStrategy,
                    onStrategySelected = { selectedStrategy = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                ActionButtons(
                    isAdvertising = isAdvertising,
                    isDiscovering = isDiscovering,
                    onStartAdvertising = { onStartAdvertising(selectedStrategy) },
                    onStartDiscovering = { onStartDiscovering(selectedStrategy) },
                    onStopAll = onStopAll
                )

                Spacer(modifier = Modifier.height(16.dp))

                NSDSection(
                    discoveredEndpoints = discoveredEndpoints,
                    onEndpointSelected = onEndpointSelected,
                    selectedFileUri = selectedFileUri,
                    onPickFile = onPickFile,
                    onSendFile = onSendFile,
                    isDeviceConnected = isDeviceConnected
                )

                Spacer(modifier = Modifier.height(16.dp))

                WiFiDirectManagerSection(
                    peers = wifiDirectPeers,
                    connectionStatus = wifiDirectConnectionStatus,
                    isConnected = isWifiDirectConnected,
                    connectedDevices = connectedWifiDirectDevices,
                    onStartDiscovery = onStartWifiDirectDiscovery,
                    onConnectPeer = onConnectWifiDirectPeer,
                    onDisconnect = onDisconnectWifiDirect,
                    onSendFile = onSendFileWifiDirect,
                    selectedFileUri = selectedFileUri,
                    onPickFile = onPickFile  // Add this line
                )

            }
        }
    }
}

@Composable
fun WiFiDirectManagerSection(
    peers: List<WifiP2pDevice>,
    connectionStatus: String,
    isConnected: Boolean,
    connectedDevices: List<WifiP2pDevice>,
    onStartDiscovery: () -> Unit,
    onConnectPeer: (WifiP2pDevice) -> Unit,
    onDisconnect: () -> Unit,
    onSendFile: (WifiP2pDevice, Uri) -> Unit,
    selectedFileUri: Uri?,
    onPickFile: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wi-Fi Direct", style = MaterialTheme.typography.titleMedium)
            Text("Status: $connectionStatus", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))

            if (!isConnected) {
                Button(
                    onClick = onStartDiscovery,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Wi-Fi Direct Discovery")
                }

                if (peers.isNotEmpty()) {
                    Text("Discovered Peers:", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        items(peers) { peer ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onConnectPeer(peer) }
                            ) {
                                Text(
                                    peer.deviceName,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                } else {
                    Text("No peers discovered", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Connected Devices:", style = MaterialTheme.typography.titleSmall)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    items(connectedDevices) { device ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    device.deviceName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(onClick = onPickFile) {
                                    Text("Select File")
                                }
                                if (selectedFileUri != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(onClick = { onSendFile(device, selectedFileUri) }) {
                                        Text("Send File")
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}
@Composable
fun ConnectionInfoCard(
    connectionInfoText: String,
    isDeviceConnected: Boolean,
    isConnecting: Boolean,
    selectedStrategy: SelectedStrategy,
    latency: Double
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(connectionInfoText, style = MaterialTheme.typography.bodyLarge)
            when {
                isDeviceConnected -> Text("Connected", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                isConnecting -> Text("Connecting...", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            Text("Strategy: $selectedStrategy", style = MaterialTheme.typography.bodyMedium)
            Text("Latency: ${String.format("%.2f", latency)} ms", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ActionButtons(
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    onStartAdvertising: () -> Unit,
    onStartDiscovering: () -> Unit,
    onStopAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(
            onClick = {
                if (isAdvertising) onStopAll() else onStartAdvertising()
            },
            modifier = Modifier.weight(1f)
        ) {
            Text(if (isAdvertising) "Stop Advertising" else "Advertise")
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                if (isDiscovering) onStopAll() else onStartDiscovering()
            },
            modifier = Modifier.weight(1f)
        ) {
            Text(if (isDiscovering) "Stop Discovering" else "Discover")
        }
    }
}

@Composable
fun NSDSection(
    discoveredEndpoints: List<Endpoint>,
    onEndpointSelected: (String) -> Unit,
    selectedFileUri: Uri?,
    onPickFile: () -> Unit,
    onSendFile: () -> Unit,
    isDeviceConnected: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!isDeviceConnected) {
                DiscoveredDevicesSection(
                    discoveredEndpoints = discoveredEndpoints,
                    onEndpointSelected = onEndpointSelected
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            FileSendSection(
                selectedFileUri = selectedFileUri,
                onPickFile = onPickFile,
                onSendFile = onSendFile,
                isDeviceConnected = isDeviceConnected
            )
        }
    }
}

@Composable
fun DiscoveredDevicesSection(
    discoveredEndpoints: List<Endpoint>,
    onEndpointSelected: (String) -> Unit
) {
    Column {
        Text(
            "Discovered Devices",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (discoveredEndpoints.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)  // Set a fixed height
            ) {
                items(discoveredEndpoints) { endpoint ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onEndpointSelected(endpoint.id) }
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
            }
        } else {
            Text(
                "No devices discovered yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun FileSendSection(
    selectedFileUri: Uri?,
    onPickFile: () -> Unit,
    onSendFile: () -> Unit,
    isDeviceConnected: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isDeviceConnected) {
            OutlinedButton(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pick File to Send")
            }

            selectedFileUri?.let {
                Text(
                    "Selected: ${it.lastPathSegment}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = onSendFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send File")
                }
            }
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
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SelectedStrategy.values().forEach { strategy ->
            FilterChip(
                selected = selectedStrategy == strategy,
                onClick = { onStrategySelected(strategy) },
                label = { Text(strategy.name) }
            )
        }
    }
}
@Composable
fun FileReceiveDialog(
    fileName: String,
    fileSize: Long,
    senderName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Do nothing, force user to make a choice */ },
        title = { Text("Incoming File") },
        text = {
            Column {
                Text("$senderName wants to send you a file:")
                Text("File Name: $fileName")
                Text("File Size: ${Formatter.formatShortFileSize(LocalContext.current, fileSize)}")
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Accept")
            }
        },
        dismissButton = {
            Button(onClick = onReject) {
                Text("Reject")
            }
        }
    )
}