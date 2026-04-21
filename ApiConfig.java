package com.deepguard.app;

public class ApiConfig {
    // Add /api/ to the base URL
    // ========== UPDATE THIS IP ADDRESS ==========
// Find your IP: Open CMD -> type "ipconfig" -> look for IPv4 Address
// Example: 192.168.1.5, 192.168.0.105, etc.
//private static final String BASE_URL = "http://192.168.1.5:5000/api/";
   //private static final String BASE_URL = "http://10.40.79.48:5000/api/";

    //emulator
    private static final String BASE_URL = "http://10.0.2.2:5000/api/";

    //private static final String BASE_URL = "http://172.28.70.48:5000/api/";

    // Timeout Settings (milliseconds)
    public static final int CONNECTION_TIMEOUT = 60000; // 60 seconds
    public static final int SOCKET_TIMEOUT = 60000; // 60 seconds

    public static String getBaseUrl() {
        return BASE_URL;
    }

    public static boolean isLocalDevelopment() {
        return BASE_URL.contains("localhost") ||
                BASE_URL.contains("10.0.2.2") ||
                BASE_URL.contains("192.168");
    }
}