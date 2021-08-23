package com.advantech.bleeplib.utils;

/**
 * BLETaskHandlerCallback is a callback listener which will returns the task results.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public interface BLETaskHandlerCallback {

    /**
     * Task success callback.
     *
     * @param message   task success message
     */
    public void onSuccess(String message);

    /**
     * Task progress callback.
     *
     * @param progress task progress range from 0 to 100
     */
    public void onProgress(int progress);

    /**
     * Task ready callback which includes device connected, service discovered, and retry.
     *
     * @param message   task ready message
     */
    public void onReady(String message);

    /**
     * Task error callback.
     *
     * @param message   task error message
     */
    public void onError(String message);

    /**
     * Firmware read callback.
     *
     * @param firmware  firmware version
     */
    public void onFirmwareRead(String firmware);

    /**
     * LED read callback.
     *
     * @param read  read data
     */
    public void onLEDRead(byte[] read);
}
