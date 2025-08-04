package com.casparvdbroek.securecam;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Log;

/**
 * Camera settings manager using SharedPreferences
 * Handles persistent storage of camera selection preferences
 */
public class CameraSettings {
    private static final String TAG = "CameraSettings";
    private static final String PREF_NAME = "camera_settings";
    private static final String KEY_SELECTED_CAMERA_ID = "selected_camera_id";
    private static final String KEY_CAMERA_FACING = "camera_facing";
    private static final String KEY_DEFAULT_CAMERA = "default_camera";
    
    private final SharedPreferences preferences;
    
    public CameraSettings(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Log.d(TAG, "CameraSettings initialized");
    }
    
    /**
     * Save selected camera preference
     */
    public void saveSelectedCamera(String cameraId, int facing) {
        preferences.edit()
            .putString(KEY_SELECTED_CAMERA_ID, cameraId)
            .putInt(KEY_CAMERA_FACING, facing)
            .apply();
        Log.d(TAG, "Saved camera preference: " + cameraId + " (facing: " + facing + ")");
    }
    
    /**
     * Get saved camera ID
     */
    public String getSelectedCameraId() {
        return preferences.getString(KEY_SELECTED_CAMERA_ID, null);
    }
    
    /**
     * Get saved camera facing direction
     */
    public int getSelectedCameraFacing() {
        return preferences.getInt(KEY_CAMERA_FACING, CameraCharacteristics.LENS_FACING_BACK);
    }
    
    /**
     * Save default camera preference (front/back)
     */
    public void saveDefaultCamera(int facing) {
        preferences.edit()
            .putInt(KEY_DEFAULT_CAMERA, facing)
            .apply();
        Log.d(TAG, "Saved default camera preference: " + facing);
    }
    
    /**
     * Get default camera preference
     */
    public int getDefaultCamera() {
        return preferences.getInt(KEY_DEFAULT_CAMERA, CameraCharacteristics.LENS_FACING_BACK);
    }
    
    /**
     * Clear all camera preferences
     */
    public void clearPreferences() {
        preferences.edit().clear().apply();
        Log.d(TAG, "Cleared all camera preferences");
    }
    
    /**
     * Check if any camera preference is saved
     */
    public boolean hasSavedPreference() {
        return getSelectedCameraId() != null;
    }
    
    /**
     * Clear specific camera selection (but keep default preference)
     */
    public void clearSelectedCamera() {
        preferences.edit()
            .remove(KEY_SELECTED_CAMERA_ID)
            .remove(KEY_CAMERA_FACING)
            .apply();
        Log.d(TAG, "Cleared specific camera selection, default preference will be used");
    }
} 