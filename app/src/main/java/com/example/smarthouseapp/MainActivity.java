package com.example.smarthouseapp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity {

    private Button btnStartServer;
    private Button btnStartClient;

    private TextView tvStatus;

    private BluetoothAdapter bluetoothAdapter;

    // UUID partagé entre le serveur et le client
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final String NAME = "MonServeurMaison";

    // Thread de communication statique pour l'échange de données
    private static ConnectedThread connectedThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        btnStartServer = findViewById(R.id.btn_start_server);
        btnStartClient = findViewById(R.id.btn_start_client);
        tvStatus = findViewById(R.id.tv_status);

        checkPermissions();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) return;


        btnStartServer.setOnClickListener(v -> {
            if (checkBluetooth()) {
                prepareConnectionUI();
                new AcceptThread().start();
            }
        });

        btnStartClient.setOnClickListener(v -> {
            if (checkBluetooth()) {
                prepareConnectionUI();
                startClientMode();
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 1);
            }
        }
    }

    private boolean checkBluetooth() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private void prepareConnectionUI() {
        runOnUiThread(() -> {

            btnStartServer.setVisibility(View.GONE);
            btnStartClient.setVisibility(View.GONE);
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("");
        });
    }

    private void updateUIConnected(String role) {
        runOnUiThread(() -> {
            tvStatus.setText("STATUT : CONNECTÉ (" + role + ")");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            Toast.makeText(this, "Connexion établie", Toast.LENGTH_SHORT).show();
        });
    }

    private void resetUI() {
        runOnUiThread(() -> {

            btnStartServer.setVisibility(View.VISIBLE);
            btnStartClient.setVisibility(View.VISIBLE);
            tvStatus.setVisibility(View.GONE);
        });
    }

    // Thread serveur pour l'écoute des connexions entrantes
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e("BT_SERVER", "Erreur écoute", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            try {
                socket = mmServerSocket.accept();
            } catch (IOException e) {
                Log.e("BT_SERVER", "Accept échoué", e);
            }

            if (socket != null) {
                manageConnectedSocket(socket, "SERVEUR");
                try {
                    mmServerSocket.close();
                } catch (IOException e) { }
            }
        }
    }

    // Recherche et connexion au serveur
    private void startClientMode() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice targetDevice = null;

        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                // Recherche spécifique du téléphone serveur ou client
                if (name != null && (name.contains("Client") || name.contains("S22+_Samoht"))) {
                    targetDevice = device;
                    break;
                }
            }
        }

        if (targetDevice != null) {
            new ConnectThread(targetDevice).start();
        } else {
            Toast.makeText(this, "Hub non trouvé dans les appareils appairés", Toast.LENGTH_LONG).show();
            resetUI();
        }
    }

    // Thread client pour initier la connexion
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
                manageConnectedSocket(mmSocket, "CLIENT");
            } catch (IOException e) {
                Log.e("BT_CLIENT", "Connexion échouée", e);
                resetUI();
            }
        }
    }

    private void manageConnectedSocket(BluetoothSocket socket, String role) {
        updateUIConnected(role);
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        
        Intent intent = new Intent(MainActivity.this, MonitoringActivity.class);
        intent.putExtra("ROLE", role);
        startActivity(intent);
    }

    // Thread pour la gestion des flux de données après connexion
    public static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String msg = new String(buffer, 0, bytes);
                    Log.d("BT_COMM", "Message reçu : " + msg);
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }
    }
}
