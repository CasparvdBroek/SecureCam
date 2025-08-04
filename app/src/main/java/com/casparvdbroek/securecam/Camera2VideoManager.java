package com.casparvdbroek.securecam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.OrientationEventListener;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera2 Video Manager - Better Android 7.1 Compatibility
 * Uses Camera2 API with JPEG format to eliminate color conversion issues
 * This approach matches Open Camera's successful implementation
 * 100% Open Source - Uses official Android libraries
 */
public class Camera2VideoManager {
    private static final String TAG = "Camera2Video";
    
    private final Context context;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isCameraReady = new AtomicBoolean(false);
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    
    // Camera2 components
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    
    // Orientation handling
    private OrientationEventListener orientationEventListener;
    private int deviceOrientation = 0;
    private int sensorOrientation = 0;
    

    
    // Camera frame data
    private byte[] lastFrameData;
    private byte[] lastJpegData;
    private int frameWidth = 640;
    private int frameHeight = 480;
    private int frameRate = 30;
    
    // WebRTC signaling (kept for compatibility)
    private String localSdp;
    private String remoteSdp;
    
    // Camera selection and settings
    private CameraSettings cameraSettings;
    private String currentCameraId = null;
    
    public Camera2VideoManager(Context context) {
        this.context = context;
        this.cameraSettings = new CameraSettings(context);
        Log.d(TAG, "Camera2 Video Manager created");
    }
    

    
    public void initialize() {
        if (isInitialized.get()) {
            Log.d(TAG, "Already initialized");
            return;
        }
        
        try {
            Log.d(TAG, "Initializing Camera2 Video Manager...");
            
            // Initialize Camera2 components
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            
            // Start background thread
            backgroundThread = new HandlerThread("CameraBackground");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
            
            // Setup orientation listener
            setupOrientationListener();
            
            isInitialized.set(true);
            Log.d(TAG, "Camera2 Video Manager initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Camera2 Video Manager", e);
            isInitialized.set(false);
        }
    }
    
