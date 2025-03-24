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
    private int overridePreset = 0;
    private float manualDispenseRate = 0;
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

        manualOverrideButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            overrideState = isChecked;
            sendOverrideData(); // Ensure the server knows about the override
            updateSaltRate(); // Update UI immediately
        });



        saltRateSlider.setMax(4); // Set max to 4 (values: 0, 1, 2, 3, 4)
        saltRateSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    overridePreset = progress;
                    Log.d("SLIDER", "Override Preset set to: " + overridePreset);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendOverrideData();
                updateSaltRate(); // Update UI when slider is released
            }
        });



        startRepeatingTask();
    }

    private void sendOverrideData() {
        new Thread(() -> {
            try {
                ApiClient.sendOverrideData(MainActivity.this, manualOverrideButton.isChecked(), overridePreset);
            } catch (Exception e) {
                Log.e("API", "Error sending override data", e);
            }
        }).start();
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
        float kg_km;

        if (overrideState) {
            // Override mode: Use manual preset rate
            kg_km = getSaltRateFromPreset(overridePreset);
        } else {
            // Auto mode: Use API-calculated dispensing rate
            kg_km = calculatedDispenseRate;
        }

        int percentRate =(int)((calculatedDispenseRate)/2.6);

        saltRateBar.setProgress(percentRate);
        saltRateValue.setText(String.format("Salt Rate: %d%% (%.0f kg/lane km)", percentRate, kg_km));
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
                        boolean overrideFlagFromApi = snowData.overrideFlag;
                        int overridePresetFromApi = snowData.overridePreset;
                        double dispensingRateFromApi = snowData.dispensingRate;

                        Log.d("API_RESPONSE", "Snow Level: " + snowLevel);
                        Log.d("API_RESPONSE", "Segment Coverage: " + Arrays.toString(segmentCoverage));
                        Log.d("API_RESPONSE", "Override Flag: " + overrideFlagFromApi);
                        Log.d("API_RESPONSE", "Override Preset: " + overridePresetFromApi);
                        Log.d("API_RESPONSE", "Dispensing Rate: " + dispensingRateFromApi);

                        leftSnow = (float) segmentCoverage[0] / 100f;
                        middleSnow = (float) segmentCoverage[1] / 100f;
                        rightSnow = (float) segmentCoverage[2] / 100f;
                        calculatedDispenseRate = (float) dispensingRateFromApi; // Use API value

                        handler.post(() -> {
                            updateSaltRate();
                            leftSnowIndicator.setAlpha(1.0f - leftSnow);
                            middleSnowIndicator.setAlpha(1.0f - middleSnow);
                            rightSnowIndicator.setAlpha(1.0f - rightSnow);


                            // Update UI for override values
                            manualOverrideButton.setChecked(overrideFlagFromApi);
                            statusText.setText("Override Preset: " + overridePresetFromApi);
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

    private float getSaltRateFromPreset(int preset) {
        switch (preset) {
            case 1: return 90f;
            case 2: return 130f;
            case 3: return 180f;
            case 4: return 260f;
            default: return 0f; // Case 0: No salt applied
        }
    }


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
