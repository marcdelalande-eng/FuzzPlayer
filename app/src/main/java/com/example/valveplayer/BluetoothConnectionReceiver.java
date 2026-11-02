package com.example.valveplayer;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class BluetoothConnectionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Prefs.isAutoCar(context)
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        String action = intent.getAction();
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                && !BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) return;
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (device == null) return;
        String address;
        try { address = device.getAddress(); } catch (SecurityException e) { return; }
        if (!Prefs.carDevices(context).contains(address)) return;

        boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
        Diagnostics.install(context);
        Diagnostics.log("BLUETOOTH", (connected ? "Connected " : "Disconnected ") + address);
        Prefs.setCarMode(context, connected);
        PlayerWidgetProvider.updateAll(context);
        Intent service = new Intent(context, PlaybackService.class)
                .setAction(PlaybackService.ACTION_COMMAND)
                .putExtra(PlaybackService.EXTRA_COMMAND,
                        connected ? PlaybackService.CMD_BT_CONNECTED : PlaybackService.CMD_BT_DISCONNECTED);
        try {
            // If Android disallows background service launch, log the restriction.
            context.startService(service);
        } catch (RuntimeException ex) {
            Diagnostics.log("BLUETOOTH", "Service start restricted; event could not be delivered", ex);
        }
    }
}
