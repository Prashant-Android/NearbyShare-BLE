package org.googlenearby.project

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class SelectedStrategy { P2P_CLUSTER, P2P_STAR, BOTH }

@Composable
fun FileShareApp(
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    discoveredEndpoints: List<Endpoint>,
    connectionInfoText: String,
    isDeviceConnected: Boolean,
    selectedFileUri: Uri?,
    onStartAdvertising: (SelectedStrategy) -> Unit,
    onStartDiscovering: (SelectedStrategy) -> Unit,
    onStopAll: () -> Unit,
    onEndpointSelected: (String) -> Unit,
    onSendFile: () -> Unit,
    onPickFile: () -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(SelectedStrategy.P2P_CLUSTER) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F0F0))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Android Nearby Share",
            style = MaterialTheme.typography.h5,
            color = Color(0xFF333333),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp,
            backgroundColor = Color(0xFFE3F2FD)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(connectionInfoText, style = MaterialTheme.typography.body2)
                if (isDeviceConnected) {
                    Text("Connected", color = Color.Green, style = MaterialTheme.typography.body2)
                } else  {
                    Text("Not Connected", color = Color.Blue, style = MaterialTheme.typography.body2)
                }
                Text("Current Strategy: $selectedStrategy", style = MaterialTheme.typography.body2)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { selectedStrategy = SelectedStrategy.P2P_CLUSTER },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedStrategy == SelectedStrategy.P2P_CLUSTER) Color.Green else Color.Gray
                )
            ) {
                Text("P2P_CLUSTER", color = Color.White)
            }
            Button(
                onClick = { selectedStrategy = SelectedStrategy.P2P_STAR },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedStrategy == SelectedStrategy.P2P_STAR) Color.Green else Color.Gray
                )
            ) {
                Text("P2P_STAR", color = Color.White)
            }
            Button(
                onClick = { selectedStrategy = SelectedStrategy.BOTH },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedStrategy == SelectedStrategy.BOTH) Color.Green else Color.Gray
                )
            ) {
                Text("BOTH", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (isAdvertising) onStopAll()
                    else onStartAdvertising(selectedStrategy)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = if (isAdvertising) Color.Red else Color(0xFF1976D2))
            ) {
                Text(if (isAdvertising) "Stop Advertising" else "Advertise", color = Color.White)
            }
            Button(
                onClick = {
                    if (isDiscovering) onStopAll()
                    else onStartDiscovering(selectedStrategy)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = if (isDiscovering) Color.Red else Color(0xFF1976D2))
            ) {
                Text(if (isDiscovering) "Stop Discovering" else "Discover", color = Color.White)
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        if (isDeviceConnected) {
            Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                Text("Pick File to Send")
            }

            selectedFileUri?.let {
                Text("Selected: ${it.lastPathSegment}", style = MaterialTheme.typography.caption)
                Button(onClick = onSendFile, modifier = Modifier.fillMaxWidth()) {
                    Text("Send File")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (discoveredEndpoints.isNotEmpty()) {
            Text("Discovered Devices:", style = MaterialTheme.typography.subtitle1)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(discoveredEndpoints) { endpoint ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onEndpointSelected(endpoint.id) },
                        elevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF1976D2))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(endpoint.name)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}