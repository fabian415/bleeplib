# bleeplib

### Overview
This is an Android BLE library which can communicate with Advantech ePaper (EPD) devices.

### Gradle
**Step 1.** Add the JitPack repository to your build file. 
Add it in your root build.gradle at the end of repositories:
```java
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the dependency. 
[Tag] must be replaced as the specific version, such as [1.1.0].
```java
dependencies {
    implementation 'com.github.fabian415:bleeplib:Tag'
}
```

### Maven
**Step 1.** Add the JitPack repository to your build file.
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**Step 2.** Add the dependency. 
[Tag] must be replaced as the specific version, such as [1.1.0].
```xml
<dependency>
    <groupId>com.github.fabian415</groupId>
    <artifactId>bleeplib</artifactId>
    <version>Tag</version>
</dependency>
```

### JavaDoc
https://fabian415.github.io/bleeplib/

### Source Code
You can download this project for basic usage
https://github.com/fabian415/bleeplib.git

### Basic Usage 
**IDE tool:** Android Studio

**Language:** Java

**Device:** Advantech EPD-250/EPD-252/EPD-353

**Index:**
- Step 1 ~ 6. **BLEUtil** initilization and BLE scan
- Step 7 ~ 8. Connect to a device using **BLEUtil**
- Step 9. Send LED command
- Step 10. Push image command
- Step 11. Bulk push images by **BLETaskHandler**

**Step 1.** In your **Manifest.xml**, you must add the user-permission for the usage of bluetooth resource.

Manifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.advantech.bleep">

    <!--Bluetooth management-->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.BLEeP">
        ...
    </application>

</manifest>
```

**Step 2.** On the **onCreate** method of **MainActivity**, ask the permissions about access files and location from users and initial the **BLEUtil** utility using Activity context.

MainActivity.java
```java
...
private BLEUtil bleUtil = BLEUtil.getInstance();
...

@Override
protected void onCreate(Bundle savedInstanceState) {
    ...
    context = this;

    // Ask permissions for file locations
    askPermissions();

    // BLE initial
    if (!bleUtil.initial(context)) {
        Toast.makeText(getBaseContext(), "Not Support Bluetooth!", Toast.LENGTH_SHORT).show();
        finish();
        return;
    }
    ...
}


// Ask permissions from user
private void askPermissions() {
    String[] permissions = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    };

    Set<String> permissionRequest = new HashSet<>();
    for (String permission : permissions) {
        int result = ContextCompat.checkSelfPermission(this, permission);
        if (result != PackageManager.PERMISSION_GRANTED) {
            permissionRequest.add(permission);
        }
    }
    if (!permissionRequest.isEmpty()) {
        ActivityCompat.requestPermissions(this, permissionRequest.toArray(new String[permissionRequest.size()]), REQUEST_PERMISSIONS);
    }
}

// Ask permissions callback
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    switch (requestCode) {
        case REQUEST_PERMISSIONS:
            String text = "";
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    text += permissions[i] + "\n";
                }
            }
            if (!text.isEmpty()) {
                Toast.makeText(this, "Permission for access files and location should be granted!", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
    }
}
```

**Step 3.** On the **onCreate** method of **MainActivity**, register a BLE scanning listener after the BLEUtil utility initialization.

MainActivity.java
```java
...
private BLEUtil bleUtil = BLEUtil.getInstance();
...

@Override
protected void onCreate(Bundle savedInstanceState) {
    ...
    // BLE scanning callback listener
    bleUtil.addScanListener(bleScanListener);
}

...

private BLEScanListener bleScanListener = new BLEScanListener() {
    
    // Scan results
    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        // Remove the non-Advantech devices
        if (device.getName() == null || device.getName().indexOf("Advantech_") == -1) return;

        // Parse Manufacturer Data
        byte[] data = Common.parseManufacturerData(scanRecord);
        if (data == null) return;
        String hexStr = byteArrayToHexStr(data);

        String macAddress = device.getAddress();
        String name = device.getName();
        Log.i(TAG, String.format("mac: %s; name: %s; rssi: %d manufacturer data: %s", macAddress, name, rssi, hexStr));
    }

    // Scan status changed
    @Override
    public void onScanStatusChanged(boolean isScanning) {
        Log.i(TAG, String.format("isScan: %s", isScanning));
    }
};
```

