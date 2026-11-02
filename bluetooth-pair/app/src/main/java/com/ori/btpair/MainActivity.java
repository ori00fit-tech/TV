package com.ori.btpair;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds and pairs Bluetooth headphones and speakers, for TV firmware that hides its Bluetooth screen (CVTE).
 * Pairing uses the public createBond(); the system's own pairing dialog and profile auto-connect do the rest.
 */
@SuppressLint("MissingPermission") // every Bluetooth call runs only after hasPermissions()
public class MainActivity extends Activity {

    private static final int REQ_PERMS = 1;
    private static final int REQ_ENABLE = 2;

    private BluetoothAdapter adapter;
    private BluetoothA2dp a2dp;
    private final Map<String, BluetoothDevice> found = new LinkedHashMap<>();

    private TextView status;
    private Button actionButton;
    private LinearLayout pairedList;
    private LinearLayout foundList;
    private TextView foundTitle;
    private boolean receiverOn;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action) {
                case BluetoothDevice.ACTION_FOUND:
                    if (device != null && device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        found.put(device.getAddress(), device);
                    }
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    if (device != null && state == BluetoothDevice.BOND_BONDED) {
                        found.remove(device.getAddress());
                        toast(getString(R.string.paired_ok, nameOf(device)));
                    }
                    break;
                default:
                    break;
            }
            refresh();
        }
    };

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.A2DP) {
                a2dp = (BluetoothA2dp) proxy;
                refresh();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) a2dp = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && hasPermissions()) start();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null && hasPermissions() && adapter.isDiscovering()) adapter.cancelDiscovery();
    }

    @Override
    protected void onDestroy() {
        if (receiverOn) unregisterReceiver(receiver);
        if (adapter != null && a2dp != null) adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp);
        super.onDestroy();
    }

    private void start() {
        if (!receiverOn) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, filter);
            }
            receiverOn = true;
        }
        if (a2dp == null) adapter.getProfileProxy(this, profileListener, BluetoothProfile.A2DP);
    }

    private boolean hasPermissions() {
        for (String p : neededPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[] {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        }
        return new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (hasPermissions()) start();
        refresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        refresh();
    }

    // ---- UI ----

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 20, 24));
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(32);
        root.setPadding(pad * 2, pad, pad * 2, pad);
        scroll.addView(root);

        TextView title = text(getString(R.string.title), 30, Color.WHITE);
        root.addView(title);

        status = text("", 18, Color.rgb(176, 190, 197));
        status.setPadding(0, dp(8), 0, dp(16));
        root.addView(status);

        actionButton = button("");
        actionButton.setOnClickListener(v -> onAction());
        root.addView(actionButton, wrap());

        root.addView(section(getString(R.string.paired)));
        pairedList = new LinearLayout(this);
        pairedList.setOrientation(LinearLayout.VERTICAL);
        root.addView(pairedList);

        foundTitle = section(getString(R.string.found));
        root.addView(foundTitle);
        foundList = new LinearLayout(this);
        foundList.setOrientation(LinearLayout.VERTICAL);
        root.addView(foundList);

        setContentView(scroll);
        actionButton.requestFocus();
    }

    private void onAction() {
        if (adapter == null) return;
        if (!hasPermissions()) {
            requestPermissions(neededPermissions(), REQ_PERMS);
        } else if (!adapter.isEnabled()) {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE);
            } catch (Exception e) {
                toast(getString(R.string.bt_off));
            }
        } else if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        } else {
            found.clear();
            adapter.startDiscovery();
        }
        refresh();
    }

    private void refresh() {
        if (status == null) return;
        if (adapter == null) {
            status.setText(R.string.no_bluetooth);
            actionButton.setVisibility(View.GONE);
            return;
        }
        boolean perms = hasPermissions();
        if (!perms) {
            status.setText(R.string.need_permission);
            actionButton.setText(R.string.allow);
        } else if (!adapter.isEnabled()) {
            status.setText(R.string.bt_off);
            actionButton.setText(R.string.turn_on);
        } else if (adapter.isDiscovering()) {
            status.setText(R.string.hint);
            actionButton.setText(R.string.searching);
        } else {
            status.setText(R.string.hint);
            actionButton.setText(R.string.search);
        }

        pairedList.removeAllViews();
        foundList.removeAllViews();
        if (!perms || !adapter.isEnabled()) return;

        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        List<BluetoothDevice> paired = sorted(bonded != null ? new ArrayList<>(bonded) : new ArrayList<>());
        if (paired.isEmpty()) {
            pairedList.addView(text(getString(R.string.none_paired), 18, Color.GRAY));
        }
        for (BluetoothDevice d : paired) {
            String state = isConnected(d) ? getString(R.string.connected) : getString(R.string.not_connected);
            Button b = button(label(d) + "  ·  " + state);
            b.setOnClickListener(v -> showPairedMenu(d));
            pairedList.addView(b, wrap());
        }

        List<BluetoothDevice> nearby = sorted(new ArrayList<>(found.values()));
        foundTitle.setVisibility(nearby.isEmpty() && !adapter.isDiscovering() ? View.GONE : View.VISIBLE);
        if (nearby.isEmpty() && adapter.isDiscovering()) {
            foundList.addView(text(getString(R.string.none_found), 18, Color.GRAY));
        }
        for (BluetoothDevice d : nearby) {
            String suffix = d.getBondState() == BluetoothDevice.BOND_BONDING ? "  ·  " + getString(R.string.pairing) : "";
            Button b = button(label(d) + suffix);
            b.setOnClickListener(v -> pair(d));
            foundList.addView(b, wrap());
        }
    }

    private void pair(BluetoothDevice d) {
        if (adapter.isDiscovering()) adapter.cancelDiscovery();
        if (d.createBond()) {
            toast(getString(R.string.pairing_with, nameOf(d)));
        } else {
            toast(getString(R.string.pair_failed, nameOf(d)));
        }
        refresh();
    }

    private void showPairedMenu(BluetoothDevice d) {
        boolean connected = isConnected(d);
        String[] items = {
            getString(connected ? R.string.disconnect : R.string.connect),
            getString(R.string.unpair)
        };
        new AlertDialog.Builder(this)
            .setTitle(nameOf(d))
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    if (!callA2dp(connected ? "disconnect" : "connect", d) && !connected) {
                        toast(getString(R.string.connect_hint));
                    }
                } else if (!callDevice("removeBond", d)) {
                    toast(getString(R.string.unpair_failed));
                }
                refresh();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    // ---- Bluetooth helpers ----

    private boolean isConnected(BluetoothDevice d) {
        if (a2dp != null) {
            try {
                if (a2dp.getConnectionState(d) == BluetoothProfile.STATE_CONNECTED) return true;
            } catch (Exception ignored) {
            }
        }
        return callDevice("isConnected", d);
    }

    /** Hidden BluetoothA2dp.connect/disconnect: may be refused on newer Android; then the device reconnects itself. */
    private boolean callA2dp(String method, BluetoothDevice d) {
        if (a2dp == null) return false;
        try {
            Method m = BluetoothA2dp.class.getMethod(method, BluetoothDevice.class);
            Object r = m.invoke(a2dp, d);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean callDevice(String method, BluetoothDevice d) {
        try {
            Method m = BluetoothDevice.class.getMethod(method);
            Object r = m.invoke(d);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isAudio(BluetoothDevice d) {
        BluetoothClass c = d.getBluetoothClass();
        return c != null && c.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }

    private List<BluetoothDevice> sorted(List<BluetoothDevice> list) {
        Collections.sort(list, (a, b) -> {
            boolean aa = isAudio(a), ba = isAudio(b);
            if (aa != ba) return aa ? -1 : 1;
            return nameOf(a).compareToIgnoreCase(nameOf(b));
        });
        return list;
    }

    private String nameOf(BluetoothDevice d) {
        String n = d.getName();
        return n == null || n.trim().isEmpty() ? d.getAddress() : n;
    }

    private String label(BluetoothDevice d) {
        return isAudio(d) ? getString(R.string.audio_tag) + "  " + nameOf(d) : nameOf(d);
    }

    // ---- view helpers ----

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 20, Color.rgb(100, 181, 246));
        t.setPadding(0, dp(24), 0, dp(8));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setFocusable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, rounded(Color.rgb(25, 118, 210)));
        bg.addState(new int[] {android.R.attr.state_pressed}, rounded(Color.rgb(21, 101, 192)));
        bg.addState(new int[] {}, rounded(Color.rgb(38, 50, 56)));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        return b;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(12));
        return g;
    }

    private LinearLayout.LayoutParams wrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(6));
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
