package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.advantech.bleeplib.bean.PanelType;
import com.advantech.bleeplib.bean.TaskType;

import java.io.UnsupportedEncodingException;


public class BLETaskHandler {
    private static final String TAG = "BLETaskHandler";
    private Context context;
    private BLEUtil bleUtil = BLEUtil.getInstance();
    private String deviceMac;
    private TaskType taskType;
    private byte[] packageData;
    private Bitmap image;
    private int page = 1;
    private int action = 1;
    private PanelType panelType;
    private int connectStatus = BluetoothProfile.STATE_DISCONNECTED;
    private BLETaskHandlerCallback bleTaskHandlerCallback;
    private boolean isTaskExecuting = false;
    private int retry = 0;
    private static final int MAX_RETRY_TIMES = 5;
    private String read_firmware;
    private byte[] read_led;
    private String firmwareVersion;

    public BLETaskHandler(Context context, String deviceMac, BLETaskHandlerCallback bleTaskHandlerCallback) {
        this.context = context;
        this.deviceMac = deviceMac;
        this.bleTaskHandlerCallback = bleTaskHandlerCallback;
    }

    // FOTA 工作
    public boolean startTask(TaskType taskType, byte[] packageData, String firmwareVersion) {
        if (isTaskExecuting) return false;

        this.taskType = taskType;
        this.packageData = packageData;
        this.firmwareVersion = firmwareVersion;
        // 先註冊 BLE 連接後的回調事件
        bleUtil.addConnectListener(deviceMac, bleConnectListener);
        // 連接設備
        bleUtil.connect(deviceMac);
        return true;
    }

    // 推圖工作
    public boolean startTask(TaskType taskType, PanelType panelType, Bitmap image, int page, int action) {
        if (isTaskExecuting) return false;

        this.taskType = taskType;
        this.panelType = panelType;
        this.image = image;
        this.page = page;
        this.action = action;
        // 先註冊 BLE 連接後的回調事件
        bleUtil.addConnectListener(deviceMac, bleConnectListener);
        // 連接設備
        bleUtil.connect(deviceMac);
        return true;
    }

    // 檢查狀態工作
    public boolean startTask(TaskType taskType) {
        if (isTaskExecuting) return false;

        this.taskType = taskType;
        this.packageData = packageData;
        // 先註冊 BLE 連接後的回調事件
        bleUtil.addConnectListener(deviceMac, bleConnectListener);
        // 連接設備
        bleUtil.connect(deviceMac);
        return true;
    }

    public void disconnect() {
        // 解除工作狀態
        isTaskExecuting = false;
        // 取消註冊 BLE 連接後的回調事件
        bleUtil.removeConnectListener(deviceMac);
        // 斷開設備
        bleUtil.disconnect(deviceMac);
    }

    private void executeTask() {
        boolean result = false;
        if (taskType == TaskType.FIRMWARE_UPGRADE) {
            if(firmwareVersion != null && firmwareVersion.equals(read_firmware)) {
                if (bleTaskHandlerCallback != null) {
                    bleTaskHandlerCallback.onSuccess("Identical Firmware Version!");
                }
                // 解除工作狀態
                isTaskExecuting = false;
                result = true;
            } else {
                result = bleUtil.firmwareUpgrade(deviceMac, packageData);
            }
        } else if (taskType == TaskType.PUSH_IMAGE) {
            result = bleUtil.pushImage(deviceMac, panelType, image, page, action);
        } else if (taskType == TaskType.CHECK_STATUS) {
            if (bleTaskHandlerCallback != null) {
                bleTaskHandlerCallback.onFirmwareRead(read_firmware);
                bleTaskHandlerCallback.onLEDRead(read_led);
            }
            // 解除工作狀態
            isTaskExecuting = false;
            result = true;
        }
        if (!result) {
            if (bleTaskHandlerCallback != null)
                bleTaskHandlerCallback.onError("Task Existed!");
        }
    }

    private void retryTask() {
        retry++;
        if (retry < MAX_RETRY_TIMES) {
            // 連接設備
            if (bleTaskHandlerCallback != null)
                bleTaskHandlerCallback.onReady("Lost Connection! " + retry + " times");
            // 強制斷線並重新連線
            bleUtil.reconnect(deviceMac);
        } else {
            if (bleTaskHandlerCallback != null)
                bleTaskHandlerCallback.onError("Lost Connection! " + retry + " times");
        }
    }

    private BLEConnectListener bleConnectListener = new BLEConnectListener() {
        @Override
        public void onConnectionStateChange(int result) {
            connectStatus = result;
            if (connectStatus == BluetoothGatt.STATE_CONNECTED) {
                if (bleTaskHandlerCallback != null) bleTaskHandlerCallback.onReady("Connected!");
            } else if (connectStatus == BluetoothGatt.STATE_DISCONNECTED) {
                retryTask();
            } else if (connectStatus == BluetoothGatt.STATE_CONNECTING) {
            }
        }

        @Override
        public void onServicesDiscovered(int status, BluetoothGatt gatt) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (bleTaskHandlerCallback != null)
                    bleTaskHandlerCallback.onReady("Service Discovered!");
            }
        }

        @Override
        public void onFirmwareRead(int status, byte[] read) {
            try {
                read_firmware = new String(read, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                read_firmware = null;
            }
        }

        @Override
        public void onLEDRead(int status, byte[] read) {
            read_led = read;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (connectStatus == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, " Job Executed! " + deviceMac);
                    // 開啟工作的狀態
                    isTaskExecuting = true;
                    // execute job here!
                    executeTask();
                } else {
                    retryTask();
                }
            }
        }

        @Override
        public void onLEDWrite(int status, byte[] read) {
        }

        @Override
        public void onImageWrite(BLEImageWriteStatus status, int progress, String message) {
            switch (status) {
                case START:
                    if (bleTaskHandlerCallback != null) bleTaskHandlerCallback.onReady(message);
                    break;
                case IN_PROGRESS:
                    if (bleTaskHandlerCallback != null) bleTaskHandlerCallback.onProgress(progress);
                    break;
                case FINISH:
                    // 解除工作狀態
                    isTaskExecuting = false;
                    if (bleTaskHandlerCallback != null) bleTaskHandlerCallback.onSuccess(message);
                    break;
                case TIMEOUT:
                case ERROR:
                    // 解除工作狀態
                    isTaskExecuting = false;
                    if (bleTaskHandlerCallback != null) bleTaskHandlerCallback.onError(message);
                    break;
            }
        }

        @Override
        public void onImageRefresh(boolean isSuccess, int page) {

        }

        @Override
        public void onAlarmDetected(boolean isWarning) {

        }

        @Override
        public void onConnectionTimeout(String message) {
            // 解除工作狀態
            isTaskExecuting = false;
            if (bleTaskHandlerCallback != null) bleTaskHandlerCallback.onError(message);
        }
    };

}
