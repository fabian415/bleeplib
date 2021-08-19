package com.advantech.bleeplib.bean;

import com.advantech.bleeplib.utils.BLETaskHandler;

public class RunningTask {
    private RunningTaskStatus status;
    private BLETaskHandler bleTaskHandler;

    public RunningTask(RunningTaskStatus status, BLETaskHandler bleTaskHandler) {
        this.status = status;
        this.bleTaskHandler = bleTaskHandler;
    }

    public RunningTaskStatus getStatus() {
        return status;
    }

    public void setStatus(RunningTaskStatus status) {
        this.status = status;
    }

    public BLETaskHandler getBleTaskHandler() {
        return bleTaskHandler;
    }

    public void setBleTaskHandler(BLETaskHandler bleTaskHandler) {
        this.bleTaskHandler = bleTaskHandler;
    }
}