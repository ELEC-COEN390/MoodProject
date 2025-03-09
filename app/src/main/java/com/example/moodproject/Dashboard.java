package com.example.moodproject;


import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class Dashboard extends AppCompatActivity {

    private static final String TAG = "ESP32AudioClient";

    // Audio constants (match ESP32 settings)
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // ESP32 connection settings
    private static final String ESP32_IP = "192.168.4.1";
    private static final int ESP32_PORT = 80;
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds

    // 10 seconds of audio at 44.1kHz, 16-bit, mono
    private static final int RECORDING_DURATION_MS = 10000;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit = 2 bytes
    private static final int TOTAL_BYTES = (SAMPLE_RATE * RECORDING_DURATION_MS / 1000) * BYTES_PER_SAMPLE;

    private Button connectButton;
    private Button recordButton;
    private Button playButton;
    private TextView statusText;
    private ProgressBar progressBar;

    private Socket socket;
    private byte[] audioData;
    private boolean isRecording = false;
    private AudioTrack audioTrack;

    private static final int PERMISSION_REQUEST_CODE = 200;
    private String[] requiredPermissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        // Initialize UI components
        connectButton = findViewById(R.id.connectButton);
        recordButton = findViewById(R.id.recordButton);
        playButton = findViewById(R.id.playButton);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);

        // Disable buttons initially
        recordButton.setEnabled(false);
        playButton.setEnabled(false);

        // Setup audio buffer
        audioData = new byte[TOTAL_BYTES];

        // Setup AudioTrack for playback
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                .setBufferSizeInBytes(BUFFER_SIZE)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        // Check and request permissions
        if (!checkPermissions()) {
            requestPermissions();
        }

        // Set button click listeners
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new ConnectTask().execute();
            }
        });

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    new RecordTask().execute();
                } else {
                    isRecording = false;
                    statusText.setText("Recording stopped");
                }
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new PlayAudioTask().execute();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
        closeConnection();
    }

    // Check if we have the required permissions
    private boolean checkPermissions() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // Request the required permissions
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE);
    }

    // Close socket connection
    private void closeConnection() {
        if (socket != null && socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket: " + e.getMessage());
            }
            socket = null;
        }
    }

    // Task to connect to ESP32
    private class ConnectTask extends AsyncTask<Void, String, Boolean> {
        @Override
        protected void onPreExecute() {
            statusText.setText("Connecting to ESP32...");
            progressBar.setVisibility(View.VISIBLE);
            connectButton.setEnabled(false);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                publishProgress("Connecting to " + ESP32_IP + ":" + ESP32_PORT);
                closeConnection(); // Close any existing connection

                socket = new Socket();
                socket.connect(new InetSocketAddress(ESP32_IP, ESP32_PORT), CONNECTION_TIMEOUT);
                return true;
            } catch (IOException e) {
                publishProgress("Connection failed: " + e.getMessage());
                Log.e(TAG, "Connection error: " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            statusText.setText(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            progressBar.setVisibility(View.GONE);
            connectButton.setEnabled(true);

            if (success) {
                statusText.setText("Connected to ESP32");
                recordButton.setEnabled(true);
                Toast.makeText(Dashboard.this, "Connected to ESP32", Toast.LENGTH_SHORT).show();
            } else {
                statusText.setText("Connection failed");
                recordButton.setEnabled(false);
                Toast.makeText(Dashboard.this, "Connection failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Task to record audio from ESP32
    private class RecordTask extends AsyncTask<Void, Integer, Boolean> {
        private long startTime;

        @Override
        protected void onPreExecute() {
            isRecording = true;
            recordButton.setText("Stop");
            playButton.setEnabled(false);
            statusText.setText("Recording...");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            startTime = System.currentTimeMillis();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                if (socket == null || !socket.isConnected()) {
                    publishProgress(-1);
                    return false;
                }

                int totalBytesRead = 0;
                InputStream inputStream = socket.getInputStream();

                // Calculate time intervals for progress updates (every 5%)
                int progressInterval = TOTAL_BYTES / 20;
                int nextProgressUpdate = progressInterval;

                while (isRecording && totalBytesRead < TOTAL_BYTES) {
                    int availableBytes = inputStream.available();
                    if (availableBytes > 0) {
                        int bytesToRead = Math.min(availableBytes, TOTAL_BYTES - totalBytesRead);
                        int bytesRead = inputStream.read(audioData, totalBytesRead, bytesToRead);

                        if (bytesRead > 0) {
                            totalBytesRead += bytesRead;

                            // Update progress
                            if (totalBytesRead >= nextProgressUpdate) {
                                int progress = (totalBytesRead * 100) / TOTAL_BYTES;
                                publishProgress(progress);
                                nextProgressUpdate += progressInterval;
                            }

                            // Check if we've recorded enough data
                            if (totalBytesRead >= TOTAL_BYTES) {
                                break;
                            }
                        }
                    } else {
                        // Small delay to prevent CPU hogging
                        Thread.sleep(10);
                    }

                    // Check for timeout (15 seconds max)
                    if (System.currentTimeMillis() - startTime > 15000) {
                        publishProgress(-2);
                        return false;
                    }
                }

                // Save the audio file
                saveAudioToFile();
                return true;

            } catch (Exception e) {
                Log.e(TAG, "Recording error: " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            int progress = values[0];
            if (progress >= 0) {
                progressBar.setProgress(progress);
                statusText.setText("Recording... " + progress + "%");
            } else if (progress == -1) {
                statusText.setText("Error: Not connected");
            } else if (progress == -2) {
                statusText.setText("Error: Recording timed out");
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            isRecording = false;
            recordButton.setText("Record");
            progressBar.setVisibility(View.GONE);

            if (success) {
                statusText.setText("Recording complete");
                playButton.setEnabled(true);
            } else {
                statusText.setText("Recording failed");
            }
        }
    }

    // Task to play recorded audio
    private class PlayAudioTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected void onPreExecute() {
            statusText.setText("Playing audio...");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            connectButton.setEnabled(false);
            recordButton.setEnabled(false);
            playButton.setEnabled(false);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                audioTrack.play();

                // Calculate chunks for smoother playback
                int chunkSize = BUFFER_SIZE;
                int totalChunks = TOTAL_BYTES / chunkSize;

                for (int i = 0; i < totalChunks; i++) {
                    int offset = i * chunkSize;
                    int length = Math.min(chunkSize, TOTAL_BYTES - offset);

                    audioTrack.write(audioData, offset, length);

                    // Update progress
                    int progress = (i * 100) / totalChunks;
                    publishProgress(progress);
                }

                // Play any remaining data
                int remainingBytes = TOTAL_BYTES % chunkSize;
                if (remainingBytes > 0) {
                    audioTrack.write(audioData, TOTAL_BYTES - remainingBytes, remainingBytes);
                }

                // Wait for playback to complete
                audioTrack.stop();

            } catch (Exception e) {
                Log.e(TAG, "Playback error: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            statusText.setText("Playback complete");
            progressBar.setVisibility(View.GONE);
            connectButton.setEnabled(true);
            recordButton.setEnabled(true);
            playButton.setEnabled(true);
        }
    }

    // Save audio data to file
    private void saveAudioToFile() {
        try {
            File directory = new File(getExternalFilesDir(null), "AudioRecordings");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String timeStamp = String.valueOf(System.currentTimeMillis());
            File file = new File(directory, "ESP32_Recording_" + timeStamp + ".pcm");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(audioData);
            fos.close();

            Log.i(TAG, "Audio saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving audio file: " + e.getMessage());
        }
    }

    // Simple helper for converting byte array of PCM data to 16-bit shorts
    // (Useful for processing audio data if needed)
    private short[] byteToShortArray(byte[] bytes) {
        ShortBuffer shortBuffer = ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer();
        short[] shorts = new short[shortBuffer.capacity()];
        shortBuffer.get(shorts);
        return shorts;
    }
}