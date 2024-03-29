package com.example.droneremote;

import androidx.appcompat.app.AppCompatActivity;
import io.github.controlwear.virtual.joystick.android.JoystickView;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class Remote extends AppCompatActivity implements View.OnClickListener {

    // Declaration
    private static final String TAG = "bluetooth1";
    Button btnOn, btnOff;
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;

    // SPP UUID сервиса
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC-адрес Bluetooth модуля
    private static String address = "00:19:10:09:2C:6C";

    Thread channel_thread;
    String ThreadTag = "Channel Thread: ";

    private TextView wChannelInfo;
    private TextView wChannelInfoRight;

    //Variables needed for works with Sending data
    int Throttle=50, Yaw=50, Pitch=50, Roll=50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remote);

        findViewById(R.id.btnOn).setOnClickListener(this);
        findViewById(R.id.btnOff).setOnClickListener(this);
        findViewById(R.id.btnDisconect).setOnClickListener(this);
        wChannelInfo = (TextView) findViewById(R.id.textView_sendingdata);
        wChannelInfoRight = (TextView) findViewById(R.id.textView_sendingdataRight);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();

        final JoystickView leftStick = (JoystickView) findViewById(R.id.leftStick);
        leftStick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // do whatever you want
                Throttle = leftStick.getNormalizedY();
                Yaw = leftStick.getNormalizedX();

                wChannelInfo.setText(angle + "°\n" + strength + "%\n" + String.format("x%03d:y%03d",
                        leftStick.getNormalizedX(),
                        leftStick.getNormalizedY()));
            }
        });

        final JoystickView rightStick = (JoystickView) findViewById(R.id.rightStick);
        rightStick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // do whatever you want
                Pitch = rightStick.getNormalizedY();
                Roll = rightStick.getNormalizedX();

                wChannelInfoRight.setText(angle + "°\n" + strength + "%\n" + String.format("x%03d:y%03d",
                        rightStick.getNormalizedX(),
                        rightStick.getNormalizedY()));
            }
        });

        channel_thread = new Thread((new Runnable() {
            @Override
            public synchronized void run() {
                while (!channel_thread.isInterrupted()){

                    Log.i(ThreadTag, "Send data "
                            +String.format("%03d", Math.round(Math.abs(Throttle-50)*5.08))
                            +" "+String.format("%03d", Math.round(Yaw*2.54))
                            +" "+String.format("%03d", Math.round(Math.abs(Pitch-100)*2.54))
                            +" "+String.format("%03d", Math.round(Roll*2.54)));

                    // Wysyłanie treści na Arduino
                    sendData("c" + String.format("%03d", Math.round(Math.abs(Throttle-50)*5.08))
                            + String.format("%03d", Math.round(Yaw*2.54))
                            + String.format("%03d", Math.round(Math.abs(Pitch-100)*2.54))
                            + String.format("%03d", Math.round(Roll*2.54)));








                    try {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException ex){
                        break;
                    }
                }
            }
        }));
//        channel_thread.start();



    }

    @Override
    public void onClick(View view) {

        int i = view.getId();
        if (i == R.id.btnOn){
            sendData("1");
            channel_thread.start();
            Log.i(ThreadTag, "Thread was Started");
            Toast.makeText(getBaseContext(), "Включаем LED", Toast.LENGTH_SHORT).show();
        }
        if (i == R.id.btnOff){
            sendData("0");
            try {
                channel_thread.interrupt();
                Log.i(ThreadTag, "Thread is Closed");
            }catch (Exception ex){}

            Toast.makeText(getBaseContext(), "Выключаем LED", Toast.LENGTH_SHORT).show();
        }
        if (i == R.id.btnDisconect){
            startActivity(new Intent(Remote.this, MainActivity.class));
            Toast.makeText(getBaseContext(), "Disconnected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "...onResume - попытка соединения...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        mBluetoothAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Соединяемся...");
        try {
            btSocket.connect();
            Log.d(TAG, "...Соединение установлено и готово к передачи данных...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Создание Socket...");

        try {
            outStream = btSocket.getOutputStream();
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and output stream creation failed:" + e.getMessage() + ".");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        Log.d(TAG, "...In onPause()...");

        if (outStream != null) {
            try {
                outStream.flush();
            } catch (IOException e) {
                errorExit("Fatal Error", "In onPause() and failed to flush output stream: " + e.getMessage() + ".");
            }
        }

        try     {
            btSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(mBluetoothAdapter ==null) {
            errorExit("Fatal Error", "Bluetooth не поддерживается");
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth включен...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(mBluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    private synchronized void sendData(String message) {
        byte[] msgBuffer = message.getBytes();

        Log.d(TAG, "...Посылаем данные: " + message + "...");

        try {
            outStream.write(msgBuffer);
        } catch (IOException e) {
            String msg = "In onResume() and an exception occurred during write: " + e.getMessage();
            if (address.equals("00:00:00:00:00:00"))
                msg = msg + ".\n\nВ переменной address у вас прописан 00:00:00:00:00:00, вам необходимо прописать реальный MAC-адрес Bluetooth модуля";
                msg = msg +  ".\n\nПроверьте поддержку SPP UUID: " + MY_UUID.toString() + " на Bluetooth модуле, к которому вы подключаетесь.\n\n";

                errorExit("Fatal Error", msg);
        }
    }



}
