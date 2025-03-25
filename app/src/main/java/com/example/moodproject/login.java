package com.example.moodproject;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class login extends AppCompatActivity {

    private EditText email;
    private EditText password;
    private Button loginButton;
    private TextView forgotPassword;
    private TextView register;
    VideoView videoBackground;

    // Firebase Authentication
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        loginButton = findViewById(R.id.login_button);
        register = findViewById(R.id.register);
        videoBackground = findViewById(R.id.videoBackground);

        setupVideoBackground();

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        // Optional: Add a forgot password functionality
        forgotPassword = findViewById(R.id.forgot_password);
        forgotPassword.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 // Send password reset email
                 String emailAddress = email.getText().toString().trim();
                 if (!TextUtils.isEmpty(emailAddress)) {
                     sendPasswordResetEmail(emailAddress);
                 } else {
                     email.setError("Please enter your email");
                 }
             }
         });


        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(login.this, registration.class);
                startActivity(intent);
            }
        });


    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null){
            // User is already signed in, redirect to main dashboard
            startActivity(new Intent(login.this, Dashboard.class)); // Create a dashboard activity
            finish();
        }
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

    private void loginUser() {
        String emailText = email.getText().toString().trim();
        String passwordText = password.getText().toString().trim();

        // Validate inputs
        if (TextUtils.isEmpty(emailText)) {
            email.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(passwordText)) {
            password.setError("Password is required");
            return;
        }

        // Show progress (you can add a progress dialog here)

        // Sign in with email and password
        mAuth.signInWithEmailAndPassword(emailText, passwordText)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            FirebaseUser user = mAuth.getCurrentUser();
                            Toast.makeText(login.this, "Login successful",
                                    Toast.LENGTH_SHORT).show();

                            // Navigate to dashboard or main activity
                            Intent intent = new Intent(login.this, Dashboard.class); // Create a dashboard activity
                            startActivity(intent);
                            finish(); // Close this activity
                        } else {
                            // If sign in fails, display a message to the user.
                            Toast.makeText(login.this, "Authentication failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Send password reset email
    private void sendPasswordResetEmail(String emailAddress) {
        mAuth.sendPasswordResetEmail(emailAddress)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(login.this, "Password reset email sent",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(login.this, "Failed to send reset email: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }
}