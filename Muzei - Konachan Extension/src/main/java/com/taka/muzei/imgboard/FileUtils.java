package com.taka.muzei.imgboard;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    private static final Logger logger = new Logger(FileUtils.class);

    public static String extractFileExtension(String path) {
        final int lastDotPos = path.lastIndexOf('.');
        if(lastDotPos < 0)
            return path;
        return path.substring(lastDotPos + 1).toLowerCase();
    }

    public static String cleanFileName(String name) {
        return name.replaceAll("[^\\d\\s\\w.\\-+\\[\\]()]", "_");
    }

    public static void copyFile(File src, File dst) throws IOException {
        logger.i("Copying file " + src.getAbsolutePath() + " to " + dst.getAbsolutePath());
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static File createDirOrCheckAccess(String fPath) throws IOException {
        logger.i( "Checking if dir exists and app can write to it: " + fPath);
        File fDir = new File(fPath);
        if (!fDir.exists()){
            logger.i("Creating dir " + fPath);
            if(!fDir.mkdirs()) {
                logger.e("Can not create dir " + fPath);
                throw new IOException("Failed to create local dir " + fPath);
            }
        }
        if(!fDir.canWrite()) {
            logger.e("Can not write to dir " + fPath);
            throw new IOException("Can not write to local dir " + fPath + ". Check permissions");
        }

        return fDir;
    }

    public static void checkWriteAccessToFile(String path) throws IOException {
        final File file = new File(path);
        final String dir = file.getParent();
        if(null == dir)
            throw new IOException("Can not write to root");
        createDirOrCheckAccess(dir);
        if(file.createNewFile())
            return;
        if(!file.canWrite()) {
            logger.e("No write permission for file " + path);
            throw new IOException("Can not write to file " + path);
        }
    }

    public interface listFilesCallback {
        void next(File file);
    }

    public static void listFiles(@NonNull File dir, @NonNull listFilesCallback callback) throws IOException {
        if(!dir.isDirectory())
            throw new IOException(dir + " is not a directory");
        for(File f : dir.listFiles()) {
            callback.next(f);
        }
    }
}
