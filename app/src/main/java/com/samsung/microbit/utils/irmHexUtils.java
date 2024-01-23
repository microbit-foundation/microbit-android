package com.samsung.microbit.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;


/**
 *  A Class to manipulate micro:bit hex files
 *
 *  (c) 2017 - 2024, Micro:bit Educational Foundation and contributors
 *
 *  SPDX-License-Identifier: MIT
 *
 */

public class irmHexUtils {
    private final static String TAG = com.samsung.microbit.utils.irmHexUtils.class.getSimpleName();
    public final static int MICROBIT_V1 = 1;
    public final static int MICROBIT_V2 = 2;

    // hex file data types for micro:bit v1.X
    public final static int irmHexBlock00 = 0x9900;
    public final static int irmHexBlock01 = 0x9901;  // hexBlock parameter to use when the connected micro:bit is v1.X
    public final static int irmHexBlock02 = 0x9902;
    // hex file data types for micro:bit v2.X
    public final static int irmHexBlock03 = 0x9903;  // hexBlock parameter to use when the connected micro:bit is v2.X
    public final static int irmHexBlock04 = 0x9904;
    public int scanHexSize;
    public long scanAddrMin;
    public long scanAddrNext;
    public int lineNext;
    public int lineHidx;
    public int lineCount;
    public long lineAddr;
    public int lineType;
    public int lineBlockType;
    public long lastBaseAddr;
    public long resultAddrMin;
    public long resultAddrNext;
    public int resultDataSize;
    public byte [] resultHex;

    public void scanInit() {
        scanHexSize = 0;
        lineNext = 0;
        lineHidx = 0;
        scanAddrMin = 0;
        scanAddrNext = Integer.MAX_VALUE;
        lineCount = 0;
        lineAddr = 0;
        lineType = 0;
        lineBlockType = 0;
        lastBaseAddr = 0;
        resultAddrMin = Long.MAX_VALUE;
        resultAddrNext = 0;
    }

    public static int hextodigit( final byte c) {
        if ( c >= '0' && c <= '9') {
            return c - '0';
        }
        if ( c >= 'A' && c <= 'F') {
            return 10 + c - 'A';
        }
        return -1;
    }


    public static int hextobyte( final byte [] hex, final int idx)
    {
        int hi = hextodigit( hex[ idx]);
        int lo = hextodigit( hex[ idx + 1]);
        if ( hi < 0 || lo < 0)
            return -1;
        return 16 * hi + lo;
    }

    public static int hextoaddr( final byte [] hex, final int idx) {
        int hi = hextobyte( hex, idx);
        int lo = hextobyte( hex, idx + 2);
        if ( hi < 0 || lo < 0)
            return -1;
        return hi * 256 + lo;
    }

    public boolean parseLine( final byte [] hex)
    {
        if ( lineNext > scanHexSize - 3)
            return false;

        lineHidx = lineNext;

        if ( hex[ lineHidx] != ':')
            return false;

        lineCount = hextobyte( hex, lineHidx + 1);
        if ( lineCount < 0)
            return false;

        int bytes  = 5 + lineCount;
        int digits = bytes * 2;
        int next   = digits + 1;  // +1 for colon

        while ( lineHidx + next < scanHexSize)
        {
            byte b = hex[ lineHidx + next];
            if ( b == '\r')
                next++;
            else if ( b == '\n')
                next++;
            else if ( b == ':')
                break;
            else
                return false;
        }

        lineNext += next; // bump lineNext to next line or eof

        lineType = hextobyte( hex,  lineHidx + 7);
        if ( lineType < 0)
            return false;

        switch ( lineType) {
            case 0:                 // Data
            case 0x0D:
                lineAddr = hextoaddr( hex, lineHidx + 3);
                if ( lineAddr < 0)
                    return false;
                break;

            case 0x0A: {               // Extended Segment Address
                if (lineCount != 4)
                    return false;
                int hi = hextobyte(hex, lineHidx + 9);
                int lo = hextobyte(hex, lineHidx + 11);
                lineBlockType = hi * 256 + lo;
                break;
            }
            case 2: {               // Extended Segment Address
                if (lineCount != 2)
                    return false;
                int hi = hextobyte(hex, lineHidx + 9);
                int lo = hextobyte(hex, lineHidx + 11);
                lastBaseAddr = (long) hi * (long) 0x1000 + (long) lo * (long) 0x10;
                break;
            }
            case 3:                 // Start Segment Address
                break;

            case 4: {               // Extended Linear Address
                if (lineCount != 2)
                    return false;
                int hi = hextobyte(hex, lineHidx + 9);
                int lo = hextobyte(hex, lineHidx + 11);
                lastBaseAddr = (long) hi * (long) 0x1000000 + (long) lo * (long) 0x10000;
                break;
            }
            case 5:                 // Start Linear Address
                break;
        }

        return true;
    }

