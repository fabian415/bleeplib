package com.advantech.bleep;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.advantech.bleeplib.bean.PanelType;
import com.advantech.bleeplib.utils.Common;
import com.advantech.bleeplib.utils.BLEConnectListener;
import com.advantech.bleeplib.bean.BLEImageWriteStatus;
import com.advantech.bleeplib.utils.BLEUtil;

import java.io.UnsupportedEncodingException;

import static com.advantech.bleeplib.utils.Common.byteArrayToHexStr;

public class DeviceActivity extends AppCompatActivity {
    private static final String TAG = "DeviceActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private BLEUtil bleUtil = BLEUtil.getInstance();
    private Context context;
    private String deviceName;
    private String mac;
    private PanelType panelType;
    // Get elements from UI
    private EditText etName;
    private EditText etMac;
    private EditText etStatus;
    private EditText etFirmware;
    private EditText etAlarm;
    private EditText etPanel;
    private Button btnReconnect;
    private Button btnDisconnect;
    private Button btnOpenLED1;
    private Button btnOpenLED2;
    private Button btnOpenLED3;
    private Button btnCloseLED1;
    private Button btnCloseLED2;
    private Button btnCloseLED3;
    private Button btnPush1;
    private Button btnPush2;
    private Button btnPush3;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        getSupportActionBar().setTitle("Device Activity");

        context = this;

        // BLE initial
        if (!bleUtil.initial(context)) {
            Toast.makeText(getBaseContext(), "Not Support Bluetooth!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get bundle data from MainActivity
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        deviceName = bundle.getString("deviceName");
        mac = bundle.getString("mac");

        // Get elements from UI
        etName = (EditText) findViewById(R.id.etName);
        etMac = (EditText) findViewById(R.id.etMac);
        etStatus = (EditText) findViewById(R.id.etStatus);
        etFirmware = (EditText) findViewById(R.id.etFirmware);
        etAlarm = (EditText) findViewById(R.id.etAlarm);
        etPanel = (EditText) findViewById(R.id.etPanel);
        btnReconnect = (Button) findViewById(R.id.btnReconnect);
        btnDisconnect = (Button) findViewById(R.id.btnDisconnect);
        btnOpenLED1 = (Button) findViewById(R.id.btnOpenLED1);
        btnOpenLED2 = (Button) findViewById(R.id.btnOpenLED2);
        btnOpenLED3 = (Button) findViewById(R.id.btnOpenLED3);
        btnCloseLED1 = (Button) findViewById(R.id.btnCloseLED1);
        btnCloseLED2 = (Button) findViewById(R.id.btnCloseLED2);
        btnCloseLED3 = (Button) findViewById(R.id.btnCloseLED3);
        btnPush1 = (Button) findViewById(R.id.btnPush1);
        btnPush2 = (Button) findViewById(R.id.btnPush2);
        btnPush3 = (Button) findViewById(R.id.btnPush3);

        // Button click action
        btnReconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Reconnect device
                bleUtil.reconnect(mac);
            }
        });

        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Disconnect device
                bleUtil.disconnect(mac);
            }
        });

        btnOpenLED1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open LED1
                bleUtil.writeLED1(mac, true);
            }
        });

        btnOpenLED2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open LED2
                bleUtil.writeLED2(mac, true);
            }
        });

        btnOpenLED3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open LED3
                bleUtil.writeLED3(mac, true);
            }
        });

        btnCloseLED1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Close LED1
                bleUtil.writeLED1(mac, false);
            }
        });

        btnCloseLED2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Close LED2
                bleUtil.writeLED2(mac, false);
            }
        });

        btnCloseLED3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Close LED3
                bleUtil.writeLED3(mac, false);
            }
        });

        btnPush1.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(panelType == null) return;
                // get bitmap from drawable
                String panel = panelType.getValue();
                String file = getFilename(panel, "1");
                int drawable = getResources().getIdentifier(file, "drawable", getPackageName());
                Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawable);
                // Resize bitmap to fit each size of panel-type
                Bitmap resizeBitmap = Common.resizeBitmap(bitmap, panelType.getWidth(), panelType.getHeight());
                // Rotate bitmap
                Bitmap finalBitmap = Common.rotateImage(resizeBitmap, 180);
                // Push Image
                bleUtil.pushImage(mac, panelType, finalBitmap, 1, 1);
                Toast.makeText(context, "Send Image 1", Toast.LENGTH_SHORT).show();
            }
        });

        btnPush2.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(panelType == null) return;
                // get bitmap from drawable
                String panel = panelType.getValue();
                String file = getFilename(panel, "2");
                int drawable = getResources().getIdentifier(file, "drawable", getPackageName());
                Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawable);
                // Resize bitmap to fit each size of panel-type
                Bitmap resizeBitmap = Common.resizeBitmap(bitmap, panelType.getWidth(), panelType.getHeight());
                // Rotate bitmap
                Bitmap finalBitmap = Common.rotateImage(resizeBitmap, 180);
                // Push Image
                bleUtil.pushImage(mac, panelType, finalBitmap, 1, 1);
                Toast.makeText(context, "Send Image 2", Toast.LENGTH_SHORT).show();
            }
        });

        btnPush3.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(panelType == null) return;
                // get bitmap from drawable
                String panel = panelType.getValue();
                String file = getFilename(panel, "w");
                int drawable = getResources().getIdentifier(file, "drawable", getPackageName());
                Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawable);
                // Resize bitmap to fit each size of panel-type
                Bitmap resizeBitmap = Common.resizeBitmap(bitmap, panelType.getWidth(), panelType.getHeight());
                // Rotate bitmap
                Bitmap finalBitmap = Common.rotateImage(resizeBitmap, 180);
                // Push Image
                bleUtil.pushImage(mac, panelType, finalBitmap, 1, 1);
                Toast.makeText(context, "Send White Image", Toast.LENGTH_SHORT).show();
            }
        });

        etName.setText(deviceName);
        etMac.setText(mac);
        panelType = Common.getPanelTypeByName(deviceName);
        if(panelType != null) etPanel.setText(panelType.getValue());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register BLE connection listener
        bleUtil.addConnectListener(mac, bleConnectListener);
        // Connect device via BLE
        bleUtil.connect(mac);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister BLE connection listener
        bleUtil.removeConnectListener(mac);
        // Disconnect device
        bleUtil.disconnect(mac);
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
                Log.i(TAG, "Firmware Read: " + byteArrayToHexStr(read));
                try {
                    refreshUI(etFirmware, new String(read, "UTF-8"));
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

    // Run on UI thread to refresh deviceAdapter UI
    private void refreshUI(EditText editText, String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                editText.setText(message);
            }
        });
    }

    private String getFilename(String panel, String index) {
        return "epd" + panel.split("-")[1] + "_" + index;
    }
}