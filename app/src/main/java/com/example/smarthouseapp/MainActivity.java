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
import java.util.Set;
import java.util.UUID;

// L'annotation ci-dessous dit à Android Studio d'ignorer les alertes rouges de permissions pour ce TP
@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity {

    private Button btnStartServer;
    private Button btnStartClient;
    private Button btnStartDomotique;
    private TextView tvStatus;

    // L'adaptateur Bluetooth local
    private BluetoothAdapter bluetoothAdapter;

    // L'UUID commun au Serveur et au Client (le mot de passe secret)
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private static final String NAME = "MonServeurMaison";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartDomotique = findViewById(R.id.btn_start);
        btnStartServer = findViewById(R.id.btn_start_server);
        btnStartClient = findViewById(R.id.btn_start_client);
        tvStatus = findViewById(R.id.tv_status);

        // --- NOUVEAU : Demande de permission pour Android 12+ ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                }, 1);
            }
        }
        // ---------------------------------------------------------

        // Initialisation du Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Cet appareil ne supporte pas le Bluetooth", Toast.LENGTH_LONG).show();
        }

        btnStartDomotique.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MonitoringActivity.class);
                startActivity(intent);
            }
        });

        btnStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateUIForWaiting("SERVEUR EN ATTENTE\n*Attente de connexion d'un client*");
                startServerMode();
            }
        });

        btnStartClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateUIForWaiting("CLIENT EN ATTENTE\n*Attente de connexion au serveur*");
                startClientMode();
            }
        });
    }

    private void updateUIForWaiting(String statusMessage) {
        btnStartDomotique.setVisibility(View.GONE);
        btnStartServer.setVisibility(View.GONE);
        btnStartClient.setVisibility(View.GONE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText(statusMessage);
    }

    // ==========================================
    // LOGIQUE SERVEUR (Le Hub)
    // ==========================================
    private void startServerMode() {
        AcceptThread acceptThread = new AcceptThread();
        acceptThread.start();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e("BluetoothServeur", "Erreur lors de la création du ServerSocket", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            while (true) {
                try {
                    Log.d("BluetoothServeur", "Serveur en écoute...");
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e("BluetoothServeur", "Erreur lors de l'acceptation", e);
                    break;
                }

                if (socket != null) {
                    Log.d("BluetoothServeur", "Connexion acceptée avec succès !");
                    // TODO: Étape 4 - Gérer le socket pour lire/écrire
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.e("BluetoothServeur", "Erreur lors de la fermeture", e);
                    }
                    break;
                }
            }
        }
    }

    // ==========================================
    // LOGIQUE CLIENT (La Télécommande)
    // ==========================================
    private void startClientMode() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice targetDevice = null;

        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                // ATTENTION : Remplace "Nom_Du_Tel_Serveur" par le vrai nom du téléphone Hub
                if (device.getName() != null && device.getName().equals("serveur")) {
                    targetDevice = device;
                    break;
                }
            }
        }

        if (targetDevice != null) {
            ConnectThread connectThread = new ConnectThread(targetDevice);
            connectThread.start();
        } else {
            Toast.makeText(this, "Hub non trouvé dans les appareils appairés", Toast.LENGTH_LONG).show();
            btnStartDomotique.setVisibility(View.VISIBLE);
            btnStartServer.setVisibility(View.VISIBLE);
            btnStartClient.setVisibility(View.VISIBLE);
            tvStatus.setVisibility(View.GONE);
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket tmp = null;
            mmDevice = device;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e("BluetoothClient", "Erreur de création du socket", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                Log.d("BluetoothClient", "Tentative de connexion au Hub...");
                mmSocket.connect();
                Log.d("BluetoothClient", "Connecté au Hub avec succès !");

                // TODO: Étape 4 - Gérer le socket pour lire/écrire

            } catch (IOException connectException) {
                Log.e("BluetoothClient", "Impossible de se connecter", connectException);
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("BluetoothClient", "Erreur fermeture socket", closeException);
                }
            }
        }
    }
}