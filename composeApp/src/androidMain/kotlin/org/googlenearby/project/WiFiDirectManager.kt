package org.googlenearby.project

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
        if (info.groupFormed) {
            _isConnected.value = true
            _connectionStatus.value = if (info.isGroupOwner) "Connected as Group Owner" else "Connected as Client"
        } else {
            _isConnected.value = false
            _connectionStatus.value = "Disconnected"
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.d("org.googlenearby.project.WiFiDirectManager", "Wi-Fi P2P is enabled")
                    } else {
                        Log.d("org.googlenearby.project.WiFiDirectManager", "Wi-Fi P2P is not enabled")
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager.requestPeers(channel) { peerList ->
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
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Not handling this action for now
                }
            }
        }
    }

    fun startDiscovery() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.value = "Discovery started"
                Log.d("org.googlenearby.project.WiFiDirectManager", "Discovery started successfully")
            }

            override fun onFailure(reason: Int) {
                _connectionStatus.value = "Discovery failed: $reason"
                Log.e("org.googlenearby.project.WiFiDirectManager", "Discovery failed with reason: $reason")
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
                Log.d("org.googlenearby.project.WiFiDirectManager", "Connection initiated to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                _connectionStatus.value = "Connection failed: $reason"
                when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> Log.e("org.googlenearby.project.WiFiDirectManager", "Wi-Fi Direct is not supported on this device")
                    WifiP2pManager.ERROR -> Log.e("org.googlenearby.project.WiFiDirectManager", "Internal error occurred")
                    WifiP2pManager.BUSY -> Log.e("org.googlenearby.project.WiFiDirectManager", "System is too busy to process the request")
                    else -> Log.e("org.googlenearby.project.WiFiDirectManager", "Unknown error occurred: $reason")
                }
            }
        })
    }

    fun disconnectFromPeer() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionStatus.value = "Disconnected"
                _isConnected.value = false
                Log.d("org.googlenearby.project.WiFiDirectManager", "Disconnected successfully")
            }

            override fun onFailure(reason: Int) {
                _connectionStatus.value = "Failed to disconnect"
                Log.e("org.googlenearby.project.WiFiDirectManager", "Failed to disconnect with reason: $reason")
            }
        })
    }

    fun registerReceiver() {
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver() {
        context.unregisterReceiver(receiver)
    }

//    fun stopDiscovery() {
//        wifiP2pManager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
//            override fun onSuccess() {
//                Log.d("org.googlenearby.project.WiFiDirectManager", "Discovery stopped successfully")
//            }
//
//            override fun onFailure(reason: Int) {
//                Log.e("org.googlenearby.project.WiFiDirectManager", "Failed to stop discovery with reason: $reason")
//            }
//        })
//    }
//
//    fun requestPeers() {
//        wifiP2pManager.requestPeers(channel) { peerList ->
//            _peers.value = peerList.deviceList.toList()
//        }
//    }
//
//    fun cancelConnect() {
//        wifiP2pManager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
//            override fun onSuccess() {
//                _connectionStatus.value = "Connection attempt cancelled"
//                Log.d("org.googlenearby.project.WiFiDirectManager", "Connection attempt cancelled successfully")
//            }
//
//            override fun onFailure(reason: Int) {
//                Log.e("org.googlenearby.project.WiFiDirectManager", "Failed to cancel connection attempt with reason: $reason")
//            }
//        })
//    }
}