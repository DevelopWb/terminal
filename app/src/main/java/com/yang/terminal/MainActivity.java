package com.yang.terminal;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.yang.amapmoudle.CheckPermissionsActivity;

public class MainActivity extends CheckPermissionsActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startMyService();

    }

    /**
     * 启动service
     *
     */
    private void startMyService() {
        Intent startIntent = new Intent();
        startIntent.setClass(this, MyService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(startIntent);
        } else {
            // Pre-O behavior.
            startService(startIntent);
        }
        finish();
    }

}
