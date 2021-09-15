package com.advantech.bleeplib.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.advantech.bleeplib.bean.BLEImageWriteStatus;
import com.advantech.bleeplib.bean.PanelType;
import com.advantech.bleeplib.bean.TaskType;
import com.advantech.bleeplib.image.ImageGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static android.content.Context.BLUETOOTH_SERVICE;
import static com.advantech.bleeplib.utils.Common.byteArrayToHexStr;

/**
 * BLEUtil is a singleton class for basic bluetooth communication with Advantech EPD devices. This
 * class provides customized functions for scanning nearby devices, making a connection with a
 * device, opening LED lights, firmware upgrading, and pushing image on the EPD device screen.
 * Handshake protocol between Advantech EPD devices and the Android mobile has been well-defined
 * based on Android Bluetooth Low Energy (BLE) APIs. In this class, we only allow four device
 * connections at a time and other devices will be in the waiting queue temporarily. After a device
 * is disconnected, we will pull a device from the waiting queue to continue to make the connection.
 * Note: Because each Android device has the maximum connection limitations for concurrent
 * device connections, please remember to disconnect EPD devices if no necessary to use them.
 *
 * @author Fabian Chung
 * @version 1.0.0
 * 
 */
public class BLEUtil {
    private static String TAG = "BLEUtil";
    private static BLEUtil instance = null;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private List<BLEScanListener> bleScanListeners = new ArrayList<>();
    private Map<String, BLEDeviceBean> connectionQueue = new ConcurrentHashMap<>();
    private Map<String, BLEConnectListener> bleConnectListeners = new ConcurrentHashMap<>(); // mac, listener
    private ConcurrentLinkedQueue<String> waitingQueue = new ConcurrentLinkedQueue<>(); // 最多四條同時連線，其餘放入 waitingList
    private static final int MAX_CONNECTION_NUMBER = 4; // LG X9009 最多到四條連線同時
    private boolean isScanning = false;
    private Handler mHandler; // 該 Handler 用來搜尋Devices scanTime 秒後，自動停止搜尋

    private final static int BLE_MTU = 251;
    private final static String LED_CHAR_UUID = "0000FFF3-0000-1000-8000-00805F9B34FB";
    private final static String FIRMWARE_CHAR_UUID = "00002A26-0000-1000-8000-00805F9B34FB";
    private final static String IMAGE_ID_CHAR_UUID = "F000FFC1-0451-4000-B000-000000000000";
    private final static String IMAGE_BLOCK_CHAR_UUID = "F000FFC2-0451-4000-B000-000000000000";
    private final static String IMAGE_STATUS_CHAR_UUID = "F000FFC4-0451-4000-B000-000000000000";
    private final static String DEVICE_EVENT_CHAR_UUID = "0000FFF4-0000-1000-8000-00805F9B34FB";

    private final static String IMAGE_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb".toUpperCase();
    private Context context;
    private static final int IMAGE_HEADER_LEN = 32;

    private BLEUtil() {
    }

    /**
     * Obtain the BLEUtil singleton instance.
     *
     * @return BLEUtil
     */
    public static BLEUtil getInstance() {
        if (instance == null) {
            synchronized (BLEUtil.class) {
                if (instance == null) {
                    instance = new BLEUtil();
                }
            }
        }
        return instance;
    }

    /**
     * Initialization for Android bluetooth resources and utilities in Activity.
     * This step must be implemented in the beginning of your program.
     *
     * @param context   the context of Android activity
     * @return          {@code true} if Android device has bluetooth service and initialize
     *                  successfully;
     *                  {@code false} otherwise
     */
    public boolean initial(Context context) {
        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        mHandler = new Handler(Looper.getMainLooper());
        return bluetoothAdapter != null;
    }

    /**
     * Start a scan searching for nearby devices through BLE.
     * Please remember to register scan listener before start a scan.
     * @see BLEUtil#addScanListener(BLEScanListener)
     *
     * @param scanTime   {@code -1} start a scan without stop;
     *                   {@code number} start a scan for a limited time (unit: seconds)
     * @return           {@code true} if start a scan successfully;
     *                   {@code false} already started
     */
    public boolean startScan(int scanTime) {
        if (isScanning) return false;
        if (scanTime > -1) { // 如果 scanTime > -1，則設定倒數
            // 啟動一個 Handler，並使用 postDelayed 在 scanTime 秒後自動執行此 Runnable()
            mHandler.postDelayed(myRunnable, scanTime * 1000);
        }
        // 開始搜尋 BLE 設備
        bluetoothLeScanner.startScan(null, createScanSetting(), leScanCallback);
        isScanning = true;
        Log.d(TAG, "Start Scan");
        // notify clients
        for (BLEScanListener bleScanListener : bleScanListeners) {
            bleScanListener.onScanStatusChanged(isScanning);
        }
        return true;
    }

