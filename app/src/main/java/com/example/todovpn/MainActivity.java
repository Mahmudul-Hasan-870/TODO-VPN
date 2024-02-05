package com.example.todovpn;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final int VPN_REQUEST_CODE = 1;
    TextView status;
    TextView receivedDataText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        receivedDataText = findViewById(R.id.received_data_text);

        Button vpnStartButton = findViewById(R.id.vpn_start);
        vpnStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                establishVpnConnection();
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(vpnConnectedReceiver,
                new IntentFilter(MyVpnService.ACTION_VPN_CONNECTED));

        LocalBroadcastManager.getInstance(this).registerReceiver(receivedDataReceiver,
                new IntentFilter(MyVpnService.ACTION_RECEIVED_DATA));
    }

    private void establishVpnConnection() {
        Intent vpnIntent = VpnService.prepare(MainActivity.this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        } else {
            startVpnServiceWithIP();
        }
    }

    private void startVpnServiceWithIP() {
        Intent intent = new Intent(MainActivity.this, MyVpnService.class);
        intent.putExtra("vpnIp", VpnConfig.SERVER_IP);
        intent.putExtra("vpnPort", VpnConfig.SERVER_PORT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private BroadcastReceiver vpnConnectedReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            MyVpnService vpnService = MyVpnService.instance;
            if (vpnService == null || vpnService.getVpnInterface() == null) {
                status.setText("Connected");
            } else {
                status.setText("Disconnected");
            }
        }
    };

    private BroadcastReceiver receivedDataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            byte[] receivedData = intent.getByteArrayExtra(MyVpnService.EXTRA_RECEIVED_DATA);
            String receivedString = new String(receivedData, StandardCharsets.UTF_8);
            receivedDataText.setText("Received Data: " + receivedString);

            // Handle the received byte data as needed
        }
    };
}
