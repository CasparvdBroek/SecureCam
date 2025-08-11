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
    
    private static final String TAG = Constants.TAG_MAIN_ACTIVITY;
    
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
        
        initializePermissionLauncher();
        initializeViews();
        setupButtonListeners();
        setupWindowInsets();
        updateUI();
    }
    
    private void initializePermissionLauncher() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::handlePermissionResult
        );
    }
    
    private void handlePermissionResult(java.util.Map<String, Boolean> permissions) {
        boolean allGranted = permissions.values().stream().allMatch(granted -> granted);
        
        if (allGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Camera permission required for WebRTC streaming", Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        serverUrlText = findViewById(R.id.serverUrlText);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
    }
    
    private void setupButtonListeners() {
        startServiceButton.setOnClickListener(v -> requestPermissionsAndStartService());
        stopServiceButton.setOnClickListener(v -> stopForegroundService());
        
        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v -> openSettingsActivity());
    }
    
    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    
    private void openSettingsActivity() {
        Intent intent = new Intent(this, CameraSettingsActivity.class);
        startActivity(intent);
    }
    
    private void requestPermissionsAndStartService() {
        if (areAllPermissionsGranted()) {
            startForegroundService();
        } else {
            permissionLauncher.launch(Constants.REQUIRED_PERMISSIONS);
        }
    }
    
    private boolean areAllPermissionsGranted() {
        for (String permission : Constants.REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
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
            updateUIForRunningService();
        } else {
            updateUIForStoppedService();
        }
    }
    
    private void updateUIForRunningService() {
        statusText.setText(getString(R.string.service_status_running));
        statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        serverUrlText.setText("WebRTC Server: " + NetworkUtils.getServerUrl(Constants.HTTP_SERVER_PORT));
        serverUrlText.setVisibility(View.VISIBLE);
        startServiceButton.setEnabled(false);
        stopServiceButton.setEnabled(true);
    }
    
    private void updateUIForStoppedService() {
        statusText.setText(getString(R.string.service_status_stopped));
        statusText.setTextColor(getResources().getColor(android.R.color.black));
        serverUrlText.setVisibility(View.GONE);
        startServiceButton.setEnabled(true);
        stopServiceButton.setEnabled(false);
    }
    

    
    @Override
    protected void onResume() {
        super.onResume();
        // Check if the service is actually running and update UI accordingly
        isServiceRunning = ServiceStatusChecker.isServiceRunning(this, ForegroundService.class);
        updateUI();
    }
}