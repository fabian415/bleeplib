package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothProfile;

import com.advantech.bleeplib.bean.BLEImageWriteStatus;

/**
 * BLEConnectListener is a callback listener which will returns the connection results after you
 * register a listener using the following method.
 * @see BLEUtil#addConnectListener(String, BLEConnectListener)
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public interface BLEConnectListener {
    /**
     * BLE connection callback.
     *
     * @param result   connection result. Please reference the following results.
     * @see BluetoothProfile#STATE_DISCONNECTED
     * @see BluetoothProfile#STATE_CONNECTING
     * @see BluetoothProfile#STATE_CONNECTED
     * @see BluetoothProfile#STATE_DISCONNECTING
     */
    public void onConnectionStateChange(int result);

    /**
     * BLE connection timeout callback.
     *
     * @param message   error message
     */
    public void onConnectionTimeout(String message);

    /**
     * BLE service discovered callback.
     *
     * @param result    if success, you will receive {@code BluetoothGatt.GATT_SUCCESS}
     */
    public void onServicesDiscovered(int result);

    /**
     * Firmware read callback.
     *
     * @param result    if success, you will receive {@code BluetoothGatt.GATT_SUCCESS}
     * @param read      read data
     */
    public void onFirmwareRead(int result, byte[] read);

    /**
     * LED read callback.
     *
     * @param result    if success, you will receive {@code BluetoothGatt.GATT_SUCCESS}
     * @param read      read data
     */
    public void onLEDRead(int result, byte[] read);

    /**
     * LED write callback.
     *
     * @param result    if success, you will receive {@code BluetoothGatt.GATT_SUCCESS}
     * @param read      read data
     */
    public void onLEDWrite(int result, byte[] read);

    /**
     * Image write callback. You will receive the progress results when Android device generating
     * the image and sending data to the EPD device.
     *
     * @param status    image write status. Please see {@code BLEImageWriteStatus} for all status.
     * @param progress  image write progress
     * @param message   image write message
     */
    public void onImageWrite(BLEImageWriteStatus status, int progress, String message);

    /**
     * Image refresh callback. You will receive this event when the EPD device has finished
     * refreshing the image on its screen.
     *
     * @param isSuccess  {@code true} if the EPD device refresh the image successfully;
     *                   {@code false} otherwise
     * @param page       refresh page {@code number}
     */
    public void onImageRefresh(boolean isSuccess, int page);

    /**
     * Alarm triggered callback. You will receive this event when the EPD device is pressed on its
     * alarm button.
     *
     * @param isWarning     {@code true} if the alarm flag is raising;
     *                      {#code false} otherwise
     */
    public void onAlarmDetected(boolean isWarning);
}