    public static int calcSum( final byte [] hex, final int hexIdx) {
        int count = hextobyte( hex, hexIdx + 1);
        if ( count < 0)
            return -1;
        int bytes  = 5 + count - 1;

        int b;
        long sum = 0;

        for ( int i = 0; i < bytes; i++)
        {
            b = hextobyte( hex, hexIdx + 1 + i * 2);
            if ( b < 0)
                return -1;
            sum += b;
        }

        b = (int) (sum % 256);
        return b;
    }

    public static int lineCheck( final byte [] hex, final int hexIdx) {
        int count = hextobyte( hex, hexIdx + 1);
        if ( count < 0)
            return -1;
        return hextobyte( hex, hexIdx + 9 + count * 2);
    }

    public static boolean lineData( final byte [] hex, final int hexIdx, byte [] data, final int idx) {
        int count = hextobyte( hex, hexIdx + 1);
        if ( count < 0)
            return false;
        for (int i = 0; i < count; i++) {
            int d = hextobyte(hex, hexIdx + 9 + 2 * i);
            if (d < 0)
                return false;
            data[ idx + i] = (byte) d;
        }
        return true;
    }

    public static boolean hexBlockIsV1( final int hexBlock)
    {
        switch ( hexBlock)
        {
            case irmHexBlock00:
            case irmHexBlock01:
            case irmHexBlock02:
                return true;

            case irmHexBlock03:
            case irmHexBlock04:
                break;

            default:
                break;
        }

        return false;
    }


    public static boolean hexBlockIsV2( final int hexBlock)
    {
        switch ( hexBlock)
        {
            case irmHexBlock00:
            case irmHexBlock01:
            case irmHexBlock02:
                break;

            case irmHexBlock03:
            case irmHexBlock04:
                return true;

            default:
                break;
        }

        return false;
    }


    public static boolean hexBlocksMatch( final int blockType, final int hexBlock)
    {
        if ( hexBlockIsV1( blockType))
            return hexBlockIsV1( hexBlock);

        if ( hexBlockIsV2( blockType))
            return hexBlockIsV2( hexBlock);

        return false;
    }

    // range of addresses allowed in application region
    // return [ min, next, page]
    public static long[] hexBlockToAppRegion( int hexBlock)
    {
        long min = 0;
        long next = 0;
        long page = 0;

        switch ( hexBlock)
        {
            case irmHexBlock00:
            case irmHexBlock01:
            case irmHexBlock02:
            {
                min  = 0x18000;  // min and max addresses allowed in FOTA file
                next = 0x3C000;
                page = 0x400;
                break;
            }
            case irmHexBlock03:
            case irmHexBlock04:
            {
                min  = 0x1C000;
                next = 0x77000;
                page = 0x1000;
                break;
            }
            default:
                break;
        }

        return new long [] { min, next, page};
    }

    public static boolean bytesmatch( final byte[] b0, final int i0, final byte[] b1, final int i1, final int len) {
        for (int i = 0; i <= len; i++) {
            if ( b0[ i0 + i] != b1[ i1 + i]) {
                return false;
            }
        }
        return true;
    }

