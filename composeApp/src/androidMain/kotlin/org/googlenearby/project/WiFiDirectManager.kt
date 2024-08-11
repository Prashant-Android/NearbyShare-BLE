package org.googlenearby.project

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket


import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.provider.OpenableColumns

import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WiFiDirectManager(private val context: Context) {

    private val wifiP2pManager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }

    private val channel: WifiP2pManager.Channel by lazy {
        wifiP2pManager.initialize(context, Looper.getMainLooper(), null)
    }

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers

    private val _connectionStatus = MutableStateFlow<String>("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _groupOwnerAddress = MutableStateFlow<String?>(null)
    val groupOwnerAddress: StateFlow<String?> = _groupOwnerAddress

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.d(TAG, "Wi-Fi P2P is enabled")
                    } else {
                        Log.d(TAG, "Wi-Fi P2P is not enabled")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager.requestPeers(channel) { peerList: WifiP2pDeviceList ->
                        _peers.value = peerList.deviceList.toList()
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        wifiP2pManager.requestConnectionInfo(channel, connectionInfoListener)
                    } else {
                        _isConnected.value = false
                        _connectionStatus.value = "Disconnected"
                        _groupOwnerAddress.value = null
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Not handling this action for now
                }
            }
        }
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info: WifiP2pInfo ->
        if (info.groupFormed) {
            _isConnected.value = true
            _connectionStatus.value = if (info.isGroupOwner) "Connected as Group Owner" else "Connected as Client"
            _groupOwnerAddress.value = info.groupOwnerAddress.hostAddress
        } else {
            _isConnected.value = false
            _connectionStatus.value = "Disconnected"
            _groupOwnerAddress.value = null
        }
    }

    fun startDiscovery() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.value = "Discovery started"
                Log.d(TAG, "Discovery started successfully")
            }

            override fun onFailure(reason: Int) {
                _connectionStatus.value = "Discovery failed: $reason"
                Log.e(TAG, "Discovery failed with reason: $reason")
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.value = "Connecting to ${device.deviceName}"
                Log.d(TAG, "Connection initiated to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                _connectionStatus.value = "Connection failed: $reason"
                Log.e(TAG, "Connection failed with reason: $reason")
            }
        })
    }

    fun disconnectFromPeer() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.value = "Disconnected"
                _isConnected.value = false
                _groupOwnerAddress.value = null
                Log.d(TAG, "Disconnected successfully")
            }

            override fun onFailure(reason: Int) {
                _connectionStatus.value = "Failed to disconnect"
                Log.e(TAG, "Failed to disconnect with reason: $reason")
            }
        })
    }

    fun registerReceiver() {
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver() {
        context.unregisterReceiver(receiver)
    }

    private var fileTransferProgress = 0f


    suspend fun sendFile(fileUri: Uri): Boolean = suspendCoroutine { continuation ->
        val groupOwnerAddr = _groupOwnerAddress.value
        if (groupOwnerAddr == null) {
            Log.e(TAG, "No group owner address available")
            continuation.resume(false)
            return@suspendCoroutine
        }

        Thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(groupOwnerAddr, FILE_TRANSFER_PORT), 5000)

                val outputStream = socket.getOutputStream()
                val contentResolver = context.contentResolver

                val inputStream = contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    Log.e(TAG, "Failed to open input stream for file")
                    continuation.resume(false)
                    return@Thread
                }

                val fileName = getFileName(fileUri)
                val fileSize = getFileSize(fileUri)

                // Send file metadata
                val metadata = "$fileName|$fileSize"
                outputStream.write(metadata.toByteArray())
                outputStream.write('\n'.code)

                // Send file content
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesSent = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesSent += bytesRead
                    fileTransferProgress = totalBytesSent.toFloat() / fileSize.toFloat()
                }

                inputStream.close()
                socket.close()

                Log.d(TAG, "File sent successfully")
                continuation.resume(true)
            } catch (e: IOException) {
                Log.e(TAG, "Error sending file", e)
                continuation.resume(false)
            }
        }.start()
    }

    fun getFileTransferProgress(): Float {
        return fileTransferProgress
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
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
        return result ?: "unknown_file"
    }

    private fun getFileSize(uri: Uri): Long {
        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        return fileDescriptor?.statSize ?: -1
    }


    fun startReceivingFiles(onFileReceived: (File) -> Unit) {
        Thread {
            try {
                val serverSocket = ServerSocket(FILE_TRANSFER_PORT)
                while (true) {
                    val client = serverSocket.accept()
                    Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}")

                    Thread {
                        handleClient(client, onFileReceived)
                    }.start()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error in file receiving server", e)
            }
        }.start()
    }

    private fun handleClient(client: Socket, onFileReceived: (File) -> Unit) {
        try {
            val inputStream = client.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Read metadata
            val metadata = reader.readLine()
            val (fileName, fileSizeStr) = metadata.split("|")
            val fileSize = fileSizeStr.toLong()

            val file = File(context.getExternalFilesDir(null), fileName)
            val outputStream = FileOutputStream(file)

            var bytesReceived = 0L
            val buffer = ByteArray(4096)
            var bytesRead: Int = 0

            while (bytesReceived < fileSize && inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesReceived += bytesRead
            }

            outputStream.close()
            client.close()

            Log.d(TAG, "File received: ${file.absolutePath}")
            onFileReceived(file)
        } catch (e: IOException) {
            Log.e(TAG, "Error handling client", e)
        }
    }

    fun startGroupOwnerNegotiation(callback: (WifiP2pInfo?, Int?) -> Unit) {
        val config = WifiP2pConfig()
        config.groupOwnerIntent = 15  // Max intent to become group owner

        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group created successfully")
                // We need to wait for the WIFI_P2P_CONNECTION_CHANGED_ACTION broadcast
                // to get the group info. So we'll set up a temporary listener.
                val tempListener = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                            val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                            if (networkInfo?.isConnected == true) {
                                wifiP2pManager.requestGroupInfo(channel) { group ->
                                    if (group != null) {
                                        val info = WifiP2pInfo().apply {
                                            groupFormed = true
                                            isGroupOwner = true
                                            groupOwnerAddress = group.owner.deviceAddress.let { InetAddress.getByName(it) }
                                        }
                                        callback(info, null)
                                        context.unregisterReceiver(this)
                                    }
                                }
                            }
                        }
                    }
                }
                context.registerReceiver(tempListener, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to create group with reason: $reason")
                callback(null, reason)
            }
        })
    }

    fun connectToGroup(groupOwnerAddress: String, callback: (Boolean) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = groupOwnerAddress
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated successfully")
                // We need to wait for the WIFI_P2P_CONNECTION_CHANGED_ACTION broadcast
                // to confirm the connection. So we'll set up a temporary listener.
                val tempListener = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) {
                            val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                            if (networkInfo?.isConnected == true) {
                                callback(true)
                                context.unregisterReceiver(this)
                            }
                        }
                    }
                }
                context.registerReceiver(tempListener, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed with reason: $reason")
                callback(false)
            }
        })
    }

    companion object {
        private const val TAG = "WiFiDirectManager"
        private const val FILE_TRANSFER_PORT = 8888
    }
}