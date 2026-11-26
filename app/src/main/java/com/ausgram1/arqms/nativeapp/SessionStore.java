package com.ausgram1.arqms.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionStore {
    public static String token = "";
    public static String user = "";
    public static String name = "";
    public static String role = "";
    public static String deviceLabel = "Android Phone";

    private static final String PREF = "arqms_native_prefs";

    public static void saveDevicePrefs(Context ctx, String uid, String devName) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putString("last_uid", uid).putString("dev_name", devName).apply();
    }

    public static String getSavedUid(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("last_uid", "");
    }

    public static String getSavedDeviceName(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("dev_name", "My Android Phone");
    }

    public static void clearSession() {
        token = "";
        user = "";
        name = "";
        role = "";
    }
}
