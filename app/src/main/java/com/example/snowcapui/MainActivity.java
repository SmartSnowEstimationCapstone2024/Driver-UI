package com.example.snowcapui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.snowcapui.network.ApiClient;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;
    private EditText ipInput;
    private Button saveIpButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final int UPDATE_INTERVAL = 2000; // Update every 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        ipInput = findViewById(R.id.ipInput);
        saveIpButton = findViewById(R.id.saveIpButton);

        // Load the saved IP from SharedPreferences and update ApiClient
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedIp = prefs.getString("server_ip", "10.190.43.190"); // Default IP
        ipInput.setText(savedIp);
        ApiClient.loadServerIp(this); // Load and apply saved IP

        // Set user-provided IP when the button is clicked
        saveIpButton.setOnClickListener(v -> {
            String ipAddress = ipInput.getText().toString().trim();
            if (!ipAddress.isEmpty()) {
                ApiClient.setServerIp(this, ipAddress);
                statusText.setText("IP Set to: " + ipAddress);
            }
        });

        // Start fetching the snow level every 2 seconds
        startRepeatingTask();
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            executorService.execute(() -> {
                try {
                    String result = ApiClient.getSnowLevel(MainActivity.this);
                    handler.post(() -> statusText.setText("Snow Level: " + result));
                } catch (IOException e) {
                    handler.post(() -> statusText.setText("Error: " + e.getMessage()));
                }
            });

            handler.postDelayed(this, UPDATE_INTERVAL); // Schedule next update
        }
    };

    private void startRepeatingTask() {
        handler.post(updateTask);
    }

    private void stopRepeatingTask() {
        handler.removeCallbacks(updateTask);
        executorService.shutdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRepeatingTask();
    }
}
