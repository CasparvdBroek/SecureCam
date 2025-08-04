package com.casparvdbroek.securecam;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class CameraSettingsActivity extends AppCompatActivity {
    private static final String TAG = "CameraSettingsActivity";

    private CameraSettings cameraSettings;
    private Camera2VideoManager videoManager;
    private Spinner cameraSpinner;
    private Switch defaultCameraSwitch;
    private TextView serviceStatusText;
    private TextView cameraInfoText;
    private Button restartServiceButton;
    private boolean serviceIsRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_settings);
        
        cameraSettings = new CameraSettings(this);
        videoManager = new Camera2VideoManager(this);
        serviceIsRunning = ServiceStatusChecker.isServiceRunning(this, ForegroundService.class);
        
        setupUI();
        loadCurrentSettings();
        showServiceStatus();
    }
    
    private void setupUI() {
        // Initialize views
        cameraSpinner = findViewById(R.id.cameraSpinner);
        defaultCameraSwitch = findViewById(R.id.defaultCameraSwitch);
        serviceStatusText = findViewById(R.id.serviceStatusText);
        cameraInfoText = findViewById(R.id.cameraInfoText);
        restartServiceButton = findViewById(R.id.restartServiceButton);
        
        // Camera selection spinner
        cameraSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CameraInfo selectedCamera = (CameraInfo) parent.getItemAtPosition(position);
                cameraSettings.saveSelectedCamera(selectedCamera.cameraId, selectedCamera.facing);
                Log.d(TAG, "Selected camera: " + selectedCamera.displayName + " (ID: " + selectedCamera.cameraId + ")");
                
                // Show camera information
                showCameraInfo(selectedCamera);
                
                if (serviceIsRunning) {
                    showRestartNotification();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // Default camera switch
        defaultCameraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int facing = isChecked ? 
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT : 
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK;
            cameraSettings.saveDefaultCamera(facing);
            
            // Clear specific camera selection so default preference takes effect
            cameraSettings.clearSelectedCamera();
            
            // Update spinner to show the default camera
            updateSpinnerToDefaultCamera(facing);
            
            Log.d(TAG, "Default camera set to: " + (isChecked ? "Front" : "Back") + " and specific selection cleared");
            
            if (serviceIsRunning) {
                showRestartNotification();
            }
        });
        
        // Restart service button
        restartServiceButton.setOnClickListener(v -> restartService());
    }
    
    private void loadCurrentSettings() {
        try {
            // Initialize video manager to access camera list
            videoManager.initialize();
            
            // Load available cameras (already deduplicated in Camera2VideoManager)
            List<CameraInfo> cameras = videoManager.getAvailableCameras();
            
            if (cameras.isEmpty()) {
                Log.w(TAG, "No cameras found");
                Toast.makeText(this, getString(R.string.no_cameras_found), Toast.LENGTH_LONG).show();
                return;
            }
            
            Log.d(TAG, "Received " + cameras.size() + " cameras from Camera2VideoManager");
            
            // Log each camera for debugging
            for (int i = 0; i < cameras.size(); i++) {
                CameraInfo camera = cameras.get(i);
                Log.d(TAG, "Camera " + i + " in settings: " + camera.toString());
            }
            
            // Create adapter with the deduplicated camera list
            ArrayAdapter<CameraInfo> adapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, cameras);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            cameraSpinner.setAdapter(adapter);
            
            // Select current camera
            String currentCameraId = cameraSettings.getSelectedCameraId();
            if (currentCameraId != null) {
                // Use saved specific camera selection
                for (int i = 0; i < cameras.size(); i++) {
                    if (cameras.get(i).cameraId.equals(currentCameraId)) {
                        cameraSpinner.setSelection(i);
                        showCameraInfo(cameras.get(i));
                        Log.d(TAG, "Selected saved camera: " + currentCameraId);
                        break;
                    }
                }
            } else {
                // Use default camera preference
                int defaultFacing = cameraSettings.getDefaultCamera();
                updateSpinnerToDefaultCamera(defaultFacing);
                defaultCameraSwitch.setChecked(defaultFacing == 
                    android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading camera settings", e);
            Toast.makeText(this, "Error loading camera settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void showServiceStatus() {
        if (serviceIsRunning) {
            serviceStatusText.setText(getString(R.string.service_status_running));
            restartServiceButton.setVisibility(View.VISIBLE);
        } else {
            serviceStatusText.setText(getString(R.string.service_status_stopped));
            restartServiceButton.setVisibility(View.GONE);
        }
    }
    
    private void showRestartNotification() {
        restartServiceButton.setVisibility(View.VISIBLE);
        Toast.makeText(this, getString(R.string.camera_setting_changed), Toast.LENGTH_SHORT).show();
    }
    
    private void showCameraInfo(CameraInfo camera) {
        if (camera != null) {
            cameraInfoText.setText("Camera ID: " + camera.cameraId + "\nFacing: " + 
                (camera.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT ? "Front" : "Back"));
            cameraInfoText.setVisibility(View.VISIBLE);
        } else {
            cameraInfoText.setVisibility(View.GONE);
        }
    }
    
    private void updateSpinnerToDefaultCamera(int facing) {
        try {
            List<CameraInfo> cameras = videoManager.getAvailableCameras();
            for (int i = 0; i < cameras.size(); i++) {
                CameraInfo camera = cameras.get(i);
                if (camera.facing == facing) {
                    cameraSpinner.setSelection(i);
                    showCameraInfo(camera);
                    Log.d(TAG, "Updated to default camera: " + camera.displayName);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating spinner to default camera", e);
        }
    }
    
    private void restartService() {
        try {
            // Stop the current service
            Intent stopIntent = new Intent(this, ForegroundService.class);
            stopService(stopIntent);
            
            // Wait a moment for the service to stop
            Thread.sleep(500);
            
            // Start the service again
            Intent startIntent = new Intent(this, ForegroundService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(startIntent);
            } else {
                startService(startIntent);
            }
            
            Toast.makeText(this, getString(R.string.service_restarted), Toast.LENGTH_SHORT).show();
            restartServiceButton.setVisibility(View.GONE);
            
        } catch (Exception e) {
            Log.e(TAG, "Error restarting service", e);
            Toast.makeText(this, "Error restarting service: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Camera2VideoManager doesn't have a release method, so we just clean up references
        videoManager = null;
    }
} 