package com.example.moodproject;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.auth.FirebaseUser;

public class PreferencesActivity extends AppCompatActivity {

    private CheckBox musicCheckBox;
    private CheckBox sportsCheckBox;
    private CheckBox foodCheckBox;
    private CheckBox healthCheckBox;
    private CheckBox artsCheckBox;
    private CheckBox travelCheckBox;
    private Button save_button;

    VideoView videoBackground;

    // Firebase Helper
    private FirebaseHelper firebaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_preferences);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        videoBackground = findViewById(R.id.videoBackground);

        // Set up the video background
        setupVideoBackground();

        // Initialize Firebase Helper
        firebaseHelper = FirebaseHelper.getInstance();

        // Check if user is signed in
        if (firebaseHelper.getCurrentUser() == null) {
            // Not signed in, redirect to login
            Intent intent = new Intent(PreferencesActivity.this, login.class);
            startActivity(intent);
            finish();
            return;
        }

        // Find views
        save_button = findViewById(R.id.saveButton);
        musicCheckBox = findViewById(R.id.checkBox_music);
        sportsCheckBox = findViewById(R.id.checkBox_sports);
        foodCheckBox = findViewById(R.id.checkBox_food);
        healthCheckBox = findViewById(R.id.checkBox_health);
        artsCheckBox = findViewById(R.id.checkBox_arts);
        travelCheckBox = findViewById(R.id.checkBox_travel);

        // Load existing preferences
        loadPreferences();

        // Set up click listeners for checkboxes
        musicCheckBox.setOnClickListener(v -> updatePreferences());
        sportsCheckBox.setOnClickListener(v -> updatePreferences());
        foodCheckBox.setOnClickListener(v -> updatePreferences());
        healthCheckBox.setOnClickListener(v -> updatePreferences());
        artsCheckBox.setOnClickListener(v -> updatePreferences());
        travelCheckBox.setOnClickListener(v -> updatePreferences());

        // Set up save button click listener
        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                Intent intent = new Intent(PreferencesActivity.this, Dashboard.class);
                startActivity(intent);
            }
        });
    }

    private void savePreferences() {
        // Create a UserPreferences object
        UserPreferences preferences = new UserPreferences(
                musicCheckBox.isChecked(),
                sportsCheckBox.isChecked(),
                foodCheckBox.isChecked(),
                healthCheckBox.isChecked(),
                artsCheckBox.isChecked(),
                travelCheckBox.isChecked()
        );

        // Save to Firebase using the helper
        firebaseHelper.saveUserPreferences(preferences)
                .addOnSuccessListener(aVoid -> {
                    // Successfully saved
                    Toast.makeText(PreferencesActivity.this, "Preferences saved successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    // Failed to save
                    Toast.makeText(PreferencesActivity.this, "Failed to save preferences: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadPreferences() {
        firebaseHelper.getUserPreferences(new FirebaseHelper.PreferencesCallback() {
            @Override
            public void onPreferencesLoaded(UserPreferences preferences) {
                // Update UI with loaded preferences
                musicCheckBox.setChecked(preferences.isMusic());
                sportsCheckBox.setChecked(preferences.isSports());
                foodCheckBox.setChecked(preferences.isFood());
                healthCheckBox.setChecked(preferences.isHealth());
                artsCheckBox.setChecked(preferences.isArts());
                travelCheckBox.setChecked(preferences.isTravel());
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(PreferencesActivity.this,
                        "Failed to load preferences: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePreferences() {
        // This method can be used for live updates or UI changes when checkboxes are clicked
        // For now, we'll just save preferences when the Save button is pressed
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
                    mp.setLooping(true);
                    mp.setVolume(0, 0); // Mute the video
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
    }

}