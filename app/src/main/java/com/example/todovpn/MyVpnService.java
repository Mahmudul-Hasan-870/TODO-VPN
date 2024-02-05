package com.example.todovpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyVpnService extends VpnService {
    private static final String TAG = "MyVpnService";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ParcelFileDescriptor vpnInterface;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long vpnStartTime = 0;

    public static final String ACTION_VPN_CONNECTED = "com.example.todovpn.ACTION_VPN_CONNECTED";
    public static final String ACTION_RECEIVED_DATA = "com.example.todovpn.ACTION_RECEIVED_DATA";
    public static final String EXTRA_RECEIVED_DATA = "com.example.todovpn.EXTRA_RECEIVED_DATA";

    public static MyVpnService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public ParcelFileDescriptor getVpnInterface() {
        return vpnInterface;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Thread vpnThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    MyVpnService.this.runVpnConnection();
                }
            });
            vpnThread.start();
        }
        return START_STICKY;
    }

    private void runVpnConnection() {
        try {
            vpnStartTime = System.currentTimeMillis();
            showToast("VPN Connection Starting...");

            if (establishedVpnConnection()) {
                showToast("VPN Connection Established");
                readFromVpnInterface();
            }
        } catch (Exception e) {
            showToast("VPN Connection Failed");
            e.printStackTrace();
        } finally {
            stopVpnConnecting();
            showToast("VPN Connection Stopped");
        }
    }

    private void stopVpnConnecting() {
        showToast("Stopping VPN Connection");

        // Calculate and log VPN duration
        long vpnEndTime = System.currentTimeMillis();
        long vpnDuration = vpnEndTime - vpnStartTime;
        Log.d(TAG, "VPN Duration: " + vpnDuration + " milliseconds");

        isRunning.set(false);
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface");
            }
        }
    }

    private boolean establishedVpnConnection() {
        if (vpnInterface == null) {
            VpnService.Builder builder = new VpnService.Builder();
            builder.addAddress(VpnConfig.SERVER_IP, 32);
            builder.addRoute("0.0.0.0", 0);

            vpnInterface = builder.setSession(getString(R.string.app_name))
                    .setConfigureIntent(null)
                    .establish();

            return vpnInterface != null;
        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    onVpnConnectedSuccess();
                    showToast("VPN Connection Already Established");
                }
            });
        }
        return true;
    }

    private void readFromVpnInterface() {
        isRunning.set(true);
        ByteBuffer byteBuffer = ByteBuffer.allocate(32767);

        while (isRunning.get()) {
            try {
                FileInputStream inputStream = new FileInputStream(vpnInterface.getFileDescriptor());
                int length = inputStream.read(byteBuffer.array());

                if (length > 0) {
                    byteBuffer.position(0);  // Reset position to start
                    byte[] receivedData = new byte[length];
                    byteBuffer.get(receivedData, 0, length);

                    Intent intent = new Intent(ACTION_RECEIVED_DATA);
                    intent.putExtra(EXTRA_RECEIVED_DATA, receivedData);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                    // Note: Moved writeToNetwork outside the if block
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Moved writeToNetwork outside the try-catch block
            if (isRunning.get()) {
                writeToNetwork(byteBuffer.array());  // Send data to the network outside the if block
            }
        }
    }

    private void writeToNetwork(byte[] data) {
        try {
            Socket socket = new Socket(VpnConfig.SERVER_IP, VpnConfig.SERVER_PORT);
            OutputStream outputStream = socket.getOutputStream();

            outputStream.write(data);

            outputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpnConnection();
    }

    private void stopVpnConnection() {
        stopVpnConnecting();
    }

    private void onVpnConnectedSuccess() {
        Intent intent = new Intent(ACTION_VPN_CONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void showToast(final String message) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MyVpnService.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
