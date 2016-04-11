package io.yile.hible;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 541;
    private static final long SCAN_PERIOD = 10000;

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private ListView mDevicesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 检查当前Android系统是否支持BLE
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // 检查手机是否支持蓝牙
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mHandler = new Handler();
        mLeDeviceListAdapter = new LeDeviceListAdapter();

        mDevicesList = (ListView)findViewById(R.id.list_devices);
        mDevicesList.setAdapter(mLeDeviceListAdapter);

        mDevicesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            /*
             * 点击某个蓝牙设备后, 启动连接该设备, 并停止扫描
             */
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) {
                    return;
                }

                /*
                 * 将选中的设备名和MAC地址传递给DeviceControlActivity
                 * 启动DeviceControlActivity
                 */
                final Intent intent = new Intent(MainActivity.this, DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                if (mScanning) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                }
                startActivity(intent);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 加载菜单
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // 显示打开BLE设备的菜单,隐藏扫描菜单
        menu.setGroupVisible(R.id.menu_group_open, true);
        menu.setGroupVisible(R.id.menu_group_list, false);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        /*
         * 如果手机已打开蓝牙,则显示扫描相关菜单; 否则, 显示打开蓝牙设备菜单
         */
        if (!mBluetoothAdapter.isEnabled()) {
            menu.setGroupVisible(R.id.menu_group_open, true);
            menu.setGroupVisible(R.id.menu_group_list, false);
        } else {
            menu.setGroupVisible(R.id.menu_group_open, false);
            menu.setGroupVisible(R.id.menu_group_list, true);
            if (mScanning) {
                menu.findItem(R.id.menu_scan).setEnabled(false);
                menu.findItem(R.id.menu_stop).setEnabled(true);
                menu.findItem(R.id.menu_refresh).setEnabled(false);
            } else {
                menu.findItem(R.id.menu_scan).setEnabled(true);
                menu.findItem(R.id.menu_stop).setEnabled(false);
                menu.findItem(R.id.menu_refresh).setEnabled(true);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_open:

                /*
                 * 弹出提示用户打开蓝牙设备
                 */
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return true;
            case R.id.menu_scan:
                // 启动扫描
                scanLeDevice(true);
                return true;
            case R.id.menu_stop:
                // 停止扫描
                scanLeDevice(false);
                return true;
            case R.id.menu_refresh:
                // 清空列表, 刷新设备
                scanLeDevice(false);
                mLeDeviceListAdapter.clear();
                mLeDeviceListAdapter.notifyDataSetChanged();
                scanLeDevice(true);
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            /*
             * 扫描开始, 并在SCAN_PERIOD时间后,自动停止扫描
             */
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                }
            }, SCAN_PERIOD);
            /*
             * 扫描所有BLE设备
             * 还可使用startLeScan(UUID[], BluetoothAdapter.LeScanCallback)，通过UUID来精确匹配设备.
             */
            mBluetoothAdapter.startLeScan(mLeScanCallback);
            mScanning = true;
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                             byte[] scanRecord) {

            /*
             * 启动工作线程刷新UI
             */
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    // er for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.list_device_item, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
            }
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }
    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
