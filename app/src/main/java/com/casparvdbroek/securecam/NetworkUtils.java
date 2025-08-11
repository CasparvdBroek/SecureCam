package com.casparvdbroek.securecam;

import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/**
 * Utility class for network-related operations.
 * Provides centralized methods for IP address retrieval and network interface management.
 */
public class NetworkUtils {
    private static final String TAG = Constants.TAG_NETWORK_UTILS;
    
    /**
     * Gets the local IP address of the device.
     * Prioritizes non-loopback, IPv4 addresses from active network interfaces.
     * 
     * @return The local IP address as a string, or fallback IP if none found
     */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (isValidNetworkInterface(networkInterface)) {
                    String ipAddress = getFirstValidIpAddress(networkInterface);
                    if (ipAddress != null) {
                        return ipAddress;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting local IP address", e);
        }
        return Constants.FALLBACK_IP;
    }
    
    /**
     * Checks if a network interface is valid for IP address retrieval.
     * 
     * @param networkInterface The network interface to check
     * @return true if the interface is valid, false otherwise
     */
    private static boolean isValidNetworkInterface(NetworkInterface networkInterface) {
        try {
            return networkInterface != null && 
                   !networkInterface.isLoopback() && 
                   networkInterface.isUp();
        } catch (SocketException e) {
            Log.w(TAG, "Error checking network interface status", e);
            return false;
        }
    }
    
    /**
     * Gets the first valid IP address from a network interface.
     * 
     * @param networkInterface The network interface to get IP from
     * @return The first valid IP address, or null if none found
     */
    private static String getFirstValidIpAddress(NetworkInterface networkInterface) {
        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress address = addresses.nextElement();
            if (isValidIpAddress(address)) {
                return address.getHostAddress();
            }
        }
        return null;
    }
    
    /**
     * Checks if an IP address is valid for local network communication.
     * 
     * @param address The IP address to check
     * @return true if the address is valid, false otherwise
     */
    private static boolean isValidIpAddress(InetAddress address) {
        return address != null && 
               !address.isLoopbackAddress() && 
               address.getHostAddress().indexOf(':') < 0; // Exclude IPv6 addresses
    }
    
    /**
     * Gets the server URL for the WebRTC server.
     * 
     * @param port The port number for the server
     * @return The complete server URL
     */
    public static String getServerUrl(int port) {
        return "http://" + getLocalIpAddress() + ":" + port;
    }
}
