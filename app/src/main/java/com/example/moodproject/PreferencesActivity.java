package com.example.moodproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PreferencesActivity extends AppCompatActivity {

    private CheckBox musicCheckBox;
    private CheckBox sportsCheckBox;
    private CheckBox foodCheckBox;
    private CheckBox healthCheckBox;
    private CheckBox artsCheckBox;
    private CheckBox travelCheckBox;
    private Button save_button;

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
}