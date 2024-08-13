package org.googlenearby.project

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import kotlin.concurrent.thread


class MainActivity : ComponentActivity(), WifiP2pManager.ChannelListener {

    private var connectionState by mutableStateOf(ConnectionState.IDLE)

    private var selectedStrategy by mutableStateOf(SelectedStrategy.P2P_CLUSTER)

    enum class ConnectionState {
        IDLE, CONNECTING, CONNECTED, DISCONNECTING
    }


    private var serverSocket: ServerSocket? = null
    private var isServerSocketOpen = false
    private val MAX_RETRIES = 5
    private val RETRY_INTERVAL = 2000L // 2 seconds
    private val SOCKET_TIMEOUT = 30000 // 30 seconds
    private var isConnecting = false

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var wifiP2pChannel: WifiP2pManager.Channel

    private var isAdvertising by mutableStateOf(false)
    private var isDiscovering by mutableStateOf(false)
    private var discoveredEndpoints by mutableStateOf<List<Endpoint>>(emptyList())
    private var connectionInfoText by mutableStateOf("Searching Devices...")
    private var isDeviceConnected by mutableStateOf(false)

    private var showFileTransferDialog by mutableStateOf(false)
    private var fileTransferProgress by mutableFloatStateOf(0f)
    private var fileTransferSpeed by mutableStateOf("0 Mbps")
    private var fileName by mutableStateOf("")
    private var fileSize by mutableLongStateOf(0L)
    private var isReceivingFile by mutableStateOf(false)
    private var isTransferComplete by mutableStateOf(false)

    private var selectedFileUri: Uri? by mutableStateOf(null)
    private var selectedEndpointId: String? by mutableStateOf(null)

    private var startTime: Long = 0

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = WiFiDirectBroadcastReceiver()

    companion object {
        const val SERVICE_ID = "com.example.wifidirectfileshare"
        private const val PORT = 8888
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionsClient = Nearby.getConnectionsClient(this)
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, this)


