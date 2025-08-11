package com.casparvdbroek.securecam;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    
    private TextView statusText;
    private TextView serverUrlText;
    private Button startServiceButton;
    private Button stopServiceButton;
    private boolean isServiceRunning = false;
    
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean allGranted = true;
                    for (Boolean granted : permissions.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    
                    if (allGranted) {
                        Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Camera permission required for WebRTC streaming", Toast.LENGTH_LONG).show();
                    }
                }
        );
        
        // Initialize views
        statusText = findViewById(R.id.statusText);
        serverUrlText = findViewById(R.id.serverUrlText);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
        
        // Set up button click listeners
        startServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissionsAndStartService();
            }
        });
        
        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopForegroundService();
            }
        });
        
        // Settings button
        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraSettingsActivity.class);
                startActivity(intent);
            }
        });
        
        // Set initial button states
        updateUI();
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    
    private void requestPermissionsAndStartService() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };
        
        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }
        
        if (allPermissionsGranted) {
            startForegroundService();
        } else {
            permissionLauncher.launch(permissions);
        }
    }
    
    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isServiceRunning = true;
        updateUI();
    }
    
    private void stopForegroundService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        updateUI();
    }
    
    private void updateUI() {
        if (isServiceRunning) {
            statusText.setText(getString(R.string.service_status_running));
            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            serverUrlText.setText("WebRTC Server: http://" + getLocalIpAddress() + ":8080");
            serverUrlText.setVisibility(View.VISIBLE);
            startServiceButton.setEnabled(false);
            stopServiceButton.setEnabled(true);
        } else {
            statusText.setText(getString(R.string.service_status_stopped));
            statusText.setTextColor(getResources().getColor(android.R.color.black));
            serverUrlText.setVisibility(View.GONE);
            startServiceButton.setEnabled(true);
            stopServiceButton.setEnabled(false);
        }
    }
    
    private String getLocalIpAddress() {
        try {
            java.net.NetworkInterface networkInterface = java.net.NetworkInterface.getByName("wlan0");
            if (networkInterface == null) {
                networkInterface = java.net.NetworkInterface.getByName("eth0");
            }
            if (networkInterface != null) {
                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "192.168.1.100"; // Fallback IP
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Check if the service is actually running and update UI accordingly
        isServiceRunning = ServiceStatusChecker.isServiceRunning(this, ForegroundService.class);
        updateUI();
    }
}