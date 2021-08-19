package com.advantech.bleeplib.utils;

public interface BLEDeviceBeanTimeoutCallback {
    public void onTaskTimeout(int progress);
    public void onConnectionTimeout();
}
