package com.example.sc2079_group25.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sc2079_group25.R;
import com.example.sc2079_group25.bluetooth.BluetoothEventListener;
import com.example.sc2079_group25.bluetooth.BluetoothSerialService;
import com.example.sc2079_group25.bluetooth.BtConstants;
import com.example.sc2079_group25.bluetooth.DeviceListAdapter;

import java.util.Set;

public class BluetoothTerminalActivity extends AppCompatActivity implements BluetoothEventListener {

    private BluetoothAdapter btAdapter;
    private BluetoothSerialService serial;

    private DeviceListAdapter deviceAdapter;

    private TextView txtConnState, txtTerminal;
    private EditText edtSend;
    private ScrollView scrollTerminal;

    private boolean pendingStartDiscovery = false;

    private final ActivityResultLauncher<Intent> enableBtLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (btAdapter != null && btAdapter.isEnabled()) {
                    if (pendingStartDiscovery) {
                        pendingStartDiscovery = false;
                        startScanFlow();
                    }
                    preloadPairedDevices();
                } else {
                    Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_LONG).show();
                }
            });

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) deviceAdapter.upsert(device);

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                appendTerminal("[Scan] Discovery finished");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_terminal);

        txtConnState = findViewById(R.id.txtConnState);
        txtTerminal = findViewById(R.id.txtTerminal);
        edtSend = findViewById(R.id.edtSend);
        scrollTerminal = findViewById(R.id.scrollTerminal);

        Button btnScan = findViewById(R.id.btnScan);
        Button btnSend = findViewById(R.id.btnSend);
        Button btnDisconnect = findViewById(R.id.btnDisconnect);
        Button btnReconnect = findViewById(R.id.btnReconnect);

        RecyclerView recycler = findViewById(R.id.recyclerDevices);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        deviceAdapter = new DeviceListAdapter(this, this::onDeviceSelected);
        recycler.setAdapter(deviceAdapter);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        serial = new BluetoothSerialService(this, this);

        btnScan.setOnClickListener(v -> {
            pendingStartDiscovery = true;
            startScanFlow();
        });

        btnDisconnect.setOnClickListener(v -> serial.disconnect());

        btnReconnect.setOnClickListener(v -> {
            if (!hasConnectPermission()) {
                requestBtPermissions();
                return;
            }
            serial.reconnect();
        });

        btnSend.setOnClickListener(v -> {
            String text = edtSend.getText().toString();
            if (text.trim().isEmpty()) return;

            serial.writeLine(text);
            appendTerminal("[TX] " + text);
            edtSend.setText("");
        });

        ensureBluetoothEnabled();
        preloadPairedDevices();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryReceiver, f);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (Exception ignored) {}

        if (btAdapter == null) return;
        try {
            if (hasScanPermission()) {
                if (btAdapter.isDiscovering()) {
                    btAdapter.cancelDiscovery();
                }
            }
        } catch (SecurityException ignored) {}
    }

    private void ensureBluetoothEnabled() {
        if (!btAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtLauncher.launch(intent);
        }
    }

    private void preloadPairedDevices() {
        if (btAdapter == null || !btAdapter.isEnabled() || !hasConnectPermission()) return;

        try {
            Set<BluetoothDevice> bonded = btAdapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) deviceAdapter.upsert(d);
            }
        } catch (SecurityException ignored) {}
    }

    private void startScanFlow() {
        if (btAdapter == null) return;
        if (!btAdapter.isEnabled()) {
            ensureBluetoothEnabled();
            return;
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            requestBtPermissions();
            return;
        }
        startDiscovery();
    }

    private void startDiscovery() {
        if (btAdapter == null) return;
        deviceAdapter.clear();
        preloadPairedDevices();
        try {
            if (btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
            btAdapter.startDiscovery();
            appendTerminal("[Scan] Discovery started");
        } catch (SecurityException ignored) {}
    }

    private void onDeviceSelected(BluetoothDevice device) {
        if (!hasConnectPermission()) {
            requestBtPermissions();
            return;
        }
        try {
            if (btAdapter != null && btAdapter.isDiscovering()) btAdapter.cancelDiscovery();
        } catch (SecurityException ignored) {}
        serial.connect(device);
    }

    private void appendTerminal(String line) {
        txtTerminal.append(line + "\n");
        scrollTerminal.post(() -> scrollTerminal.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, 2001);
        } else {
            requestPermissions(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 2002);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = (grantResults.length > 0);
        for (int r : grantResults) granted &= (r == PackageManager.PERMISSION_GRANTED);

        if (!granted) {
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show();
            return;
        }
        if (pendingStartDiscovery) startDiscovery();
        else preloadPairedDevices();
    }

    @Override
    public void onConnectionStateChanged(int state, String detail) {
        String s;
        if (state == BtConstants.STATE_CONNECTED) s = "Connected";
        else if (state == BtConstants.STATE_CONNECTING) s = "Connecting";
        else s = "Not connected";

        txtConnState.setText(s + " - " + detail);
        appendTerminal("[State] " + s);
    }

    @Override
    public void onLineReceived(String line) {
        appendTerminal("[RX] " + line);
    }

    @Override
    public void onError(String message, Throwable t) {
        appendTerminal("[Error] " + message);
    }
}
