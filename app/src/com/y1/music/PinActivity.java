package com.y1.music;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;

/** Wheel PIN entry for pairing (car shows a random PIN). Wheel = pick digit 0-9 or OK, center = add digit / submit, back = delete / cancel. */
public class PinActivity extends Base {
    BluetoothDevice dev;
    boolean passkey, done;
    String pin = "";
    int cand; // 0-9 = digit, 10 = OK
    TextView t;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        dev = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        passkey = i.getBooleanExtra("passkey", false);
        t = new TextView(this);
        t.setTextSize(30);
        t.setGravity(android.view.Gravity.CENTER);
        setContentView(t);
        show();
    }

    @Override void onTheme() { if (t != null) { t.setBackgroundColor(bg()); t.setTextColor(fg()); } }

    void show() {
        t.setText("Enter PIN from car\n\n" + pin + "[" + (cand == 10 ? "OK" : "" + cand) + "]\n\ncenter: add   back: delete");
    }

    void finishPair() {
        done = true;
        try {
            if (passkey) BluetoothDevice.class.getMethod("setPasskey", int.class).invoke(dev, Integer.parseInt(pin));
            else dev.setPin(pin.getBytes());
        } catch (Exception e) { PlayerService.log("pin failed: " + e); }
        finish();
    }

    @Override protected void onDestroy() {
        if (!done) try { BluetoothDevice.class.getMethod("cancelPairingUserInput").invoke(dev); } catch (Exception e) { /* pairing times out */ }
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_DOWN) cand = (cand + 1) % 11;
        else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_UP) cand = (cand + 10) % 11;
        else if (k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (cand == 10) { if (pin.length() > 0) finishPair(); return true; }
            if (pin.length() < 16) pin += cand;
        } else if (k == KeyEvent.KEYCODE_BACK) {
            if (pin.length() == 0) return super.onKeyDown(k, e); // cancel
            pin = pin.substring(0, pin.length() - 1);
        } else return super.onKeyDown(k, e);
        show();
        return true;
    }
}