**Step 4.** On the **onResume** method of **MainActivity**, use **isValid** method to double check if the user disable the bluetooth function when the App is resumed.

MainActivity.java
```java
@Override
protected void onResume() {
    super.onResume();
    // If user close BLE, please ask them to enable BLE
    if (!bleUtil.isValid()) {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, REQUEST_ENABLE_BT);
    }
}

...

@Override
public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
        finish();
        return;
    }
    super.onActivityResult(requestCode, resultCode, data);
}
```

**Step 5.** Start BLE scan. You can pass the time parameter (unit: seconds) to stop the scan after defined seconds or pass -1 for endless scaning.

MainActivity.java
```java
private void startScan() {
    bleUtil.startScan(60); // (Unit: Seconds)
    ...
}
```

**Step 6.** Stop BLE scan. Tips: In order to save power, on the **onStop** method of **MainActivity** to stop BLE scan in case the user forget to stop after the app is paused.  

MainActivity.java
```java
private void stopScan() {
    bleUtil.stopScan();
    ...
}

@Override
protected void onPause() {
    super.onPause();
    // BLE stop scan to save power
    stopScan();
}
```

**Step 7.** Connect a device. On the **onResume** method of **DeviceActivity**, add a BLE connection listener and connect a device using its mac address. If connection is successful, you will see the logs on the **onConnectionStateChange** method. Tips: Make sure to stop BLE scan before connect to a device.

DeviceActivity.java
```java
@Override
protected void onResume() {
    super.onResume();
    // Register BLE connection listener
    bleUtil.addConnectListener(mac, bleConnectListener);
    // Connect device via BLE
    bleUtil.connect(mac);
}

private BLEConnectListener bleConnectListener = new BLEConnectListener() {

    @Override
    public void onConnectionStateChange(int result) {
        switch (result) {
            case BluetoothProfile.STATE_CONNECTED:
                refreshUI(etStatus, "Connected");
                Log.i(TAG, "Device Connected");
                break;
            case BluetoothProfile.STATE_CONNECTING:
                refreshUI(etStatus, "Connecting");
                Log.i(TAG, "Device Connecting");
                break;
            case BluetoothProfile.STATE_DISCONNECTING:
                refreshUI(etStatus, "Disconnecting");
                Log.i(TAG, "Device Disconnecting");
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                refreshUI(etStatus, "Disconnected");
                Log.i(TAG, "Device Disconnected");
                break;
        }
    }

    @Override
    public void onConnectionTimeout(String message) {
        Log.i(TAG, message);
    }

    @Override
    public void onServicesDiscovered(int result) {
        if(result == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "Service Discovered");
        } else {
            Log.i(TAG, "Service Discovered Failure");
        }
    }

    @Override
    public void onFirmwareRead(int result, byte[] read) {
        if(result == BluetoothGatt.GATT_SUCCESS) {
            try {
                Log.i(TAG, "Firmware Read: " + new String(read, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Firmware Read Failure");
        }
    }

    @Override
    public void onLEDRead(int result, byte[] read) {
        if(result == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "LED Read: " + byteArrayToHexStr(read));
        } else {
            Log.i(TAG, "LED Read Failure");
        }
    }

    @Override
    public void onLEDWrite(int result, byte[] read) {
        if(result == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "LED Write: " + byteArrayToHexStr(read));
        } else {
            Log.i(TAG, "LED Write Failure");
        }
    }

    @Override
    public void onImageWrite(BLEImageWriteStatus status, int progress, String message) {
        switch (status) {
            case START:
                Log.i(TAG, "Image Write Start");
                break;
            case IN_PROGRESS:
                Log.i(TAG, "Image Writing..." + progress);
                break;
            case FINISH:
                Log.i(TAG, "Image Write Finish!");
                break;
            case ERROR:
                Log.i(TAG, "Image Write Error!");
                break;
            case TIMEOUT:
                Log.i(TAG, "Image Write Timeout!");
                break;
        }
    }

    @Override
    public void onImageRefresh(boolean isSuccess, int page) {
        if(isSuccess) {
            Log.i(TAG, "Image Refresh Success. Page Number: " + page);
        } else {
            Log.i(TAG, "Image Refresh Failure.");
        }
    }

    @Override
    public void onAlarmDetected(boolean isWarning) {
        refreshUI(etAlarm, isWarning ? "Warning!" : "N/A");
    }
};
```

