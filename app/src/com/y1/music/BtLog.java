package com.y1.music;

import java.io.*;

/** Always-on Bluetooth log -> /storage/sdcard1/y1-bt.log (diagnosing car pairing). Keeps only bluetooth-looking lines; one thread, dies with the app. */
class BtLog {
    static boolean on;
    static final String F = "/storage/sdcard1/y1-bt.log";
    static final java.util.regex.Pattern P = java.util.regex.Pattern.compile("(?i)bluetooth|a2dp|avrcp|hfp|headset|bluedroid|btif|bta_|bt_|pair|bond|sdp|rfcomm|l2c");

    static synchronized void start() {
        if (on) return;
        on = true;
        new Thread() { public void run() {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "time"});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                PrintWriter w = new PrintWriter(new FileWriter(F, true), true);
                w.println("=== BtLog start ===");
                long n = new File(F).length();
                for (String l; (l = r.readLine()) != null; ) {
                    if (!P.matcher(l).find()) continue;
                    w.println(l);
                    if ((n += l.length() + 1) > 3000000) { // ponytail: internal storage (SD may be unmounted at boot, killed the old log); 3 MB cap, keep one .old
                        w.close(); new File(F).renameTo(new File(F + ".old"));
                        w = new PrintWriter(new FileWriter(F, true), true); n = 0;
                    }
                }
            } catch (Throwable t) { on = false; }
        } }.start();
    }
}
