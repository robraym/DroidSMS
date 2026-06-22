package sms.droid.com.droidsms;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TrustedBluetooth {
    private static final Object LOCK = new Object();
    private static final Map<Integer, BluetoothProfile> PROFILE_PROXIES = new HashMap<>();
    private static final Set<Integer> REQUESTED_PROFILES = new HashSet<>();
    private static final BluetoothProfile.ServiceListener SERVICE_LISTENER =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    synchronized (LOCK) {
                        PROFILE_PROXIES.put(profile, proxy);
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    synchronized (LOCK) {
                        PROFILE_PROXIES.remove(profile);
                        REQUESTED_PROFILES.remove(profile);
                    }
                }
            };

    private TrustedBluetooth() {
    }

    static String getRequiredPermission() {
        return Manifest.permission.BLUETOOTH_CONNECT;
    }

    static boolean requiresRuntimePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    static boolean hasRequiredPermission(Context context) {
        return !requiresRuntimePermission()
                || ContextCompat.checkSelfPermission(context, getRequiredPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    static List<Device> getBondedDevices(Context context) {
        if (!hasRequiredPermission(context)) {
            return new ArrayList<>();
        }

        BluetoothAdapter adapter = getAdapter(context);
        if (adapter == null) {
            return new ArrayList<>();
        }

        Set<BluetoothDevice> bondedDevices;
        try {
            bondedDevices = adapter.getBondedDevices();
        } catch (SecurityException exception) {
            return new ArrayList<>();
        }

        List<Device> devices = new ArrayList<>();
        for (BluetoothDevice device : bondedDevices) {
            Device trustedDevice = toDevice(device);
            if (trustedDevice != null) {
                devices.add(trustedDevice);
            }
        }
        Collections.sort(devices, (first, second) ->
                first.getLabel().toLowerCase(Locale.getDefault())
                        .compareTo(second.getLabel().toLowerCase(Locale.getDefault())));
        return devices;
    }

    static boolean isAnyTrustedDeviceConnected(Context context) {
        return getConnectedTrustedDevice(context) != null;
    }

    static Device getConnectedTrustedDevice(Context context) {
        Set<String> trustedAddresses = AuthStore.getTrustedBluetoothAddresses(context);
        if (trustedAddresses.isEmpty() || !hasRequiredPermission(context)) {
            return null;
        }

        warmUp(context);
        for (Device device : getConnectedDevices(context)) {
            if (trustedAddresses.contains(device.address)) {
                String savedName = AuthStore.getTrustedBluetoothName(context, device.address);
                if (!TextUtils.isEmpty(savedName) && TextUtils.equals(device.name, device.address)) {
                    return new Device(savedName, device.address);
                }
                return device;
            }
        }
        return null;
    }

    static void warmUp(Context context) {
        if (!hasRequiredPermission(context)) {
            return;
        }

        BluetoothAdapter adapter = getAdapter(context);
        if (adapter == null) {
            return;
        }

        try {
            if (!adapter.isEnabled()) {
                return;
            }
        } catch (SecurityException exception) {
            return;
        }

        Context appContext = context.getApplicationContext();
        for (int profile : getSupportedProfiles()) {
            synchronized (LOCK) {
                if (PROFILE_PROXIES.containsKey(profile) || REQUESTED_PROFILES.contains(profile)) {
                    continue;
                }
                REQUESTED_PROFILES.add(profile);
            }

            boolean requested;
            try {
                requested = adapter.getProfileProxy(appContext, SERVICE_LISTENER, profile);
            } catch (SecurityException | IllegalArgumentException exception) {
                requested = false;
            }

            if (!requested) {
                synchronized (LOCK) {
                    REQUESTED_PROFILES.remove(profile);
                }
            }
        }
    }

    private static List<Device> getConnectedDevices(Context context) {
        Map<String, Device> devicesByAddress = new HashMap<>();
        addConnectedGattDevices(context, devicesByAddress);

        List<BluetoothProfile> profileProxies;
        synchronized (LOCK) {
            profileProxies = new ArrayList<>(PROFILE_PROXIES.values());
        }

        for (BluetoothProfile profile : profileProxies) {
            List<BluetoothDevice> devices;
            try {
                devices = profile.getConnectedDevices();
            } catch (SecurityException exception) {
                continue;
            }
            addDevices(devicesByAddress, devices);
        }

        return new ArrayList<>(devicesByAddress.values());
    }

    private static void addConnectedGattDevices(Context context, Map<String, Device> devicesByAddress) {
        BluetoothManager manager = (BluetoothManager) context.getApplicationContext()
                .getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return;
        }

        try {
            addDevices(devicesByAddress, manager.getConnectedDevices(BluetoothProfile.GATT));
            addDevices(devicesByAddress, manager.getConnectedDevices(BluetoothProfile.GATT_SERVER));
        } catch (SecurityException | IllegalArgumentException ignored) {
            // The profile may be unavailable on some devices.
        }
    }

    private static void addDevices(Map<String, Device> devicesByAddress, List<BluetoothDevice> devices) {
        for (BluetoothDevice bluetoothDevice : devices) {
            Device device = toDevice(bluetoothDevice);
            if (device != null) {
                devicesByAddress.put(device.address, device);
            }
        }
    }

    private static int[] getSupportedProfiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return new int[]{
                    BluetoothProfile.HEADSET,
                    BluetoothProfile.A2DP,
                    BluetoothProfile.GATT,
                    BluetoothProfile.GATT_SERVER,
                    BluetoothProfile.HEARING_AID,
                    BluetoothProfile.HID_DEVICE
            };
        }

        return new int[]{
                BluetoothProfile.HEADSET,
                BluetoothProfile.A2DP,
                BluetoothProfile.GATT,
                BluetoothProfile.GATT_SERVER
        };
    }

    private static BluetoothAdapter getAdapter(Context context) {
        BluetoothManager manager = (BluetoothManager) context.getApplicationContext()
                .getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return null;
        }
        return manager.getAdapter();
    }

    private static Device toDevice(BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null) {
            return null;
        }

        try {
            String address = bluetoothDevice.getAddress();
            if (TextUtils.isEmpty(address)) {
                return null;
            }

            String name = bluetoothDevice.getName();
            if (TextUtils.isEmpty(name)) {
                name = address;
            }
            return new Device(name, address);
        } catch (SecurityException exception) {
            return null;
        }
    }

    static final class Device {
        final String name;
        final String address;

        Device(String name, String address) {
            this.name = name;
            this.address = address;
        }

        String getLabel() {
            return TextUtils.isEmpty(name) ? address : name;
        }
    }
}
