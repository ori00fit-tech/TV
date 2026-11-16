package com.ori.screencast;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** A launcher tile for the TV's built-in Miracast receiver, which has no launcher entry of its own. */
public class MainActivity extends Activity {

    static final ComponentName RECEIVER =
        new ComponentName("com.droidlogic.miracast", "com.droidlogic.miracast.WiFiDirectMainActivity");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(RECEIVER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
