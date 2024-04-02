package com.samsung.microbit.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.samsung.microbit.MBApp;
import com.samsung.microbit.data.constants.Constants;
import com.samsung.microbit.data.constants.FileConstants;
import com.samsung.microbit.data.model.Project;
import com.samsung.microbit.ui.activity.ProjectActivity;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Provides functionality to operate with a project items,
 * such as get a number of total saved projects and search
 * projects on a mobile device.
 */
public class ProjectsHelper {
    //TODO: Change to data/data/appName/files MBApp.getContext().getFilesDir();
    private static final String TAG = ProjectsHelper.class.getSimpleName();

    private ProjectsHelper() {
    }

    public static SharedPreferences prefsGet() {
        Context ctx = MBApp.getApp();
        return ctx.getSharedPreferences(Constants.PREFERENCES, Context.MODE_MULTI_PROCESS);
    }

    public static void prefsPutString(String name, String value) {
        SharedPreferences.Editor prefs = prefsGet().edit();
        prefs.putString( name, value);
        prefs.apply();
    }

    public static String prefsGetString(String name, String def) {
        SharedPreferences prefs = prefsGet();
        return prefs.getString( name, def);
    }

    public static boolean legacyStorage() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
    }

    public static File getDownloadsDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static File projectRoot(Context context) {
        if ( legacyStorage()) {
            return getDownloadsDirectory();
        }
        return context.getFilesDir();
    }

    public static File projectFile(Context context, String name)
    {
        return new File ( projectRoot( context), name);
    }

    public static String projectPath(Context context, String name)
    {
        return projectFile( context, name).getAbsolutePath();
    }

    public static File[] projectFilesList( Context context, java.io.FilenameFilter filter )
    {
        File root = projectRoot( context);
        if ( !root.exists())
            return null;
        return projectRoot( context).listFiles( filter);
    }

    public static File[] projectFilesListHEX( Context context)
    {
        FilenameFilter hexFilenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".hex");
            }
        };

        File root = projectRoot( context);
        if ( !root.exists())
            return null;
        return projectFilesList( context, hexFilenameFilter);
    }

    public static File[] downloadsFilesListHEX( Context context)
    {
        FilenameFilter hexFilenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".hex");
            }
        };

        File root = getDownloadsDirectory();
        if ( !root.exists())
            return null;
        return projectFilesList( context, hexFilenameFilter);
    }

    public static boolean havePermissions(Activity activity) {
        if ( legacyStorage()) {
            return checkPermissionsLegacy( activity);
        }
        return true;
    }

    private static boolean checkPermissionLegacy(Activity activity, String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PermissionChecker.PERMISSION_GRANTED;
    }

    public static boolean checkPermissionsLegacy(Activity activity) {
        if ( !checkPermissionLegacy(activity, Manifest.permission.READ_EXTERNAL_STORAGE))
            return false;
        if ( !checkPermissionLegacy(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return false;
        return true;
    }

    public static boolean requestPermissions(Activity activity, final int requestCode) {
        if ( legacyStorage()) {
            String[] permissionsNeeded = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(activity, permissionsNeeded, requestCode);
            return true;
        }
        return false;
    }

    public static int findProjectsAndPopulate( Context context, HashMap<String, String> prettyFileNameMap, List<Project> list) {
        Log.d("MicroBit", "Searching files in " + projectRoot( context).getAbsolutePath());

        File files[] = projectFilesListHEX( context);
        if ( files == null)
            return 0;

        for (File file : files) {
            String fileName = file.getName();
            //Beautify the filename
            String parsedFileName;
            int dot = fileName.lastIndexOf(".");
            parsedFileName = fileName.substring(0, dot);
            parsedFileName = parsedFileName.replace('_', ' ');

            if (prettyFileNameMap != null) {
                prettyFileNameMap.put(parsedFileName, fileName);
            }

            if (list != null) {
                list.add(new Project(parsedFileName, file.getAbsolutePath(), file.lastModified(),
                        null, false));
            }
        }
        return files.length;
    }

    /**
     * Allows to install standard project examples by unzipping them
     * from the raw resources to Downloads directory on a mobile device.
     *
     * @return True if installing completed successfully.
     */
    public static boolean installSamples( Context context) {
        try {
            MBApp app = MBApp.getApp();

            Resources resources = app.getResources();
            final int internalResource = resources.getIdentifier(FileConstants.ZIP_INTERNAL_NAME, "raw", app.getPackageName());
            Log.d("MicroBit", "Resource id: " + internalResource);
            //Unzip the file now
            ZipInputStream zin = new ZipInputStream(resources.openRawResource(internalResource));
            ZipEntry ze;
            while((ze = zin.getNextEntry()) != null) {
                Log.v("MicroBit", "Unzipping " + ze.getName());

                File f = projectFile(context, ze.getName());
                if (!f.getCanonicalPath().startsWith(projectRoot(context).getCanonicalPath())) {
                    throw new SecurityException();
                }

                if (ze.isDirectory()) {
                    if ( !f.isDirectory()) {
                        f.mkdirs();
                    }
                } else {
                    FileOutputStream fout = new FileOutputStream(f);
                    BufferedOutputStream bufout = new BufferedOutputStream(fout);
                    byte[] buffer = new byte[1024];
                    int read = 0;
                    while ((read = zin.read(buffer)) != -1) {
                        bufout.write(buffer, 0, read);
                    }
                    zin.closeEntry();
                    bufout.close();
                    fout.close();
                }
            }
            zin.close();
        } catch(Resources.NotFoundException e) {
            Log.e("MicroBit", "No internal zipfile present", e);
            return false;
        } catch(IOException e) {
            Log.e("MicroBit", "unzip", e);
            return false;
        }
        return true;
    }

    /**
     * Copy HEX files from downloads directory
     *
     * @return True if installing completed successfully.
     */
    public static boolean copyDownloads( Context context) {
        File[] downloads = downloadsFilesListHEX( context);
        for ( int index = 0; index < downloads.length; index++) {
            File downFile = downloads[index];
            String fileName = downFile.getName();
            File newFile = projectFile(context, fileName);
            Log.v("MicroBit", "Copying " + fileName);
            try {
                IOUtils.copy(new FileInputStream(downFile), new FileOutputStream(newFile));
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        return true;
    }

    public static boolean writeStringToProjectHex( String hex, String fileName, Context ctx)
    {
        boolean ok = true;

        File file = ProjectsHelper.projectFile( ctx, fileName);

        try {
            byte[] bytes = hex.getBytes();

            if ( file.exists()) {
                file.delete();
            }
            file.createNewFile();

            FileOutputStream os = new FileOutputStream(file);
            os.write( bytes);
            os.flush();
            os.close();
        } catch ( Exception e) {
            ok = false;
        }
        return ok;
    }

    public static enum enumImportResult {
        Success,
        NotFound,
        ReadFailed,
        NotHex,
        AlreadyExists,
        WriteFailed
    }

    public static class ProjectsHelperImportResult {

        public enumImportResult result;
        public File file;

        public ProjectsHelperImportResult( enumImportResult resultIn) {
            super();
            result = resultIn;
            file = null;
        }

        public ProjectsHelperImportResult( enumImportResult resultIn, File fileIn) {
            super();
            result = resultIn;
            file = fileIn;
        }
    }

    public static ProjectsHelperImportResult importToProjectsWork(Uri uri, Context ctx) {
        if ( uri == null) {
            return new ProjectsHelperImportResult( ProjectsHelper.enumImportResult.NotFound);
        }

        byte [] bytes = FileUtils.readBytesFromUri( uri, ctx);
        if ( bytes == null) {
            return new ProjectsHelperImportResult( ProjectsHelper.enumImportResult.ReadFailed);
        }

        if ( !FileUtils.isHex( bytes)) {
            return new ProjectsHelperImportResult( ProjectsHelper.enumImportResult.NotHex);
        }

        String fileName = FileUtils.fileNameFromUri( uri, ctx);
        if ( fileName == null) {
            fileName = "import.hex";
        }

        File file = ProjectsHelper.projectFile( ctx, fileName);
        if ( file.exists()) {
            return new ProjectsHelperImportResult( ProjectsHelper.enumImportResult.AlreadyExists);
        }

        boolean saved = FileUtils.writeBytesToFile( file, bytes);
        if ( !saved) {
            if ( file.exists()) {
                file.delete();
            }
            return new ProjectsHelperImportResult( ProjectsHelper.enumImportResult.WriteFailed);
        }

        return new ProjectsHelperImportResult( ProjectsHelper.enumImportResult.Success, file);
    }

    public static void importToProjectsToast(ProjectsHelper.enumImportResult result, Context ctx) {
        switch ( result) {
            case Success:
                Toast.makeText( ctx, "micro:bit HEX imported", Toast.LENGTH_LONG).show();
                break;
            case NotFound:
                Toast.makeText( ctx, "File not found", Toast.LENGTH_LONG).show();
                break;
            case ReadFailed:
                Toast.makeText( ctx, "Could not read micro:bit HEX", Toast.LENGTH_LONG).show();
                break;
            case NotHex:
                Toast.makeText( ctx, "Not a micro:bit HEX", Toast.LENGTH_LONG).show();
                break;
            case AlreadyExists:
                Toast.makeText( ctx, "A project with the same name already exists", Toast.LENGTH_LONG).show();
                break;
            case WriteFailed:
                Toast.makeText( ctx, "Could not store micro:bit HEX", Toast.LENGTH_LONG).show();
                break;
        }
    }
}
