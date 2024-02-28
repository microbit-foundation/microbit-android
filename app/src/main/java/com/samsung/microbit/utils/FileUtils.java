package com.samsung.microbit.utils;

import android.content.res.Resources;
import android.os.Environment;
import android.util.Log;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.data.constants.FileConstants;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility that provides methods to work with
 * file operations, such as file renaming, deleting and etc.
 */
public class FileUtils {

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
}
