package utils;

import java.io.File;

public class ClearDirectory {
    public static boolean clearDir(File dir) {
        if (!dir.isDirectory()) return false;
        boolean cleared = true;
        for (File file : dir.listFiles())
            if (file.isDirectory()) {
                if (!clearDir(file)) cleared = false;
            } else if (!file.delete()) cleared = false;
        return cleared;
    }
}
