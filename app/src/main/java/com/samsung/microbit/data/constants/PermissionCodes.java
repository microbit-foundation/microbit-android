package com.samsung.microbit.data.constants;

/**
 * Contains common permission codes.
 * It uses to identify which permission need to grand.
 */
public class PermissionCodes {
    private PermissionCodes() {
    }

    //Can only use lower 8 bits for requestCode
    public static final int APP_STORAGE_PERMISSIONS_REQUESTED = 0x01;
    public static final int BLUETOOTH_PERMISSIONS_REQUESTED = 0x02;
    public static final int CAMERA_PERMISSIONS_REQUESTED = 0x03;
    public static final int INCOMING_CALL_PERMISSIONS_REQUESTED = 0x03;
    public static final int INCOMING_SMS_PERMISSIONS_REQUESTED = 0x04;
    public static final int BLUETOOTH_PERMISSIONS_REQUESTED_API28 = 0x10;
    public static final int BLUETOOTH_PERMISSIONS_REQUESTED_API29 = 0x11;
    public static final int BLUETOOTH_PERMISSIONS_REQUESTED_API30_FOREGROUND = 0x12;
//    // REMOVE BACKGROUND
//    public static final int BLUETOOTH_PERMISSIONS_REQUESTED_API30_BACKGROUND = 0x13;
    public static final int BLUETOOTH_PERMISSIONS_REQUESTED_API31 = 0x14;
    public static final int BLUETOOTH_PERMISSIONS_REQUESTED_FLASHING_API31 = 0x15;


}
