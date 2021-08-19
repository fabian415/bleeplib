package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothDevice;

public interface BLEScanListener {
    public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord);
    public void onScanStatusChanged(boolean isScanning);
}
