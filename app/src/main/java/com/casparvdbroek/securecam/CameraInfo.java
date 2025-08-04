package com.casparvdbroek.securecam;

import android.hardware.camera2.CameraCharacteristics;

/**
 * Camera metadata container for settings system
 * Holds information about available cameras for user selection
 */
public class CameraInfo {
    public final String cameraId;
    public final int facing;
    public final String displayName;
    
    public CameraInfo(String cameraId, int facing) {
        this.cameraId = cameraId;
        this.facing = facing;
        this.displayName = generateDisplayName(cameraId, facing);
    }
    
    private String generateDisplayName(String cameraId, int facing) {
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return "Front Camera";
        } else {
            // For back cameras, try to provide more descriptive names
            // This helps distinguish between multiple back cameras
            switch (cameraId) {
                case "0":
                    return "Main Camera";
                case "1":
                    return "Front Camera";
                case "2":
                    return "Wide Camera";
                case "3":
                    return "Telephoto Camera";
                case "4":
                    return "Ultra Wide Camera";
                default:
                    return "Back Camera " + cameraId;
            }
        }
    }
    
    @Override
    public String toString() {
        return displayName + " (ID: " + cameraId + ")";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CameraInfo that = (CameraInfo) obj;
        return cameraId.equals(that.cameraId) && facing == that.facing;
    }
    
    @Override
    public int hashCode() {
        return cameraId.hashCode() * 31 + facing;
    }
} 