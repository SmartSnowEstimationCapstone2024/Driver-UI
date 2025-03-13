package com.example.snowcapui.network;

import com.example.snowcapui.MainActivity;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import org.json.JSONObject;


import java.io.*;
import java.net.*;
import org.json.JSONObject;

public class ApiClient {
    private static String SERVER_URL = "http://10.190.43.190/test_api/get_snow_level.php";

    public static String getSnowLevel() throws IOException {
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
