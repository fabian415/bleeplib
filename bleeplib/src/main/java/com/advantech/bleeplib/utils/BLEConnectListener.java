package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothGatt;

public interface BLEConnectListener {
    public void onConnectionStateChange(int result); // BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTED
    public void onServicesDiscovered(int status, BluetoothGatt gatt);
    public void onFirmwareRead(int status, byte[] read);
    public void onLEDRead(int status, byte[] read);
    public void onLEDWrite(int status, byte[] read);
    public void onImageWrite(BLEImageWriteStatus status, int progress, String message);
    public void onImageRefresh(boolean isSuccess, int page);
    public void onAlarmDetected(boolean isWarning);
    public void onConnectionTimeout(String message);
}
