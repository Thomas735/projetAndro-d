package com.example.smarthouseapp;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.android.volley.AuthFailureError;
import com.android.volley.toolbox.StringRequest;
import java.util.HashMap;
import java.util.Map;

public class MonitoringActivity extends AppCompatActivity {

    private LinearLayout devicesListLayout;
    private Handler handler = new Handler();
    private Runnable runnableCode;
    private final int REFRESH_DELAY = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitoring);

        devicesListLayout = findViewById(R.id.devices_list_layout);

        // Lancement de la requête réseau au lieu du faux appareil
        fetchDevices();

        runnableCode = new Runnable() {
            @Override
            public void run() {
                fetchDevices(); // On lance la requête réseau
                handler.postDelayed(this, REFRESH_DELAY); // On reprogramme la tâche pour dans 5 secondes
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // L'activité est visible : on lance la première exécution de la boucle
        handler.post(runnableCode);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // L'activité n'est plus visible : on arrête la boucle
        handler.removeCallbacks(runnableCode);
    }

    private void fetchDevices() {
        String idMaison = "42";
        String url = "http://happyresto.enseeiht.fr/smartHouse/api/v1/devices/" + idMaison;

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET, url, null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        Log.d("API_JSON", response.toString());
                        devicesListLayout.removeAllViews();

                        try {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject device = response.getJSONObject(i);

                                int deviceId = device.optInt("ID", -1);

                                // Récupération du nom
                                String name = device.optString("NAME", "Appareil Inconnu");

                                // Récupération de l'état (1 = allumé, 0 = éteint)
                                int stateValue = device.optInt("STATE", 0);
                                boolean isOn = (stateValue == 1);

                                // Récupération des informations complémentaires
                                String model = device.optString("MODEL", "");
                                String data = device.optString("DATA", "");
                                String info = model;
                                if (!data.isEmpty()) {
                                    info += " | " + data;
                                }

                                // Création de la vue
                                View deviceView = createDeviceView(deviceId, name, info, isOn);
                                devicesListLayout.addView(deviceView);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("API_ERROR", "Erreur réseau : " + error.getMessage());
                    }
                }
        );

        queue.add(jsonArrayRequest);
    }

    private void toggleDevice(int deviceId) {
        String url = "http://happyresto.enseeiht.fr/smartHouse/api/v1/devices/";
        RequestQueue queue = Volley.newRequestQueue(this);

        StringRequest postRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Actualisation de l'affichage en rechargeant la liste après la commande
                        fetchDevices();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("API_ERROR", "Erreur POST : " + error.getMessage());
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("deviceId", String.valueOf(deviceId));
                params.put("houseId", "42");
                params.put("action", "turnOnOff");
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", "application/x-www-form-urlencoded");
                return params;
            }
        };

        queue.add(postRequest);
    }

    private View createDeviceView(int deviceId, String name, String info, boolean isOn) {
        RelativeLayout layout = new RelativeLayout(this);
        layout.setPadding(16, 16, 16, 16);

        TextView nameTextView = new TextView(this);
        nameTextView.setId(View.generateViewId());
        nameTextView.setText(name);
        nameTextView.setTextSize(18);
        nameTextView.setTextColor(Color.BLACK);

        RelativeLayout.LayoutParams nameParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        nameParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        nameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        layout.addView(nameTextView, nameParams);

        TextView infoTextView = new TextView(this);
        infoTextView.setText(info);

        RelativeLayout.LayoutParams infoParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        infoParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        infoParams.addRule(RelativeLayout.BELOW, nameTextView.getId());
        layout.addView(infoTextView, infoParams);

        Button actionButton = new Button(this);
        actionButton.setText(isOn ? "ON" : "OFF");

        actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDevice(deviceId); // Appel de la méthode d'envoi de commande
            }
        });

        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        buttonParams.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
        layout.addView(actionButton, buttonParams);

        return layout;
    }
}