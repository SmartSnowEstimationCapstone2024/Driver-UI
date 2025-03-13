package com.example.snowcapui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.snowcapui.network.ApiClient;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final int UPDATE_INTERVAL = 2000; // Update every 2 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);

        // Start fetching the snow level every 2 seconds
        startRepeatingTask();
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            executorService.execute(() -> {
                try {
                    String result = ApiClient.getSnowLevel();
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
