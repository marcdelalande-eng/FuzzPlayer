package com.example.valveplayer;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class BluetoothConnectionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Prefs.isAutoCar(context) || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (device == null || !Prefs.carDevices(context).contains(device.getAddress())) return;
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) Prefs.setCarMode(context, true);
        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) Prefs.setCarMode(context, false);
        PlayerWidgetProvider.updateAll(context);
    }
}
