package com.example.valveplayer;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CarSettingsActivity extends Activity {
    private final Set<String> selected = new HashSet<>();
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_settings);
        selected.addAll(Prefs.carDevices(this));
        Switch auto = findViewById(R.id.switchAuto), manual = findViewById(R.id.switchManualCar);
        auto.setChecked(Prefs.isAutoCar(this)); manual.setChecked(Prefs.isCarMode(this));
        auto.setOnCheckedChangeListener((v, checked) -> Prefs.setAutoCar(this, checked));
        manual.setOnCheckedChangeListener((v, checked) -> { Prefs.setCarMode(this, checked); PlayerWidgetProvider.updateAll(this); });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        populate();
    }

    private void populate() {
        LinearLayout box = findViewById(R.id.deviceContainer); box.removeAllViews();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            TextView t = new TextView(this); t.setText("Autorisation Bluetooth requise. Retourne à l'écran principal pour l'accorder."); t.setTextColor(getColor(R.color.amp_cream)); box.addView(t); return;
        }
        BluetoothManager bm = getSystemService(BluetoothManager.class); BluetoothAdapter a = bm == null ? null : bm.getAdapter();
        if (a == null) { TextView t = new TextView(this); t.setText("Bluetooth indisponible."); t.setTextColor(getColor(R.color.amp_cream)); box.addView(t); return; }
        List<BluetoothDevice> devices = new ArrayList<>(a.getBondedDevices());
        devices.sort(Comparator.comparing(d -> d.getName() == null ? d.getAddress() : d.getName(), String.CASE_INSENSITIVE_ORDER));
        for (BluetoothDevice d : devices) {
            CheckBox cb = new CheckBox(this); String name = d.getName() == null ? "Périphérique" : d.getName();
            cb.setText(name + "\n" + d.getAddress()); cb.setTextColor(getColor(R.color.amp_cream)); cb.setPadding(0, 10, 0, 10); cb.setChecked(selected.contains(d.getAddress()));
            cb.setOnCheckedChangeListener((button, checked) -> { if (checked) selected.add(d.getAddress()); else selected.remove(d.getAddress()); Prefs.setCarDevices(this, selected); });
            box.addView(cb);
        }
        if (devices.isEmpty()) { TextView t = new TextView(this); t.setText("Aucun périphérique Bluetooth appairé."); t.setTextColor(getColor(R.color.amp_dim)); box.addView(t); }
    }
}
