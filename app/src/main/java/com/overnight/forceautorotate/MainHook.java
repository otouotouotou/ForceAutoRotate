package com.overnight.forceautorotate;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * スコープに入れたアプリに対して強制自動回転をかける LSPosed モジュール。
 * Modern Xposed API (libxposed) 102 版。
 *
 * 旧 API 82 版との違い:
 *  - エントリは IXposedHookLoadPackage ではなく XposedModule を継承し、
 *    META-INF/xposed/java_init.list に登録する (引数なしコンストラクタ必須)
 *  - パッケージの読み込み通知は onPackageReady で受ける
 *    (ClassLoader の準備が終わったタイミングで呼ばれる API 102 のコールバック)
 *  - フックは XC_MethodHook ではなく interceptor chain 形式:
 *    hook(method).intercept(chain -> ...) で、元の処理は chain.proceed()、
 *    引数の差し替えは chain.proceed(newArgs) で行う
 *  - API 102 以降、旧 de.robv.android.xposed API は併用できない
 *
 * やっていることは旧版と同じ:
 *  1. Activity の生成 / 再開のたびに setRequestedOrientation(FULL_SENSOR) を呼ぶ
 *  2. アプリ自身が setRequestedOrientation(固定向き) を呼んだら横取りして
 *     FULL_SENSOR に置き換える
 */
public class MainHook extends XposedModule {

    private static final String TAG = "ForceAutoRotate";
    private static final String OWN_PACKAGE = "com.overnight.forceautorotate";

    /** 同一プロセスへの二重登録防止 (onPackageReady は複数回呼ばれうる) */
    private boolean hooksInstalled = false;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        // モジュール自身のアプリにはかけない
        if (OWN_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        // そのプロセスで最初に読み込まれるパッケージのときに 1 回だけ入れる。
        // Activity はフレームワークのクラスなので、フックはプロセス全体に効く。
        if (!param.isFirstPackage() || hooksInstalled) {
            return;
        }
        hooksInstalled = true;

        log(Log.INFO, TAG, "applied to " + param.getPackageName());
        installHooks();
    }

    private void installHooks() {
        try {
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            Method setRequestedOrientation =
                    Activity.class.getDeclaredMethod("setRequestedOrientation", int.class);

            // 1-a. onCreate の直後に強制する (元の処理を先に流してから)
            hook(onCreate)
                    .setId("forceautorotate.onCreate")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        forceFullSensor((Activity) chain.getThisObject());
                        return result;
                    });

            // 1-b. onResume の直後にも強制する
            hook(onResume)
                    .setId("forceautorotate.onResume")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        forceFullSensor((Activity) chain.getThisObject());
                        return result;
                    });

            // 2. アプリが固定向きを指定してきたら FULL_SENSOR に差し替えて流す
            hook(setRequestedOrientation)
                    .setId("forceautorotate.setRequestedOrientation")
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int requested = (int) chain.getArg(0);
                        if (isFixedOrLimited(requested)) {
                            // API 102 の proceed は引数を Object[] で受け取る
                            return chain.proceed(new Object[]{
                                    ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR});
                        }
                        return chain.proceed();
                    });

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed to install hooks", t);
        }
    }

    private void forceFullSensor(Activity activity) {
        try {
            if (activity.getRequestedOrientation()
                    != ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR) {
                // この呼び出しも上のフックを通るが、FULL_SENSOR は素通しされる
                activity.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed for " + activity, t);
        }
    }

    /**
     * 「回転を制限する指定」かどうか。
     * FULL_SENSOR / SENSOR / FULL_USER / UNSPECIFIED などは制限ではないので素通し。
     */
    private static boolean isFixedOrLimited(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:         // 0
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:          // 1
            case ActivityInfo.SCREEN_ORIENTATION_NOSENSOR:          // 5
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:  // 6
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:   // 7
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE: // 8
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:  // 9
            case ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE:    // 11
            case ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT:     // 12
            case ActivityInfo.SCREEN_ORIENTATION_LOCKED:            // 14
                return true;
            default:
                return false;
        }
    }
}
