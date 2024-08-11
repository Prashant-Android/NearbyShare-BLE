package org.googlenearby.project

import android.Manifest
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var showFileReceiveDialog by mutableStateOf(false)
    private var incomingFileName by mutableStateOf("")
    private var incomingFileSize by mutableLongStateOf(0L)
    private var incomingSenderName by mutableStateOf("")

    private var pendingWifiDirectRequest: String? = null
    private lateinit var wifiDirectManager: WiFiDirectManager
    private var wifiDirectPeers by mutableStateOf<List<WifiP2pDevice>>(emptyList())
    private var wifiDirectConnectionStatus by mutableStateOf("Disconnected")
    private var isWifiDirectConnected by mutableStateOf(false)

    private lateinit var connectionsClient: ConnectionsClient
    private var latency by mutableStateOf(0.0)
    private var isAdvertisingCluster by mutableStateOf(false)
    private var isAdvertisingStar by mutableStateOf(false)
    private var isDiscoveringCluster by mutableStateOf(false)
    private var isDiscoveringStar by mutableStateOf(false)

    private var isSenderUser by mutableStateOf(false)
    private var isReceiverUser by mutableStateOf(false)


    private var showFileTransferDialog by mutableStateOf(false)
    private var fileTransferProgress by mutableFloatStateOf(0f)
    private var fileTransferSpeed by mutableStateOf("0 Mbps")
    private var fileName by mutableStateOf("")
    private var fileSize by mutableLongStateOf(0L)
    private var isReceivingFile by mutableStateOf(false)

    private var receivedFileName: String? = null
    private var receivedFileSize: Long = 0

    private var selectedFileUri: Uri? by mutableStateOf(null)
    private var selectedEndpointId: String? by mutableStateOf(null)

    private var isConnecting by mutableStateOf(false)
    private var isAdvertising by mutableStateOf(false)
    private var isDiscovering by mutableStateOf(false)
    private var discoveredEndpoints by mutableStateOf<List<Endpoint>>(emptyList())
    private var connectionInfoText by mutableStateOf("Searching Devices...")
    private var isDeviceConnected by mutableStateOf(false)
    private var isTransferComplete by mutableStateOf(false)
    private var startTime: Long = 0

    companion object {
        const val SERVICE_ID = "your-service-id"
        const val WIFI_DIRECT_REQUEST = "WIFI_DIRECT_REQUEST"
        const val WIFI_DIRECT_RESPONSE = "WIFI_DIRECT_RESPONSE"
        const val WIFI_DIRECT_CONFIRMATION = "WIFI_DIRECT_CONFIRMATION"

        private fun getDeviceNameString(): String {
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            return if (model.startsWith(manufacturer)) {
                model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } else {
                "$manufacturer $model".replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionsClient = Nearby.getConnectionsClient(this)
        wifiDirectManager = WiFiDirectManager(this)

        setContent {

            FileShareApp(
                isAdvertising = isAdvertisingCluster || isAdvertisingStar,
                isDiscovering = isDiscoveringCluster || isDiscoveringStar,
                discoveredEndpoints = discoveredEndpoints,
                connectionInfoText = connectionInfoText,
                isDeviceConnected = isDeviceConnected,
                selectedFileUri = selectedFileUri,
                isConnecting = isConnecting,
                latency = latency,
                onStartAdvertising = { strategy ->
                    when (strategy) {
                        SelectedStrategy.P2P_CLUSTER -> startAdvertising(Strategy.P2P_CLUSTER)
                        SelectedStrategy.P2P_STAR -> startAdvertising(Strategy.P2P_STAR)
                        SelectedStrategy.BOTH -> {
                            startAdvertising(Strategy.P2P_CLUSTER)
                            startAdvertising(Strategy.P2P_STAR)
                        }
                    }
                },
                onStartDiscovering = { strategy ->
                    when (strategy) {
                        SelectedStrategy.P2P_CLUSTER -> startDiscovering(Strategy.P2P_CLUSTER)
                        SelectedStrategy.P2P_STAR -> startDiscovering(Strategy.P2P_STAR)
                        SelectedStrategy.BOTH -> {
                            startDiscovering(Strategy.P2P_CLUSTER)
                            startDiscovering(Strategy.P2P_STAR)
                        }
                    }
                },
                onStopAll = { stopAllEndpoints() },
                onEndpointSelected = { endpointId ->
                    selectedEndpointId = endpointId
                    stopDiscovering()
                    isConnecting = true
                    connectionsClient.requestConnection(
                        getDeviceNameString(), endpointId, connectionLifecycleCallback
                    )
                },
                onSendFile = {
                    selectedFileUri?.let { uri ->
                        selectedEndpointId?.let { endpointId ->
                            handleFileTransfer()
                        }
                    }
                },
                onPickFile = {
                    filePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    })
                },

                wifiDirectPeers = wifiDirectPeers,
                wifiDirectConnectionStatus = wifiDirectConnectionStatus,
                isWifiDirectConnected = isWifiDirectConnected,
                connectedWifiDirectDevices = wifiDirectPeers,
                onStartWifiDirectDiscovery = { wifiDirectManager.startDiscovery() },
                onConnectWifiDirectPeer = { device -> wifiDirectManager.connectToPeer(device) },
                onDisconnectWifiDirect = { wifiDirectManager.disconnectFromPeer() },
                onSendFileWifiDirect = { device, fileUri ->
                    wifiDirectSendFile(device, fileUri)
                }            )

            if (showFileReceiveDialog) {
                FileReceiveDialog(
                    fileName = incomingFileName,
                    fileSize = incomingFileSize,
                    senderName = incomingSenderName,
                    onAccept = {
                        showFileReceiveDialog = false
                        startTime = System.currentTimeMillis()
                        showFileTransferDialog = true
                        fileName = incomingFileName
                        fileSize = incomingFileSize
                        fileTransferProgress = 0f
                        isTransferComplete = false
                        isReceivingFile = true
                    },
                    onReject = {
                        showFileReceiveDialog = false
                        // Notify sender that the file was rejected
                        selectedEndpointId?.let { endpointId ->
                            val rejectMessage = JSONObject().apply {
                                put("type", "FILE_REJECTED")
                            }
                            connectionsClient.sendPayload(endpointId, Payload.fromBytes(rejectMessage.toString().toByteArray(Charset.forName("UTF-8"))))
                        }
                    }
                )
            }

            if (showFileTransferDialog) {
                FileTransferDialog(latency = latency,
                    isReceiving = isReceivingFile,
                    fileName = fileName,
                    fileSize = fileSize,
                    progress = fileTransferProgress,
                    speed = fileTransferSpeed,
                    isTransferComplete = isTransferComplete,
                    onDismiss = { showFileTransferDialog = false },
                    onCancel = { cancelFileTransfer() },
                    onOpenFile = { openDownloadsFolder() })
            }
        }
        wifiDirectManager.startReceivingFiles { file ->
            // This closure will be called whenever a file is received
            runOnUiThread {
                // Update UI or handle the received file
                handleReceivedFile(file)
            }
        }

        requestPermissions()

        lifecycleScope.launch {
            wifiDirectManager.peers.collectLatest { peers ->
                wifiDirectPeers = peers
            }
        }

        lifecycleScope.launch {
            wifiDirectManager.connectionStatus.collectLatest { status ->
                wifiDirectConnectionStatus = status
            }
        }

        lifecycleScope.launch {
            wifiDirectManager.isConnected.collectLatest { connected ->
                isWifiDirectConnected = connected
            }
        }
    }

    private fun handleFileTransfer() {
        selectedFileUri?.let { uri ->
            selectedEndpointId?.let { endpointId ->
                initiateWifiDirectRequest(endpointId)
            }
        }
    }

    private fun initiateWifiDirectRequest(endpointId: String) {
        val request = JSONObject().apply {
            put("type", WIFI_DIRECT_REQUEST)
            put("deviceId", getDeviceNameString())
            put("requestId", UUID.randomUUID().toString())
        }
        pendingWifiDirectRequest = request.getString("requestId")
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(request.toString().toByteArray()))
    }

    private fun handleWifiDirectRequest(endpointId: String, request: JSONObject) {
        val requesterDeviceId = request.getString("deviceId")
        val requestId = request.getString("requestId")

        fun attemptGroupCreation(retryCount: Int = 0) {
            wifiDirectManager.startGroupOwnerNegotiation { info, error ->
                if (info != null) {
                    val response = JSONObject().apply {
                        put("type", WIFI_DIRECT_RESPONSE)
                        put("deviceId", getDeviceNameString())
                        put("requestId", requestId)
                        put("groupOwnerAddress", info.groupOwnerAddress?.hostAddress ?: "")
                        put("isGroupOwner", info.isGroupOwner)
                        put("groupFormed", info.groupFormed)
                    }
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(response.toString().toByteArray()))
                } else if (error == 2 && retryCount < 3) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        attemptGroupCreation(retryCount + 1)
                    }, 1000)
                } else {
                    Log.e("WiFiDirect", "Failed to create group after retries")
                    // Notify the other device about the failure
                }
            }
        }

        attemptGroupCreation()
    }

    private fun handleWifiDirectResponse(endpointId: String, response: JSONObject) {
        val responseRequestId = response.getString("requestId")
        if (responseRequestId == pendingWifiDirectRequest) {
            pendingWifiDirectRequest = null
            val groupOwnerAddress = response.getString("groupOwnerAddress")
            val isGroupOwner = response.getBoolean("isGroupOwner")
            val groupFormed = response.getBoolean("groupFormed")

            if (groupFormed) {
                wifiDirectManager.connectToGroup(groupOwnerAddress) { success ->
                    if (success) {
                        val confirmation = JSONObject().apply {
                            put("type", WIFI_DIRECT_CONFIRMATION)
                            put("deviceId", getDeviceNameString())
                            put("status", "connected")
                        }
                        connectionsClient.sendPayload(endpointId, Payload.fromBytes(confirmation.toString().toByteArray()))

                        selectedFileUri?.let { uri ->
                            wifiDirectSendFile(WifiP2pDevice(), uri)
                        }
                    } else {
                        Log.e("WiFiDirect", "Failed to connect to Wi-Fi Direct group")
                        Toast.makeText(this, "Failed to connect to Wi-Fi Direct group", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.e("WiFiDirect", "Group not formed")
                Toast.makeText(this, "Failed to form Wi-Fi Direct group", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleWifiDirectConfirmation(endpointId: String, confirmation: JSONObject) {
        val status = confirmation.getString("status")
        if (status == "connected") {
            Log.d("WiFiDirect", "Wi-Fi Direct connection confirmed")
            Toast.makeText(this, "Wi-Fi Direct connection established", Toast.LENGTH_SHORT).show()
            // You might want to update UI or prepare for incoming file
        }
    }


    private var currentTransferJob: Job? = null

    private fun cancelFileTransfer() {
        currentTransferJob?.cancel()
    }


    private fun handleReceivedFile(file: File) {
        // 1. Move the file to the Downloads folder
        val downloadedFile = moveFileToDownloads(file)

        // 2. Notify the user
        Toast.makeText(this, "Received file: ${downloadedFile.name}", Toast.LENGTH_LONG).show()

        // 3. Update your UI
        updateReceivedFilesList(downloadedFile)

        // 4. Scan the file so it appears in the gallery if it's a media file
        MediaScannerConnection.scanFile(this, arrayOf(downloadedFile.toString()), null) { path, uri ->
            Log.i("FileScanner", "Scanned $path:")
            Log.i("FileScanner", "-> uri=$uri")
        }
    }

    private fun moveFileToDownloads(sourceFile: File): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destFile = File(downloadsDir, sourceFile.name)

        try {
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Delete the original file after successful copy
            if (sourceFile.delete()) {
                Log.d("FileTransfer", "Original file deleted successfully")
            } else {
                Log.w("FileTransfer", "Failed to delete original file")
            }

            Log.d("FileTransfer", "File moved to Downloads: ${destFile.absolutePath}")
            return destFile
        } catch (e: IOException) {
            Log.e("FileTransfer", "Error moving file to Downloads", e)
            // If moving fails, return the original file
            return sourceFile
        }
    }

    private fun updateReceivedFilesList(file: File) {

    }

    private fun openDownloadsFolder() {
        try {
            // Get the Downloads directory
            val downloadsUri = Uri.parse(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .toString()
            )

            // Create an intent to view the Downloads folder
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(downloadsUri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Start the activity to open the Downloads folder if there's an app that can handle the intent
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(
                    this, "No app found to open the Downloads folder", Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening Downloads folder: ${e.message}", Toast.LENGTH_LONG)
                .show()
            Log.e("FileShare", "Error opening Downloads folder", e)
        }
    }


    private fun requestPermissions() {
        val requiredPermissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    // Scoped storage permissions
                    Manifest.permission.READ_EXTERNAL_STORAGE, // Scoped storage read access
                    Manifest.permission.WRITE_EXTERNAL_STORAGE // Write access is limited, use Scoped Storage APIs
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    // Scoped storage permissions
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    // Scoped storage permissions
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }

            else -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    // Storage permissions for Android 9 and below
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }

        requestMultiplePermissions.launch(requiredPermissions)
    }

    override fun onResume() {
        super.onResume()
        wifiDirectManager.registerReceiver()
    }

    override fun onPause() {
        super.onPause()
        wifiDirectManager.unregisterReceiver()
    }


    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.values.all { it }
        if (!allPermissionsGranted) {
            Log.e("FileShare", "ALl permission not granted !")

        }
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri: Uri? = result.data?.data
                if (uri != null) {
                    selectedFileUri = uri
                }
            }
        }


    private fun startAdvertising(strategy: Strategy) {
        Log.d("FileShare", "Starting advertising with strategy: $strategy")
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()

        connectionsClient.startAdvertising(
            getDeviceNameString(), SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            when (strategy) {
                Strategy.P2P_CLUSTER -> isAdvertisingCluster = true
                Strategy.P2P_STAR -> isAdvertisingStar = true
            }
            isAdvertising = isAdvertisingCluster || isAdvertisingStar
            Log.d("FileShare", "Advertising started successfully with strategy: $strategy")
        }.addOnFailureListener {
            Log.e("FileShare", "Advertising failed with strategy: $strategy", it)
        }
    }

    private fun startDiscovering(strategy: Strategy) {
        Log.d("FileShare", "Starting discovering with strategy: $strategy")
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()

        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener {
            when (strategy) {
                Strategy.P2P_CLUSTER -> isDiscoveringCluster = true
                Strategy.P2P_STAR -> isDiscoveringStar = true
            }
            isDiscovering = isDiscoveringCluster || isDiscoveringStar
            Log.d("FileShare", "Discovery started successfully with strategy: $strategy")
        }.addOnFailureListener {
            Log.e("FileShare", "Discovery failed with strategy: $strategy", it)
        }
    }

    private fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()
        isAdvertisingCluster = false
        isAdvertisingStar = false
        isDiscoveringCluster = false
        isDiscoveringStar = false
        isAdvertising = false
        isDiscovering = false
        discoveredEndpoints = emptyList()
    }

    private fun stopDiscovering() {
        connectionsClient.stopDiscovery()
        isDiscoveringCluster = false
        isDiscoveringStar = false
        isDiscovering = false
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

    private var latencyResponseCallback: ((Long) -> Unit)? = null


    private fun measureLatency(endpointId: String) {
        val startTime = System.nanoTime()
        val payload = Payload.fromBytes("LATENCY_TEST".toByteArray(Charset.forName("UTF-8")))

        connectionsClient.sendPayload(endpointId, payload).addOnSuccessListener {
            latencyResponseCallback = { responseTime ->
                val latency = (System.nanoTime() - startTime) / 1_000_000.0 // Convert nanoseconds to milliseconds
                this.latency = (this.latency + latency) / 2 // Moving average
                runOnUiThread {
                    // Update latency on UI
                    Log.d("Latency", "Measured latency: $latency ms")
                }
            }
        }.addOnFailureListener { e ->
            Log.e("Latency", "Failed to send latency test payload", e)
        }
    }


    private fun startLatencyMeasurement(endpointId: String) {
        lifecycleScope.launch {
            while (isDeviceConnected) {
                measureLatency(endpointId)
                delay(1000) // Reduce delay to update more frequently
            }
        }
    }


    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            isDeviceConnected = true
            isConnecting = false
            connectionInfoText = "Connected to ${info.endpointName}"
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "Device is connected to ${info.endpointName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            startLatencyMeasurement(endpointId) // Start measuring latency
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectionInfoText = "Connected to $endpointId"
                isDeviceConnected = true
                selectedEndpointId = endpointId
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity, "Device connected successfully", Toast.LENGTH_SHORT
                    ).show()
                }

                startLatencyMeasurement(endpointId)
            } else {
                connectionInfoText = "Connection failed with $endpointId"
                isDeviceConnected = false
            }
        }

        override fun onDisconnected(endpointId: String) {
            isDeviceConnected = false
            connectionInfoText = "Disconnected from $endpointId"
        }
    }

    private fun sendFileMetadata(endpointId: String, uri: Uri) {
        val originalFileName = getOriginalFileName(uri)
        val fileSize = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()) ?: ""
        val mimeType = contentResolver.getType(uri) ?: ""

        val metadata = JSONObject().apply {
            put("fileName", originalFileName)
            put("fileSize", fileSize)
            put("fileExtension", fileExtension)
            put("mimeType", mimeType)
            put("senderName", getDeviceNameString())
        }

        connectionsClient.sendPayload(
            endpointId, Payload.fromBytes(metadata.toString().toByteArray(Charset.forName("UTF-8")))
        )
    }
    private fun sendFile(endpointId: String, uri: Uri) {
        try {
            isReceiverUser = false
            isSenderUser = true

            val originalFileName = getOriginalFileName(uri)
            val fileSize = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
            Log.d("FileShare", "Sending file: $originalFileName, size: $fileSize")

            sendFileMetadata(endpointId, uri)

            val inputStream = contentResolver.openInputStream(uri)
                ?: throw FileNotFoundException("File not found: $uri")

            startTime = System.currentTimeMillis()

            val buffer = ByteArray(65536) // 64KB buffer
            var bytesRead: Int
            var totalBytesSent = 0L
            var lastUpdateTime = startTime

            runOnUiThread {
                showFileTransferDialog = true
                fileName = originalFileName
                this.fileSize = fileSize
                isReceivingFile = false
                fileTransferProgress = 0f
                fileTransferSpeed = "0 Mbps"
            }

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val chunk = Payload.fromBytes(buffer.copyOf(bytesRead))
                connectionsClient.sendPayload(endpointId, chunk)
                totalBytesSent += bytesRead

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 1000) {
                    val speed = calculateTransferSpeed(totalBytesSent)
                    val progress = totalBytesSent.toFloat() / fileSize.toFloat()
                    runOnUiThread {
                        fileTransferProgress = progress
                        fileTransferSpeed = speed
                    }
                    lastUpdateTime = currentTime
                }

            }

            // Send an empty payload to signify the end of the file
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(ByteArray(0)))

            inputStream.close()

            Log.d("FileShare", "File sent completely. Total bytes sent: $totalBytesSent")

            runOnUiThread {
                isTransferComplete = true
                fileTransferProgress = 1f
                fileTransferSpeed = "0 Mbps" // Reset speed after completion
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("FileShare", "Exception during file sending", e)
            runOnUiThread {
                Toast.makeText(this, "Error sending file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun calculateTransferSpeed(bytesTransferred: Long): String {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = (currentTime - startTime) / 1000.0 // Convert to seconds
        val speedInBps = bytesTransferred / elapsedTime
        val speedInMbps = speedInBps * 8 / 1_000_000 // Convert to Mbps
        return String.format("%.2f Mbps", speedInMbps)
    }

    private fun getOriginalFileName(uri: Uri): String {
        val cursor =
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    return it.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment ?: "unknown_file"
    }

    // Payload callback to update file transfer progress and status
    private val payloadCallback = object : PayloadCallback() {
        private var receivingFile = false
        private var outputStream: FileOutputStream? = null
        private var bytesReceived = 0L
        private var lastUpdateTime = 0L

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            isReceiverUser = true
            isSenderUser = false

            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val receivedMessage = String(bytes, Charset.forName("UTF-8"))

                    try {
                        val jsonMessage = JSONObject(receivedMessage)
                        when (jsonMessage.getString("type")) {
                            WIFI_DIRECT_REQUEST -> handleWifiDirectRequest(endpointId, jsonMessage)
                            WIFI_DIRECT_RESPONSE -> handleWifiDirectResponse(endpointId, jsonMessage)
                            WIFI_DIRECT_CONFIRMATION -> handleWifiDirectConfirmation(endpointId, jsonMessage)
                            else -> {
                                // Handle other message types (existing code)
                                when {
                                    receivedMessage == "LATENCY_TEST" -> {
                                        // Existing latency test code
                                    }
                                    !receivingFile -> handleMetadata(bytes)
                                    bytes.isEmpty() -> finishFileReception()
                                    else -> writeChunkToFile(bytes)
                                }
                            }
                        }
                    } catch (e: JSONException) {
                        // Handle non-JSON messages (existing code)
                    }                }

                else -> Log.w("FileShare", "Unhandled payload type: ${payload.type}")
            }
        }

        private fun handleMetadata(metadataBytes: ByteArray) {
            val metadataString = String(metadataBytes, Charset.forName("UTF-8"))
            try {
                val metadata = JSONObject(metadataString)
                receivedFileName = metadata.getString("fileName")
                receivedFileSize = metadata.getLong("fileSize")
                val senderName = metadata.getString("senderName")
                Log.d("FileShare", "Received metadata: $receivedFileName, size: $receivedFileSize, sender: $senderName")

                runOnUiThread {
                    incomingFileName = receivedFileName ?: "Unknown"
                    incomingFileSize = receivedFileSize
                    incomingSenderName = senderName
                    showFileReceiveDialog = true
                }
            } catch (e: JSONException) {
                Log.e("FileShare", "Error parsing metadata", e)
            }
        }
        private fun writeChunkToFile(chunk: ByteArray) {
            outputStream?.write(chunk)
            bytesReceived += chunk.size

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= 1000) {
                updateProgress()
                lastUpdateTime = currentTime
            }
        }

        private fun finishFileReception() {
            outputStream?.flush()
            outputStream?.close()
            outputStream = null
            receivingFile = false

            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, receivedFileName ?: "unknown_file")
            Log.d("FileShare", "File received: ${file.name}, size: ${file.length()}")

            if (file.length() != receivedFileSize) {
                Log.w("FileShare", "File size mismatch: expected $receivedFileSize, got ${file.length()}")
            }

            MediaScannerConnection.scanFile(
                this@MainActivity, arrayOf(file.toString()), null
            ) { path, uri ->
                Log.i("FileShare", "Scanned $path: -> uri=$uri")
            }

            runOnUiThread {
                isTransferComplete = true
                fileTransferProgress = 1f
                isReceivingFile = false
                Toast.makeText(this@MainActivity, "File received: ${file.name}", Toast.LENGTH_LONG).show()
            }
        }

        private fun updateProgress() {
            val progress = bytesReceived.toFloat() / receivedFileSize.toFloat()
            val speed = calculateTransferSpeed(bytesReceived)
            runOnUiThread {
                fileTransferProgress = progress
                fileTransferSpeed = speed
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // This method is less relevant now as we're handling progress in writeChunkToFile
        }
    }


    private fun wifiDirectSendFile(device: WifiP2pDevice, fileUri: Uri) {
        showFileTransferDialog = true
        isReceivingFile = false
        isTransferComplete = false
        fileTransferProgress = 0f
        fileTransferSpeed = "0 Mbps"

        val originalFileName = getOriginalFileName(fileUri)
        val fileSize = contentResolver.openFileDescriptor(fileUri, "r")?.statSize ?: 0L

        fileName = originalFileName
        this.fileSize = fileSize

        lifecycleScope.launch {
            try {
                val success = wifiDirectManager.sendFile(fileUri)
                if (success) {
                    runOnUiThread {
                        isTransferComplete = true
                        fileTransferProgress = 1f
                        fileTransferSpeed = "0 Mbps" // Reset speed after completion
                        Toast.makeText(this@MainActivity, "File sent successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to send file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("FileShare", "Error sending file via Wi-Fi Direct", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error sending file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Start a coroutine to update the progress
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            while (!isTransferComplete) {
                delay(1000) // Update every second
                val progress = wifiDirectManager.getFileTransferProgress()
                val bytesTransferred = (progress * fileSize).toLong()
                val currentTime = System.currentTimeMillis()
                val elapsedTime = (currentTime - startTime) / 1000.0 // Convert to seconds
                val speedInMbps = (bytesTransferred * 8 / elapsedTime) / 1_000_000 // Convert to Mbps

                runOnUiThread {
                    fileTransferProgress = progress
                    fileTransferSpeed = String.format("%.2f Mbps", speedInMbps)
                }
            }
        }
    }

}

