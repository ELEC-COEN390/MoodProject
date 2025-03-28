package com.example.moodproject;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import org.vosk.android.StorageService;

public class Dashboard extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

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
    private ImageButton recordButton;
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView spokenText;
    private TextView micLabel;

    private Socket socket;
    private byte[] audioData;
    private boolean isRecording = false;
    private AudioTrack audioTrack;

    VideoView videoBackground;

    // Navigation Drawer components
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private FirebaseAuth mAuth;

    // Speech recognition components
    private VoskSpeechRecognizer voskRecognizer;
    private File lastRecordedFile;

    private static final int PERMISSION_REQUEST_CODE = 200;
    private String[] requiredPermissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO // Add record audio permission for speech recognition
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        // Initialize UI components
        connectButton = findViewById(R.id.connectButton);
        recordButton = findViewById(R.id.ImageButton);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        spokenText = findViewById(R.id.textView3);

        // May be null if not in your layout
        try {
            micLabel = findViewById(R.id.micLabel);
        } catch (Exception e) {
            Log.e(TAG, "micLabel not found in layout");
        }

        // Disable buttons initially
        recordButton.setEnabled(false);

        videoBackground = findViewById(R.id.videoBackground);

        // Set up the video background
        setupVideoBackground();

        // Set up the navigation drawer
        setupNavigationDrawer();

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

// Remove the duplicate initialization
        if (checkPermissions()) {
            Log.d(TAG, "Permissions already granted, initializing speech recognizer");
            initSpeechRecognizer();
        } else {
            Log.d(TAG, "Requesting permissions");
            requestPermissions();
            // The initialization will happen in onRequestPermissionsResult
        }
