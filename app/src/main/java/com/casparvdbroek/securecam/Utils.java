package com.casparvdbroek.securecam;

import android.content.Context;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;

/**
 * Utility class for common operations across the SecureCam application.
 * Provides methods for resource management, error handling, and validation.
 */
public final class Utils {
    
    // Prevent instantiation
    private Utils() {}
    
    /**
     * Safely closes a closeable resource, logging any errors.
     * 
     * @param closeable The resource to close
     * @param tag The log tag to use for error messages
     */
    public static void closeQuietly(Closeable closeable, String tag) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                Log.e(tag, "Error closing resource", e);
            }
        }
    }
    
    /**
     * Safely closes multiple closeable resources.
     * 
     * @param closeables The resources to close
     * @param tag The log tag to use for error messages
     */
    public static void closeQuietly(Closeable... closeables) {
        for (Closeable closeable : closeables) {
            closeQuietly(closeable, "Utils");
        }
    }
    
    /**
     * Validates that an object is not null.
     * 
     * @param object The object to validate
     * @param name The name of the object for error messages
     * @throws IllegalArgumentException if the object is null
     */
    public static void requireNonNull(Object object, String name) {
        if (object == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }
    
    /**
     * Validates that a string is not null or empty.
     * 
     * @param string The string to validate
     * @param name The name of the string for error messages
     * @throws IllegalArgumentException if the string is null or empty
     */
    public static void requireNonEmpty(String string, String name) {
        requireNonNull(string, name);
        if (string.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
    }
    
    /**
     * Validates that a number is within a valid range.
     * 
     * @param value The value to validate
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @param name The name of the value for error messages
     * @throws IllegalArgumentException if the value is outside the range
     */
    public static void requireInRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                name + " must be between " + min + " and " + max + ", got: " + value);
        }
    }
    
    /**
     * Gets a human-readable error message for an exception.
     * 
     * @param e The exception
     * @return A user-friendly error message
     */
    public static String getErrorMessage(Exception e) {
        if (e == null) {
            return "Unknown error occurred";
        }
        
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName() + " occurred";
        }
        
        return message;
    }
    
    /**
     * Logs an error with additional context information.
     * 
     * @param tag The log tag
     * @param message The error message
     * @param e The exception
     * @param context Additional context information
     */
    public static void logError(String tag, String message, Exception e, String context) {
        String fullMessage = context != null ? message + " - Context: " + context : message;
        Log.e(tag, fullMessage, e);
    }
    
    /**
     * Checks if the application is running in debug mode.
     * 
     * @param context The application context
     * @return true if in debug mode, false otherwise
     */
    public static boolean isDebugMode(Context context) {
        return (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
    
    /**
     * Formats a byte size in human-readable format.
     * 
     * @param bytes The size in bytes
     * @return A formatted string (e.g., "1.5 MB")
     */
    public static String formatByteSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
