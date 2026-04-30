package com.byron.trucaller.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.byron.trucaller.data.model.NearbyDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

class BluetoothForensicsService(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Performs a quick scan (5-7 seconds) for nearby Bluetooth devices.
     * Collects both Classic and BLE devices.
     */
    suspend fun scanNearbyDevices(): List<NearbyDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || !hasBluetoothPermission()) {
            return emptyList()
        }

        val foundDevices = mutableMapOf<String, NearbyDevice>()

        // 1. BLE Scan
        val bleScanner = bluetoothAdapter.bluetoothLeScanner
        val bleDeferred = CompletableDeferred<Unit>()
        val bleCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = try { device.name ?: "Unknown BLE" } catch (_: SecurityException) { "Restricted BLE" }
                val mac = device.address
                foundDevices[mac] = NearbyDevice(name, mac, result.rssi, "BLE")
            }
        }

        // 2. Classic Scan (Discovery)
        val classicReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (BluetoothDevice.ACTION_FOUND == intent.action) {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let {
                        val name = try { it.name ?: "Unknown" } catch (_: SecurityException) { "Restricted" }
                        val mac = it.address
                        foundDevices[mac] = NearbyDevice(name, mac, rssi, "CLASSIC")
                    }
                }
            }
        }

        try {
            context.registerReceiver(classicReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            
            // Start scans
            bleScanner?.startScan(bleCallback)
            bluetoothAdapter.startDiscovery()

            // Wait for 6 seconds of scanning
            delay(6000)

        } catch (e: Exception) {
            Log.e("BluetoothForensics", "Scan failed", e)
        } finally {
            try {
                bleScanner?.stopScan(bleCallback)
                bluetoothAdapter.cancelDiscovery()
                context.unregisterReceiver(classicReceiver)
            } catch (_: Exception) {}
        }

        return foundDevices.values.toList()
    }
}
