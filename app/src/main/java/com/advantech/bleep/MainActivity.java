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
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.advantech.bleeplib.utils.BLEScanListener;
import com.advantech.bleeplib.utils.BLEUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static com.advantech.bleeplib.common.Common.byteArrayToHexStr;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final int REQUEST_PERMISSIONS = 0;
    public static final int REQUEST_ENABLE_BT = 1;
    private Context context;
    private Button btnScan;
    private Button btnStop;
    private RecyclerView recyclerView;
    private DeviceAdapter deviceAdapter;
    private ArrayList<Device> deviceList = new ArrayList<>(); // 將搜尋到的 devices 存起來  很重要！不可複寫該物件實體！
    private BLEUtil bleUtil = BLEUtil.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        // 向使用者要求權限
        askPermissions();

        // 初始化，並檢查手機是否支援 Bluetooth
        if (!bleUtil.initial(context)) {
            Toast.makeText(getBaseContext(), "No Support Bluetooth!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 先註冊 BLE 掃描後的回調事件
        bleUtil.addScanListener(bleScanListener);

        // 抓取元素
        btnScan = (Button) findViewById(R.id.btnScan);
        btnStop = (Button) findViewById(R.id.btnStop);

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // BLE 開始掃描
                startScan();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // BLE 停止掃描
                stopScan();
            }
        });

        // 設備列表
        recyclerView = (RecyclerView) findViewById(R.id.rvDevices);
        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
        deviceAdapter = new DeviceAdapter(context, deviceList);
        recyclerView.setAdapter(deviceAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 如果使用者已經將 bluetooth 關閉，會詢問是否打開？
        if (!bleUtil.isValid()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT); //再利用 startActivityForResult 啟動該 Intent
        }

        btnScan.setEnabled(true);
        btnStop.setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // BLE 停止掃描
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
        bleUtil.startScan(60); // 永遠掃描
        btnScan.setEnabled(false);
        btnStop.setEnabled(true);
    }

    private void stopScan() {
        bleUtil.stopScan();
        btnScan.setEnabled(true);
        btnStop.setEnabled(false);
    }

    private BLEScanListener bleScanListener = new BLEScanListener() {
        // 設備掃描後的結果
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            // 去除非研華設備
            if (device.getName() == null || device.getName().indexOf("Advantech_") == -1) return;

            // Parse Manufacturer Data
            byte[] data = bleUtil.parseManufacturerData(scanRecord);
            if (data == null) return;
            String hexStr = byteArrayToHexStr(data);

            String macAddress = device.getAddress();
            String name = device.getName();
            Log.e(TAG, String.format("mac: %s; name: %s; rssi: %d manufacturer data: %s", macAddress, name, rssi, hexStr));

            Device d = new Device(macAddress);
            int index = deviceList.indexOf(d);
            if (index == -1) { // 新的設備
                d.setDeviceName(device.getName());
                d.setRssi(rssi);
                d.setAlarm(data[0] == 0x01);
                d.setProgress(0);
                d.setMessage("");
                deviceList.add(d);
            } else { // 重複的設備
                d = deviceList.get(index);
                d.setRssi(rssi);
                d.setAlarm(data[0] == 0x01);
            }

            refreshDeviceAdapterUI(); // trigger UI changed
        }

        // 掃描狀態改變
        @Override
        public void onScanStatusChanged(boolean isScanning) {
            Log.e(TAG, String.format("isScanning: %s", isScanning));
            if (!isScanning) {
                btnScan.setEnabled(true);
                btnStop.setEnabled(false);
            }
        }
    };

    private void refreshDeviceAdapterUI() {
        // 更新 deviceAdapter UI 上的內容
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceAdapter.notifyDataSetChanged();
            }
        });
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
        }

        @Override
        public int getItemCount() {
            return deviceList.size();
        }

        class MyViewHolder extends RecyclerView.ViewHolder {
            TextView tvRssi, tvName, tvMac, tvMessage;
            ImageView ivAlarm;

            public MyViewHolder(@NonNull View itemView) {
                super(itemView);
                tvRssi = (TextView) itemView.findViewById(R.id.tvRssi);
                tvName = (TextView) itemView.findViewById(R.id.tvName);
                tvMac = (TextView) itemView.findViewById(R.id.tvMac);
                tvMessage = (TextView) itemView.findViewById(R.id.tvMessage);
                ivAlarm = (ImageView) itemView.findViewById(R.id.ivAlarm);
            }
        }
    }

    // 向使用者要求權限
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

    // 向使用者要求權限的回調函式
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