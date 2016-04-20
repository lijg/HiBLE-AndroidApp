package io.yile.hible;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.UUID;

public class DeviceControlActivity extends AppCompatActivity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private Button mActionOnOff;
    private Button mActionConnect;
    private ImageView mImageBulb;
    private String mDeviceAddress;
    private String mDeviceName;
    private BluetoothGatt mBluetoothGatt;
    private boolean mConnectState;
    private BluetoothGattService mHiBLEService;
    private BluetoothGattCharacteristic mHiBLESWChar;
    private boolean mSWState;
    private boolean mWriteState, mReadState;
    private boolean mConnecting;
    private ProgressDialog mProgressDialog;

    /*
     * 连接蓝牙设备成功后，会调用此Callback
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS
                    && newState == BluetoothProfile.STATE_CONNECTED) {
                // 连接成功
                Log.v(TAG, "BluetoothGatt connected.");

                // 扫描该设备所支持的服务，完成后会调用onServicesDiscovered
                // 所以，此时即使连接成功了，设备服务仍然不可用
                gatt.discoverServices();
            } else if (status == BluetoothGatt.GATT_SUCCESS
                    && newState == BluetoothProfile.STATE_DISCONNECTED) {
                // 断开连接
                Log.v(TAG, "BluetoothGatt disconnected.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // 此时才可以访问设备支持的服务与特性
            mConnectState = true;
            mConnecting = false;
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mActionConnect.setText(getString(R.string.action_disconnect));
                    mProgressDialog.dismiss();
                }
            });

            // 获取HIBLE服务
            mHiBLEService = gatt.getService(DeviceProfile.UUID_HIBLE_SERVICE);
            if (mHiBLEService == null) {
                Log.e(TAG, "Cannot get service: " + DeviceProfile.UUID_HIBLE_SERVICE);
                return;
            }

            // 获取SW特性
            mHiBLESWChar = mHiBLEService.getCharacteristic(DeviceProfile.UUID_HIBLE_SW_CHAR);
            if (mHiBLESWChar == null) {
                Log.e(TAG, "Cannot get Characteristic: " + DeviceProfile.UUID_HIBLE_SW_CHAR);
                return;
            }

            readCharacteristic(mHiBLESWChar);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // 写完成
            mWriteState = true;
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            mReadState = true;
            final byte ret = characteristic.getValue()[0];
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mActionOnOff.setVisibility(View.VISIBLE);
                    mProgressDialog.dismiss();
                    if(ret == 1) {
                        mImageBulb.setImageDrawable(ContextCompat.getDrawable(DeviceControlActivity.this, R.drawable.bulb_on));
                        mActionOnOff.setText(getString(R.string.turn_off));
                        mSWState = true;
                    } else {
                        mImageBulb.setImageDrawable(ContextCompat.getDrawable(DeviceControlActivity.this, R.drawable.bulb_off));
                        mActionOnOff.setText(getString(R.string.turn_on));
                        mSWState = false;
                    }
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_control);

        /*
         * 获取传递过来的name和address
         */
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(mDeviceName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mActionOnOff = (Button) findViewById(R.id.button_blinky);
        mActionConnect = (Button) findViewById(R.id.action_connect);
        mImageBulb = (ImageView) findViewById(R.id.img_bulb);

        mActionOnOff.setVisibility(View.INVISIBLE);

        mConnectState = false;
        mHiBLEService = null;
        mHiBLESWChar = null;
        mSWState = false;
        mWriteState = true;
        mReadState = true;
        mConnecting = false;

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setTitle("Connecting");
        mProgressDialog.setMessage("Wait while connecting...");

        mActionConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mConnecting) {
                    if (!mConnectState) {
                    /*
                     * 启动连接
                     */
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (getDeviceByAddress(mDeviceAddress) != null) {
                                    mBluetoothGatt = getDeviceByAddress(mDeviceAddress).connectGatt(getApplicationContext(), true, mGattCallback);
                                    mConnecting = true;
                                    mProgressDialog.show();
                                }
                            }
                        });
                    } else {
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mBluetoothGatt != null) {
                                    disconnect();
                                    mActionConnect.setText(getApplicationContext().getString(R.string.action_connect));
                                    mActionOnOff.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
                    }
                }
            }
        });

        mActionOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mWriteState && mSWState) {
                    if (write(false)) {
                        mImageBulb.setImageDrawable(ContextCompat.getDrawable(DeviceControlActivity.this, R.drawable.bulb_off));
                        mActionOnOff.setText(getString(R.string.turn_on));
                    }
                } else if(mWriteState) {
                    if (write(true)) {
                        mImageBulb.setImageDrawable(ContextCompat.getDrawable(DeviceControlActivity.this, R.drawable.bulb_on));
                        mActionOnOff.setText(getString(R.string.turn_off));
                    }
                }
                mSWState = !mSWState;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    private void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        mConnectState = false;
    }

    public boolean write(final boolean onOff) {
        // 检查是否连接成功
        if (mHiBLESWChar == null) {
            return false;
        }

        byte [] command;
        if (onOff){
            command = new byte [] {1};
        } else {
            command = new byte [] {0};
        }
        mHiBLESWChar.setValue(command);
        return writeCharacteristic(mHiBLESWChar);
    }

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mConnectState == false || mHiBLEService == null || mHiBLESWChar == null || mBluetoothGatt == null || mWriteState == false) {
            return false;
        }

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0)
            return false;

        boolean ret = mBluetoothGatt.writeCharacteristic(characteristic);
        mWriteState = false;
        return ret;
    }

    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mConnectState == false || mHiBLEService == null || mHiBLESWChar == null || mBluetoothGatt == null || mReadState == false) {
            return false;
        }

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
            return false;

        boolean ret = mBluetoothGatt.readCharacteristic(characteristic);
        mReadState = false;
        return ret;
    }

    private BluetoothDevice getDeviceByAddress(final String address) {
        BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager == null) {
            return null;
        }

        BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            return null;
        }

        return mBluetoothAdapter.getRemoteDevice(address);
    }
}