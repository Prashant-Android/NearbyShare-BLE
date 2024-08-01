package org.googlenearby.project

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun FileShareApp(
    isAdvertising: Boolean,
    isDiscovering: Boolean,
    discoveredEndpoints: List<Endpoint>,
    connectionInfoText: String,
    isDeviceConnected: Boolean,
    selectedFileUri: Uri?,
    wifiTransferSpeed: String,
    bluetoothTransferSpeed: String,
    isConnecting: Boolean,
    fileTransferProgress: Float,
    transferSpeed: String,
    onStartAdvertising: () -> Unit,
    onStartDiscovering: () -> Unit,
    onStopAll: () -> Unit,
    onEndpointSelected: (String) -> Unit,
    onSendFile: () -> Unit,
    onPickFile: () -> Unit
) {
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
                } else if (isConnecting) {
                    Text("Connecting...", color = Color.Blue, style = MaterialTheme.typography.body2)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(250.dp)
                .background(Color(0xFFFFFFFF), shape = CircleShape)
        ) {
            Image(
                painter = painterResource(id = R.drawable.images), // Replace with your drawable resource ID
                contentDescription = "Share",
                modifier = Modifier.size(180.dp).align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = if (isAdvertising) onStopAll else onStartAdvertising,
                colors = ButtonDefaults.buttonColors(backgroundColor = if (isAdvertising) Color.Red else Color(0xFF1976D2))
            ) {
                Text(if (isAdvertising) "Stop Advertising" else "Advertise", color = Color.White)
            }
            Button(
                onClick = if (isDiscovering) onStopAll else onStartDiscovering,
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

        if (fileTransferProgress > 0f) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = fileTransferProgress,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Transfer Speed: $transferSpeed", style = MaterialTheme.typography.caption)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("WiFi: $wifiTransferSpeed", style = MaterialTheme.typography.caption)
            Text("Bluetooth: $bluetoothTransferSpeed", style = MaterialTheme.typography.caption)
        }
    }
}
