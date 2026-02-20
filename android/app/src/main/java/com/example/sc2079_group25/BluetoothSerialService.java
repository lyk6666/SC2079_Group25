package com.example.sc2079_group25;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

public class BluetoothSerialService {
    private static final String TAG = "BtSerialService";
    private static final int AUTO_RECONNECT_DELAY_MS = 5000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothEventListener listener;
    private final Context appContext;
    private final SharedPreferences prefs;

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private volatile int state = BtConstants.STATE_NONE;
    private boolean persistentConnection = false;

    public BluetoothSerialService(Context context, BluetoothEventListener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.prefs = appContext.getSharedPreferences(BtConstants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized int getState() { return state; }

    public synchronized void connect(BluetoothDevice device) {
        connectInternal(device);
    }

    private synchronized void connectInternal(BluetoothDevice device) {
        disconnectInternal();

        updateState(
                BtConstants.STATE_CONNECTING,
                "Connecting to " + safeDeviceName(device)
        );

        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    public void reconnect() {
        String lastAddr = prefs.getString(BtConstants.KEY_LAST_DEVICE, null);
        if (lastAddr == null) {
            postError("No last device saved", null);
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return;

        try {
            BluetoothDevice device = adapter.getRemoteDevice(lastAddr);
            connectInternal(device);
        } catch (IllegalArgumentException e) {
            postError("Invalid device address: " + lastAddr, e);
        }
    }

    public synchronized void disconnect() {
        persistentConnection = false;
        disconnectInternal();
        updateState(BtConstants.STATE_NONE, "Disconnected");
    }

    public void enableAutoReconnect() {
        persistentConnection = true;
    }

    public void disableAutoReconnect() {
        persistentConnection = false;
    }

    private synchronized void disconnectInternal() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        state = BtConstants.STATE_NONE;
    }

    public void writeLine(String text) {
        ConnectedThread r;
        synchronized (this) {
            if (state != BtConstants.STATE_CONNECTED || connectedThread == null) return;
            r = connectedThread;
        }
        if (!text.endsWith("\r\n")) {
            if (text.endsWith("\n")) text = text.substring(0, text.length() - 1) + "\r\n";
            else text = text + "\r\n";
        }
        Log.d(TAG, "Writing: " + text.replace("\r", "[R]").replace("\n", "[N]"));
        r.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private void updateState(int newState, String detail) {
        state = newState;
        mainHandler.post(() -> listener.onConnectionStateChanged(newState, detail));
    }

    private void postLine(String line) {
        mainHandler.post(() -> listener.onLineReceived(line));
    }

    private void postError(String msg, Throwable t) {
        mainHandler.post(() -> listener.onError(msg, t));
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String safeDeviceName(BluetoothDevice device) {
        if (device == null) return "Unknown device";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasConnectPermission()) {
                return device.getAddress();
            }
            String name = device.getName();
            return (name != null && !name.isEmpty()) ? name : device.getAddress();
        } catch (SecurityException e) {
            return device.getAddress();
        }
    }

    private void saveLastDevice(BluetoothDevice device) {
        prefs.edit().putString(BtConstants.KEY_LAST_DEVICE, device.getAddress()).apply();
    }

    private class ConnectThread extends Thread {
        private final BluetoothDevice device;
        private BluetoothSocket socket;

        ConnectThread(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void run() {
            setName("ConnectThread-" + device.getAddress());

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                try {
                    if (hasConnectPermission()) adapter.cancelDiscovery();
                } catch (SecurityException ignored) {}
            }

            try {
                try {
                    Log.d(TAG, "Attempting Secure RFCOMM connection...");
                    socket = device.createRfcommSocketToServiceRecord(BtConstants.SPP_UUID);
                    socket.connect();
                } catch (IOException e) {
                    Log.w(TAG, "Secure connect failed, trying insecure...", e);
                    try {
                        socket = device.createInsecureRfcommSocketToServiceRecord(BtConstants.SPP_UUID);
                        socket.connect();
                    } catch (IOException e2) {
                        Log.w(TAG, "Insecure failed, trying fallback...", e2);
                        Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                        socket = (BluetoothSocket) m.invoke(device, 1);
                        socket.connect();
                    }
                }

                saveLastDevice(device);

                synchronized (BluetoothSerialService.this) {
                    if (state == BtConstants.STATE_NONE) {
                        try { socket.close(); } catch (IOException ignored) {}
                        return;
                    }
                    updateState(BtConstants.STATE_CONNECTED, "Connected to " + safeDeviceName(device));

                    connectThread = null;
                    connectedThread = new ConnectedThread(socket);
                    connectedThread.start();
                }

            } catch (Exception e) {
                Log.e(TAG, "Connect failed", e);
                postError("Connection failed: " + e.getMessage(), e);
                updateState(BtConstants.STATE_NONE, "Connection failed");
                try {
                    if (socket != null) socket.close();
                } catch (IOException ignored) {}

                synchronized (BluetoothSerialService.this) {
                    if (persistentConnection) {
                        mainHandler.postDelayed(BluetoothSerialService.this::reconnect, AUTO_RECONNECT_DELAY_MS);
                    }
                }
            }
        }

        void cancel() {
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream in;
        private final OutputStream out;
        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

        ConnectedThread(BluetoothSocket socket) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        @Override
        public void run() {
            setName("ConnectedThread");
            byte[] buffer = new byte[1024];

            try {
                Log.d(TAG, "ConnectedThread started, waiting for data...");
                while (state == BtConstants.STATE_CONNECTED) {
                    int n = in.read(buffer);
                    if (n == -1) {
                        Log.d(TAG, "InputStream.read() returned -1 (EOF)");
                        break;
                    }

                    Log.d(TAG, "Read " + n + " bytes from device");
                    String rawData = new String(buffer, 0, n, StandardCharsets.UTF_8);
                    postLine(rawData.replace("\r", "[R]").replace("\n", "[N]"));

                    for (int i = 0; i < n; i++) {
                        byte b = buffer[i];
                        if (b == '\n' || b == '\r') {
                            if (lineBuffer.size() > 0) {
                                byte[] bytes = lineBuffer.toByteArray();
                                String line = new String(bytes, StandardCharsets.UTF_8).trim();
                                if (!line.isEmpty()) {
                                    postLine("MSG: " + line);
                                }
                                lineBuffer.reset();
                            }
                        } else {
                            lineBuffer.write(b);
                        }
                    }
                }
            } catch (IOException e) {
                if (state == BtConstants.STATE_CONNECTED) {
                    Log.e(TAG, "ConnectedThread IOException during read", e);
                    postError("Connection lost: " + e.getMessage(), e);
                }
            } finally {
                Log.d(TAG, "ConnectedThread exiting");
                cancel();
                synchronized (BluetoothSerialService.this) {
                    if (state == BtConstants.STATE_CONNECTED) {
                        updateState(BtConstants.STATE_NONE, "Disconnected");
                    }
                    if (persistentConnection) {
                        mainHandler.postDelayed(BluetoothSerialService.this::reconnect, AUTO_RECONNECT_DELAY_MS);
                    }
                }
            }
        }

        void write(byte[] bytes) {
            try {
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error during write", e);
                postError("Send failed", e);
            }
        }

        void cancel() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
