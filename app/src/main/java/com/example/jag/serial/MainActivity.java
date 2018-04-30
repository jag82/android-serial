package com.example.jag.serial;

import android.app.PendingIntent;
import android.bluetooth.BluetoothClass;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PermissionInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Permission;
import java.security.Permissions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    UsbManager usbManager;
    UsbDevice device;
    UsbDeviceConnection connection;
    boolean usbConnected;

    EditText editText;
    TextView textView;
    Button startButton;
    Button sendButton;
    Button stopButton;
    Button clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        editText = findViewById(R.id.editText);
        textView = findViewById(R.id.textView);
        startButton = findViewById(R.id.buttonStart);
        sendButton = findViewById(R.id.buttonSend);
        stopButton = findViewById(R.id.buttonStop);
        clearButton = findViewById(R.id.buttonClear);

        setUiEnabled(false);

        //test for usb devices
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection;
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        UsbDevice device = null;
        while(deviceIterator.hasNext()){
            device = deviceIterator.next();
            String s = device.getDeviceName();
            int pid = device.getProductId();
            int did = device.getDeviceId();
            int vid = device.getVendorId();
            textView.setText(s+"\n"+Integer.toString(pid)+"\n"+Integer.toString(did));
        }
        connection = manager.openDevice(device);
        Toast.makeText(this, "end of onCreate approaching...", Toast.LENGTH_SHORT).show();

        // listen for new devices
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onResume() {
        super.onResume();

        Intent intent = getIntent();
        Log.d(TAG, "intent: " + intent);
        String action = intent.getAction();


        if (usbConnected==false ) {
            Toast.makeText(this, "checking for usb!", Toast.LENGTH_SHORT).show();
            //check to see if USB is now connected
        }
    }

    public void setUiEnabled(boolean value) {
        startButton.setEnabled(value);
        sendButton.setEnabled(value);
        stopButton.setEnabled(value);
        clearButton.setEnabled(value);
    }
    public void onClickStart(View view) {

        HashMap usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            Toast.makeText(this, "usb devices: " + usbDevices.size(), Toast.LENGTH_SHORT).show();

            boolean keep = true;

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

            while (deviceIterator.hasNext()) {
                device = deviceIterator.next();

                int deviceVID = device.getVendorId();
                if (deviceVID == 0x2341)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                            new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
        }
    }

    public void onClickSend(View view) {
        if(serialPort == null) {
            Toast.makeText(this, "serialPort undefined", Toast.LENGTH_SHORT).show();
        } else {
            String string = editText.getText().toString();
            serialPort.write(string.getBytes());
        }
    }

    public void onClickStop(View view) {
        if(serialPort == null) {
            Toast.makeText(this, "serialPort undefined", Toast.LENGTH_SHORT).show();
        } else {
            serialPort.close();
        }
    }

    public void onClickClear(View view) {
        editText.setText("");
        textView.setText("");
    }


    UsbSerialDevice serialPort;
    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                usbConnected=false;
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            setUiEnabled(true); //Enable Buttons in UI
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            serialPort.read(mCallback); //
                            tvAppend(textView, "Serial Connection Opened!\n");
                            usbConnected = true;

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                onClickStart(startButton);
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                onClickStop(stopButton);
            }
        }

    };

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] bytes) {
            String data = null;
            try {
                data = new String(bytes, "UTF-8");
                data.concat("/n");
                tvAppend(textView, data);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        }
    };

    private void tvAppend(TextView tv, CharSequence text) { final TextView ftv = tv; final CharSequence ftext = text; runOnUiThread(new Runnable() { @Override public void run() { ftv.append(ftext); } }); }
}
