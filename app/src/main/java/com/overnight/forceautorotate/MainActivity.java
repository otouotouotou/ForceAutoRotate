package com.overnight.forceautorotate;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.TextView;

/** モジュール自身の説明画面。それ以外の機能はない。 */
public class MainActivity extends Activity {

    private static final int BASE_PADDING = 48;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(BASE_PADDING, BASE_PADDING, BASE_PADDING, BASE_PADDING);
        tv.setTextSize(16);
        tv.setText(
                "Force Auto Rotate\n\n"
                        + "1. LSPosed マネージャーでこのモジュールを有効化\n"
                        + "2. スコープで、強制回転したいアプリを選ぶ\n"
                        + "3. 対象アプリを強制停止してから開き直す\n\n"
                        + "選んだアプリでは、縦固定の画面でも端末の傾きに合わせて"
                        + "4方向に自動回転するようになります。");
        setContentView(tv);

        // Android 15 (targetSdk 35) では edge-to-edge がデフォルトで強制される。
        // ステータスバー / ナビゲーションバーと文字が重ならないように
        // システムバーの insets 分だけパディングを足す。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tv.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(
                        BASE_PADDING + bars.left,
                        BASE_PADDING + bars.top,
                        BASE_PADDING + bars.right,
                        BASE_PADDING + bars.bottom);
                return insets;
            });
        }
    }
}
