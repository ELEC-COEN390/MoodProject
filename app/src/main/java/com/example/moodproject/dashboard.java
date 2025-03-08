package com.example.moodproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class dashboard extends AppCompatActivity {

    private TextView userEmail;
    private Button logoutButton;

    // Firebase Authentication
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard); // Create this layout

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        userEmail = findViewById(R.id.user_email);
        logoutButton = findViewById(R.id.logout_button);

        // Get current user
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // User is signed in
            userEmail.setText("Welcome, " + user.getEmail());
        } else {
            // No user is signed in, redirect to login
            startActivity(new Intent(dashboard.this, MainActivity.class));
            finish();
        }

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signOut();
            }
        });
    }

    private void signOut() {
        mAuth.signOut();
        // Redirect to login screen
        startActivity(new Intent(dashboard.this, MainActivity.class));
        finish();
    }
}