        setContent {
            MaterialTheme {
                FileShareApp(
                    isAdvertising = isAdvertising,
                    isDiscovering = isDiscovering,
                    discoveredEndpoints = discoveredEndpoints,
                    connectionInfoText = connectionInfoText,
                    isDeviceConnected = isDeviceConnected,
                    selectedFileUri = selectedFileUri,
                    selectedStrategy = selectedStrategy,
                    onStrategySelected = { strategy -> selectedStrategy = strategy },
                    onStartAdvertising = { startAdvertising() },
                    onStartDiscovering = { startDiscovering() },
                    onStopAll = { stopAllEndpoints() },
                    onEndpointSelected = { endpointId -> connectToEndpoint(endpointId) },
                    onSendFile = { handleFileTransfer() },
                    onPickFile = { pickFile() }
                )

                if (showFileTransferDialog) {
                    FileTransferDialog(
                        isReceiving = isReceivingFile,
                        fileName = fileName,
                        fileSize = fileSize,
                        progress = fileTransferProgress,
                        speed = fileTransferSpeed,
                        isTransferComplete = isTransferComplete,
                        onDismiss = { showFileTransferDialog = false },
                        onCancel = { cancelFileTransfer() },
                        onOpenFile = { openDownloadsFolder() }
                    )
                }
            }
        }
        requestPermissions()
    }

    private fun updateConnectionUI(isConnected: Boolean, role: String, peerName: String?) {
        runOnUiThread {
            isDeviceConnected = isConnected
            connectionInfoText = if (isConnected) {
                "Connected as $role to ${peerName ?: "unknown device"}"
            } else {
                "Disconnected"
            }
        }
    }



    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun requestPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        requestMultiplePermissions.launch(requiredPermissions)
    }

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted, proceed with initialization
        } else {
            Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startAdvertising(
            Build.MODEL, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            isAdvertising = true
            connectionInfoText = "Advertising for connections..."
        }.addOnFailureListener { e ->
            Log.e("WiFiDirect", "Advertising failed: ${e.message}")
        }
    }

    private fun startDiscovering() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
        ).addOnSuccessListener {
            isDiscovering = true
            connectionInfoText = "Discovering devices..."
        }.addOnFailureListener { e ->
            Log.e("WiFiDirect", "Discovery failed: ${e.message}")
        }
    }


    private fun connectToEndpoint(endpointId: String) {
        when (connectionState) {
            ConnectionState.IDLE -> {
                connectionState = ConnectionState.CONNECTING
                connectionsClient.requestConnection(Build.MODEL, endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener {
                        Log.d("WiFiDirect", "Connection request successful")
                    }
                    .addOnFailureListener { e ->
                        Log.e("WiFiDirect", "Connection request failed: ${e.message}")
                        connectionState = ConnectionState.IDLE
                        runOnUiThread {
                            Toast.makeText(this, "Connection request failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            ConnectionState.CONNECTING -> {
                Log.d("WiFiDirect", "Already attempting to connect. Ignoring request.")
            }
            ConnectionState.CONNECTED -> {
                Log.d("WiFiDirect", "Already connected. Ignoring request.")
            }
            ConnectionState.DISCONNECTING -> {
                Log.d("WiFiDirect", "Currently disconnecting. Please wait and try again.")
            }
        }
    }
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (!discoveredEndpoints.any { it.id == endpointId }) {
                discoveredEndpoints = discoveredEndpoints + Endpoint(endpointId, info.endpointName)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            discoveredEndpoints = discoveredEndpoints.filterNot { it.id == endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d("WiFiDirect", "Connection initiated with $endpointId")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d("WiFiDirect", "Connected to $endpointId")
                connectionState = ConnectionState.CONNECTED
                isDeviceConnected = true
                selectedEndpointId = endpointId
                connectionInfoText = "Connected to $endpointId"
                initiateWifiDirectConnection(endpointId)
            } else {
                Log.e("WiFiDirect", "Connection failed: ${result.status}")
                connectionState = ConnectionState.IDLE
                connectionInfoText = "Connection failed: ${result.status}"
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Connection failed: ${result.status}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("WiFiDirect", "Disconnected from $endpointId")
            connectionState = ConnectionState.IDLE
            isDeviceConnected = false
            connectionInfoText = "Disconnected from $endpointId"
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Disconnected from $endpointId", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val metadata = String(payload.asBytes()!!, Charset.forName("UTF-8"))
                    val json = JSONObject(metadata)
                    fileName = json.getString("fileName")
                    fileSize = json.getLong("fileSize")
                    isReceivingFile = true
                    showFileTransferDialog = true
                    fileTransferProgress = 0f
                }
                Payload.Type.FILE -> {
                    // Handle file payload
                }
                else -> Log.w("WiFiDirect", "Unknown payload type received")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    fileTransferProgress = update.bytesTransferred.toFloat() / fileSize
                    fileTransferSpeed = calculateTransferSpeed(update.bytesTransferred)
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    isTransferComplete = true
                    showFileTransferDialog = false
                    Toast.makeText(this@MainActivity, "File transfer completed", Toast.LENGTH_SHORT).show()
                }
                else -> Log.d("WiFiDirect", "Payload transfer status: ${update.status}")
            }
        }
    }

    private fun handleFileTransfer() {
        if (connectionState != ConnectionState.CONNECTED) {
            Log.e("WiFiDirect", "Cannot transfer file. Not connected to any device.")
            runOnUiThread {
                Toast.makeText(this, "Cannot transfer file. Not connected to any device.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        selectedFileUri?.let { uri ->
            selectedEndpointId?.let { endpointId ->
                try {
                    val metadata = JSONObject().apply {
                        put("fileName", getFileName(uri))
                        put("fileSize", getFileSize(uri))
                    }
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(metadata.toString().toByteArray()))

                    val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                    parcelFileDescriptor?.let { pfd ->
                        val filePayload = Payload.fromFile(pfd)
                        connectionsClient.sendPayload(endpointId, filePayload)

                        showFileTransferDialog = true
                        isReceivingFile = false
                        fileTransferProgress = 0f
                        fileName = getFileName(uri)
                        fileSize = getFileSize(uri)
                    } ?: run {
                        Log.e("FileTransfer", "Failed to open file: $uri")
                        Toast.makeText(this, "Failed to open file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("FileTransfer", "Error preparing file transfer", e)
                    Toast.makeText(this, "Error preparing file transfer: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
// You might need to update these functions if they're not already correct:

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "unknown"
    }

    private fun getFileSize(uri: Uri): Long {
        var size: Long = 0
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex != -1 && cursor.moveToFirst()) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    private fun calculateTransferSpeed(bytesTransferred: Long): String {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = (currentTime - startTime) / 1000.0 // Convert to seconds
        val speedInMbps = (bytesTransferred * 8 / 1_000_000) / elapsedTime
        return String.format("%.2f Mbps", speedInMbps)
    }

    private fun cancelFileTransfer() {
        // Implement cancellation logic
    }

    private fun openDownloadsFolder() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(Environment.DIRECTORY_DOWNLOADS), "*/*")
        }
        startActivity(intent)
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedFileUri = uri
            }
        }
    }

    override fun onChannelDisconnected() {
        // Implement channel disconnection handling
    }

    inner class WiFiDirectBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    when (state) {
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED -> {
                            Log.d("WiFiDirect", "Wi-Fi P2P is enabled")
                        }
                        else -> {
                            Log.e("WiFiDirect", "Wi-Fi P2P is not enabled")
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d("WiFiDirect", "Wi-Fi P2P peers changed")
                    // Request available peers from the wifi p2p manager
                    wifiP2pManager.requestPeers(wifiP2pChannel) { peerList ->
                        val refreshedPeers = peerList.deviceList
                        Log.d("WiFiDirect", "Number of peers: ${refreshedPeers.size}")
                        refreshedPeers.forEach { device ->
                            Log.d("WiFiDirect", "Peer: ${device.deviceName} (${device.deviceAddress})")
                        }
                        if (refreshedPeers != discoveredEndpoints) {
                            discoveredEndpoints = refreshedPeers.map { Endpoint(it.deviceAddress, it.deviceName) }
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    Log.d("WiFiDirect", "Wi-Fi P2P connection changed")
                    val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        Log.d("WiFiDirect", "Device is connected")
                        wifiP2pManager.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
                    } else {
                        Log.d("WiFiDirect", "Device is disconnected")
                        isDeviceConnected = false
                        connectionInfoText = "Disconnected"
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    Log.d("WiFiDirect", "This device's Wi-Fi P2P details changed")
                    val device: WifiP2pDevice? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    device?.let {
                        Log.d("WiFiDirect", "This device: ${it.deviceName} (${it.deviceAddress})")
                    }
                }
            }
        }
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        val groupOwnerAddress: String? = info.groupOwnerAddress?.hostAddress

        if (groupOwnerAddress == null) {
            Log.e("WiFiDirect", "Group owner address is null")
            updateConnectionUI(false, "", null)
            return@ConnectionInfoListener
        }

        Log.d("WiFiDirect", "Connection info received. Group owner: $groupOwnerAddress, Am I owner: ${info.isGroupOwner}")

        if (info.groupFormed && info.isGroupOwner) {
            // Do group owner tasks (server)
            updateConnectionUI(true, "group owner", null)
            startServerSocket()
        } else if (info.groupFormed) {
            // Do client tasks
            updateConnectionUI(true, "client", groupOwnerAddress)
            connectToGroupOwner(groupOwnerAddress)
        } else {
            Log.e("WiFiDirect", "Group not formed")
            updateConnectionUI(false, "", null)
        }

        // Request group info to get the connected device name
        wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
            val connectedDevice = group?.clientList?.firstOrNull()
            val deviceName = connectedDevice?.deviceName ?: "Unknown Device"
            updateConnectionUI(info.groupFormed, if (info.isGroupOwner) "group owner" else "client", deviceName)
        }
    }
    private fun startServerSocket() {
        thread(start = true) {
            try {
                closeServerSocket() // Ensure any existing socket is closed

                val localIpAddress = getLocalIpAddress()
                Log.d("WiFiDirect", "Local IP Address: $localIpAddress")

                serverSocket = ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))
                serverSocket?.reuseAddress = true
                serverSocket?.soTimeout = SOCKET_TIMEOUT
                isServerSocketOpen = true
                Log.d("WiFiDirect", "Server socket started on port $PORT")

                if (isServerSocketListening()) {
                    Log.d("WiFiDirect", "Server socket is listening on port $PORT")
                } else {
                    Log.e("WiFiDirect", "Server socket is not listening on port $PORT")
                }

                while (isServerSocketOpen) {
                    try {
                        Log.d("WiFiDirect", "Waiting for client connection...")
                        val client = serverSocket?.accept()
                        client?.let {
                            Log.d("WiFiDirect", "Client connected: ${it.inetAddress.hostAddress}")
                            // Handle client connection
                            receiveFile(it)
                        }
                    } catch (e: SocketTimeoutException) {
                        Log.d("WiFiDirect", "No client connected within timeout period. Continuing to listen.")
                    } catch (e: IOException) {
                        if (e is SocketException && e.message?.contains("Socket closed") == true) {
                            Log.d("WiFiDirect", "Server socket was closed")
                        } else {
                            Log.e("WiFiDirect", "Error accepting client connection", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("WiFiDirect", "Error starting server socket", e)
            } finally {
                closeServerSocket()
            }
        }
    }


    private fun connectToGroupOwner(hostAddress: String) {
        thread(start = true) {
            var retries = 0
            while (retries < MAX_RETRIES) {
                try {
                    Log.d("WiFiDirect", "Attempting to connect to group owner: $hostAddress:$PORT (attempt ${retries + 1})")
                    val socket = Socket()
                    socket.connect(InetSocketAddress(hostAddress, PORT), SOCKET_TIMEOUT)
                    Log.d("WiFiDirect", "Connected to group owner")
                    // Handle connection (e.g., send file)
                    selectedFileUri?.let { uri ->
                        sendFile(socket, uri)
                    } ?: run {
                        Log.e("WiFiDirect", "No file selected for sending")
                        socket.close()
                    }
                    return@thread // Exit the thread if connection is successful
                } catch (e: IOException) {
                    Log.e("WiFiDirect", "Error connecting to group owner (attempt ${retries + 1})", e)
                    retries++
                    if (retries < MAX_RETRIES) {
                        Log.d("WiFiDirect", "Retrying in $RETRY_INTERVAL ms...")
                        Thread.sleep(RETRY_INTERVAL)
                    }
                }
            }
            Log.e("WiFiDirect", "Failed to connect to group owner after $MAX_RETRIES attempts")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Failed to connect to group owner", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun isServerSocketListening(): Boolean {
        return try {
            Socket("localhost", PORT).use { socket ->
                socket.close()
                true
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun getLocalIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    private fun initiateWifiDirectConnection(endpointId: String) {
        wifiP2pManager.requestPeers(wifiP2pChannel) { peers: WifiP2pDeviceList ->
            val device = peers.deviceList.find { it.deviceAddress == endpointId }
            device?.let {
                val config = WifiP2pConfig().apply {
                    deviceAddress = it.deviceAddress
                    wps.setup = WpsInfo.PBC
                }
                wifiP2pManager.connect(wifiP2pChannel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d("WiFiDirect", "Wi-Fi Direct connection initiated")
                    }
                    override fun onFailure(reason: Int) {
                        Log.e("WiFiDirect", "Wi-Fi Direct connection failed: $reason")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Wi-Fi Direct connection failed: $reason", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            } ?: run {
                Log.e("WiFiDirect", "Device not found in peer list")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Device not found in peer list", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun closeServerSocket() {
        isServerSocketOpen = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e("WiFiDirect", "Error closing server socket", e)
        }
        serverSocket = null
        Log.d("WiFiDirect", "Server socket closed")
    }
    private fun stopAllEndpoints() {
        when (connectionState) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> {
                connectionState = ConnectionState.DISCONNECTING
                connectionsClient.stopAllEndpoints()
                wifiP2pManager.cancelConnect(wifiP2pChannel, null)
                wifiP2pManager.removeGroup(wifiP2pChannel, null)
            }
            else -> {
                Log.d("WiFiDirect", "No active connection to stop.")
            }
        }
        isAdvertising = false
        isDiscovering = false
        discoveredEndpoints = emptyList()
        connectionInfoText = "Stopped all endpoints"
        connectionState = ConnectionState.IDLE
    }


    private fun sendFile(socket: Socket, uri: Uri) {
        try {
            socket.use { connectedSocket ->
                val outputStream: OutputStream = connectedSocket.getOutputStream()
                val inputStream: InputStream = contentResolver.openInputStream(uri) ?: throw FileNotFoundException()

                // Send file metadata
                val metadata = JSONObject().apply {
                    put("fileName", getFileName(uri))
                    put("fileSize", getFileSize(uri))
                }
                val metadataBytes = metadata.toString().toByteArray()
                outputStream.write(metadataBytes.size)
                outputStream.write(metadataBytes)

                // Send file content
                inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val fileSize = getFileSize(uri)
                    startTime = System.currentTimeMillis()

                    showFileTransferDialog = true
                    isReceivingFile = false
                    fileName = getFileName(uri)
                    this.fileSize = fileSize

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        updateFileTransferProgress(totalBytesRead, fileSize)
                    }
                }

                // Wait for confirmation from receiver
                val confirmation = connectedSocket.getInputStream().read()
                if (confirmation == 1) {
                    isTransferComplete = true
                    runOnUiThread {
                        showFileTransferDialog = false
                        Toast.makeText(this@MainActivity, "File sent successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    throw IOException("File transfer failed")
                }
            }
        } catch (e: Exception) {
            Log.e("WiFiDirect", "Error sending file", e)
            runOnUiThread {
                showFileTransferDialog = false
                Toast.makeText(this@MainActivity, "Error sending file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun receiveFile(client: Socket) {
        try {
            client.use { socket ->
                val inputStream: InputStream = socket.getInputStream()

                // Read metadata
                val metadataSize = inputStream.read()
                val metadataBuffer = ByteArray(metadataSize)
                inputStream.read(metadataBuffer)
                val metadataString = String(metadataBuffer, Charset.forName("UTF-8"))
                val metadataJson = JSONObject(metadataString)
                val receivedFileName = metadataJson.getString("fileName")
                val receivedFileSize = metadataJson.getLong("fileSize")

                Log.d("WiFiDirect", "Receiving file: $receivedFileName, size: $receivedFileSize")

                // Use the Downloads directory
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadDir, receivedFileName)

                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int =0
                    var totalBytesRead = 0L
                    startTime = System.currentTimeMillis()

                    showFileTransferDialog = true
                    isReceivingFile = true
                    fileName = receivedFileName
                    fileSize = receivedFileSize

                    while (totalBytesRead < receivedFileSize &&
                        inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        updateFileTransferProgress(totalBytesRead, receivedFileSize)
                    }
                }

                // Send confirmation to sender
                socket.getOutputStream().write(1)

                isTransferComplete = true
                runOnUiThread {
                    showFileTransferDialog = false
                    Toast.makeText(this@MainActivity, "File received successfully in Downloads folder", Toast.LENGTH_SHORT).show()
                }

                // Notify the system that a new file has been added to the Downloads folder
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(file.toString()),
                    null
                ) { path, uri ->
                    Log.i("WiFiDirect", "Scanned $path:")
                    Log.i("WiFiDirect", "-> uri=$uri")
                }
            }
        } catch (e: Exception) {
            Log.e("WiFiDirect", "Error receiving file", e)
            runOnUiThread {
                showFileTransferDialog = false
                Toast.makeText(this@MainActivity, "Error receiving file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateFileTransferProgress(bytesTransferred: Long, totalBytes: Long) {
        val progress = bytesTransferred.toFloat() / totalBytes.toFloat()
        val speed = calculateTransferSpeed(bytesTransferred)
        runOnUiThread {
            fileTransferProgress = progress
            fileTransferSpeed = speed
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        closeServerSocket()
        connectionsClient.stopAllEndpoints()
        wifiP2pManager.cancelConnect(wifiP2pChannel, null)
        wifiP2pManager.removeGroup(wifiP2pChannel, null)
    }
}

data class Endpoint(val id: String, val name: String)