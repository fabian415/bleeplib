package com.advantech.bleep;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.advantech.bleeplib.bean.PanelType;
import com.advantech.bleeplib.bean.TaskType;
import com.advantech.bleeplib.utils.BLEScanListener;
import com.advantech.bleeplib.utils.BLETaskHandler;
import com.advantech.bleeplib.utils.BLETaskHandlerCallback;
import com.advantech.bleeplib.utils.BLEUtil;
import com.advantech.bleeplib.utils.Common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.advantech.bleeplib.utils.Common.byteArrayToHexStr;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 0;
    private static final int REQUEST_ENABLE_BT = 1;
    private BLEUtil bleUtil = BLEUtil.getInstance();
    private Context context;
    private Button btnScan;
    private Button btnStop;
    private Button btnPush1;
    private Button btnPush2;
    private Button btnPush3;
    private RecyclerView recyclerView;
    private DeviceAdapter deviceAdapter;
    private ArrayList<Device> deviceList = new ArrayList<>();
    private Map<String, Device> selectDevices = new HashMap<>();
    private boolean isScan = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setTitle("Main Activity");

        context = this;

        // Ask permissions for file locations
        askPermissions();

        // BLE initial
        if (!bleUtil.initial(context)) {
            Toast.makeText(getBaseContext(), "Not Support Bluetooth!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // BLE scanning callback listener
        bleUtil.addScanListener(bleScanListener);

        // Get elements from UI
        btnScan = (Button) findViewById(R.id.btnScan);
        btnStop = (Button) findViewById(R.id.btnStop);
        btnPush1 = (Button) findViewById(R.id.btnPush1);
        btnPush2 = (Button) findViewById(R.id.btnPush2);
        btnPush3 = (Button) findViewById(R.id.btnPush3);

        // Button click action
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // BLE scan
                startScan();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // BLE stop scan
                stopScan();
            }
        });

        btnPush1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTasks("1");
            }
        });

        btnPush2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTasks("2");
            }
        });

        btnPush3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTasks("w");
            }
        });

        // Device Lists
        recyclerView = (RecyclerView) findViewById(R.id.rvDevices);
        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
        deviceAdapter = new DeviceAdapter(context, deviceList);
        recyclerView.setAdapter(deviceAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If user close BLE, please ask them to enable BLE
        if (!bleUtil.isValid()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }

        btnScan.setEnabled(true);
        btnStop.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // BLE stop scan to save power
        stopScan();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startScan() {
        bleUtil.startScan(60); // (Unit: Seconds)
        btnScan.setEnabled(false);
        btnStop.setEnabled(true);
        btnPush1.setEnabled(false);
        btnPush2.setEnabled(false);
        btnPush3.setEnabled(false);
        isScan = true;
    }

    private void stopScan() {
        bleUtil.stopScan();
        btnScan.setEnabled(true);
        btnStop.setEnabled(false);
        btnPush1.setEnabled(true);
        btnPush2.setEnabled(true);
        btnPush3.setEnabled(true);
        isScan = false;
        refreshDeviceAdapterUI(); // trigger UI changed
    }

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

            Device d = new Device(macAddress);
            int index = deviceList.indexOf(d);
            if (index == -1) { // New device
                d.setDeviceName(device.getName());
                d.setRssi(rssi);
                d.setAlarm(data[0] == 0x01);
                d.setProgress(0);
                d.setMessage("");
                deviceList.add(d);
            } else { // Old device
                d = deviceList.get(index);
                d.setRssi(rssi);
                d.setAlarm(data[0] == 0x01);
            }

            refreshDeviceAdapterUI(); // trigger UI changed
        }

        // Scan status changed
        @Override
        public void onScanStatusChanged(boolean isScanning) {
            Log.i(TAG, String.format("isScan: %s", isScanning));
            if (!isScanning) {
                btnScan.setEnabled(true);
                btnStop.setEnabled(false);
                btnPush1.setEnabled(true);
                btnPush2.setEnabled(true);
                btnPush3.setEnabled(true);
            }
        }
    };

    // Run on UI thread to refresh deviceAdapter UI
    private void refreshDeviceAdapterUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceAdapter.notifyDataSetChanged();
            }
        });
    }

    private String getFilename(String panel, String index) {
        return "epd" + panel.split("-")[1] + "_" + index;
    }

    private void startTasks(String index) {
        int count = 0;
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

            String deviceName = d.getDeviceName();
            PanelType panelType = Common.getPanelTypeByName(deviceName);
            // get bitmap from drawable
            String panel = panelType.getValue();
            String file = getFilename(panel, index);
            int drawable = getResources().getIdentifier(file, "drawable", getPackageName());
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawable);
            // Resize bitmap to fit each size of panel-type
            Bitmap resizeBitmap = Common.resizeBitmap(bitmap, panelType.getWidth(), panelType.getHeight());
            // Rotate bitmap
            Bitmap finalBitmap = Common.rotateImage(resizeBitmap, 180);
            // 2. Start a Task to Push Image
            boolean result = bleTaskHandler.startTask(TaskType.PUSH_IMAGE, panelType, finalBitmap, 1, 1, true);
            if (result) {
                count++;
            } else {
                Toast.makeText(context, "Connection Existed!!! ( " + mac + " )", Toast.LENGTH_SHORT).show();
            }
        }

        Toast.makeText(context, "Push Image Jobs: " + count, Toast.LENGTH_SHORT).show();
    }

    // RecyclerView
    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.MyViewHolder> {

        private Context context;
        private List<Device> deviceList;

        public DeviceAdapter(Context context, List<Device> deviceList) {
            this.context = context;
            this.deviceList = deviceList;
        }

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(context);
            View view = layoutInflater.inflate(R.layout.itemview_device, parent, false);
            return new MyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
            Device device = deviceList.get(position);
            holder.tvRssi.setText(device.getRssi() + "");
            holder.tvMac.setText(device.getMac());
            holder.tvName.setText(device.getDeviceName());
            holder.tvMessage.setText(device.getMessage());
            if (device.isAlarm())
                holder.ivAlarm.setVisibility(View.VISIBLE);
            else
                holder.ivAlarm.setVisibility(View.INVISIBLE);
            if (isScan) {
                holder.btnConnect.setEnabled(false);
                holder.cbDevice.setEnabled(false);
            } else {
                holder.btnConnect.setEnabled(true);
                holder.cbDevice.setEnabled(true);
            }
            holder.btnConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Shift from MainActivity to DeviceActivity
                    Intent intent = new Intent(context, DeviceActivity.class);
                    intent.putExtra("deviceName", device.getDeviceName());
                    intent.putExtra("mac", device.getMac());
                    startActivity(intent);
                }
            });
            holder.cbDevice.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        selectDevices.put(device.getMac(), device);
                    } else {
                        selectDevices.remove(device.getMac());
                    }
                }
            });
            if (selectDevices.containsKey(device.getMac())) {
                holder.cbDevice.setChecked(true);
            } else {
                holder.cbDevice.setChecked(false);
            }
        }

        @Override
        public int getItemCount() {
            return deviceList.size();
        }

        class MyViewHolder extends RecyclerView.ViewHolder {
            TextView tvRssi, tvName, tvMac, tvMessage;
            ImageView ivAlarm;
            CheckBox cbDevice;
            Button btnConnect;

            public MyViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRssi = (TextView) itemView.findViewById(R.id.tvRssi);
                tvName = (TextView) itemView.findViewById(R.id.tvName);
                tvMac = (TextView) itemView.findViewById(R.id.tvMac);
                tvMessage = (TextView) itemView.findViewById(R.id.tvMessage);
                ivAlarm = (ImageView) itemView.findViewById(R.id.ivAlarm);
                cbDevice = (CheckBox) itemView.findViewById(R.id.cbDevice);
                btnConnect = (Button) itemView.findViewById(R.id.btnConnect);
            }
        }
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

}