// Remove this line: initSpeechRecognizer();


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
    }

    // Initialize the Vosk-based speech recognizer
    private void initSpeechRecognizer() {
        voskRecognizer = new VoskSpeechRecognizer(this);
        Log.d(TAG, "Speech recognizer initialized");
    }

    // This method will be called by the VoskSpeechRecognizer to update the UI with recognized text
    public void updateRecognizedText(String text) {
        // Update the UI with recognized text
        if (spokenText != null) {
            spokenText.setText(text);
        }
    }

    // Setup the navigation drawer
    private void setupNavigationDrawer() {
        // Initialize components
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup the toggle button
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Setup navigation item click listener
        navigationView.setNavigationItemSelectedListener(this);
    }

    // Handle navigation item clicks
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Already on home screen, just close drawer
            Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_settings) {
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
            // Launch settings activity
            // Intent intent = new Intent(this, SettingsActivity.class);
            // startActivity(intent);
        } else if (id == R.id.nav_history) {
            Toast.makeText(this, "Recording History", Toast.LENGTH_SHORT).show();
            // Launch history activity
            // Intent intent = new Intent(this, HistoryActivity.class);
            // startActivity(intent);
        } else if (id == R.id.nav_wifi_settings) {
            Toast.makeText(this, "WiFi Settings", Toast.LENGTH_SHORT).show();
            // Open WiFi settings
            startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
        } else if (id == R.id.nav_connection) {
            Toast.makeText(this, "Connection Settings", Toast.LENGTH_SHORT).show();
            // Show connection dialog or activity
        } else if (id == R.id.nav_about) {
            Toast.makeText(this, "About", Toast.LENGTH_SHORT).show();
            // Show about dialog
            showAboutDialog();
        } else if (id == R.id.nav_logout) {
            Toast.makeText(this, "Logout", Toast.LENGTH_SHORT).show();
            // Show help dialog or activity

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);

            mAuth = FirebaseAuth.getInstance();
            mAuth.signOut();

            finish();
        }

        // Close the drawer
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    // Method to show About dialog
    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About ESP32 Audio Client");
        builder.setMessage("Version 1.0\n\nThis application allows you to record and process audio using an ESP32 microcontroller.");
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    // Handle back button press
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // Check if we have the required permissions
    private boolean checkPermissions() {
        String[] permissions = getRequiredPermissions();
        for (String permission : permissions) {
            boolean granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Permission " + permission + " granted: " + granted);
            if (!granted) {
                return false;
            }
        }
        return true;
    }

    // Request the required permissions
    private void requestPermissions() {
        Log.d(TAG, "Requesting permissions...");
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_REQUEST_CODE);
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
            recordButton.setEnabled(success);

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
            // recordButton.setText("Stop");
            statusText.setText("Recording...");
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            startTime = System.currentTimeMillis();

            // Update mic label if it exists
            if (micLabel != null) {
                micLabel.setText("Recording... 🔴");
            }
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
            //recordButton.setText("Record");
            progressBar.setVisibility(View.GONE);

            // Reset mic label if it exists
            if (micLabel != null) {
                micLabel.setText("Tap to Speak 🗣️");
            }

            if (success) {
                statusText.setText("Recording complete");
                // Process speech to text
                setSpeachToText();
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
        }
    }

    // Save audio data to file and process for speech recognition
    private void saveAudioToFile() {
        try {
            // Use internal storage instead of external
            File directory = new File(getFilesDir(), "AudioRecordings");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String timeStamp = String.valueOf(System.currentTimeMillis());
            File file = new File(directory, "ESP32_Recording_" + timeStamp + ".pcm");

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(audioData);
            fos.close();

            Log.i(TAG, "Audio saved to " + file.getAbsolutePath());

            // Store the file reference for speech recognition
            lastRecordedFile = file;

        } catch (IOException e) {
            Log.e(TAG, "Error saving audio file: " + e.getMessage());
        }
    }

    // Process the audio file for speech recognition
    private void processSpeechRecognition(File audioFile) {
        if (voskRecognizer != null) {
            // Show loading indicator
            progressBar.setVisibility(View.VISIBLE);
            statusText.setText("Processing speech...");

            // Process the file in a background thread
            new Thread(() -> {
                voskRecognizer.processAudioFile(audioFile);

                // Hide loading indicator on the UI thread
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Speech processing complete");
                });
            }).start();
        } else {
            Log.e(TAG, "Speech recognizer is not initialized");
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
        }
    }

    // Process audio data directly (alternative to file-based approach)
    private void processAudioDataForSpeechRecognition() {
        if (voskRecognizer != null && audioData != null) {
            voskRecognizer.processAudioData(audioData);
        }
    }

    // Updated speech to text method
    private void setSpeachToText() {
        // If we have a recorded file, process it
        if (lastRecordedFile != null && lastRecordedFile.exists()) {
            processSpeechRecognition(lastRecordedFile);
        } else if (audioData != null) {
            // Fallback to processing the audio data directly
            processAudioDataForSpeechRecognition();
        } else {
            Log.e(TAG, "No recorded audio data found to process");
            spokenText.setText("No audio recorded to transcribe");
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

    private void makeFullScreen() {
        // Make the activity full screen
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Hide the status bar and navigation bar
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(),
                getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Add these flags for older Android versions
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        // Additional flags that help with full immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void setupVideoBackground() {
        try {
            // Path to the video file in raw folder
            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/raw/wave");
            videoBackground.setVideoURI(videoUri);

            // Loop the video
            videoBackground.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // Important: Set this for better scaling on different screens
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);

                    // Make video loop continuously
                    mp.setLooping(true);

                    // Mute the video
                    mp.setVolume(0, 0);
                }
            });

            // Handle video completion
            videoBackground.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    videoBackground.start(); // Restart the video when it ends
                }
            });

            // Start playing the video
            videoBackground.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-hide system bars when returning to the activity
        makeFullScreen();

        // Resume video playback when activity comes to foreground
        if (videoBackground != null && !videoBackground.isPlaying()) {
            videoBackground.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause video when activity is not visible
        if (videoBackground != null && videoBackground.isPlaying()) {
            videoBackground.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up resources
        if (videoBackground != null) {
            videoBackground.stopPlayback();
        }
        if (audioTrack != null) {
            audioTrack.release();
            audioTrack = null;
        }
        if (voskRecognizer != null) {
            voskRecognizer.destroy();
            voskRecognizer = null;
        }
        closeConnection();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Count denied permissions
            int deniedCount = 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    deniedCount++;
                }
            }

            if (deniedCount == 0) {
                // All permissions granted
                Log.d(TAG, "All permissions granted, initializing components");
                initSpeechRecognizer();
            } else {
                // Some permissions were denied
                Log.e(TAG, deniedCount + " permissions were denied");

                // Show a more detailed explanation dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Permissions Required");
                builder.setMessage("This app requires several permissions to function properly. " +
                        "Without these permissions, some features like speech recognition and " +
                        "audio recording will not work.");

                builder.setPositiveButton("Request Again", (dialog, which) -> {
                    requestPermissions();
                });

                builder.setNegativeButton("I Understand", (dialog, which) -> {
                    Toast.makeText(this, "Some features may be limited due to missing permissions",
                            Toast.LENGTH_LONG).show();

                    // Try to initialize with limited functionality
                    initSpeechRecognizer();
                });

                builder.show();
            }
        }
    }

    private String[] getRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { // Android 13+
            return new String[] {
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.RECORD_AUDIO
            };
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) { // Android 11+
            return new String[] {
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
            };
        } else { // Android 10 and below
            return new String[] {
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
            };
        }
    }



}