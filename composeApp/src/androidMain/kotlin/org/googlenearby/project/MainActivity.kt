package org.googlenearby.project

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset


class MainActivity : ComponentActivity() {

    private lateinit var connectionsClient: ConnectionsClient
    private var selectedFileUri: Uri? by mutableStateOf(null)
    private var selectedEndpointId: String? by mutableStateOf(null)

    private var fileTransferProgress by mutableStateOf(0f)
    private var transferSpeed by mutableStateOf("0 Mbps")
    private var isConnecting by mutableStateOf(false)

    private var wifiTransferSpeed by mutableStateOf("Not Available")
    private var bluetoothTransferSpeed by mutableStateOf("Not Available")

    private var isAdvertising by mutableStateOf(false)
    private var isDiscovering by mutableStateOf(false)
    private var discoveredEndpoints by mutableStateOf<List<Endpoint>>(emptyList())
    private var connectionInfoText by mutableStateOf("Searching Devices...")
    private var isDeviceConnected by mutableStateOf(false)
    private var usingBluetooth by mutableStateOf(false)

    companion object {
        const val SERVICE_ID = "your-service-id"

        fun getDeviceNameString(): String {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer)) {
                model.capitalize()
            } else {
                "$manufacturer $model".capitalize()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionsClient = Nearby.getConnectionsClient(this)

        setContent {
            FileShareApp(
                isAdvertising = isAdvertising,
                isDiscovering = isDiscovering,
                discoveredEndpoints = discoveredEndpoints,
                connectionInfoText = connectionInfoText,
                isDeviceConnected = isDeviceConnected,
                selectedFileUri = selectedFileUri,
                wifiTransferSpeed = wifiTransferSpeed,
                bluetoothTransferSpeed = bluetoothTransferSpeed,
                isConnecting = isConnecting,
                fileTransferProgress = fileTransferProgress,
                transferSpeed = transferSpeed,
                onStartAdvertising = { startAdvertising() },
                onStartDiscovering = { startDiscovering() },
                onStopAll = { stopAllEndpoints() },
                onEndpointSelected = { endpointId ->
                    selectedEndpointId = endpointId
                    stopDiscovering()
                    isConnecting = true
                    connectionsClient.requestConnection(getDeviceNameString(), endpointId, connectionLifecycleCallback)
                },
                onSendFile = {
                    selectedFileUri?.let { uri ->
                        selectedEndpointId?.let { endpointId ->
                            handleFileTransfer()
                        }
                    }
                },
                onPickFile = { filePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }) }
            )
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_COARSE_LOCATION, // Required for older APIs
                Manifest.permission.ACCESS_FINE_LOCATION   // Required for older APIs
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        requestMultiplePermissions.launch(requiredPermissions)
    }

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.values.all { it }
        if (!allPermissionsGranted) {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode ==RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                selectedFileUri = uri
            }
        }
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            getDeviceNameString(),
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            isAdvertising = true
        }.addOnFailureListener {
            isAdvertising = false
            Log.e("FileShare", "Advertising failed", it)
        }
    }

    private fun startDiscovering() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            isDiscovering = true
        }.addOnFailureListener {
            isDiscovering = false
            Log.e("FileShare", "Discovery failed", it)
        }
    }

    private fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()
        isAdvertising = false
        isDiscovering = false
        discoveredEndpoints = emptyList()
    }

    private fun stopDiscovering() {
        connectionsClient.stopDiscovery()
        isDiscovering = false
    }


    private fun isWiFiConnected(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun handleFileTransfer() {
        if (isWiFiConnected()) {
            Log.d("FileShare", "Using Wi-Fi for file transfer.")
            selectedFileUri?.let { uri ->
                selectedEndpointId?.let { endpointId ->
                    sendFile(endpointId, uri)
                }
            }
        } else {
            Log.d("FileShare", "Using Bluetooth for file transfer.")
            usingBluetooth = true
            selectedFileUri?.let { uri ->
                selectedEndpointId?.let { endpointId ->
                    sendFileBluetooth(endpointId, uri)
                }
            }
        }
    }

    private fun showFileTransferDialog(action: String, fileName: String, fileSize: Long, transferSpeed: String) {
        val message = "File: $fileName\nSize: ${fileSize / (1024 * 1024)} MB\nTransfer Speed: $transferSpeed"
        AlertDialog.Builder(this)
            .setTitle(action)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }


    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (!discoveredEndpoints.any { it.id == endpointId }) {
                discoveredEndpoints = discoveredEndpoints + Endpoint(endpointId, info.endpointName)
                connectionInfoText = "Found ${discoveredEndpoints.size} devices"
            }
        }

        override fun onEndpointLost(endpointId: String) {
            discoveredEndpoints = discoveredEndpoints.filterNot { it.id == endpointId }
            connectionInfoText = "Found ${discoveredEndpoints.size} devices"
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            isDeviceConnected = true
            isConnecting = false
            connectionInfoText = "Connected to ${info.endpointName}"
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Device is connected to ${info.endpointName}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectionInfoText = "Connected to ${endpointId}"
                isDeviceConnected = true
                selectedEndpointId = endpointId  // Set the selected endpoint
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Device connected successfully", Toast.LENGTH_SHORT).show()
                }
            } else {
                connectionInfoText = "Connection failed with $endpointId"
                isDeviceConnected = false
            }
        }

        override fun onDisconnected(endpointId: String) {
            isDeviceConnected = false
            connectionInfoText = "Disconnected from ${endpointId}"
        }
    }

    private fun sendFileBluetooth(endpointId: String, uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: throw FileNotFoundException("File not found: $uri")
            val originalFileName = getOriginalFileName(uri)
            val file = File(cacheDir, originalFileName)

            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            val fileSize = file.length()
            val startTime = System.currentTimeMillis()

            // Send file name and payload
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(originalFileName.toByteArray()))
            connectionsClient.sendPayload(endpointId, Payload.fromFile(file))

            // Calculate transfer speed
            val endTime = System.currentTimeMillis()
            val transferDuration = endTime - startTime
            val bytesPerSecond = fileSize.toFloat() / (transferDuration.toFloat() / 1000.0)
            val megaBitsPerSecond = (bytesPerSecond * 8) / (1024 * 1024)
            bluetoothTransferSpeed = String.format("%.2f Mbps", megaBitsPerSecond)

            runOnUiThread {
                Toast.makeText(this, "File transfer initiated", Toast.LENGTH_SHORT).show()
                // Show dialog with 0% progress initially
                showFileTransferDialog("File Transferring", originalFileName, fileSize, wifiTransferSpeed)            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("FileShare", "Exception during file sending", e)
            runOnUiThread {
                Toast.makeText(this, "Error sending file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendFile(endpointId: String, uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: throw FileNotFoundException("File not found: $uri")
            val originalFileName = getOriginalFileName(uri)
            val file = File(cacheDir, originalFileName)

            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            val fileSize = file.length()
            val startTime = System.currentTimeMillis()

            // Send file name and payload
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(originalFileName.toByteArray()))
            connectionsClient.sendPayload(endpointId, Payload.fromFile(file))

            // Calculate transfer speed
            val endTime = System.currentTimeMillis()
            val transferDuration = endTime - startTime
            val bytesPerSecond = fileSize.toFloat() / (transferDuration.toFloat() / 1000.0)
            val megaBitsPerSecond = (bytesPerSecond * 8) / (1024 * 1024)
            wifiTransferSpeed = String.format("%.2f Mbps", megaBitsPerSecond)

            runOnUiThread {
                Toast.makeText(this, "File transfer initiated", Toast.LENGTH_SHORT).show()
                // Show dialog with 0% progress initially
                showFileTransferDialog("File Transferring", originalFileName, fileSize, bluetoothTransferSpeed)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("FileShare", "Exception during file sending", e)
            runOnUiThread {
                Toast.makeText(this, "Error sending file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun getOriginalFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
        }
        return uri.lastPathSegment ?: "unknown_file"
    }
    private fun saveReceivedFile() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            val fileName = receivedFileName ?: "received_file_${System.currentTimeMillis()}"
            val destinationFile = File(downloadsDir, fileName)

            receivedFilePayload?.asFile()?.let { file ->
                FileInputStream(file.asParcelFileDescriptor().fileDescriptor).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } ?: throw IOException("Payload file is null")

            if (!destinationFile.exists() || destinationFile.length() == 0L) {
                throw IOException("File was not saved properly")
            }

            // Notify the media scanner about the new file
            MediaScannerConnection.scanFile(
                this,
                arrayOf(destinationFile.toString()),
                null
            ) { path, uri ->
                Log.i("FileShare", "Scanned $path:")
                Log.i("FileShare", "-> uri=$uri")
            }

            runOnUiThread {
                if (!isFinishing) {
                    Toast.makeText(this, "File received and saved successfully", Toast.LENGTH_LONG).show()

                    AlertDialog.Builder(this)
                        .setTitle("File Received")
                        .setMessage("File saved to ${destinationFile.absolutePath}")
                        .setPositiveButton("Open Folder") { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(Uri.parse(downloadsDir.absolutePath), "resource/folder")
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "No app found to open this folder", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }

            // Reset the received file name and payload
            receivedFileName = null
            receivedFilePayload = null
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("FileShare", "Error saving received file", e)
            runOnUiThread {
                if (!isFinishing) {
                    Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private var receivedFileName: String? = null
    private var receivedFilePayload: Payload? = null
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes()
                    if (bytes != null) {
                        receivedFileName = String(bytes, Charset.forName("UTF-8"))
                    }
                }
                Payload.Type.FILE -> {
                    receivedFilePayload = payload
                    saveReceivedFile()
                }
                else -> {
                    Log.w("FileShare", "Unhandled payload type: ${payload.type}")
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d("FileShare", "Payload transfer succeeded: ${update.bytesTransferred}/${update.totalBytes}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "File transfer successful", Toast.LENGTH_SHORT).show()
                    }
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.e("FileShare", "Payload transfer failed: ${update.status}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "File transfer failed", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> Log.d("FileShare", "Payload transfer status: ${update.status}")
            }
        }
    }}

data class Endpoint(val id: String, val name: String)
