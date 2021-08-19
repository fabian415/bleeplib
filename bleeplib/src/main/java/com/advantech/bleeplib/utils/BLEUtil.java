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
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.advantech.bleeplib.bean.PanelType;
import com.advantech.bleeplib.bean.TaskType;
import com.advantech.bleeplib.image.ImageGenerator;
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;

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
import static com.advantech.bleeplib.common.Common.byteArrayToHexStr;

public class BLEUtil {
    private static String TAG = "BLEUtil";
    private static BLEUtil instance = null;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private List<BLEScanListener> bleScanListeners = new ArrayList<>();
    private Map<String, BLEDeviceBean> connectionQueue = new ConcurrentHashMap<>();
    private Map<String, BLEConnectListener> bleConnectListeners = new ConcurrentHashMap<>(); // mac, listener
    private ConcurrentLinkedQueue<String> waitingQueue = new ConcurrentLinkedQueue<>(); // 最多六條同時連線，其餘放入 waitingList
    public static final int MAX_CONNECTION_NUMBER = 4; // LG X9009 最多到四條連線同時
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
    public static final int IMAGE_HEADER_LEN = 32;

    private BLEUtil() {
    }

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

    public boolean initial(Context context) {
        this.context = context;
        bluetoothManager = (BluetoothManager) context.getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        mHandler = new Handler();
        return bluetoothAdapter != null;
    }

    public boolean isValid() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void startScan(int scanTime) {
        if (isScanning) return;
        if (scanTime > -1) { // 如果 scanTime > -1，則設定倒數
            // 啟動一個 Handler，並使用 postDelayed 在 scanTime 秒後自動執行此 Runnable()
            mHandler.postDelayed(myRunnable, scanTime * 1000);
        }
        // 開始搜尋 BLE 設備
        bluetoothAdapter.startLeScan(leScanCallback);
        isScanning = true;
        Log.d(TAG, "Start Scan");
        // notify each listener
        for (BLEScanListener bleScanListener : bleScanListeners) {
            bleScanListener.onScanStatusChanged(false);
        }
    }

    public void stopScan() {
        // 停止搜尋
        bluetoothAdapter.stopLeScan(leScanCallback);
        isScanning = false;
        Log.d(TAG, "Stop Scan");
        mHandler.removeCallbacks(myRunnable);
        // notify each listener
        for (BLEScanListener bleScanListener : bleScanListeners) {
            bleScanListener.onScanStatusChanged(false);
        }
    }

    private Runnable myRunnable = new Runnable() {
        @Override
        public void run() {
            stopScan();
        }
    };


