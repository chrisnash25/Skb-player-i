package com.skb.aiplayer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;

/** Experimental BLE GATT command transport; HID pairing is separate. */
public final class BleControllerBridge implements ControllerEngine.Sink {
    public interface Listener { void onStatus(String message); }
    public static final UUID SERVICE = UUID.fromString("7a31f0e0-5bd1-4df5-b36e-6c8c1b319001");
    public static final UUID COMMAND = UUID.fromString("7a31f0e0-5bd1-4df5-b36e-6c8c1b319002");
    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic command;
    private boolean scanning;
    private boolean connected;
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
            if ("SKB AI Controller".equals(name)) {
                stopScan();
                post("SKB bridge found; connecting GATT...");
                gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        }
        @Override public void onScanFailed(int errorCode) {
            scanning = false; post("BLE scan failed: " + errorCode);
        }
    };
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
            if (status == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                post("GATT connected; discovering services...");
                g.discoverServices();
            } else {
                connected = false; command = null;
                post("BLE disconnected (status " + status + ")");
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService service = g.getService(SERVICE);
            command = status == BluetoothGatt.GATT_SUCCESS && service != null ? service.getCharacteristic(COMMAND) : null;
            connected = command != null;
            post(connected ? "SKB command characteristic ready (HID not yet verified)" : "SKB command service unavailable");
        }
    };
    public BleControllerBridge(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
    }
    private void post(String message) { handler.post(() -> listener.onStatus(message)); }
    public boolean isReady() { return connected && command != null; }
    public boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= 31) return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    @SuppressLint("MissingPermission")
    public void connect() {
        if (!hasPermission()) { post("Bluetooth permissions required"); return; }
        BluetoothManager manager = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { post("Enable Bluetooth first"); return; }
        disconnect();
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { post("BLE scanner unavailable"); return; }
        scanning = true;
        scanner.startScan(scanCallback);
        post("Searching for SKB AI Controller...");
        handler.postDelayed(() -> { if (scanning) { stopScan(); post("Scan timed out: no SKB bridge found"); } }, 15000);
    }
    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (scanning && scanner != null && hasPermission()) scanner.stopScan(scanCallback);
        scanning = false;
    }
    @SuppressLint("MissingPermission")
    public void disconnect() {
        stopScan(); connected = false; command = null;
        if (gatt != null) { gatt.disconnect(); gatt.close(); gatt = null; }
    }
    private static int signed(float x) { return Math.round(Math.max(-1f, Math.min(1f, x)) * 127f) & 255; }
    private static int trigger(float x) { return Math.round(Math.max(0f, Math.min(1f, x)) * 255f) & 255; }
    @Override @SuppressLint("MissingPermission")
    public void send(float lx,float ly,float rx,float ry,float lt,float rt,int buttons) {
        if (!isReady() || !hasPermission()) return;
        byte[] packet = {(byte)buttons,(byte)(buttons >>> 8),(byte)signed(lx),(byte)signed(ly),
                (byte)signed(rx),(byte)signed(ry),(byte)trigger(lt),(byte)trigger(rt)};
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(command,packet,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            command.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            command.setValue(packet); gatt.writeCharacteristic(command);
        }
    }
}
