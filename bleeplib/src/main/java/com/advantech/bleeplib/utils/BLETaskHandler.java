package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.graphics.Bitmap;
import android.util.Log;

import com.advantech.bleeplib.bean.BLEImageWriteStatus;
import com.advantech.bleeplib.bean.PanelType;
import com.advantech.bleeplib.bean.TaskType;

import java.io.UnsupportedEncodingException;

/**
 * BLETaskHandler is an ready-to-use handler class that can assist user to do batch tasks for
 * transmit image or firmware upgrade for more than one Advantech EPD devices at the same time.
 * This handler will execute the sequential tasks one after one. These tasks including connecting
 * to the EPD device, sending image/upgrading firmware patch, reporting the task status and
 * progress, and disconnecting the EPD device after the job was done. This handler also implements
 * the function for at most 5 times retry if device is disconnected when sending the task.
 * connection timeout is set to 20 seconds and task timeout is set to 60 seconds to address the
 * problems about no response from EPD devices. We highly recommend to use this handler if you have
 * more than one jobs need to done for bulk of devices.
 *
 * @author Fabian Chung
 * @version 1.0.0
 *
 */
public class BLETaskHandler {
    private static final String TAG = "BLETaskHandler";
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
    private boolean autoDisconnect = false;

    /**
     * New a BLETaskHandler constructor.
     *
     * @param deviceMac                 device mac address
     * @param bleTaskHandlerCallback    add a task handler callback which will returns the task
     *                                  results.
     */
    public BLETaskHandler(String deviceMac, BLETaskHandlerCallback bleTaskHandlerCallback) {
        this.deviceMac = deviceMac;
        this.bleTaskHandlerCallback = bleTaskHandlerCallback;
    }

    /**
     * Start a task for firmware upgrade using this BLETaskHandler object.
     *
     * @param taskType          task type, must be {@code TaskType.FIRMWARE_UPGRADE}
     * @param packageData       package data in the byte array format
     * @param firmwareVersion   firmware version
     * @param autoDisconnect    {@code true} disconnect the device after the task is done;
     *                          {@code false} remain the connection after the task is done
     * @return                  {@code true} send this command successfully;
     *                          {@code false} an existing task is still running or device in the waiting queue
     */
    public boolean startTask(TaskType taskType, byte[] packageData, String firmwareVersion, boolean autoDisconnect) {
        if (isTaskExecuting) return false;

        this.taskType = taskType;
        this.packageData = packageData;
        this.firmwareVersion = firmwareVersion;
        this.autoDisconnect = autoDisconnect;

        // 先判斷是否已經被連線？
        if(bleUtil.isConnectedOrInWaitingQueue(deviceMac)) {
            return false;
        } else {
            // 如果沒有，才註冊 BLE 連接後的回調事件
            bleUtil.addConnectListener(deviceMac, bleConnectListener);
            // 再去連接設備
            return bleUtil.connect(deviceMac);
        }
    }

    /**
     * Start a task for pushing image using this BLETaskHandler object.
     *
     * @param taskType       task type, must be {@code TaskType.PUSH_IMAGE}
     * @param panelType      EPD panel-type {@see PanelType}
     * @param image          image in the bitmap format which is ready to transmit; please resize image size to fit each EPD panel-type {@see PanelType}
     * @param page           which page {@code number} you want to transmit image on the EPD device; this number must be larger than 0
     * @param action         refresh this image immediately {@code 1} or not {@code 0}
     * @param autoDisconnect {@code true} disconnect the device after the task is done;
     *                       {@code false} remain the connection after the task is done
     * @return               {@code true} send this command successfully;
     *                       {@code false} an existing task is still running or device in the waiting queue
     */
    public boolean startTask(TaskType taskType, PanelType panelType, Bitmap image, int page, int action, boolean autoDisconnect) {
        if (isTaskExecuting) return false;

        this.taskType = taskType;
        this.panelType = panelType;
        this.image = image;
        this.page = page;
        this.action = action;
        this.autoDisconnect = autoDisconnect;

        // 先判斷是否已經被連線？
        if(bleUtil.isConnectedOrInWaitingQueue(deviceMac)) {
            return false;
        } else {
            // 如果沒有，才註冊 BLE 連接後的回調事件
            bleUtil.addConnectListener(deviceMac, bleConnectListener);
            // 再去連接設備
            return bleUtil.connect(deviceMac);
        }
    }

    /**
     * Start a task for check status using this BLETaskHandler object.
     *
     * @param taskType       task type, must be {@code TaskType.CHECK_STATUS}
     * @param autoDisconnect {@code true} disconnect the device after the task is done;
     *                       {@code false} remain the connection after the task is done
     * @return               {@code true} send this command successfully;
     *                       {@code false} an existing task is still running or device in the waiting queue
     */
    public boolean startTask(TaskType taskType, boolean autoDisconnect) {
        if (isTaskExecuting) return false;

        this.taskType = taskType;
        this.autoDisconnect = autoDisconnect;

        // 先判斷是否已經被連線？
        if(bleUtil.isConnectedOrInWaitingQueue(deviceMac)) {
            return false;
        } else {
            // 如果沒有，才註冊 BLE 連接後的回調事件
            bleUtil.addConnectListener(deviceMac, bleConnectListener);
            // 再去連接設備
            return bleUtil.connect(deviceMac);
        }
    }

    /**
     * Terminate the task and disconnect the device.
     */
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
                // Device disconnect
                if(autoDisconnect) {
                    disconnect();
                }
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
                bleTaskHandlerCallback.onError("Task Existed / Image not valid");
            // Device disconnect
            if(autoDisconnect) {
                disconnect();
            }
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
        public void onServicesDiscovered(int status) {
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
                    // Device disconnect
                    if(autoDisconnect) {
                        disconnect();
                    }
                    break;
                case TIMEOUT:
                case ERROR:
                    // 解除工作狀態
                    isTaskExecuting = false;
                    if (bleTaskHandlerCallback != null) bleTaskHandlerCallback.onError(message);
                    // Device disconnect
                    if(autoDisconnect) {
                        disconnect();
                    }
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
            // Device disconnect
            if(autoDisconnect) {
                disconnect();
            }
        }
    };

}
