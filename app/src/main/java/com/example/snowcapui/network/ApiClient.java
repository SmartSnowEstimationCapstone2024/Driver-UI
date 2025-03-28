package com.example.snowcapui.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
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

    public static SnowData getSnowData(Context context) throws IOException {
        if (SERVER_URL == null) {
            loadServerIp(context); // Ensure SERVER_URL is set
        }

        URL url = new URL(SERVER_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Connection", "close");
        StringBuilder response = new StringBuilder();

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
            } catch (IOException e) {
                Log.e("Failed to receive http stream.", e.getMessage());
            }
        }
        connection.disconnect();

        try {
            Log.i("API", response.toString());
            JSONObject jsonObject = new JSONObject(response.toString());

            double snowLevel = jsonObject.getDouble("snow_level");
            JSONArray coverageArray = jsonObject.getJSONArray("segment_coverage");
            double[] segmentCoverage = new double[3];
            for (int i = 0; i < 3; i++) {
                segmentCoverage[i] = coverageArray.getDouble(i);
            }

            // New values from Flask server
            boolean overrideFlag = jsonObject.getBoolean("override_flag");
            int overridePreset = jsonObject.getInt("override_preset");
            double dispensingRate = jsonObject.getDouble("dispensing_rate");


            return new SnowData(snowLevel, segmentCoverage, overrideFlag, overridePreset, dispensingRate);
        } catch (Exception e) {
            Log.e("API", "Error receiving dispense data", e);
            return null; // Return null if parsing fails
        }
    }

    // Updated Data class to store additional values
    public static class SnowData {
        public double snowLevel;
        public double[] segmentCoverage;
        public boolean overrideFlag;
        public int overridePreset;
        public double dispensingRate;

        public SnowData(double snowLevel, double[] segmentCoverage, boolean overrideFlag, int overridePreset, double dispensingRate) {
            this.snowLevel = snowLevel;
            this.segmentCoverage = segmentCoverage;
            this.overrideFlag = overrideFlag;
            this.overridePreset = overridePreset;
            this.dispensingRate = dispensingRate;
        }
    }

    public static void sendOverrideData(Context context, boolean overrideFlag, int overridePreset) {
        if (SERVER_URL == null) {
            loadServerIp(context);
        }

        // Run network call in a background thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                // Create JSON payload
                JSONObject json = new JSONObject();
                json.put("override_flag", overrideFlag);
                json.put("override_preset", overridePreset);

                // Send JSON data
                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(json.toString());
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("API", "Override data sent successfully");
                } else {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();

                    Log.e("API", "Failed to send override data, Response Code: " + responseCode + ", Error: " + errorResponse.toString());
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e("API", "Error sending override data", e);
            }
        });
    }


}
