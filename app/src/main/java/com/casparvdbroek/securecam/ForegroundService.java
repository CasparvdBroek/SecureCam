package com.casparvdbroek.securecam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class ForegroundService extends Service {
    private static final String TAG = "ForegroundService";
    private static final String CHANNEL_ID = "SecureCamServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private Camera2VideoManager videoManager;
    private SimpleHttpServer httpServer;
    private boolean isVideoInitialized = false;
    private Thread watchdogThread;
    private boolean isRunning = true;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        // Start foreground immediately to prevent crashes
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        Log.d(TAG, "Foreground notification started successfully");
        
        // Initialize CameraX Video Manager on main thread to avoid threading issues
        try {
            initializeVideoSDK();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CameraX Video Manager", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        // Don't start camera automatically - let it be triggered manually
        // This prevents crashes during service startup
        
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        isRunning = false;
        if (watchdogThread != null) {
            watchdogThread.interrupt();
        }
        cleanupVideo();
    }

    private void initializeVideoSDK() {
        try {
            // Camera2 Video Manager implementation for better Android 7.1 compatibility
            Log.d(TAG, "Initializing Camera2 Video Manager service");
            
            // Start HTTP server first (this will work even without camera)
            try {
                httpServer = new SimpleHttpServer(this, null);
                Log.d(TAG, "HTTP server started successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start HTTP server", e);
                httpServer = null;
            }
            
            // Create Camera2 Video Manager
            try {
                videoManager = new Camera2VideoManager(this);
                Log.d(TAG, "Camera2 Video Manager created successfully");
                
                // Initialize Camera2 Video Manager
                videoManager.initialize();
                Log.d(TAG, "Camera2 Video Manager initialized successfully");
                
                // Update HTTP server with video manager
                if (httpServer != null) {
                    httpServer.setVideoManager(videoManager);
                    Log.d(TAG, "HTTP server updated with video manager");
                }
                
                // Mark as initialized
                isVideoInitialized = true;
                Log.d(TAG, "Camera2 Video Manager initialization completed successfully");
                
                // Start camera immediately (Camera2 is more reliable)
                startVideoCamera();
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to create Camera2 Video Manager", e);
                videoManager = null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Camera2 Video Manager service", e);
        }
    }

    private void startVideoCamera() {
        if (videoManager == null) {
            Log.e(TAG, "Camera2 Video Manager is null, cannot start camera");
            return;
        }
        
        try {
            Log.d(TAG, "Starting camera with Camera2 Video Manager...");
            
            // Camera2 is more reliable, no need for complex timing
            videoManager.startCamera();
            Log.d(TAG, "Camera started successfully");
            
            // Go live after a short delay
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Shorter delay for Camera2
                    Log.d(TAG, "Going live...");
                    videoManager.goLive();
                    Log.d(TAG, "Camera is now live");
                    
                    // Start watchdog after camera is live
                    Thread.sleep(2000);
                    startWatchdog();
                    
                } catch (InterruptedException e) {
                    Log.e(TAG, "Camera startup interrupted", e);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start camera", e);
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera", e);
        }
    }

    private void startWatchdog() {
        watchdogThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Thread.sleep(15000); // Check every 15 seconds (less aggressive)
                    
                    // Check if HTTP server is running
                    if (httpServer != null && !httpServer.isRunning()) {
                        Log.w(TAG, "HTTP server stopped, restarting...");
                        restartHttpServer();
                    }
                    
                    // Check if video manager is working (only if it exists)
                    if (videoManager != null) {
                        boolean isReady = videoManager.isCameraReady();
                        boolean isStreaming = videoManager.isStreaming();
                        boolean isInitialized = videoManager.isInitialized();
                        Log.d(TAG, "Watchdog check - Initialized: " + isInitialized + ", Camera ready: " + isReady + ", Streaming: " + isStreaming);
                        
                        // Only restart if initialized but not ready after a reasonable time
                        // Give camera more time to start (30 seconds total)
                        if (isInitialized && !isReady && isVideoInitialized) {
                            Log.w(TAG, "Camera initialized but not ready after 15 seconds, restarting...");
                            restartVideo();
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Log.d(TAG, "Watchdog thread interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Watchdog error", e);
                }
            }
        });
        watchdogThread.start();
        Log.d(TAG, "Watchdog thread started with 15-second intervals");
    }

    private void restartHttpServer() {
        try {
            if (httpServer != null) {
                httpServer.stop();
            }
            httpServer = new SimpleHttpServer(this, videoManager);
            Log.d(TAG, "HTTP server restarted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart HTTP server", e);
        }
    }

    private void restartVideo() {
        try {
            if (videoManager != null) {
                videoManager.dispose();
            }
            videoManager = new Camera2VideoManager(this);
            videoManager.initialize();
            
            // Start camera with proper timing - camera initialization is asynchronous
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Wait for initialization to complete
                    Log.d(TAG, "Starting camera after initialization delay...");
                    videoManager.startCamera();
                    
                    Thread.sleep(2000); // Wait for camera to start and bind
                    Log.d(TAG, "Going live after camera start delay...");
                    videoManager.goLive();
                    
                    if (httpServer != null) {
                        httpServer.setVideoManager(videoManager);
                    }
                    
                    Log.d(TAG, "Video manager restarted successfully with proper timing");
                } catch (InterruptedException e) {
                    Log.e(TAG, "Camera restart interrupted", e);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restart camera with timing", e);
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart video manager", e);
        }
    }

    private void cleanupVideo() {
        try {
            if (videoManager != null) {
                videoManager.dispose();
                videoManager = null;
            }
            
            if (httpServer != null) {
                httpServer.stop();
                httpServer = null;
            }
            
            Log.d(TAG, "Video cleanup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error during video cleanup", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SecureCam Service Channel",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Channel for SecureCam foreground service");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        String serverUrl = "http://" + getLocalIpAddress() + ":8080";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SecureCam Camera Server")
            .setContentText("Server running at: " + serverUrl)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);
        
        return builder.build();
    }

    private void performBackgroundWork() {
        // This method can be used for any background work
        Log.d(TAG, "Performing background work");
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting local IP address", e);
        }
        return "127.0.0.1";
    }
} 