    // 建立一個 BLAdapter 的 Callback，當使用 startLeScan 或 stopLeScan 時，每搜尋到一次設備都會跳到此 callback
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            // notify each listener
            for (BLEScanListener bleScanListener : bleScanListeners) {
                bleScanListener.onLeScan(device, rssi, scanRecord);
            }
        }
    };

    // scan record parser
    public byte[] parseManufacturerData(byte[] scanRecord) {
        byte[] result = null;
        List<ADStructure> structures = ADPayloadParser.getInstance().parse(scanRecord);
        for (ADStructure structure : structures) {
            byte type = Integer.valueOf(structure.getType()).byteValue();
            byte[] data = structure.getData();
            if (type == (byte) 0xFF) { // Manufacturer Specific Data
                result = data;
                break;
            }
        }
        return result;
    }

    public synchronized void connect(String address) {
        BLEDeviceBean bean = connectionQueue.get(address);
        if (bean == null) {
            // 超過最大上限，則先存入 waitingQueue 中
            if (connectionQueue.size() >= MAX_CONNECTION_NUMBER) {
                waitingQueue.offer(address); // 放入
                return;
            }

            // 根據地址獲取設備
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            // 獲取鏈接 這個時候需要實現 BluetoothGattCallback
            BluetoothGatt bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback);
            BLEDeviceBean bleDeviceBean = new BLEDeviceBean(address, bluetoothGatt, new BLEDeviceBeanTimeoutCallback() {
                @Override
                public void onTaskTimeout(int progress) {
                    // notify user
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
                    // notify user
                    for (String mac : bleConnectListeners.keySet()) {
                        if (mac.equals(address)) {
                            BLEConnectListener listener = bleConnectListeners.get(address);
                            listener.onConnectionTimeout("Connect Timeout");
                            break;
                        }
                    }
                }
            });
            // 開啟連線 timer
            boolean result = bleDeviceBean.startConnTimeoutChecker();
            connectionQueue.put(address, bleDeviceBean);
            Log.e(TAG, "====== Connection Number (add " +address+ "): " + connectionQueue.size());
        } else {
            Log.e(TAG, "====== Connection Existed!!! ( " +address+ " )");
        }
    }

    // When multiple devices are disconnected, they need to be removed one by one.
    // This is different from when a single device is disconnected.
    public synchronized void disconnect(String address) {
        BLEDeviceBean bean = connectionQueue.get(address);
        if (bean != null) {
            BluetoothGatt bluetoothGatt = bean.getBluetoothGatt();
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
//                bluetoothGatt.close();

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(1000);
                            connectionQueue.remove(address);
                            bleConnectListeners.remove(address);
                            Log.e(TAG, "====== Connection Number (remove " +address+ "): " + connectionQueue.size());
                            // 詢問 waitingQueue 上是否有等待清單？
                            if (waitingQueue.size() > 0) {
                                String nextMac = waitingQueue.poll(); // 取出
                                // 解決問題 Can't create handler inside thread Thread[Thread-80,5,main] that has not called Looper.prepare()
                                Looper.prepare(); // *
                                connect(nextMac); // 這裡面有 handler
                                Looper.loop(); // * 這種情形下，Runnable 物件是運行在子執行緒的，可以進行長時間的聯網操作，但不能更新 UI
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
            // 停掉 Timeout Timer
            bean.removeTaskTimeoutChecker();
            bean.removeConnTimeoutChecker();
        }
    }

    public synchronized void reconnect(String address) {
        BLEDeviceBean bean = connectionQueue.get(address);
        if (bean != null) {
            BluetoothGatt bluetoothGatt = bean.getBluetoothGatt();
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
//                bluetoothGatt.close();

                new Thread(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(1000);
                            connectionQueue.remove(address);
                            Log.e(TAG, "====== Connection Number: " + connectionQueue.size());
                            // 解決問題 Can't create handler inside thread Thread[Thread-80,5,main] that has not called Looper.prepare()
                            Looper.prepare(); // *
                            connect(address); // 這裡面有 handler
                            Looper.loop(); // * 這種情形下，Runnable 物件是運行在子執行緒的，可以進行長時間的聯網操作，但不能更新 UI
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
            // 停掉 Timeout Timer
            bean.removeTaskTimeoutChecker();
            bean.removeConnTimeoutChecker();
        }
    }


    BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        /**
         * 返回鏈接狀態
         * @param gatt
         * @param status 鏈接或者斷開連接是否成功 {@link BluetoothGatt#GATT_SUCCESS}
         * @param newState 返回一個新的狀態{@link BluetoothProfile#STATE_DISCONNECTED} or
         *                                  {@link BluetoothProfile#STATE_CONNECTED}
         */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String mac = gatt.getDevice().getAddress();
            BLEDeviceBean bean = connectionQueue.get(mac);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.e(TAG, mac + " Connected");
                // 連接成功！主動發現遠程設備提供的服務，以及它們包含的特征特性和描述符等
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, mac + " Disconnected");
                // 斷線時，主動停掉 Timeout Timer
                bean.removeTaskTimeoutChecker();
                bean.removeConnTimeoutChecker();

                // 確定收到 disconnect 後才關閉 gatt 資源，Issue 183108: https://code.google.com/p/android/issues/detail?id=183108
                try {
                    gatt.close();
                } catch (Exception e) {
                    Log.e(TAG, "close ignoring: " + e);
                }
            }
            // notify
            for (String address : bleConnectListeners.keySet()) {
                if (address.equals(mac)) {
                    BLEConnectListener listener = bleConnectListeners.get(address);
                    listener.onConnectionStateChange(newState);
                    break;
                }
            }
        }

        /**
         *  獲取到鏈接設備的GATT服務時的回調
         * @param gatt
         * @param status 成功返回{@link BluetoothGatt#GATT_SUCCESS}
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            String mac = gatt.getDevice().getAddress();
            if (BluetoothGatt.GATT_SUCCESS == status) {
                Log.e(TAG, mac + " Service Discovery...");
                Map<String, BluetoothGattCharacteristic> characteristicMap = new HashMap<>();
                List<BluetoothGattService> gattServices = gatt.getServices();
                // 獲取服務
                for (BluetoothGattService gattService : gattServices) {
                    // 獲取每個服務中包含的特征
                    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                        String uuid = gattCharacteristic.getUuid().toString();
                        if (uuid == null) continue;
                        uuid = uuid.toUpperCase();
                        // 將重要的 characteristic，先存入 HashMap
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
                bean.setCharMap(characteristicMap);

                // 設定 MTU Level
                boolean result = gatt.requestMtu(BLE_MTU);
            } else {
                Log.e(TAG, mac + " Service Discovery Error");
            }

            // notify
            for (String address : bleConnectListeners.keySet()) {
                if (address.equals(mac)) {
                    BLEConnectListener listener = bleConnectListeners.get(address);
                    listener.onServicesDiscovered(status, gatt);
                    break;
                }
            }
        }

        /**
         * 讀特征的時候的回調
         * @param gatt
         * @param characteristic 從相關設備上面讀取到的特征值
         * @param status
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            String mac = gatt.getDevice().getAddress();
            byte[] read = characteristic.getValue();

            if (LED_CHAR_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                Log.d(TAG, mac + " LED Read: 0x" + byteArrayToHexStr(read));
                // notify
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onLEDRead(status, read);
                        break;
                    }
                }
                // 停止連線 timer
                BLEDeviceBean bean = connectionQueue.get(mac);
                bean.removeConnTimeoutChecker();
            } else if (FIRMWARE_CHAR_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                try {
                    Log.d(TAG, mac + " Firmware Read: " + new String(read, "UTF-8"));
                } catch (Exception e) {
                }
                // notify
                for (String address : bleConnectListeners.keySet()) {
                    if (address.equals(mac)) {
                        BLEConnectListener listener = bleConnectListeners.get(address);
                        listener.onFirmwareRead(status, read);
                        break;
                    }
                }
                readLEDStatus(gatt);
            }
        }

        /**
         * 指定特征寫入操作的回調結果
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            String mac = gatt.getDevice().getAddress();
            byte[] read = characteristic.getValue();
            String data = byteArrayToHexStr(read);
            BLEDeviceBean bean = connectionQueue.get(mac);

//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.e(TAG, gatt.getDevice().getAddress() + " 寫入成功: " + data);
//            }

            if (LED_CHAR_UUID.equalsIgnoreCase(characteristic.getUuid().toString())) {
                // notify
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
                    // continue running blocks --- Fabian
                    writeBlock(bean, running_block_number);
                }
            }
        }

        /**
         * 設備發出通知時會調用到該接口
         * @param gatt
         * @param characteristic
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String mac = gatt.getDevice().getAddress();
            String uuid = characteristic.getUuid().toString();
            byte[] notify_data = characteristic.getValue();
            BLEDeviceBean bean = connectionQueue.get(mac);

//            Log.d(TAG, gatt.getDevice().getAddress() + " 特徵值改變：" + uuid + " 回應：" + byteArrayToHexStr(notify_data));
            if (IMAGE_ID_CHAR_UUID.equalsIgnoreCase(uuid)) {
                // 停掉 Timeout Timer
                bean.removeTaskTimeoutChecker();
                Log.e(TAG, "Error! Send Image Identify Characteristic Error!");

                // notify
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
                // start running blocks --- Fabian
                bean.setRunning_block_number(0);
                writeBlock(bean, 0);

            } else if (IMAGE_STATUS_CHAR_UUID.equalsIgnoreCase(uuid)) {
                // 停掉 Timeout Timer
                bean.removeTaskTimeoutChecker();
                bean.setEnd_send_image_time(new Date().getTime());
                long time = bean.getEnd_send_image_time() - bean.getStart_send_image_time();
                Log.d(TAG, "Done! Send Image Done!");
                Log.e(TAG, gatt.getDevice().getAddress() + " Time elapsed: " + time + " ms");
                // notify
                String data = byteArrayToHexStr(notify_data);
                boolean result = false;
                String message = "";
                switch (data) {
                    case "00": {
                        result = true;
//                        message = "Success";
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
                // notify
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
                            // notify
                            for (String address : bleConnectListeners.keySet()) {
                                if (address.equals(mac)) {
                                    BLEConnectListener listener = bleConnectListeners.get(address);
                                    listener.onAlarmDetected(false);
                                    break;
                                }
                            }
                        } else if (second == 0x01) {
                            // notify
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
                                // notify
                                for (String address : bleConnectListeners.keySet()) {
                                    if (address.equals(mac)) {
                                        BLEConnectListener listener = bleConnectListeners.get(address);
                                        listener.onImageRefresh(true, (page & 0xff) + 1);
                                        break;
                                    }
                                }
                            } else {
                                // notify
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
         * 指定描述符的讀操作的回調
         * @param gatt
         * @param descriptor
         * @param status
         */
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                     int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "讀取描述符: " + descriptor);
        }

        /**
         * 指定描述符的寫操作
         * @param gatt
         * @param descriptor
         * @param status
         */
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                      int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            String mac = gatt.getDevice().getAddress();

            BLEDeviceBean bean = connectionQueue.get(mac);
            Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String uuid = descriptor.getUuid().toString();
                if (IMAGE_DESCRIPTOR_UUID.equalsIgnoreCase(descriptor.getUuid().toString())) {
                    BluetoothGattCharacteristic gattCharacteristic = descriptor.getCharacteristic();
                    if (IMAGE_ID_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.e(TAG, mac + " Enable Image Identify notify success");
                        bean.addDescCounter(1);

                        // 2. Enable Image Block notify ......
                        if (characteristicMap != null) {
                            BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(IMAGE_BLOCK_CHAR_UUID);
                            enableNotification(gatt, gattCharacteristic1, UUID.fromString(IMAGE_DESCRIPTOR_UUID), true);
                        }
                    } else if (IMAGE_BLOCK_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.d(TAG, mac + " Enable Image Block notify success");
                        bean.addDescCounter(1);

                        // 3. Enable Image Status notify ......
                        if (characteristicMap != null) {
                            BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(IMAGE_STATUS_CHAR_UUID);
                            enableNotification(gatt, gattCharacteristic1, UUID.fromString(IMAGE_DESCRIPTOR_UUID), true);
                        }
                    } else if (IMAGE_STATUS_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.d(TAG, mac + " Enable Image Status notify success");
                        bean.addDescCounter(1);

                        // 4. Enable Device event notify ......
                        if (characteristicMap != null) {
                            BluetoothGattCharacteristic gattCharacteristic1 = characteristicMap.get(DEVICE_EVENT_CHAR_UUID);
                            enableNotification(gatt, gattCharacteristic1, UUID.fromString(IMAGE_DESCRIPTOR_UUID), true);
                        }
                    } else if (DEVICE_EVENT_CHAR_UUID.equalsIgnoreCase(gattCharacteristic.getUuid().toString())) {
                        Log.d(TAG, mac + " Enable Device event notify success");
                        bean.addDescCounter(1);

                        if (bean.getDescCounter() == 4) {
                            // Read Firmware
                            readFirmware(gatt);
                        }
                    }
                }
            }
        }

        /**
         * 當一個寫入事物完成時的回調
         * @param gatt
         * @param status
         */
        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.d(TAG, "寫入事物完成時的回調: ");

        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            String mac = gatt.getDevice().getAddress();
            Log.e(TAG, mac + " MTU Changed " + mtu);
            // 開始讀取狀態
            readStatusSequence(gatt);
        }

