package com.advantech.bleeplib.utils;

public interface BLETaskHandlerCallback {
    public void onSuccess(String message);
    public void onProgress(int progress);
    public void onReady(String message);
    public void onError(String message);
    public void onFirmwareRead(String firmware);
    public void onLEDRead(byte[] read);
}
