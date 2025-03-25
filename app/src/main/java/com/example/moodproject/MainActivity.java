package com.example.moodproject;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    Button start;
    VideoView videoBackground;

    // Firebase Authentication
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable full screen mode
        makeFullScreen();

        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        start = findViewById(R.id.start);
        videoBackground = findViewById(R.id.videoBackground);

        // Set up the video background
        setupVideoBackground();

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, login.class);
                startActivity(intent);
            }
        });
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

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null){
            // User is already signed in, redirect to dashboard
            startActivity(new Intent(MainActivity.this, Dashboard.class)); // Create a dashboard activity
            finish();
        }
    }

    // Optional method to sign out user
    private void signOut() {
        mAuth.signOut();
        // Update UI or redirect to login screen
    }
}