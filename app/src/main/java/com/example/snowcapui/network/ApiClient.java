package com.example.snowcapui.network;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import org.json.JSONObject;

public class ApiClient {
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_IP = "server_ip";
    private static String SERVER_URL;

    // Method to set and save the server IP
    public static void setServerIp(Context context, String ipAddress) {
        SERVER_URL = "http://" + ipAddress + ":5000/snowlevel"; // Update the API URL

        // Save the new IP in SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_IP, ipAddress);
        editor.apply();
    }

    // Load saved IP, or if none exists, set and save default
    public static void loadServerIp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedIp = prefs.getString(KEY_IP, null); // Get saved IP or null

        if (savedIp == null) { // No saved IP? Set the default as if user entered it
            savedIp = "10.190.43.190";
            setServerIp(context, savedIp); // Save it so it persists
        }

        SERVER_URL = "http://" + savedIp + ":5000/snowlevel"; // Set API URL
    }

    // Fetch the latest snow level from the server
    public static String getSnowLevel(Context context) throws IOException {
        if (SERVER_URL == null) {
            loadServerIp(context); // Ensure SERVER_URL is set
        }

        URL url = new URL(SERVER_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        try {
            JSONObject jsonResponse = new JSONObject(response.toString());
            return jsonResponse.getString("snow_level");
        } catch (Exception e) {
            return "Invalid response format";
        }
    }
}
