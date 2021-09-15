package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;

import com.advantech.bleeplib.image.ImageGenerator;

import java.util.Map;
import java.util.Objects;

/**
 * BLEDeviceBean is a java bean for internal usage.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
class BLEDeviceBean {
    private String address; // Mac Address
    private BluetoothGatt bluetoothGatt; // BluetoothGatt 物件實體
    private Map<String, BluetoothGattCharacteristic> charMap = new ArrayMap<>(); // characteristics map

    private int descCounter = 0; // 用來計數是否達到三
    private ImageGenerator imageGenerator;
    private long start_send_image_time = -1;
    private long end_send_image_time = -1;
    private int progress_percent = 0;
    private int running_block_number = 0;
    private boolean isImageWriting = false;
    private BLEDeviceBeanTimeoutCallback timeoutCallback; // Task Timeout callback
    private Handler taskTimeoutHandler; // 該 Handler 用來確認推圖工作是否能在 60 秒內完成，否則 timeout
    private static final int TASK_TIMEOUT_TIME = 60 * 1000; // 60 sec

    private boolean isConnecting = false;
    private Handler connTimeoutHandler; // 該 Handler 用來確認連線是否能在 30 秒內完成，否則 timeout
    private static final int CONN_TIMEOUT_TIME = 30 * 1000; // 30 sec

    public BLEDeviceBean(String address, BluetoothGatt bluetoothGatt, BLEDeviceBeanTimeoutCallback timeoutCallback) {
        this.address = address;
        this.bluetoothGatt = bluetoothGatt;
        this.timeoutCallback = timeoutCallback;
        taskTimeoutHandler = new Handler(Looper.getMainLooper());
        connTimeoutHandler = new Handler(Looper.getMainLooper());
    }

    // Task Timer --- START
    public boolean startTaskTimeoutChecker() {
        if (isImageWriting) return false;
        isImageWriting = true;
        taskTimeoutHandler.postDelayed(taskTimeoutRunnable, TASK_TIMEOUT_TIME);
        return true;
    }

    public void removeTaskTimeoutChecker() {
        isImageWriting = false;
        taskTimeoutHandler.removeCallbacks(taskTimeoutRunnable);
    }

    private Runnable taskTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            removeTaskTimeoutChecker();
            // notify user
            timeoutCallback.onTaskTimeout(progress_percent);
        }
    };
    // Task Timer --- END

    // Connection Timer --- START
    public boolean startConnTimeoutChecker() {
        if (isConnecting) return false;
        isConnecting = true;
        connTimeoutHandler.postDelayed(connTimeoutRunnable, CONN_TIMEOUT_TIME);
        return true;
    }

    public void removeConnTimeoutChecker() {
        isConnecting = false;
        connTimeoutHandler.removeCallbacks(connTimeoutRunnable);
    }

    private Runnable connTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            removeConnTimeoutChecker();
            // notify user
            timeoutCallback.onConnectionTimeout();
        }
    };
    // Connection Timer --- END

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    public void setBluetoothGatt(BluetoothGatt bluetoothGatt) {
        this.bluetoothGatt = bluetoothGatt;
    }

    public Map<String, BluetoothGattCharacteristic> getCharMap() {
        return charMap;
    }

    public void setCharMap(Map<String, BluetoothGattCharacteristic> charMap) {
        this.charMap = charMap;
    }

    public int getDescCounter() {
        return descCounter;
    }

    public void addDescCounter(int add) {
        this.descCounter += add;
    }

    public void setDescCounter(int descCounter) {
        this.descCounter = descCounter;
    }

    public boolean isImageWriting() {
        return isImageWriting;
    }

    public void setImageWriting(boolean imageWriting) {
        isImageWriting = imageWriting;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public void setConnecting(boolean connecting) {
        isConnecting = connecting;
    }

    public int getProgress_percent() {
        return progress_percent;
    }

    public void setProgress_percent(int progress_percent) {
        this.progress_percent = progress_percent;
    }

    public ImageGenerator getImageGenerator() {
        return imageGenerator;
    }

    public void setImageGenerator(ImageGenerator imageGenerator) {
        this.imageGenerator = imageGenerator;
    }

    public long getStart_send_image_time() {
        return start_send_image_time;
    }

    public void setStart_send_image_time(long start_send_image_time) {
        this.start_send_image_time = start_send_image_time;
    }

    public long getEnd_send_image_time() {
        return end_send_image_time;
    }

    public void setEnd_send_image_time(long end_send_image_time) {
        this.end_send_image_time = end_send_image_time;
    }

    public int getRunning_block_number() {
        return running_block_number;
    }

    public void setRunning_block_number(int running_block_number) {
        this.running_block_number = running_block_number;
    }

    public void addRunning_block_number(int add) {
        this.running_block_number += add;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BLEDeviceBean)) return false;
        BLEDeviceBean that = (BLEDeviceBean) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
