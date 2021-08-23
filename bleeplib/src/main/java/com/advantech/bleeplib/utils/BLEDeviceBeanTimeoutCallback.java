package com.advantech.bleeplib.utils;

/**
 * BLEDeviceBeanTimeoutCallback is a callback listener which returns the connection / task timeout
 * results.
 *
 * @author Fabian Chung
 * @version 1.0.0
 */
public interface BLEDeviceBeanTimeoutCallback {
    /**
     * Task timeout callback.
     *
     * @param progress
     */
    public void onTaskTimeout(int progress);

    /**
     * Connection timeout callback.
     */
    public void onConnectionTimeout();
}
