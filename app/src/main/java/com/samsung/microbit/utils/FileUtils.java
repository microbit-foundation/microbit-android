package com.samsung.microbit.utils;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.data.constants.FileConstants;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility that provides methods to work with
 * file operations, such as file renaming, deleting and etc.
 */
public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();

    /**
     * Represents common results of rename operation.
     */
    public enum RenameResult {
        SUCCESS,
        NEW_PATH_ALREADY_EXIST,
        OLD_PATH_NOT_CORRECT,
        RENAME_ERROR
    }

    private FileUtils() {
    }

    /**
     * Tries to rename a file with a given parameter and returns a result code
     * as RenameResult.
     *
     * @param filePath Full path to the file.
     * @param newName  New name of the file.
     * @return Result of the rename operation.
     */
    public static RenameResult renameFile(String filePath, String newName) {
        File oldPathname = new File(filePath);
        newName = newName.replace(' ', '_');
        if(!newName.toLowerCase().endsWith(".hex")) {
            newName = newName + ".hex";
        }

        File newPathname = new File(oldPathname.getParentFile().getAbsolutePath(), newName);
        if(newPathname.exists()) {
            return RenameResult.NEW_PATH_ALREADY_EXIST;
        }

        if(!oldPathname.exists() || !oldPathname.isFile()) {
            return RenameResult.OLD_PATH_NOT_CORRECT;
        }

        if(oldPathname.renameTo(newPathname)) {
            return RenameResult.SUCCESS;
        } else {
            return RenameResult.RENAME_ERROR;
        }
    }

    /**
     * Check if a path is a file
     *
     * @param filePath Full file path.
     * @return True if the file exists.
     */
    public static boolean fileExists( String filePath) {
        File file = new File( filePath);
        return file.exists() && file.isFile();
    }

    /**
     * Tries to delete a file by given path.
     *
     * @param filePath Full file path.
     * @return True if the file deleted successfully.
     */
    public static boolean deleteFile(String filePath) {
        File fileForDelete = new File(filePath);
        if(fileForDelete.exists()) {
            if(fileForDelete.delete()) {
                Log.d("MicroBit", "file Deleted :" + filePath);
                return true;
            } else {
                Log.d("MicroBit", "file not Deleted :" + filePath);
            }
        }

        return false;
    }

    /**
     * Gets a file size by given path and returns String representation
     * of the size.
     *
     * @param filePath Path to the file.
     * @return String representation of file size.
     */
    public static String getFileSize(String filePath) {
        String size = "0";
        File file = new File(filePath);
        if(file.exists()) {
            size = Long.toString(file.length());
        }
        return size;
    }

    public static String fileNameFromPath( String fullPathOfFile) {
        String[] path = fullPathOfFile.split("/");
        if ( path.length > 0) {
            String name = path[ path.length - 1];
            if ( name.endsWith(".hex")) {
                return name;
            }
        }
        return null;
    }

    public static String fileNameFromUri(Uri uri, Context ctx) {
        if (uri != null) {
            String scheme = uri.getScheme();
            if ( scheme != null && scheme.equals("file")) {
                String encodedPath = uri.getEncodedPath();
                if (encodedPath != null) {
                    String fullPathOfFile = URLDecoder.decode(encodedPath);
                    if (fullPathOfFile != null) {
                        return fileNameFromPath( fullPathOfFile);
                    }
                }
            } else if ( scheme != null && scheme.equals("content")) {
                Cursor cursor = ctx.getContentResolver().query( uri, null, null, null, null);
                if ( cursor != null)
                {
                    if ( cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                        if ( index >= 0) {
                            String name = cursor.getString( index);
                            cursor.close();
                            return name;
                        }
                    }
                    cursor.close();
                }
            } else {
                Log.e( TAG, "Unsupported scheme " + scheme);
            }
        }
        return null;
    }

    public static long fileSizeFromUri( Uri uri, Context ctx) {
        if (uri != null) {
            String scheme = uri.getScheme();
            if ( scheme != null && scheme.equals("file")) {
                String path = uri.getPath();
                if ( path != null) {
                    File file = new File(path);
                    return file.length();
                }
            } else if ( scheme != null && scheme.equals("content")) {
                Cursor cursor = ctx.getContentResolver().query( uri, null, null, null, null);
                if ( cursor != null)
                {
                    if ( cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
                        if ( index >= 0) {
                            long size = cursor.getLong( index);
                            cursor.close();
                            return size;
                        }
                    }
                    cursor.close();
                }
            } else {
                Log.e( TAG, "Unsupported scheme " + scheme);
            }
        }
        return -1;
    }

    public static byte[] readBytesFromInputStream(InputStream is, final int size) {
        byte[] bytes = null;
        try {
            bytes = new byte[size];
            int remain = size;
            int offset = 0;
            while ( remain > 0) {
                int read = is.read( bytes, offset, remain);
                remain -= read;
                offset += read;
            }
        }  catch ( Exception e){
            bytes = null;
        }
        return bytes;
    }

    public static byte[] readBytesFromFile( File file) {
        byte[] bytes = null;
        try {
            FileInputStream is = new FileInputStream( file);
            int size = (int) file.length();
            bytes = readBytesFromInputStream(is, size);
            is.close();
        }  catch ( Exception e){
            bytes = null;
        }
        return bytes;
    }

    public static byte[] readBytesFromUri( Uri uri, Context ctx) {
        byte[] bytes = null;
        try {
            InputStream is = ctx.getContentResolver().openInputStream( uri);
            if ( is != null) {
                int size = (int) fileSizeFromUri( uri, ctx);
                bytes = readBytesFromInputStream( is, size);
                is.close();
            }
        }  catch ( Exception e){
            bytes = null;
        }
        return bytes;
    }


    public static boolean stringBuilderAddStream( StringBuilder sb, InputStream is) {
        boolean ok = true;
        try {
            BufferedReader reader = new BufferedReader( new InputStreamReader( is));
            try {
                while (true) {
                    String line = reader.readLine();
                    if ( line == null) break;
                    sb.append( line);
                    sb.append("\n");
                }
            } catch ( Exception e) {
                ok = false;
            }
            reader.close();
        } catch ( Exception e) {
            ok = false;
        }
        return true;
    }

    public static boolean stringBuilderAddFile( StringBuilder sb, File file) {
        boolean ok = false;
        try {
            FileInputStream is = new FileInputStream( file);
            ok = stringBuilderAddStream( sb, is);
            is.close();
        } catch ( Exception e) {
            ok = false;
        }
        return ok;
    }

    public static boolean stringBuilderAddUri( StringBuilder sb, Uri uri, Context ctx) {
        boolean ok = false;
        try {
            InputStream is = ctx.getContentResolver().openInputStream( uri);
            if ( is != null) {
                ok = stringBuilderAddStream(sb, is);
                is.close();
            }
        } catch ( Exception e) {
            ok = false;
        }
        return ok;
    }

    public static String readStringFromHexFile( File file) {
        String hex = "";
        StringBuilder sb = new StringBuilder();
        if ( !stringBuilderAddFile(sb, file))
            return null;
        return sb.toString();
    }

    public static String readStringFromHexUri( Uri uri, Context ctx) {
        String hex = "";
        StringBuilder sb = new StringBuilder();
        if ( !stringBuilderAddUri(sb, uri, ctx))
            return null;
        return sb.toString();
    }

    public static boolean writeBytesToOutputStream( OutputStream os, byte [] bytes)
    {
        boolean ok = true;

        try {
            os.write( bytes);
            os.flush();
        } catch ( Exception e) {
            ok = false;
        }
        return ok;
    }

    public static boolean writeBytesToFile( File file, byte [] bytes)
    {
        boolean ok = true;

        try {
            if ( file.exists()) {
                file.delete();
            }
            file.createNewFile();

            FileOutputStream os = new FileOutputStream(file);
            ok = writeBytesToOutputStream( os, bytes);
            os.close();
        } catch ( Exception e) {
            ok = false;
        }
        return ok;
    }

    public static boolean writeBytesToUri( Uri uri, byte [] bytes, Context ctx)
    {
        boolean ok = true;

        try {
            OutputStream os = ctx.getContentResolver().openOutputStream( uri);;
            ok = writeBytesToOutputStream( os, bytes);
            os.close();
        } catch ( Exception e) {
            ok = false;
        }
        return ok;
    }

    public static boolean isHex( byte [] bytes) {
        boolean ok = true;
        boolean found = false;
        for ( int i = 0; !found && i < 1024 && i < bytes.length - 1; i++) {
            if ( bytes[i] == '\n' && bytes[ i + 1] == ':') {
                found = true;
            }
        }
        if ( !found) {
            return false;
        }
        try {
            BufferedReader reader = new BufferedReader( new InputStreamReader( new ByteArrayInputStream( bytes)));
            String line = null;
            while ( ok) {
                line = reader.readLine();
                if ( line == null) {
                    break;
                }
                if ( !line.isEmpty()) {
                    if ( !line.startsWith(":")) {
                        ok = false;
                    } else {
                        byte[] hex = line.getBytes();
                        int chk = irmHexUtils.lineCheck( hex,0);
                        if ( chk < 0) {
                            ok = false;
                        } else {
                            int sum = irmHexUtils.calcSum( hex,0);
                            sum = ( chk + sum) % 256;
                            if ( sum != 0 ) {
                                ok = false;
                            }
                        }
                    }
                }

            }          reader.close();
        } catch (IOException e) {
            return false;
        }
        return ok;
    }
}
