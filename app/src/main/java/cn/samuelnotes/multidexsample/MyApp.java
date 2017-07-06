package cn.samuelnotes.multidexsample;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.support.multidex.MultiDex;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import cn.samuelnotes.multidexsample.utils.PackageUtil;


/**
 * Created by samuelwang on  2015/10/7.
 */

public class MyApp extends Application {


    public static final String KEY_DEX2_SHA1 = "dex2-SHA1-Digest";

    //   =====================================================================================
    long endTime;
    private long remoteInstallStartTime;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.d("loadDex", "App attachBaseContext ");
        String currentProcessName = getCurProcessName(this);
        if (!TextUtils.isEmpty(currentProcessName)) {
            if (currentProcessName.contains(":pushservice") || currentProcessName.contains(":daemon")) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    Log.d("loadDex", "App attachBaseContext current process : " + currentProcessName);
                    remoteInstallStartTime = System.currentTimeMillis();
                    MultiDex.install(this);
                    Log.d("loadDex", "App attachBaseContext current install used : " + (System.currentTimeMillis() - remoteInstallStartTime) + "ms");
                    return;
                }
            }
        }

//        if (!TextUtils.isEmpty(currentProcessName)) {
//            if (currentProcessName.contains(":x5webviewinit")) {
//                Log.d("x5webviewinit", "App attachBaseContext current process : " + currentProcessName);
//                return;
//            }
//        }
        if (!quickStart() && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {//>=5.0的系统默认对dex进行oat优化
            if (needWait(base)) {
                Log.d("loadDex", "enter needwait ");
                waitForDexopt(base);
            }
            long startTime = System.currentTimeMillis();
            Log.d("loadDex", "App MultiDex.install start  " + startTime);

            MultiDex.install(this);
            endTime = System.currentTimeMillis();
            Log.d("loadDex", "App MultiDex.install used " + (endTime - startTime) + "ms");
        }
        Log.d("loadDex", "attachBaseContext " + System.currentTimeMillis());
    }

    public boolean quickStart() {
        String currentProcessName = getCurProcessName(this);
        if (!TextUtils.isEmpty(currentProcessName)) {
            if (currentProcessName.contains(":mini")) {
                Log.d("loadDex", ":mini start!");
                return true;
            }
        }
        return false;
    }

    //neead wait for dexopt ?
    private boolean needWait(Context context) {
        String flag = get2thDexSHA1(context);
        Log.d("loadDex", "dex2-sha1 " + flag);
        SharedPreferences sp = context.getSharedPreferences(
                PackageUtil.getPackageInfo(context).versionName, Context.MODE_MULTI_PROCESS | Context.MODE_WORLD_READABLE);
        String saveValue = sp.getString(KEY_DEX2_SHA1, "");

        Log.d("loadDex", "dex2-sha1 saveValue " + saveValue);
        //// 根据class2dex签名是否相同
        return !TextUtils.equals(flag, saveValue);
    }

    /**
     * Get classes.dex file signature
     *
     * @param context
     * @return
     */
    private String get2thDexSHA1(Context context) {
        ApplicationInfo ai = context.getApplicationInfo();
        String source = ai.sourceDir;
        try {
            JarFile jar = new JarFile(source);
            java.util.jar.Manifest mf = jar.getManifest();
            Map<String, Attributes> map = mf.getEntries();
            Attributes a = map.get("classes2.dex");
            if(a!=null){
                return a.getValue("SHA1-Digest");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // optDex finish
    public void installFinish(Context context) {
        SharedPreferences sp = context.getSharedPreferences(
                PackageUtil.getPackageInfo(context).versionName, Context.MODE_MULTI_PROCESS | Context.MODE_WORLD_READABLE);
        sp.edit().putString(KEY_DEX2_SHA1, get2thDexSHA1(context)).apply();
    }

    public static String getCurProcessName(Context context) {
        try {
            int pid = android.os.Process.myPid();
            ActivityManager mActivityManager = (ActivityManager) context
                    .getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningAppProcessInfo appProcess : mActivityManager
                    .getRunningAppProcesses()) {
                if (appProcess.pid == pid) {
                    return appProcess.processName;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    public void waitForDexopt(Context base) {
        Intent intent = new Intent();

        ComponentName componentName = new ComponentName(this, LoadResActivity.class);
        intent.setComponent(componentName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        base.startActivity(intent);
        long startWait = System.currentTimeMillis();
        long waitTime = 3 * 1000;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            waitTime = 10 * 1000;//实测发现某些场景下有些2.3版本有可能10s都不能完成optdex
        }
        while (needWait(base)) {
            try {
                long nowWait = System.currentTimeMillis() - startWait;
                Log.d("loadDex", "wait ms :" + nowWait);
                if (nowWait >= waitTime) {
                    Log.d("loadDex", "now wait ms :" + nowWait);
                    return;
                }
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    //===========================================================================

    long oncreatestart;

    @Override
    public void onCreate() {
        super.onCreate();

        String currentProcessName = getCurProcessName(this);
        if (!TextUtils.isEmpty(currentProcessName)) {
            if (currentProcessName.contains(":pushservice") || currentProcessName.contains(":daemon")) {
                Log.d("loadDex", "App onCreate current process : " + currentProcessName);
                return;
            }
        }
        if (quickStart()) {
            return;
        }
        oncreatestart = System.currentTimeMillis();

        Log.d("loadDex", "onCreate end " + System.currentTimeMillis() + " oncreate  used : " + (System.currentTimeMillis() - oncreatestart) + "ms");
    }


    private boolean shouldInit() {
        ActivityManager am = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE));
        List<ActivityManager.RunningAppProcessInfo> processInfos = am.getRunningAppProcesses();
        String mainProcessName = getPackageName();
        int myPid = android.os.Process.myPid();
        for (ActivityManager.RunningAppProcessInfo info : processInfos) {
            if (info.pid == myPid && mainProcessName.equals(info.processName)) {
                return true;
            }
        }
        return false;
    }
}