//        public void onConnectionUpdated(BluetoothGatt gatt, int interval, int latency, int timeout, int status) {
//            Log.e(TAG, gatt.getDevice().getAddress() + " -- interval: " + interval + " latency: " + latency + " timeout: " + timeout + " status: " + status);
//        }
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

    // 開始讀取狀態序列
    // enable Image descriptors -> read firmware version -> read LED status
    public void readStatusSequence(BluetoothGatt gatt) {
        enableImageDescriptors(gatt);
    }

    // Enable image descriptors
    public boolean enableImageDescriptors(BluetoothGatt gatt) {
        boolean result = false;
        // 1. Enable Image Identify notify ......
        String mac = gatt.getDevice().getAddress();
        BLEDeviceBean bean = connectionQueue.get(mac);
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
    public boolean readFirmware(BluetoothGatt gatt) {
        boolean result = false;
        String mac = gatt.getDevice().getAddress();
        BLEDeviceBean bean = connectionQueue.get(mac);
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
    public boolean readLEDStatus(BluetoothGatt gatt) {
        boolean result = false;
        String mac = gatt.getDevice().getAddress();
        BLEDeviceBean bean = connectionQueue.get(mac);
        Map<String, BluetoothGattCharacteristic> characteristicMap = bean.getCharMap();
        if (characteristicMap != null) {
            BluetoothGattCharacteristic gattCharacteristic = characteristicMap.get(LED_CHAR_UUID);
            if (gattCharacteristic != null) {
                result = gatt.readCharacteristic(gattCharacteristic);
            }
        }
        return result;
    }

    // Change Connection Priority
    public boolean changeConnectionPriority(String mac, int priority) {
        BLEDeviceBean bean = connectionQueue.get(mac);
        return bean.getBluetoothGatt().requestConnectionPriority(priority);
    }

    // Write LED Lights
    public boolean writeLED(String mac, boolean led1, boolean led2, boolean led3) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
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

    public boolean writeLED1(String mac, boolean open) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
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

    public boolean writeLED2(String mac, boolean open) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
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

    public boolean writeLED3(String mac, boolean open) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
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

    public boolean pushImage(String mac, PanelType panelType, Bitmap bitmap, int image_page, int image_action) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        ImageGenerator imageGenerator = new ImageGenerator(TaskType.PUSH_IMAGE, panelType, bitmap, image_page, image_action);
        if (!bean.isImageWriting() && imageGenerator.isValid()) {
            bean.setDescCounter(0);
            bean.setProgress_percent(0);
            bean.setImageGenerator(imageGenerator);

            result = bean.startTaskTimeoutChecker();
            if (result) {
                // notify
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

    public boolean firmwareUpgrade(String mac, byte[] packageData) {
        boolean result = false;
        BLEDeviceBean bean = connectionQueue.get(mac);
        ImageGenerator imageGenerator = new ImageGenerator(TaskType.FIRMWARE_UPGRADE, packageData);
        if (!bean.isImageWriting() && imageGenerator.isValid()) {
            bean.setDescCounter(0);
            bean.setProgress_percent(0);
            bean.setImageGenerator(imageGenerator);

            result = bean.startTaskTimeoutChecker();
            if (result) {
                // notify
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

    public boolean writeCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic gattCharacteristic, byte[] data) {
        boolean result = false;
        if (gattCharacteristic != null) {
            gattCharacteristic.setValue(data);
            result = gatt.writeCharacteristic(gattCharacteristic);
        }
        return result;
    }

    public boolean enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic gattCharacteristic, UUID descripterUUID, boolean enable) {
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


    // Getter and Setter
    public boolean isScanning() {
        return isScanning;
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public void addScanListener(BLEScanListener bleScanListener) {
        bleScanListeners.add(bleScanListener);
    }

    public void removeScanListener(BLEScanListener bleScanListener) {
        bleScanListeners.remove(bleScanListener);
    }

    public List<BLEScanListener> getScanListeners() {
        return bleScanListeners;
    }

    public void addConnectListener(String address, BLEConnectListener bleConnectListener) {
        bleConnectListeners.put(address, bleConnectListener);
    }

    public void removeConnectListener(String address) {
        bleConnectListeners.remove(address);
    }

}
