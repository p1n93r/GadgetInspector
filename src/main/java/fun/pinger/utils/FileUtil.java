package fun.pinger.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {

    private static final List<String> FILENAMES = new ArrayList<>();

    public static List<String> getFiles(String path) {
        FILENAMES.clear();
        return getFiles0(path);
    }

    private static List<String> getFiles0(String path) {
        File file = new File(path);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) {
                return FILENAMES;
            }
            for (File value : files) {
                if (value.isDirectory()) {
                    getFiles0(value.getPath());
                } else {
                    FILENAMES.add(value.getAbsolutePath());
                }
            }
        } else {
            FILENAMES.add(file.getAbsolutePath());
        }
        return FILENAMES;
    }

    public static boolean removeDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = removeDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir.delete();
    }


    public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[4096];
        int n;
        while ((n = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, n);
        }
    }

}