    /**
     * Stop a scan.
     *
     */
    public void stopScan() {
        // 停止搜尋
        bluetoothLeScanner.stopScan(leScanCallback);
        isScanning = false;
        Log.d(TAG, "Stop Scan");
        mHandler.removeCallbacks(myRunnable);
        // notify clients
        for (BLEScanListener bleScanListener : bleScanListeners) {
            bleScanListener.onScanStatusChanged(isScanning);
        }
    }

    private ScanSettings createScanSetting() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        // SCAN_MODE_LOW_POWER: 耗電最少，掃描時間間隔最短
        // SCAN_MODE_BALANCED: 平衡模式，耗電適中，掃描時間間隔一般，我使用這種模式來更新裝置狀態
        // SCAN_MODE_LOW_LATENCY: 最耗電，掃描延遲時間短，開啟掃描需要立馬返回結果可以使用
        builder.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        // builder.setReportDelay(100); // 設定掃描返回延遲時間，一般是大於零的毫秒值
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        }
        return builder.build();
    }

    private Runnable myRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            byte[] scanRecord = result.getScanRecord().getBytes();

            // notify clients
            for (BLEScanListener bleScanListener : bleScanListeners) {
                bleScanListener.onLeScan(device, rssi, scanRecord);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    /**
     * Make a connection with a device using the mac address.
     * Please remember to register connection listener before make a connection.
     * @see BLEUtil#addConnectListener(String, BLEConnectListener)
     *
     * @param address   device mac address
     * @return          {@code true} if connect a device successfully (Note: it's possible to stay
     *                  in the waiting queue temporarily if reach the four maximum connections
     *                  limit);
     *                  {@code false} if connection exists or already in the waiting queue, we will
     *                  return false
     */
    public synchronized boolean connect(String address) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(address);
        // if this mac is not in connection and not in the waiting queue
        if (bean == null && !waitingQueue.contains(address)) {
            // over the max limit, offer to the waiting queue first
            if (connectionQueue.size() >= MAX_CONNECTION_NUMBER) {
                waitingQueue.offer(address);
                return true;
            }

            // get BluetoothDevice object via mac
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            // make a connection, and also register a BluetoothGattCallback listener
            BluetoothGatt bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback);
            BLEDeviceBean bleDeviceBean = new BLEDeviceBean(address, bluetoothGatt, new BLEDeviceBeanTimeoutCallback() {
                @Override
                public void onTaskTimeout(int progress) {
                    // notify clients
                    for (String mac : bleConnectListeners.keySet()) {
                        if (mac.equals(address)) {
                            BLEConnectListener listener = bleConnectListeners.get(address);
                            listener.onImageWrite(BLEImageWriteStatus.TIMEOUT, progress, "Task Timeout");
                            break;
                        }
                    }
                }

                @Override
                public void onConnectionTimeout() {
                    // notify clients
                    for (String mac : bleConnectListeners.keySet()) {
                        if (mac.equals(address)) {
                            BLEConnectListener listener = bleConnectListeners.get(address);
                            listener.onConnectionTimeout("Connect Timeout");
                            break;
                        }
                    }
                }
            });
            // start a connection timeout timer
            result = bleDeviceBean.startConnTimeoutChecker();
            connectionQueue.put(address, bleDeviceBean);
            Log.d(TAG, "Connection Number (add " + address + "): " + connectionQueue.size());
            return result;
        } else {
            Log.d(TAG, "Connection existed or already in Waiting Queue !!! ( " + address + " )");
            result = false;
            return result;
        }
    }


    /**
     * Disconnect a device using the mac address.
     *
     * @param address   device mac address
     */
    public synchronized void disconnect(String address) {
        BLEDeviceBean bean = connectionQueue.get(address);
        if (bean != null) {
            BluetoothGatt bluetoothGatt = bean.getBluetoothGatt();
            if (bluetoothGatt != null) {
                // disconnect a device
                bluetoothGatt.disconnect();

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            // 1 seconds later, remove the device from the connection queue
                            Thread.sleep(1000);
                            connectionQueue.remove(address);
                            Log.d(TAG, "Connection Number (remove " + address + "): " + connectionQueue.size());
                            // ask the waiting queue if have other devices?
                            if (waitingQueue.size() > 0) {
                                String nextMac = waitingQueue.poll();
                                // Fix a bug: Can't create handler inside thread Thread[Thread-80,5,main] that has not called Looper.prepare()
                                Looper.prepare(); // *
                                connect(nextMac); // here has a handler
                                Looper.loop(); // * 這種情形下，Runnable 物件是運行在子執行緒的，可以進行長時間的聯網操作，但不能更新 UI
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
            // remove connection timeout timer and task timeout timer
            bean.removeTaskTimeoutChecker();
            bean.removeConnTimeoutChecker();
        }
    }

    /**
     * Reconnect a device using the mac address. This function will force to disconnect the device
     * if the connection has been existed.
     *
     * @param address   device mac address
     */
    public synchronized void reconnect(String address) {
        BLEDeviceBean bean = connectionQueue.get(address);
        if (bean != null) { // if the connection existed
            BluetoothGatt bluetoothGatt = bean.getBluetoothGatt();
            if (bluetoothGatt != null) {
                // disconnect a device
                bluetoothGatt.disconnect();

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            // 1 seconds later, remove the device from the connection queue
                            Thread.sleep(1000);
                            connectionQueue.remove(address);
                            Log.d(TAG, "Connection Number: " + connectionQueue.size());
                            // Fix a bug: Can't create handler inside thread Thread[Thread-80,5,main] that has not called Looper.prepare()
                            Looper.prepare(); // *
                            connect(address); // here has a handler
                            Looper.loop(); // * 這種情形下，Runnable 物件是運行在子執行緒的，可以進行長時間的聯網操作，但不能更新 UI
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
            // remove connection timeout timer and task timeout timer
            bean.removeTaskTimeoutChecker();
            bean.removeConnTimeoutChecker();
        } else {  // if already disconnected
            connect(address); // here has a handler
        }
    }

    /**
     * Check if the device is connected or in the waiting queue.
     *
     * @param address   device mac address
     * @return          {@code true} the device is either connected or in the waiting queue;
     *                  {@code false} otherwise
     */
    public synchronized boolean isConnectedOrInWaitingQueue(String address) {
        BLEDeviceBean bean = connectionQueue.get(address);
        return bean != null || waitingQueue.contains(address);
    }

    BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        /**
         * Return connection status.
         *
         * @param gatt      BluetoothGatt object
         * @param status    if connection success, then return {@link BluetoothGatt#GATT_SUCCESS}
         * @param newState  return a new status {@link BluetoothProfile#STATE_DISCONNECTED} or
         *                                  {@link BluetoothProfile#STATE_CONNECTED}
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String mac = gatt.getDevice().getAddress();
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, mac + " Connected");
                // Step 1. discover services
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, mac + " Disconnected");
                // close the BluetoothGatt object after receiving disconnection event.
                // fix a bug 183108: https://code.google.com/p/android/issues/detail?id=183108
                try {
                    gatt.close();
                } catch (Exception e) {
                    Log.e(TAG, "close ignoring: " + e);
                }

                // remove connection timeout timer and task timeout timer
                BLEDeviceBean bean = connectionQueue.get(mac);
                if (bean != null) {
                    bean.removeTaskTimeoutChecker();
                    bean.removeConnTimeoutChecker();
                }
            }
            // notify clients
            for (String address : bleConnectListeners.keySet()) {
                if (address.equals(mac)) {
                    BLEConnectListener listener = bleConnectListeners.get(address);
                    listener.onConnectionStateChange(newState);
                    break;
                }
            }
        }

        /**
         * BluetoothGatt service discovery callback.
         *
         * @param gatt      BluetoothGatt object
         * @param status    if success, then return {@link BluetoothGatt#GATT_SUCCESS}
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            String mac = gatt.getDevice().getAddress();
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.d(TAG, mac + " Service Discovery...");
                Map<String, BluetoothGattCharacteristic> characteristicMap = new HashMap<>();
                List<BluetoothGattService> gattServices = gatt.getServices();
                // for-loop all service
                for (BluetoothGattService gattService : gattServices) {
                    // characteristics in each service
                    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                        String uuid = gattCharacteristic.getUuid().toString();
                        if (uuid == null) continue;
                        uuid = uuid.toUpperCase();
                        // save key characteristics in the HashMap
                        switch (uuid) {
                            case FIRMWARE_CHAR_UUID:
                            case LED_CHAR_UUID:
                            case IMAGE_ID_CHAR_UUID:
                            case IMAGE_BLOCK_CHAR_UUID:
                            case IMAGE_STATUS_CHAR_UUID:
                            case DEVICE_EVENT_CHAR_UUID:
                                characteristicMap.put(uuid, gattCharacteristic);
                        }
                    }
                }
                BLEDeviceBean bean = connectionQueue.get(mac);
                if (bean != null) {
                    bean.setCharMap(characteristicMap);
                }

                // Step 2. set MTU Level to 251
                boolean result = gatt.requestMtu(BLE_MTU);
            } else {
                Log.e(TAG, mac + " Service Discovery Error");
            }

            // notify clients
            for (String address : bleConnectListeners.keySet()) {
                if (address.equals(mac)) {
                    BLEConnectListener listener = bleConnectListeners.get(address);
                    listener.onServicesDiscovered(status);
                    break;
                }
            }
        }

        /**
         * Characteristic read callback.
         *
         * @param gatt            BluetoothGatt object
         * @param characteristic  BluetoothGattCharacteristic object
         * @param status          if success, then return {@link BluetoothGatt#GATT_SUCCESS}
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            String mac = gatt.getDevice().getAddress();
            byte[] read = characteristic.getValue();

            if (LED_CHAR_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                Log.d(TAG, mac + " LED Read: 0x" + byteArrayToHexStr(read));
                // notify clients
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onLEDRead(status, read);
                        break;
                    }
                }
                // Step 6. Handshake done!
                BLEDeviceBean bean = connectionQueue.get(mac);
                if (bean == null) return;
                // remove connection timeout timer
                bean.removeConnTimeoutChecker();
            } else if (FIRMWARE_CHAR_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                try {
                    Log.d(TAG, mac + " Firmware Read: " + new String(read, "UTF-8"));
                } catch (Exception e) {
                }
                // notify clients
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onFirmwareRead(status, read);
                        break;
                    }
                }
                // Step 5. Read LED status
                readLEDStatus(gatt);
            }
        }

        /**
         * Characteristic write callback.
         * @param gatt              BluetoothGatt object
         * @param characteristic    BluetoothGattCharacteristic object
         * @param status            if success, then return {@link BluetoothGatt#GATT_SUCCESS}
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            String mac = gatt.getDevice().getAddress();
            byte[] read = characteristic.getValue();
            String data = byteArrayToHexStr(read);
            BLEDeviceBean bean = connectionQueue.get(mac);
            if (bean == null) return;

            if (LED_CHAR_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                // notify clients
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onLEDWrite(status, read);
                        break;
                    }
                }
            }

            if (IMAGE_BLOCK_CHAR_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                bean.addRunning_block_number(1);
                int running_block_number = bean.getRunning_block_number();
                if (running_block_number < bean.getImageGenerator().total_block_number) {
                    // continue running blocks --- B1
                    writeBlock(bean, running_block_number);
                }
            }
        }

        /**
         * Device send notify will trigger this callback.
         *
         * @param gatt              BluetoothGatt object
         * @param characteristic    BluetoothGattCharacteristic object
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String mac = gatt.getDevice().getAddress();
            String uuid = characteristic.getUuid().toString();
            byte[] notify_data = characteristic.getValue();
            BLEDeviceBean bean = connectionQueue.get(mac);
            if (bean == null) return;

            if (IMAGE_ID_CHAR_UUID.equalsIgnoreCase(uuid)) {
                // stop task timeout timer
                bean.removeTaskTimeoutChecker();
                Log.e(TAG, "Error! Send Image Identify Characteristic Error!");

                // notify clients
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onImageWrite(BLEImageWriteStatus.ERROR, bean.getProgress_percent(), "Characteristic Error");
                        break;
                    }
                }
            } else if (IMAGE_BLOCK_CHAR_UUID.equalsIgnoreCase(uuid)) {
                // 2. Send Image Block Characteristic
                int block_number = ((notify_data[1] & 0xff) << 8) | (notify_data[0] & 0xff);
                // notify
                ImageGenerator imageGenerator = bean.getImageGenerator();
                Log.d(TAG, mac + ": Writing image blocks.... Number: " + (block_number + 1) + " / " + imageGenerator.total_block_number);
                int progress_percent = Math.round(((float) (block_number + 1) / (float) imageGenerator.total_block_number) * 100f);
                bean.setProgress_percent(progress_percent);
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onImageWrite(BLEImageWriteStatus.IN_PROGRESS, progress_percent, "Sending image ...");
                        break;
                    }
                }

                if (block_number > 0) return;
                // start running blocks --- B1
                bean.setRunning_block_number(0);
                writeBlock(bean, 0);

            } else if (IMAGE_STATUS_CHAR_UUID.equalsIgnoreCase(uuid)) {
                // stop task timeout timer
                bean.removeTaskTimeoutChecker();
                bean.setEnd_send_image_time(new Date().getTime());
                long time = bean.getEnd_send_image_time() - bean.getStart_send_image_time();
                Log.d(TAG, "Done! Send Image Done!");
                Log.d(TAG, gatt.getDevice().getAddress() + " Time elapsed: " + time + " ms");
                String data = byteArrayToHexStr(notify_data);
                boolean result = false;
                String message = "";
                switch (data) {
                    case "00": {
                        result = true;
                        message = "Success! Take " + Math.round((double) time / (double) 1000 * 100.0) / 100.0 + " s";
                        break;
                    }
                    case "01": {
                        result = false;
                        message = "CRC Error";
                        break;
                    }
                    case "02": {
                        result = false;
                        message = "Flash Error";
                        break;
                    }
                    case "03": {
                        result = false;
                        message = "Block Overflow";
                        break;
                    }
                    case "04": {
                        result = false;
                        message = "Identify Error";
                        break;
                    }
                    default: {
                        result = false;
                        message = "Unknown Error";
                        break;
                    }
                }
                // notify clients
                int progress_percent = bean.getProgress_percent();
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        if (result) {
                            listener.onImageWrite(BLEImageWriteStatus.FINISH, progress_percent, message);
                        } else {
                            listener.onImageWrite(BLEImageWriteStatus.ERROR, progress_percent, message);
                        }
                        break;
                    }
                }
            } else if (DEVICE_EVENT_CHAR_UUID.equalsIgnoreCase(uuid)) {
                String data = byteArrayToHexStr(notify_data);
                if (notify_data != null && notify_data.length > 1) {
                    byte first = notify_data[0];
                    byte second = notify_data[1];
                    if (first == 0x01) {
                        if (second == 0x00) {
                            // notify clients
                            for (String address : bleConnectListeners.keySet()) {
                                if (address.equals(mac)) {
                                    BLEConnectListener listener = bleConnectListeners.get(address);
                                    listener.onAlarmDetected(false);
                                    break;
                                }
                            }
                        } else if (second == 0x01) {
                            // notify clients
                            for (String address : bleConnectListeners.keySet()) {
                                if (address.equals(mac)) {
                                    BLEConnectListener listener = bleConnectListeners.get(address);
                                    listener.onAlarmDetected(true);
                                    break;
                                }
                            }
                        }
                    } else if (first == 0x02) {
                        byte src = notify_data[1];
                        byte result = notify_data[2];
                        byte page = notify_data[3];
                        Log.d(TAG, "EPD Event Src: " + (src & 0xff) + " Result: " + (result & 0xff) + " Page Numb: " + (page & 0xff));
                        if (src == 0x02) { // refresh
                            if (result == 0x00) {
                                // notify clients
                                for (String address : bleConnectListeners.keySet()) {
                                    if (address.equals(mac)) {
                                        BLEConnectListener listener = bleConnectListeners.get(address);
                                        listener.onImageRefresh(true, (page & 0xff) + 1);
                                        break;
                                    }
                                }
                            } else {
                                // notify clients
                                for (String address : bleConnectListeners.keySet()) {
                                    if (address.equals(mac)) {
                                        BLEConnectListener listener = bleConnectListeners.get(address);
                                        listener.onImageRefresh(false, (page & 0xff) + 1);
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "Unknown alarm value: " + data);
                    }
                }
            }
        }

        /**
         * Descriptor read callback.
         *
         * @param gatt          BluetoothGatt object
         * @param descriptor    BluetoothGattDescriptor object
         * @param status        if success, then return {@link BluetoothGatt#GATT_SUCCESS}
         */
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                     int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        /**
         * Descriptor write callback.
         *
         * @param gatt          BluetoothGatt object
         * @param descriptor    BluetoothGattDescriptor object
         * @param status        if success, then return {@link BluetoothGatt#GATT_SUCCESS}
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            String mac = gatt.getDevice().getAddress();

            BLEDeviceBean bean = connectionQueue.get(mac);
            if (bean == null) return;

            Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (IMAGE_DESCRIPTOR_UUID.equalsIgnoreCase(descriptor.getUuid().toString())) {
                    BluetoothGattCharacteristic gattCharacteristic = descriptor.getCharacteristic();
                    if (IMAGE_ID_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.d(TAG, mac + " Enable Image Identify notify success");
                        bean.addDescCounter(1);

                        // Descriptor 2. Enable Image Block notify ......
                        if (characteristicMap != null) {
                            BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(IMAGE_BLOCK_CHAR_UUID);
                            enableNotification(gatt, gattCharacteristic1, UUID.fromString(IMAGE_DESCRIPTOR_UUID), true);
                        }
                    } else if (IMAGE_BLOCK_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.d(TAG, mac + " Enable Image Block notify success");
                        bean.addDescCounter(1);

                        // Descriptor 3. Enable Image Status notify ......
                        if (characteristicMap != null) {
                            BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(IMAGE_STATUS_CHAR_UUID);
                            enableNotification(gatt, gattCharacteristic1, UUID.fromString(IMAGE_DESCRIPTOR_UUID), true);
                        }
                    } else if (IMAGE_STATUS_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.d(TAG, mac + " Enable Image Status notify success");
                        bean.addDescCounter(1);

                        // Descriptor 4. Enable Device event notify ......
                        if (characteristicMap != null) {
                            BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(DEVICE_EVENT_CHAR_UUID);
                            enableNotification(gatt, gattCharacteristic1, UUID.fromString(IMAGE_DESCRIPTOR_UUID), true);
                        }
                    } else if (DEVICE_EVENT_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.d(TAG, mac + " Enable Device event notify success");
                        bean.addDescCounter(1);

                        if (bean.getDescCounter() == 4) {
                            // Step 4. Read Firmware
                            readFirmware(gatt);
                        }
                    }
                }
            }
        }

        /**
         * Reliable write completed callback.
         *
         * @param gatt
         * @param status
         */
        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        /**
         * Read remote rssi callback.
         *
         * @param gatt
         * @param rssi
         * @param status
         */
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        /**
         * MTU changed callback.
         *
         * @param gatt
         * @param mtu
         * @param status
         */
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            String mac = gatt.getDevice().getAddress();
            Log.d(TAG, mac + " MTU Changed " + mtu);
            // Step 3. start reading sequences
            readStatusSequence(gatt);
        }
    };

    private void writeBlock(BLEDeviceBean bean, int block_number) {
        ImageGenerator imageGenerator = bean.getImageGenerator();
        byte[] notify_data = new byte[]{(byte) block_number, (byte) (block_number >> 8)};
        byte[] data = Arrays.copyOfRange(imageGenerator.getImageData(), IMAGE_HEADER_LEN + block_number * imageGenerator.BLOCK_LEN,
                IMAGE_HEADER_LEN + (block_number * imageGenerator.BLOCK_LEN + imageGenerator.BLOCK_LEN));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(notify_data, 0, notify_data.length);
            outputStream.write(data, 0, data.length);
            byte[] sendData = outputStream.toByteArray();
            outputStream.flush();
            outputStream.close();

            Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
            BluetoothGattCharacteristic gattCharacteristic = characteristicMap.get(IMAGE_BLOCK_CHAR_UUID);
            Boolean result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, sendData);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // enable Image descriptors -> read firmware version -> read LED status
    private void readStatusSequence(BluetoothGatt gatt) {
        enableImageDescriptors(gatt);
    }

    // Enable image descriptors
    private boolean enableImageDescriptors(BluetoothGatt gatt) {
        boolean result = false;
        // Descriptor 1. Enable Image Identify notify ......
        String mac = gatt.getDevice().getAddress();
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
        if (characteristicMap != null) {
            BluetoothGattCharacteristic gattCharacteristic = characteristicMap.get(IMAGE_ID_CHAR_UUID);
            if (gattCharacteristic != null) {
                result = enableNotification(gatt, gattCharacteristic, UUID.fromString(IMAGE_DESCRIPTOR_UUID), true);
            }
        }
        return result;
    }

    // Read Firmware Version
    private boolean readFirmware(BluetoothGatt gatt) {
        boolean result = false;
        String mac = gatt.getDevice().getAddress();
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
        if (characteristicMap != null) {
            BluetoothGattCharacteristic gattCharacteristic = characteristicMap.get(FIRMWARE_CHAR_UUID);
            if (gattCharacteristic != null) {
                result = gatt.readCharacteristic(gattCharacteristic);
            }
        }
        return result;
    }

    /**
     * Read firmware version for a device.
     * Firmware read result returns in the connection listener.
     * @see BLEConnectListener#onFirmwareRead(int, byte[])
     *
     * @param mac       device mac address
     * @return          {@code true} the command was send successfully;
     *                  {@code false} device has not been connected or read characteristic failure
     */
    public boolean readFirmware(String mac) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        BluetoothGatt gatt = bean.getBluetoothGatt();
        Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
        if (characteristicMap != null) {
            BluetoothGattCharacteristic gattCharacteristic = characteristicMap.get(FIRMWARE_CHAR_UUID);
            if (gattCharacteristic != null) {
                result = gatt.readCharacteristic(gattCharacteristic);
            }
        }
        return result;
    }

    // Read LED Status
    private boolean readLEDStatus(BluetoothGatt gatt) {
        boolean result = false;
        String mac = gatt.getDevice().getAddress();
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
        if (characteristicMap != null) {
            BluetoothGattCharacteristic gattCharacteristic = characteristicMap.get(LED_CHAR_UUID);
            if (gattCharacteristic != null) {
                result = gatt.readCharacteristic(gattCharacteristic);
            }
        }
        return result;
    }

    /**
     * Read LED status for a device.
     * LED read result returns in the connection listener.
     * @see BLEConnectListener#onLEDRead(int, byte[])
     *
     * @param mac       device mac address
     * @return          {@code true} the command was send successfully;
     *                  {@code false} device has not been connected or read characteristic failure
     */
    public boolean readLEDStatus(String mac) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        BluetoothGatt gatt = bean.getBluetoothGatt();
        Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
        if (characteristicMap != null) {
            BluetoothGattCharacteristic gattCharacteristic = characteristicMap.get(LED_CHAR_UUID);
            if (gattCharacteristic != null) {
                result = gatt.readCharacteristic(gattCharacteristic);
            }
        }
        return result;
    }

    /**
     * Change BLE connection priority for a device.
     *
     * @param mac           device mac address
     * @param priority      Request a specific connection priority. Must be one of {@link
     *                      BluetoothGatt#CONNECTION_PRIORITY_BALANCED}, {@link
     *                      BluetoothGatt#CONNECTION_PRIORITY_HIGH} or {@link
     *                      BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}.
     *
     * @return              {@code true} the command was send successfully;
     *                      {@code false} otherwise
     */
    public boolean changeConnectionPriority(String mac, int priority) {
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return false;
        return bean.getBluetoothGatt().requestConnectionPriority(priority);
    }

    /**
     * Open or close LED Lights for a device.
     * Write LED Lights result will return in the connection listener.
     * @see BLEConnectListener#onLEDWrite(int, byte[])
     *
     * @param mac       device mac address
     * @param led1      open {@code true} or close {@code false} the LED light 1
     * @param led2      open {@code true} or close {@code false} the LED light 2
     * @param led3      open {@code true} or close {@code false} the LED light 3
     * @return          {@code true} the command was send successfully;
     *                  {@code false} device has not been connected or write characteristic failure
     */
    public boolean writeLED(String mac, boolean led1, boolean led2, boolean led3) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        Map<String, BluetoothGattCharacteristic> charMap = bean.getCharMap();
        BluetoothGattCharacteristic gattCharacteristic = charMap.get(LED_CHAR_UUID);
        if (gattCharacteristic != null) {
            int led1_bit = (led1) ? 0b11 : 0b10;
            int led2_bit = (led2) ? 0b11 : 0b10;
            int led3_bit = (led3) ? 0b11 : 0b10;
            int led = led3_bit << 4 | led2_bit << 2 | led1_bit;
            byte[] data = new byte[]{(byte) (led & 0xff)};
            result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, data);
        }
        return result;
    }

    /**
     * Open or close LED Light 1 for a device.
     * Write LED Lights result will return in the connection listener.
     * @see BLEConnectListener#onLEDWrite(int, byte[])
     *
     * @param mac       device mac address
     * @param open      open {@code true} or close {@code false} the LED light 1
     * @return          {@code true} the command was send successfully;
     *                  {@code false} device has not been connected or write characteristic failure
     */
    public boolean writeLED1(String mac, boolean open) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        Map<String, BluetoothGattCharacteristic> charMap = bean.getCharMap();
        BluetoothGattCharacteristic gattCharacteristic = charMap.get(LED_CHAR_UUID);
        if (gattCharacteristic != null) {
            if (open)
                result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, new byte[]{0x03});
            else
                result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, new byte[]{0x02});
        }
        return result;
    }

    /**
     * Open or close LED Light 2 for a device.
     * Write LED Lights result will return in the connection listener.
     * @see BLEConnectListener#onLEDWrite(int, byte[])
     *
     * @param mac       device mac address
     * @param open      open {@code true} or close {@code false} the LED light 2
     * @return          {@code true} the command was send successfully;
     *                  {@code false} device has not been connected or write characteristic failure
     */
    public boolean writeLED2(String mac, boolean open) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        Map<String, BluetoothGattCharacteristic> charMap = bean.getCharMap();
        BluetoothGattCharacteristic gattCharacteristic = charMap.get(LED_CHAR_UUID);
        if (gattCharacteristic != null) {
            if (open)
                result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, new byte[]{0x0c});

            else
                result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, new byte[]{0x08});
        }
        return result;
    }

    /**
     * Open or close LED Light 3 for a device.
     * Write LED Lights result will return in the connection listener.
     * @see BLEConnectListener#onLEDWrite(int, byte[])
     *
     * @param mac       device mac address
     * @param open      open {@code true} or close {@code false} the LED light 3
     * @return          {@code true} the command was send successfully;
     *                  {@code false} device has not been connected or write characteristic failure
     */
    public boolean writeLED3(String mac, boolean open) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        Map<String, BluetoothGattCharacteristic> charMap = bean.getCharMap();
        BluetoothGattCharacteristic gattCharacteristic = charMap.get(LED_CHAR_UUID);
        if (gattCharacteristic != null) {
            if (open)
                result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, new byte[]{0x30});
            else
                result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic, new byte[]{0x20});
        }
        return result;
    }

    /**
     * Push image to the EPD device.
     *
     * @param mac           device mac address
     * @param panelType     EPD panel-type {@see PanelType}
     * @param bitmap        image in the bitmap format which is ready to transmit; please resize image size to fit each EPD panel-type {@see PanelType}
     * @param image_page    which page {@code number} you want to transmit image on the EPD device; this number must be larger than 0
     * @param image_action  refresh this image immediately {@code 1} or not {@code 0}
     * @return              {@code true} send this command successfully;
     *                      {@code false} device is not connected or an existing task is still running
     */
    public boolean pushImage(String mac, PanelType panelType, Bitmap bitmap, int image_page, int image_action) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        ImageGenerator imageGenerator = new ImageGenerator(TaskType.PUSH_IMAGE, panelType, bitmap, image_page, image_action);
        if (!bean.isImageWriting() && imageGenerator.isValid()) {
            bean.setDescCounter(0);
            bean.setProgress_percent(0);
            bean.setImageGenerator(imageGenerator);

            result = bean.startTaskTimeoutChecker();
            if (result) {
                // notify clients
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onImageWrite(BLEImageWriteStatus.START, 0, "Start sending command");
                        break;
                    }
                }

                // start push image and count the timer
                bean.setStart_send_image_time(new Date().getTime());
                // 1. Send Image Identify Characteristic
                result = imageGenerator.executeTask();
                if (result) {
                    Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
                    BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(IMAGE_ID_CHAR_UUID);
                    result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic1, Arrays.copyOfRange(imageGenerator.getImageData(), 0, imageGenerator.IMAGE_HEADER_LEN));
                    Log.d(TAG, "Send result: " + result);
                }
            }
        }
        return result;
    }

    /**
     * Firmware upgrade to the EPD device.
     *
     * @param mac           device mac address
     * @param packageData   package data in the byte array format
     * @return              {@code true} send this command successfully;
     *                      {@code false} device is not connected or an existing task is still running
     */
    public boolean firmwareUpgrade(String mac, byte[] packageData) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        if (bean == null) return result;

        ImageGenerator imageGenerator = new ImageGenerator(TaskType.FIRMWARE_UPGRADE, packageData);
        if (!bean.isImageWriting() && imageGenerator.isValid()) {
            bean.setDescCounter(0);
            bean.setProgress_percent(0);
            bean.setImageGenerator(imageGenerator);

            result = bean.startTaskTimeoutChecker();
            if (result) {
                // notify clients
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onImageWrite(BLEImageWriteStatus.START, 0, "Start sending command");
                        break;
                    }
                }

                // 開始推圖並計時
                bean.setStart_send_image_time(new Date().getTime());
                // 1. Send Image Identify Characteristic
                result = imageGenerator.executeTask();
                if (result) {
                    Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
                    BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(IMAGE_ID_CHAR_UUID);
                    result = writeCharacteristic(bean.getBluetoothGatt(), gattCharacteristic1, Arrays.copyOfRange(imageGenerator.getImageData(), 0, imageGenerator.IMAGE_HEADER_LEN));
                    Log.d(TAG, "Send result: " + result);
                }
            }
        }
        return result;
    }

    private boolean writeCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic gattCharacteristic, byte[] data) {
        boolean result = false;
        if (gattCharacteristic != null) {
            gattCharacteristic.setValue(data);
            gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            result = gatt.writeCharacteristic(gattCharacteristic);
        }
        return result;
    }

    private boolean enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic gattCharacteristic, UUID descripterUUID, boolean enable) {
        boolean result = false;
        if (gattCharacteristic != null) {
            // Enable Local Notification
            gatt.setCharacteristicNotification(gattCharacteristic, enable);

            // Enable Remote Notification
            BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(descripterUUID);
            if (enable)
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            else
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            result = gatt.writeDescriptor(descriptor);
        }
        return result;
    }

    /**
     * Check whether the Bluetooth utility is ready or not.
     *
     * @return      {@code true} the Bluetooth utility is ready to use;
     *              {@code false} otherwise
     */
    public boolean isValid() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Check whether the Bluetooth utility is scanning or not.
     *
     * @return      {@code true} the Bluetooth utility is scanning;
     *              {@code false} otherwise.
     */
    public boolean isScanning() {
        return isScanning;
    }

    /**
     * Add a scan listener which will returns the scanning results.
     *
     * @param bleScanListener      BLE scanning listener
     */
    public void addScanListener(BLEScanListener bleScanListener) {
        bleScanListeners.add(bleScanListener);
    }

    /**
     * Remove a scan listener and afterward you will no longer receive the scanning results.
     *
     * @param bleScanListener
     */
    public void removeScanListener(BLEScanListener bleScanListener) {
        bleScanListeners.remove(bleScanListener);
    }

    /**
     * Add a connection listener which will returns the connection results.
     *
     * @param address               device mac address
     * @param bleConnectListener    BLE connection listener
     */
    public void addConnectListener(String address, BLEConnectListener bleConnectListener) {
        bleConnectListeners.put(address, bleConnectListener);
    }

    /**
     * Remove a connection listener according to the device mac address.
     *
     * @param address               device mac address
     */
    public void removeConnectListener(String address) {
        bleConnectListeners.remove(address);
    }

}
