package com.y1.music;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Auto-accept pairing: the stock Cancel/OK dialog can't be reached with the wheel (OK is to the right). Runs before Settings' receiver and aborts it. */
public class BtPairReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int v = i.getIntExtra("android.bluetooth.device.extra.PAIRING_VARIANT", -1);
        if (d == null) return;
        try {
            if (v == 0 || v == 1) { // PIN / passkey typed on this device (car shows a random one): wheel entry screen
                c.startActivity(new Intent(c, PinActivity.class).putExtra(BluetoothDevice.EXTRA_DEVICE, d).putExtra("passkey", v == 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            else if (v == 2 || v == 3) d.setPairingConfirmation(true); // passkey confirm / just works
            else return; // other variants: let the stock dialog show
            if (isOrderedBroadcast()) abortBroadcast();
        } catch (Throwable t) { /* stock dialog stays */ }
    }
}