    // Extract data records (0x00, 0x0D) + EOF for specific hexBlock
    // Fat file is a sequence of ELA, 0x0A, other records, ELA, 0x0A, other records, ... ELA, 0x0A, other records, EOF
    // 0x0C records, 0x0B records or newlines are used to make each (ELA, 0xA, other) block 512 bytes
    // return size on success, zero on failure
    public int scanForDataHex( byte [] datahex, final int hexBlock, final byte [] universalhex, final int universalsize) {
        scanHexSize = universalsize;
        resultDataSize = 0;

        int lastType = -1;    // Type of last record added
        int lastSize = -1;    // index of last record added
        int hexSize = 0;
        int hidxELA0 = -1;     // last ELA stored
        int sizeELA0 = 0;

        boolean dataWanted = false;  // block type matches hexBlock
        boolean isUniversal = false;

        for (lineNext = 0; lineNext < scanHexSize; /*empty*/) {
            if (!parseLine( universalhex))
                return 0;

            int rlen = lineNext - lineHidx;
            if ( rlen == 0)
                continue;

            switch ( lineType) {
                case 0:                 // Data
                case 0x0D:
                    if ( !isUniversal || dataWanted) {
                        long fullAddr = lastBaseAddr + lineAddr;
                        if ( fullAddr + lineCount > scanAddrMin && fullAddr < scanAddrNext) {
                            // TODO support part lines?
                            if ( resultAddrMin > fullAddr) {
                                resultAddrMin = fullAddr;
                            }
                            if ( resultAddrNext < fullAddr + lineCount) {
                                resultAddrNext = fullAddr + lineCount;
                            }
                            if (datahex != null) {
                                System.arraycopy(universalhex, lineHidx, datahex, hexSize, rlen);
                                datahex[hexSize + 7] = '0';
                                datahex[hexSize + 8] = '0';
                            }
                            lastSize = hexSize;
                            lastType = lineType;
                            hexSize += rlen;
                        }
                    }
                    break;

                case 1:                 //EOF
                case 2:                 // Extended Segment Address
                    if ( datahex != null)
                        System.arraycopy( universalhex, lineHidx, datahex, hexSize, rlen);
                    lastSize = hexSize;
                    lastType = lineType;
                    hexSize += rlen;
                    break;

                case 3:                 // Start Segment Address
                    break;

                case 4:                 // Extended Linear Address record
                    // Add if the address has changed
                    // If the last record added is ELA, overwrite it
                    if (sizeELA0 != rlen || !bytesmatch(universalhex, hidxELA0, universalhex, lineHidx, rlen)) {
                        hidxELA0 = lineHidx;
                        sizeELA0 = rlen;
                        if (lastType == lineType)
                            hexSize = lastSize;
                        if ( datahex != null)
                            System.arraycopy( universalhex, hidxELA0, datahex, hexSize, sizeELA0);
                        lastSize = hexSize;
                        lastType = lineType;
                        hexSize += sizeELA0;
                    }
                    break;

                case 5:                 // Start Linear Address
                    break;

                case 0x0A:              // Start block record
                {
                    if ( lineCount < 2)
                        return 0;

                    if ( sizeELA0 == 0)     // must have been at least an ELA record
                        return 0;

                    isUniversal = true;
                    dataWanted = hexBlocksMatch(lineBlockType, hexBlock);
                    break;
                }

                case 0x0B:              // End block
                    break;

                case 0x0C:              // End block
                    break;

                default:
                    break;
            }
        }

        long range = resultAddrNext > resultAddrMin ? resultAddrNext - resultAddrMin : 0;

        resultDataSize = (int) range;

        if ( resultDataSize == 0)
            hexSize = 0;       // no data for specified hexBlock

        return hexSize;
    }

    // Scan for single target data hex from universal hex
    //
    // return false on failure
    public boolean scanForDataHex( final byte [] universalHex, final int hexBlock) {
        resultHex = null;

        try {
            long hexSize = scanForDataHex( null, hexBlock, universalHex, universalHex.length);
            if ( hexSize == 0)
                return false;

            if (hexSize > 0) {
                resultHex = new byte[ (int) hexSize];
                if (resultHex == null)
                    return false;
                hexSize = scanForDataHex( resultHex, hexBlock, universalHex, universalHex.length);
                if ( hexSize == 0)
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    // Extract single target application hex from universal hex
    //
    // return false on failure
    // generated hex is in resultHex
    public boolean universalHexToApplicationHex( final byte [] universalHex, final int hexBlock) {
        scanInit();
        long [] mnp = hexBlockToAppRegion( hexBlock);
        scanAddrMin = mnp[0];
        scanAddrNext = mnp[1];
        return scanForDataHex( universalHex, hexBlock);
    }
};