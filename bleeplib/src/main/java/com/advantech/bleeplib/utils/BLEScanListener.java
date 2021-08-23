package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothDevice;

/**
 * BLEScanListener is a callback listener which will returns the scanning results after you register
 * a listener using the following method.
 * @see BLEUtil#addScanListener(BLEScanListener)
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public interface BLEScanListener {
    /**
     * BLE scanning callback.
     *
     * @param device        return a object {@code BluetoothDevice}
     * @param rssi          rssi value
     * @param scanRecord    scan record which includes the manufacturer data
     */
    public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord);

    /**
     * BLE scan status changed callback.
     *
     * @param isScanning    {@code true} Android device is scanning now;
     *                      {@code false} otherwise
     */
    public void onScanStatusChanged(boolean isScanning);
}
