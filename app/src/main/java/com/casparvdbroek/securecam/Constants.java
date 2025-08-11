package com.casparvdbroek.securecam;

/**
 * Constants used throughout the SecureCam application.
 * Centralizes magic numbers, strings, and configuration values.
 */
public final class Constants {
    
    // Prevent instantiation
    private Constants() {}
    
    // Network constants
    public static final int HTTP_SERVER_PORT = 8080;
    public static final String FALLBACK_IP = "192.168.1.100";
    
    // Camera constants
    public static final int DEFAULT_FRAME_WIDTH = 640;
    public static final int DEFAULT_FRAME_HEIGHT = 480;
    public static final int DEFAULT_FRAME_RATE = 30;
    
    // Service constants
    public static final String SERVICE_CHANNEL_ID = "SecureCamServiceChannel";
    public static final int SERVICE_NOTIFICATION_ID = 1;
    
    // Logging tags
    public static final String TAG_MAIN_ACTIVITY = "MainActivity";
    public static final String TAG_FOREGROUND_SERVICE = "ForegroundService";
    public static final String TAG_CAMERA_SETTINGS = "CameraSettingsActivity";
    public static final String TAG_CAMERA2_VIDEO = "Camera2Video";
    public static final String TAG_HTTP_SERVER = "SimpleHttpServer";
    public static final String TAG_NETWORK_UTILS = "NetworkUtils";
    
    // Permission constants
    public static final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
    };
    
    // Camera facing constants
    public static final int CAMERA_FACING_FRONT = android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT;
    public static final int CAMERA_FACING_BACK = android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK;
    
    // HTTP response constants
    public static final String HTTP_OK = "200 OK";
    public static final String HTTP_BAD_REQUEST = "400 Bad Request";
    public static final String HTTP_NOT_FOUND = "404 Not Found";
    public static final String HTTP_INTERNAL_ERROR = "500 Internal Server Error";
    
    // Content type constants
    public static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";
    public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    public static final String CONTENT_TYPE_TEXT = "text/plain; charset=UTF-8";
    public static final String CONTENT_TYPE_JPEG = "image/jpeg";
    
    // Timeout constants (in milliseconds)
    public static final int CAMERA_START_TIMEOUT = 5000;
    public static final int HTTP_SERVER_TIMEOUT = 30000;
    public static final int WATCHDOG_INTERVAL = 10000;
    
    // File constants
    public static final String JPEG_EXTENSION = ".jpg";
    public static final int JPEG_QUALITY = 85;
}
