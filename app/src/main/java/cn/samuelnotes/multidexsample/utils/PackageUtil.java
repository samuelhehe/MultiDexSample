package cn.samuelnotes.multidexsample.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Created by samuelnotes on 2016/1/7.
 */
public class PackageUtil {

    public static PackageInfo getPackageInfo(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("PackageUtil", "" + e.getLocalizedMessage());
        }
        return new PackageInfo();
    }
}