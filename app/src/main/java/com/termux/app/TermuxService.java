package com.termux.app;

import android.annotation.SuppressLint;

public final class TermuxService {
    /**
     * Note that this is a symlink on the Android M preview.
     */
    @SuppressLint("SdCardPath")
    public static final String FILES_PATH = "/data/data/vn.vhn.vsc/files";
    public static final String PREFIX_PATH = FILES_PATH + "/usr";
    public static final String HOME_PATH = FILES_PATH + "/home";
}
