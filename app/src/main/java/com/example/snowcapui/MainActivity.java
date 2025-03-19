package com.example.snowcapui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.snowcapui.network.ApiClient;

public class MainActivity extends AppCompatActivity {
    private TextView statusText, saltRateValue;
    private EditText ipInput;
    private Button saveIpButton;
    private ToggleButton manualOverrideButton;
    private SeekBar saltRateSlider;
    private ProgressBar saltRateBar;
    private View leftSnowIndicator, middleSnowIndicator, rightSnowIndicator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final int UPDATE_INTERVAL = 2000; // Update every 2 seconds

    // Variables to store API data
    private boolean overrideState = false;
    private float manualDispenseRate = 0f;
    private float calculatedDispenseRate = 0f;
    private float leftSnow = 0f, middleSnow = 0f, rightSnow = 0f;

    // Fixed slider levels
    private final int[] SLIDER_VALUES = {0, 25, 50, 100};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        ipInput = findViewById(R.id.ipInput);
        saveIpButton = findViewById(R.id.saveIpButton);
        manualOverrideButton = findViewById(R.id.manualOverrideButton);
        saltRateSlider = findViewById(R.id.saltRateSlider);
        saltRateBar = findViewById(R.id.saltRateBar);
        leftSnowIndicator = findViewById(R.id.leftSnowIndicator);
        middleSnowIndicator = findViewById(R.id.middleSnowIndicator);
        rightSnowIndicator = findViewById(R.id.rightSnowIndicator);
        saltRateValue = findViewById(R.id.saltRateValue);

        // Load saved IP
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedIp = prefs.getString("server_ip", "10.190.43.190");
        ipInput.setText(savedIp);
        ApiClient.loadServerIp(this);

        // Save new IP when button clicked
        saveIpButton.setOnClickListener(v -> {
            String ipAddress = ipInput.getText().toString().trim();
            if (!ipAddress.isEmpty()) {
                ApiClient.setServerIp(this, ipAddress);
                statusText.setText("IP Set to: " + ipAddress);
            }
        });

        // Toggle manual override
        manualOverrideButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            overrideState = isChecked;
            updateSaltRate();
        });

        // Adjust slider to 4 fixed levels
        saltRateSlider.setMax(100);
        saltRateSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int closestValue = getClosestSliderValue(progress);
                    seekBar.setProgress(closestValue);
                    manualDispenseRate = closestValue / 100f;
                    updateSaltRate();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        startRepeatingTask();
    }

    private int getClosestSliderValue(int progress) {
        int closest = SLIDER_VALUES[0];
        for (int value : SLIDER_VALUES) {
            if (Math.abs(progress - value) < Math.abs(progress - closest)) {
                closest = value;
            }
        }
        return closest;
    }

    private void updateSaltRate() {
        float rate;

        if (overrideState) {
            // Override mode: use manual slider setting
            rate = manualDispenseRate;
        } else {
            // Auto mode: Normalize snow level to dispensing range (0 - 100%)
            float minSnowLevel = 0.1f;  // Adjust based on real minimum snow level
            float maxSnowLevel = 1.7f; // Adjust based on max expected snow level
            float minDispenseRate = 0f;
            float maxDispenseRate = 1f; // 1 = 100% dispense rate

            // Ensure snow level is within range before normalizing
            float normalizedSnowLevel = Math.max(minSnowLevel, Math.min(maxSnowLevel, calculatedDispenseRate));

            // Apply normalization formula
            rate = minDispenseRate + (normalizedSnowLevel - minSnowLevel) / (maxSnowLevel - minSnowLevel) * (maxDispenseRate - minDispenseRate);
        }

        // Convert rate to percentage for display
        int displayRate = (int) (rate * 100);
        saltRateBar.setProgress(displayRate);
        saltRateValue.setText(String.format("Salt Rate: %d%%", displayRate));
    }


    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            executorService.execute(() -> {
                try {
                    ApiClient.SnowData snowData = ApiClient.getSnowData(MainActivity.this);
                    if (snowData != null) {
                        double snowLevel = snowData.snowLevel;
                        double[] segmentCoverage = snowData.segmentCoverage;

                        Log.d("API_RESPONSE", "Snow Level: " + snowLevel);
                        Log.d("API_RESPONSE", "Segment Coverage: " + Arrays.toString(segmentCoverage));

                        leftSnow = (float) segmentCoverage[0] / 100f;
                        middleSnow = (float) segmentCoverage[1] / 100f;
                        rightSnow = (float) segmentCoverage[2] / 100f;

                        calculatedDispenseRate = (float) (snowLevel * 0.2);

                        handler.post(() -> {
                            updateSaltRate();
                            leftSnowIndicator.setAlpha(leftSnow);
                            middleSnowIndicator.setAlpha(middleSnow);
                            rightSnowIndicator.setAlpha(rightSnow);
                        });

                    } else {
                        handler.post(() -> statusText.setText("Error: Invalid response"));
                    }

                } catch (IOException e) {
                    handler.post(() -> {
                        statusText.setText("Error: " + e.getMessage());
                        saltRateValue.setText("Error");
                    });
                }
            });

            handler.postDelayed(this, UPDATE_INTERVAL);
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
