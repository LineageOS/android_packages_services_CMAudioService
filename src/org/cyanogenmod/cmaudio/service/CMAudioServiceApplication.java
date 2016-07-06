package org.cyanogenmod.cmaudio.service;

import android.app.Application;
import android.util.Log;

public class CMAudioServiceApplication extends Application {

    private static final String TAG = "CMAudioServiceApplication";

    static {
        System.loadLibrary("cmaudio_jni");
        Log.d(TAG, "Loaded jni library");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
