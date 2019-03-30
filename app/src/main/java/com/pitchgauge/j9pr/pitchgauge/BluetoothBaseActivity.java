package com.pitchgauge.j9pr.pitchgauge;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static com.pitchgauge.j9pr.pitchgauge.BluetoothPipe.REQUEST_CONNECT_DEVICE;
import static com.pitchgauge.j9pr.pitchgauge.BluetoothPipe.REQUEST_ENABLE_BT;
import static com.pitchgauge.j9pr.pitchgauge.BluetoothState.STATE_NONE;

public class BluetoothBaseActivity extends AppCompatActivity {

    protected final String TAG = "BLActivity";

    protected BluetoothService mBluetoothService;

    public byte[] writeBuffer;
    public byte[] readBuffer;
    protected boolean isOpen;

    protected Handler mHandler ;
    protected boolean mIsBound;
    protected BluetoothPipe mBluetoothPipe;
    protected BluetoothDevice device;
    protected DeviceTag mDeviceTag;

    protected ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            if (iBinder instanceof BluetoothService.BackgroundBluetoothBinder) {
                mBluetoothService = ((BluetoothService.BackgroundBluetoothBinder) iBinder).service();

                if(!mBluetoothPipe.isBluetoothEnabled()) {
                    mBluetoothPipe.enable();
                } else if (!mBluetoothPipe.isServiceAvailable()) {
                    mBluetoothPipe.setupService(mBluetoothService, mHandler);
                    mBluetoothPipe.startService();
                    mBluetoothPipe.autoConnect("yoyo");
                }

                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        connectDevices();
                    }
                }, 5000);

            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (mBluetoothPipe != null) {
                mBluetoothPipe.stopService();
            }
        }
    };

    protected void connectDevices() {
        if (mBluetoothPipe.getServiceState() != STATE_NONE) {
            if (device != null && device.getBondState() == BOND_BONDED) {
                boolean temp = device.fetchUuidsWithSdp();
                UUID uuid = null;
                if (temp) {
                    uuid = device.getUuids()[0].getUuid();
                }
                int aPos = mBluetoothService.getAvailablePosIndexForNewConnection(device);

                // BluetoothPreferences.setKeyringUUID(getApplicationContext(), uuid.toString());
                // BluetoothPreferences.setKeyringAddress(getApplicationContext(), device.getAddress());
                // BluetoothPreferences.setKeyringName(getApplicationContext(), device.getName())
                //BluetoothPreferences.setKeyringPos(getApplicationContext(), aPos);

                DeviceTag tag = new DeviceTag();
                tag.setAddress(device.getAddress());
                tag.setName(device.getName());
                tag.setPos(aPos);
                tag.setUuid(uuid.toString());

                ArrayList<DeviceTag> list = new ArrayList<>();
                list.add(tag);

                BluetoothPreferences.setKeyrings(getApplicationContext(),  list);

                mBluetoothPipe.connect(device, aPos);

            } else {

                connectDevicesFromKeyring();

            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_CONNECT_DEVICE) {
            if(resultCode == Activity.RESULT_OK) {
                //TODO
                // mBluetoothPipe.connect(data);
            }
        } else if(requestCode == REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK) {
                mBluetoothPipe.setupService(mBluetoothService, mHandler);
            } else {
                Toast.makeText(getApplicationContext()
                        , "Bluetooth was not enabled."
                        , Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    protected void doBind() {
        bindService(new Intent(this, BluetoothService.class), serviceConnection, BIND_AUTO_CREATE);
        mIsBound = true;
    }

    protected void doUnbind() {
        if (mIsBound) {
            unbindService(serviceConnection);
            mIsBound = false;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBluetoothPipe = new BluetoothPipe(this);

        mBluetoothPipe.setBluetoothStateListener(new BluetoothPipe.BluetoothStateListener() {
            public void onServiceStateChanged(int state) {
                if(state == BluetoothState.STATE_CONNECTED)
                    Log.d(TAG, "State : Connected");
                else if(state == BluetoothState.STATE_CONNECTING)
                    Log.d(TAG, "State : Connecting");
                else if(state == BluetoothState.STATE_LISTEN)
                    Log.d(TAG, "State : Listen");
                else if(state == BluetoothState.STATE_NONE)
                    Log.d(TAG, "State : None");
            }
        });

        mBluetoothPipe.setOnDataReceivedListener(new BluetoothPipe.OnDataReceivedListener() {
            public void onDataReceived(byte[] data, String message) {

                Log.d(TAG, "Rec Message: "+ message + "\n");

            }
        });

        mBluetoothPipe.setBluetoothConnectionListener(new BluetoothPipe.BluetoothConnectionListener() {
            public void onDeviceConnected(DeviceTag deviceTag) {
                mDeviceTag = deviceTag;

                Toast.makeText(getApplicationContext()
                        , "Connected to " + deviceTag.getName()+ " " +deviceTag.getAddress()
                        , Toast.LENGTH_SHORT).show();

                SerialPortOpen();

            }

            public void onDeviceDisconnected() {
                Toast.makeText(getApplicationContext()
                        , "Connection lost"
                        , Toast.LENGTH_SHORT).show();
            }

            public void onDeviceConnectionFailed() {
                Log.d(TAG, "Unable to connect");
            }
        });

        mBluetoothPipe.setAutoConnectionListener(new BluetoothPipe.AutoConnectionListener() {
            public void onNewConnection(DeviceTag deviceTag) {
                mDeviceTag = deviceTag;

                Log.d(TAG, "New Connection - " + deviceTag.getName()+ " " +deviceTag.getAddress());
            }

            public void onAutoConnectionStarted() {
                Log.d(TAG, "Auto menu_connection started");
            }
        });

        doBind();

        this.writeBuffer = new byte[512];
        this.readBuffer = new byte[512];
        this.isOpen = false;

    }


    private void connectDevicesFromKeyring() {
        BluetoothDevice device = null;
        ArrayList<DeviceTag> devices = BluetoothPreferences.getKeyrings(getApplicationContext());

        if (devices.size()>0) {
            for (DeviceTag tag : devices) {
                if (tag != null) {
                    device = mBluetoothPipe.getBluetoothAdapter().getRemoteDevice(tag.getAddress());
                    if (device != null) {
                        if (device.getBondState() == BOND_BONDED) {
                            if (tag.getPos() < 0) {
                                tag.setPos(mBluetoothService.getAvailablePosIndexForNewConnection(device));
                            }
                            Log.d(TAG, "connecting to device with address=" + tag.getAddress() + "on pos=" + tag.getPos() + " ");
                            mBluetoothPipe.connect(device, tag.getPos());

                        } else {
                            Log.w(TAG, "device address=" + tag.getAddress() + "on pos=" + tag.getPos() + " is not paired");
                            //TODO auto pairing
                        }
                    } else {
                        //TODO remove tag, invalid
                    }
                }
            }
        } else {
            //TODO
            Log.d(TAG, "no saved devices found. goto selection activity");
        }
    }

    public synchronized void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");

        if (!mIsBound) {
            doBind();
        } else {

            if (mBluetoothPipe.isServiceAvailable()) {

                if (mBluetoothPipe.getServiceState() == STATE_NONE) {

                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            mBluetoothPipe.startService();
                        }
                    }, 100);
                }

                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        connectDevicesFromKeyring();
                    }
                }, 3000);
            }
        }

    }

    @Override
    protected void onDestroy() {
        doUnbind();
        super.onDestroy();
    }

    private boolean SerialPortOpen() {
        this.isOpen = true;
        new readThread().start();
        return true;
    }

    private class readThread extends Thread {

        public void run() {
            byte[] buffer = new byte[4096];
            while (true) {
                Message msg = Message.obtain();
                if (BluetoothBaseActivity.this.isOpen) {
                        //ThrowActivity.this.handler.sendMessage(msg);
                    }
                else {
                    try {
                        Thread.sleep(50);
                        return;
                    } catch (Exception e) {
                        return;
                    }
                }
            }
        }
    }
}