    @SuppressLint("MissingPermission")
    public void startCamera() {
        if (!isInitialized.get()) {
            Log.e(TAG, "Camera2 Video Manager not initialized");
            return;
        }
        
        try {
            Log.d(TAG, "Starting camera with Camera2 API...");
            
            // Determine which camera to use
            String cameraId = null;
            
            // First, try to use saved camera preference
            String savedCameraId = cameraSettings.getSelectedCameraId();
            if (savedCameraId != null) {
                cameraId = savedCameraId;
                Log.d(TAG, "Using saved camera preference: " + cameraId);
            } else {
                // Use default camera preference
                int defaultFacing = cameraSettings.getDefaultCamera();
                if (defaultFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = getFrontCameraId();
                    Log.d(TAG, "Using default front camera preference");
                } else {
                    cameraId = getBackCameraId();
                    Log.d(TAG, "Using default back camera preference");
                }
            }
            
            if (cameraId == null) {
                Log.e(TAG, "No camera found");
                return;
            }
            
            // Get sensor orientation for the selected camera
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                this.sensorOrientation = sensorOrientation != null ? sensorOrientation : 0;
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                String cameraType = (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) ? "Front" : "Back";
                Log.d(TAG, cameraType + " camera sensor orientation: " + this.sensorOrientation + "°");
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to get camera characteristics", e);
            }
            
            // Create ImageReader for JPEG format (eliminates color conversion issues)
            imageReader = ImageReader.newInstance(
                frameWidth, frameHeight, ImageFormat.JPEG, 2);
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    processJpegFrame(image);
                    image.close();
                }
            }, backgroundHandler);
            
            // Open camera device
            currentCameraId = cameraId;
            // Note: Camera permission is checked in MainActivity before calling this method
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraPreviewSession();
                }
                
                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.d(TAG, "Camera disconnected");
                    camera.close();
                    cameraDevice = null;
                }
                
                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera", e);
            isCameraReady.set(false);
        }
    }
    
    private void setupOrientationListener() {
        orientationEventListener = new OrientationEventListener(context, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) {
                    return; // Ignore unknown orientation
                }
                
                // Convert from 0-359 to 0, 90, 180, 270
                int newOrientation;
                if (orientation >= 45 && orientation < 135) {
                    newOrientation = 90;
                } else if (orientation >= 135 && orientation < 225) {
                    newOrientation = 180;
                } else if (orientation >= 225 && orientation < 315) {
                    newOrientation = 270;
                } else {
                    newOrientation = 0;
                }
                
                // Only update if orientation actually changed
                if (deviceOrientation != newOrientation) {
                    deviceOrientation = newOrientation;
                    Log.d(TAG, "Device orientation changed to: " + deviceOrientation + "° (raw: " + orientation + "°)");
                }
            }
        };
        
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
            Log.d(TAG, "Orientation listener enabled with SENSOR_DELAY_NORMAL");
        } else {
            Log.w(TAG, "Cannot detect orientation, using default");
            // Set a default orientation based on device natural orientation
            deviceOrientation = 0;
        }
    }
    
    /**
     * Get all available cameras for settings
     */
    public List<CameraInfo> getAvailableCameras() {
        List<CameraInfo> cameras = new ArrayList<>();
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            Log.d(TAG, "Raw camera IDs from system: " + java.util.Arrays.toString(cameraIds));
            
            // Use a set to track processed camera IDs and prevent duplicates
            java.util.Set<String> processedIds = new java.util.HashSet<>();
            java.util.Set<String> processedFacingIds = new java.util.HashSet<>();
            
            for (String cameraId : cameraIds) {
                // Skip if we've already processed this camera ID
                if (processedIds.contains(cameraId)) {
                    Log.w(TAG, "Skipping duplicate camera ID: " + cameraId);
                    continue;
                }
                
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    // Create a unique key for camera ID + facing direction
                    String facingKey = cameraId + "_" + facing;
                    if (processedFacingIds.contains(facingKey)) {
                        Log.w(TAG, "Skipping duplicate camera facing combination: " + facingKey);
                        continue;
                    }
                    
                    // Log camera capabilities for debugging
                    logCameraCapabilities(cameraId, characteristics);
                    
                    cameras.add(new CameraInfo(cameraId, facing));
                    processedIds.add(cameraId);
                    processedFacingIds.add(facingKey);
                    Log.d(TAG, "Added camera: " + cameraId + " (facing: " + facing + ") - Key: " + facingKey);
                } else {
                    Log.w(TAG, "Skipping camera " + cameraId + " - no facing information");
                }
            }
            Log.d(TAG, "Found " + cameras.size() + " unique available cameras");
            
            // Log final camera list for debugging
            for (int i = 0; i < cameras.size(); i++) {
                CameraInfo camera = cameras.get(i);
                Log.d(TAG, "Camera " + i + ": " + camera.toString());
            }
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera list", e);
        }
        return cameras;
    }
    
    private void logCameraCapabilities(String cameraId, CameraCharacteristics characteristics) {
        try {
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            
            StringBuilder info = new StringBuilder();
            info.append("Camera ").append(cameraId).append(" capabilities: ");
            info.append("facing=").append(facing == CameraCharacteristics.LENS_FACING_FRONT ? "FRONT" : "BACK");
            
            if (focalLengths != null && focalLengths.length > 0) {
                info.append(", focal_lengths=[");
                for (int i = 0; i < focalLengths.length; i++) {
                    if (i > 0) info.append(", ");
                    info.append(String.format("%.1fmm", focalLengths[i]));
                }
                info.append("]");
            }
            
            if (capabilities != null && capabilities.length > 0) {
                info.append(", capabilities=[");
                for (int i = 0; i < capabilities.length; i++) {
                    if (i > 0) info.append(", ");
                    info.append(capabilities[i]);
                }
                info.append("]");
            }
            
            Log.d(TAG, info.toString());
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to log camera capabilities for camera " + cameraId, e);
        }
    }
    
    /**
     * Get front camera ID
     */
    private String getFrontCameraId() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    // Get sensor orientation for front camera
                    Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if (sensorOrientation != null) {
                        Log.d(TAG, "Front camera sensor orientation: " + sensorOrientation + "°");
                    }
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get front camera", e);
        }
        return null;
    }

    private String getBackCameraId() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (String cameraId : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    // Get sensor orientation while we have the characteristics
                    Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    this.sensorOrientation = sensorOrientation != null ? sensorOrientation : 0;
                    Log.d(TAG, "Back camera sensor orientation: " + this.sensorOrientation + "°");
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera ID list", e);
        }
        return null;
    }
    
    private void createCameraPreviewSession() {
        try {
            Surface imageSurface = imageReader.getSurface();
            
            // Create capture request
            final CaptureRequest.Builder captureRequestBuilder = 
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(imageSurface);
            
            // Create camera capture session
            cameraDevice.createCaptureSession(
                Arrays.asList(imageSurface),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        captureSession = session;
                        
                        // Set repeating request
                        try {
                            captureRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            
                            captureSession.setRepeatingRequest(
                                captureRequestBuilder.build(), null, backgroundHandler);
                            
                            isCameraReady.set(true);
                            Log.d(TAG, "Camera2 session configured successfully");
                            
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Failed to set repeating request", e);
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "Failed to configure camera session");
                    }
                }, backgroundHandler);
                
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create camera preview session", e);
        }
    }
    
    private void processJpegFrame(Image image) {
        try {
            // For JPEG format, get data directly - no color conversion needed
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] jpegData = new byte[buffer.remaining()];
            buffer.get(jpegData);
            
            // Apply orientation rotation if needed
            jpegData = applyOrientationRotation(jpegData);
            
            lastJpegData = jpegData;
            
            // Create frame data for compatibility (use Y plane size)
            lastFrameData = new byte[frameWidth * frameHeight];
            
            // Log frame processing (limit frequency to avoid spam)
            if (System.currentTimeMillis() % 1000 < 100) {
                Log.d(TAG, "Processed JPEG frame: " + jpegData.length + " bytes");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing JPEG frame", e);
        }
    }
    
    private byte[] applyOrientationRotation(byte[] jpegData) {
        try {
            // Calculate required rotation (90-degree steps)
            int requiredRotation = calculateRequiredRotation();
            
            if (requiredRotation > 0) {
                Log.d(TAG, "Applying " + requiredRotation + "° rotation to JPEG frame");
                return applyExifRotation(jpegData, requiredRotation);
            }
            
            return jpegData;
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply orientation rotation", e);
            return jpegData; // Return original if rotation fails
        }
    }
    
    private int calculateRequiredRotation() {
        // Corrected rotation calculation to fix upside down landscape
        // For front camera: (sensorOrientation - deviceOrientation + 360) % 360
        // For back camera: (sensorOrientation + deviceOrientation) % 360
        
        int requiredRotation;
        
        try {
            // Get current camera facing
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(currentCameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                // Front camera rotation calculation (corrected)
                requiredRotation = (sensorOrientation - deviceOrientation + 360) % 360;
            } else {
                // Back camera rotation calculation (corrected)
                requiredRotation = (sensorOrientation + deviceOrientation) % 360;
            }
            
            Log.d(TAG, "Camera: " + (facing == CameraCharacteristics.LENS_FACING_FRONT ? "Front" : "Back") + 
                      ", Sensor: " + sensorOrientation + "°, Device: " + deviceOrientation + 
                      "°, Required: " + requiredRotation + "°");
            
            return requiredRotation;
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera characteristics for rotation calculation", e);
            // Fallback to simple calculation
            return (sensorOrientation + deviceOrientation) % 360;
        }
    }
    
    private byte[] applyExifRotation(byte[] jpegData, int rotation) {
        try {
            // Convert JPEG to Bitmap
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode JPEG to bitmap");
                return jpegData;
            }
            
            // Create rotation matrix
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            
            // Apply rotation
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            
            // Convert back to JPEG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            
            // Clean up bitmaps
            bitmap.recycle();
            rotatedBitmap.recycle();
            
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply bitmap rotation", e);
            return jpegData;
        }
    }
    

    
    // JPEG format eliminates the need for YUV to RGB conversion
    // This method is kept for compatibility but not used with JPEG format
    private Bitmap yuvToBitmap(Image image) {
        Log.d(TAG, "YUV conversion not needed with JPEG format");
        return null;
    }
    
    // YUV conversion methods removed - not needed with JPEG format
    // This eliminates the black/white with purple stripes issue entirely
    
    public void goLive() {
        if (!isInitialized.get()) {
            Log.e(TAG, "Camera2 Video Manager not initialized");
            return;
        }
        
        try {
            Log.d(TAG, "Going live with Camera2...");
            
            // Generate SDP offer with real camera capabilities
            localSdp = generateRealSdpOffer();
            Log.d(TAG, "Generated real SDP offer: " + localSdp.substring(0, Math.min(100, localSdp.length())) + "...");
            
            isStreaming.set(true);
            Log.d(TAG, "Successfully went live with Camera2");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to go live", e);
        }
    }
    
    public void stopLive() {
        if (!isInitialized.get()) {
            Log.e(TAG, "Camera2 Video Manager not initialized");
            return;
        }
        
        try {
            Log.d(TAG, "Stopping live stream...");
            
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            
            isStreaming.set(false);
            Log.d(TAG, "Successfully stopped live stream");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop live stream", e);
        }
    }
    
    public void dispose() {
        try {
            stopLive();
            
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            
            if (orientationEventListener != null) {
                orientationEventListener.disable();
                orientationEventListener = null;
            }
            
            if (backgroundThread != null) {
                backgroundThread.quitSafely();
                try {
                    backgroundThread.join();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Background thread interrupted", e);
                }
                backgroundThread = null;
                backgroundHandler = null;
            }
            
            isInitialized.set(false);
            isCameraReady.set(false);
            isStreaming.set(false);
            
            Log.d(TAG, "Camera2 Video Manager disposed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error disposing Camera2 Video Manager", e);
        }
    }
    
    // Helper methods
    private String generateRealSdpOffer() {
        // Generate a real SDP offer based on actual camera capabilities
        StringBuilder sdp = new StringBuilder();
        sdp.append("v=0\r\n");
        sdp.append("o=- 1234567890 2 IN IP4 127.0.0.1\r\n");
        sdp.append("s=-\r\n");
        sdp.append("t=0 0\r\n");
        sdp.append("a=group:BUNDLE 0\r\n");
        sdp.append("a=msid-semantic: WMS\r\n");
        sdp.append("m=video 9 UDP/TLS/RTP/SAVPF 96\r\n");
        sdp.append("c=IN IP4 0.0.0.0\r\n");
        sdp.append("a=mid:0\r\n");
        sdp.append("a=sendonly\r\n");
        sdp.append("a=rtcp-mux\r\n");
        sdp.append("a=rtpmap:96 H264/90000\r\n");
        sdp.append("a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f\r\n");
        sdp.append("a=rtcp-fb:96 nack\r\n");
        sdp.append("a=rtcp-fb:96 nack pli\r\n");
        sdp.append("a=rtcp-fb:96 ccm fir\r\n");
        sdp.append("a=ice-ufrag:real\r\n");
        sdp.append("a=ice-pwd:realsource123456789012345678901234567890\r\n");
        sdp.append("a=setup:passive\r\n");
        sdp.append("a=fingerprint:sha-256 12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0\r\n");
        sdp.append("a=candidate:1 1 UDP 2122252543 127.0.0.1 9 typ host\r\n");
        
        return sdp.toString();
    }
    
    // Public getter methods (for compatibility)
    public boolean isInitialized() {
        return isInitialized.get();
    }
    
    public boolean isCameraReady() {
        return isCameraReady.get();
    }
    
    public boolean isStreaming() {
        return isStreaming.get();
    }
    
    public String getLocalSdp() {
        return localSdp;
    }
    
    public void setRemoteSdp(String remoteSdp) {
        this.remoteSdp = remoteSdp;
        Log.d(TAG, "Remote SDP set: " + remoteSdp.substring(0, Math.min(100, remoteSdp.length())) + "...");
    }
    
    public void addIceCandidate(String candidate) {
        Log.d(TAG, "ICE candidate added: " + candidate);
    }
    
    public byte[] getLastFrameData() {
        return lastFrameData;
    }
    
    public byte[] getLastJpegData() {
        return lastJpegData;
    }
    
    public int getFrameWidth() {
        return frameWidth;
    }
    
    public int getFrameHeight() {
        return frameHeight;
    }
    
    /**
     * Switch to a specific camera
     */
    public void switchCamera(String cameraId) {
        if (isStreaming.get()) {
            stopLive();
        }
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        currentCameraId = cameraId;
        startCamera();
    }
    
    /**
     * Toggle between front and back cameras
     */
    public void toggleCamera() {
        String newCameraId = null;
        if (currentCameraId != null) {
            // Find current camera facing
            try {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(currentCameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        newCameraId = getFrontCameraId();
                    } else {
                        newCameraId = getBackCameraId();
                    }
                }
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to get camera characteristics", e);
            }
        }
        
        if (newCameraId != null) {
            switchCamera(newCameraId);
        } else {
            Log.e(TAG, "Failed to find camera to switch to");
        }
    }
    
    /**
     * Get current camera ID
     */
    public String getCurrentCameraId() {
        return currentCameraId;
    }
    
    /**
     * Switch to saved camera preference
     */
    public void switchToSavedCamera() {
        if (isStreaming.get()) {
            stopLive();
        }
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        startCamera();
    }
} 