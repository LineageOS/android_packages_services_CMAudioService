package org.cyanogenmod.cmaudio.service;
/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.os.Message;
import com.android.internal.content.NativeLibraryHelper;

import android.annotation.SdkConstant;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import cyanogenmod.app.CMContextConstants;
import cyanogenmod.media.AudioSessionInfo;
import cyanogenmod.media.CMAudioManager;
import cyanogenmod.media.ICMAudioService;
import cyanogenmod.platform.Manifest;

import org.cyanogenmod.internal.media.ICMAudioServiceProvider;

public class CMAudioService extends Service {

    private static final String TAG = "CMAudioService";
    private static final boolean DEBUG = true;//Log.isLoggable(TAG, Log.DEBUG);

    static {
        System.loadLibrary("cmaudio_jni");
        Log.d(TAG, "loaded jni lib");
    }

    public static final int MSG_BROADCAST_SESSION = 1;

    private static final int AUDIO_STATUS_OK = 0;

    //keep in sync with include/media/AudioPolicy.h
    private final static int AUDIO_OUTPUT_SESSION_EFFECTS_UPDATE = 10;

    private final Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (DEBUG) Log.d(TAG, "handleMessage() called with: " + "msg = [" + msg + "]");
            switch (msg.what) {
                case MSG_BROADCAST_SESSION:
                    broadcastSessionChanged(msg.arg1 == 1, (AudioSessionInfo) msg.obj);
                    break;
            }
            return true;
        }
    });

    @Override
    public void onCreate() {
        super.onCreate();
        native_registerAudioSessionCallback(true);
    }

    @Override
    public void onDestroy() {
        native_registerAudioSessionCallback(false);
        super.onDestroy();
    }

    private final IBinder mBinder = new ICMAudioServiceProvider.Stub() {

        @Override
        public List<AudioSessionInfo> listAudioSessions(int streamType) throws RemoteException {
            final ArrayList<AudioSessionInfo> sessions = new ArrayList<AudioSessionInfo>();

            int status = native_listAudioSessions(streamType, sessions);
            if (status != AUDIO_STATUS_OK) {
                Log.e(TAG, "Error retrieving audio sessions! status=" + status);
            }

            return sessions;
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);

            pw.println();
            pw.println("CMAudio Service State:");
            try {
                List<AudioSessionInfo> sessions = listAudioSessions(-1);
                if (sessions.size() > 0) {
                    pw.println("  Audio sessions:");
                    for (AudioSessionInfo info : sessions) {
                        pw.println("   " + info.toString());
                    }
                } else {
                    pw.println("  No active audio sessions");
                }
            } catch (RemoteException e) {
                // nothing
            }
        }
    };

    private void broadcastSessionChanged(boolean added, AudioSessionInfo sessionInfo) {
        Intent i = new Intent(CMAudioManager.ACTION_AUDIO_SESSIONS_CHANGED);
        i.putExtra(CMAudioManager.EXTRA_SESSION_INFO, sessionInfo);
        i.putExtra(CMAudioManager.EXTRA_SESSION_ADDED, added);

        sendBroadcastToAll(i, Manifest.permission.OBSERVE_AUDIO_SESSIONS);
    }

    private void sendBroadcastToAll(Intent intent, String receiverPermission) {
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        if (DEBUG) Log.d(TAG, "Sending broadcast: " + intent);

        sendBroadcastAsUser(intent, UserHandle.ALL, receiverPermission);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*
     * Handles events from JNI
     */
    private void audioSessionCallbackFromNative(int event, AudioSessionInfo sessionInfo,
            boolean added) {
        AudioSessionInfo copy = new AudioSessionInfo(
                sessionInfo.getSessionId(),
                sessionInfo.getStream(),
                sessionInfo.getFlags(),
                sessionInfo.getChannelMask(),
                sessionInfo.getUid()
        );

        switch (event) {
            case AUDIO_OUTPUT_SESSION_EFFECTS_UPDATE:
                mHandler.obtainMessage(MSG_BROADCAST_SESSION, added ? 1 : 0, 0, copy)
                        .sendToTarget();
                break;
            default:
                Log.e(TAG, "Unknown event " + event);
        }
    }

    private native void native_registerAudioSessionCallback(boolean enabled);

    private native int native_listAudioSessions(int stream, ArrayList<AudioSessionInfo> sessions);

}
