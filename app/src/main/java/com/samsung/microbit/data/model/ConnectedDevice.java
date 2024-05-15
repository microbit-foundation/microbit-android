package com.samsung.microbit.data.model;

/**
 * Represents a connected device (a micro:bit board).
 * It contains common information about a micro:bit board such as
 * name, pattern, status, address, etc.
 */
public class ConnectedDevice {

    public String mName;
    public String mPattern;
    public boolean mStatus;
    public String mAddress;
    public int mPairingCode;
    public String mfirmware_version;
    public long mlast_connection_time;
    public int mhardwareVersion;

    public ConnectedDevice() {
        mPattern = null;
        mName = null;
        mStatus = false;
        mAddress = null;
        mPairingCode = 0;
        mfirmware_version = null;
        mlast_connection_time = 0;
        mhardwareVersion = 0;
    }

    public ConnectedDevice(String name, String pattern,
                           boolean status, String address,
                           int pairingCode, String firmware,
                           long connectionTime, int hardwareVersion) {
        this.mName = name;
        this.mPattern = pattern;
        this.mStatus = status;
        this.mAddress = address;
        this.mPairingCode = pairingCode;
        this.mfirmware_version = firmware;
        this.mlast_connection_time = connectionTime;
        this.mhardwareVersion = hardwareVersion;
    }
}