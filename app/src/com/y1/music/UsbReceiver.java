package com.y1.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Cable unplugged: systemui's touch-only UsbStorageActivity stays on top and the wheel can't dismiss it. Bring our task back. */
public class UsbReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if ("android.hardware.usb.action.USB_STATE".equals(i.getAction()) && i.getBooleanExtra("connected", true)) return;
        c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); // ponytail: no CLEAR_TOP, keeps the current screen
    }
}