**Step 8.** Disconnect a device. On the **onPause** method of **DeviceActivity**, remove the connection listener and disconnect a device using its mac address.

DeviceActivity.java
```java
@Override
protected void onPause() {
    super.onPause();
    // Unregister BLE connection listener
    bleUtil.removeConnectListener(mac);
    // Disconnect device
    bleUtil.disconnect(mac);
}
```

**Step 9.** Send LED command. After BLE connection, you can easily open or close LED ligths by **writeLED**x methods.

```java
...
// Open LED1
bleUtil.writeLED1(mac, true);
// Close LED1
bleUtil.writeLED1(mac, false);

// Open LED1, Close LED2, Open LED3
bleUtil.writeLED(mac, true, false, true);
```

**Step 10.** Push image command. After BLE connection, you can push image to the device by the **pushImage** method. Make sure change your image file to a **bitmap** format, and resize to fit the panel-type of the EPD device, and rotate the image for 180 degrees.

```java
// device name to panel-type
PanelType panelType = Common.getPanelTypeByName(deviceName);

// get bitmap from drawable
...
Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawable);

// Resize bitmap to fit each size of panel-type
Bitmap resizeBitmap = Common.resizeBitmap(bitmap, panelType.getWidth(), panelType.getHeight());

// Rotate bitmap
Bitmap finalBitmap = Common.rotateImage(resizeBitmap, 180);

// Push Image to the device for the first page, and refreshing image immedately
bleUtil.pushImage(mac, panelType, finalBitmap, 1, 1);
```

**Step 11.** We also provide a **BLETaskHandler** class to handle a bulk of pushing image tasks at one time. All you need to do is to prepare an ArrayList of device mac addresses and bitmap images, and then new BLETaskHandlers one by one in the loop, and finally start the tasks.  **BLETaskHandler** will help you to start a connection, push the image, re-try jobs if some errors occurred, and disconnet the device after this task is done.

MainActivity.java
```java
for (String mac : selectDevices.keySet()) {
    Device d = selectDevices.get(mac);
    
    // 1. New a BLETaskHandler and Register BLE Task Callback
    BLETaskHandler bleTaskHandler = new BLETaskHandler(mac, new BLETaskHandlerCallback() {
        @Override
        public void onSuccess(String message) {
            d.setMessage(message);
            refreshDeviceAdapterUI();
        }

        @Override
        public void onProgress(int progress) {
            d.setMessage(progress + "%");
            refreshDeviceAdapterUI();
        }

        @Override
        public void onReady(String message) {
            d.setMessage(message);
            refreshDeviceAdapterUI();
        }

        @Override
        public void onError(String message) {
            d.setMessage(message);
            refreshDeviceAdapterUI();
        }

        @Override
        public void onFirmwareRead(String firmware) {

        }

        @Override
        public void onLEDRead(byte[] read) {

        }
    });

    // get panel-type from device name
    String deviceName = d.getDeviceName();
    PanelType panelType = Common.getPanelTypeByName(deviceName);
    
    // get bitmap from drawable
    ...
    Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawable);
    
    // Resize bitmap to fit each size of panel-type
    Bitmap resizeBitmap = Common.resizeBitmap(bitmap, panelType.getWidth(), panelType.getHeight());
    
    // Rotate bitmap
    Bitmap finalBitmap = Common.rotateImage(resizeBitmap, 180);
    
    // 2. Start a Task to Push Image to device at the first page, and refresh image immediately
    boolean result = bleTaskHandler.startTask(TaskType.PUSH_IMAGE, panelType, finalBitmap, 1, 1, true);
}
```
